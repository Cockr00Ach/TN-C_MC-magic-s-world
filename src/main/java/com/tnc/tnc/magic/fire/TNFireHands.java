package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The fire-hand chain selects enemies in the aimed area and sustains its higher tiers. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireHands {
    private static final Map<String, Integer> TIERS = Map.of(
            "flame_grip", 1,
            "blaze_clutch", 2,
            "molten_crush", 3,
            "inferno_clench", 4,
            "pyre_god_hand", 5);
    private static final double RANGE = 42;
    private static final int WAVE_INTERVAL = 40;
    private static final Map<UUID, Field> FIELDS = new HashMap<>();

    private record Field(ServerLevel level, UUID caster, Vec3 center, int tier, long end, long next) {}

    private TNFireHands() {}

    public static int tierOf(ResourceLocation spellId) {
        return spellId.getNamespace().equals(TNMod.MODID) ? TIERS.getOrDefault(spellId.getPath(), 0) : 0;
    }

    static double seekRadius(int tier) { return tier == 2 ? 6 : tier >= 3 ? 12 : 0; }
    static int handsPerWave(int tier) { return tier >= 2 && tier < 5 ? 2 : 1; }
    static int maxActiveHands(int tier) { return tier >= 5 ? 2 : 4; }
    static int sustainedTicks(int tier) { return tier >= 5 ? 160 : tier == 4 ? 120 : 0; }

    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        int tier = tierOf(spellId);
        if (tier == 0) return false;
        ServerLevel level = player.serverLevel();
        LivingEntity aimed = aim(player, RANGE);
        Vec3 center = aimed == null ? aimPoint(player) : aimed.position();
        FIELDS.remove(player.getUUID());
        spawnWave(level, player, center, tier, aimed);
        int duration = sustainedTicks(tier);
        if (duration > 0) {
            long now = level.getGameTime();
            FIELDS.put(player.getUUID(), new Field(level, player.getUUID(), center, tier,
                    now + duration, now + WAVE_INTERVAL));
        }
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var iterator = FIELDS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var field = entry.getValue();
            if (field.level().getServer() != event.getServer()) continue;
            var player = field.level().getPlayerByUUID(field.caster());
            long now = field.level().getGameTime();
            if (!(player instanceof ServerPlayer caster) || !caster.isAlive() || caster.isSpectator()
                    || now >= field.end() || !field.level().hasChunkAt(BlockPos.containing(field.center()))) {
                iterator.remove();
                continue;
            }
            if (now < field.next()) continue;
            spawnWave(field.level(), caster, field.center(), field.tier(), null);
            entry.setValue(new Field(field.level(), field.caster(), field.center(), field.tier(),
                    field.end(), now + WAVE_INTERVAL));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        FIELDS.values().removeIf(field -> field.level().getServer() == event.getServer());
    }

    private static void spawnWave(ServerLevel level, ServerPlayer caster, Vec3 center, int tier, LivingEntity aimed) {
        // Include the previous cast in the active budget and avoid holding one victim with two hands.
        List<TNFlameHandEntity> active = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof TNFlameHandEntity hand && !hand.isRemoved()
                    && caster.getUUID().equals(hand.visualCaster())) active.add(hand);
        }
        int capacity = Math.max(0, maxActiveHands(tier) - active.size());
        int count = Math.min(handsPerWave(tier), capacity);
        if (count == 0) return;
        Set<UUID> held = new HashSet<>();
        for (var hand : active) {
            LivingEntity target = hand.target();
            if (target != null) held.add(target.getUUID());
        }
        List<LivingEntity> targets = new ArrayList<>();
        if (aimed != null && !held.contains(aimed.getUUID()) && TNFlameHandEntity.canDamage(caster, aimed)) targets.add(aimed);
        if (tier >= 2) {
            double radius = seekRadius(tier);
            var nearby = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    enemy -> !held.contains(enemy.getUUID()) && enemy != aimed
                            && enemy.distanceToSqr(center) <= radius * radius
                            && TNFlameHandEntity.canDamage(caster, enemy) && caster.hasLineOfSight(enemy));
            nearby.sort(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(center)));
            targets.addAll(nearby);
        }
        Vec3 across = caster.getLookAngle().cross(new Vec3(0, 1, 0)).normalize();
        if (across.lengthSqr() < .01) across = new Vec3(1, 0, 0);
        for (int i = 0; i < count; i++) {
            LivingEntity target = i < targets.size() ? targets.get(i) : null;
            Vec3 at = target == null ? center.add(across.scale((i - (count - 1) * .5) * 3.2)) : target.position();
            if (!level.hasChunkAt(BlockPos.containing(at))) continue;
            spawnHand(level, caster, target, at, tier);
        }
    }

    private static void spawnHand(ServerLevel level, ServerPlayer caster, LivingEntity target, Vec3 at, int tier) {
        boolean aerial = target != null && !target.onGround() && target.getY() > caster.getY() + 2;
        Vec3 under = aerial ? at.add(0, target.getBbHeight() * .5 - TNFlameHandEntity.gripHeight(tier), 0)
                : groundUnder(level, at);
        float heat = FireHeat.of(target);
        TNFlameHandEntity.spawn(level, caster, target, under, tier, heat, aerial);
        if (target != null) FireHeat.add(target, caster, 6 * tier);
    }

    private static LivingEntity aim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (var enemy : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.4),
                target -> TNFlameHandEntity.canDamage(player, target))) {
            var hit = enemy.getBoundingBox().inflate(.4).clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = hit.get().distanceToSqr(eye);
            if (distance < closest) {
                closest = distance;
                best = enemy;
            }
        }
        return best;
    }

    private static Vec3 aimPoint(ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        var hit = caster.level().clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
                : caster.position().add(caster.getLookAngle().scale(12));
    }

    public static Vec3 groundUnder(ServerLevel level, Vec3 at) {
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, 4, 0), at.add(0, -12, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            return new Vec3(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        }
        return at.add(0, -1.5, 0);
    }
}
