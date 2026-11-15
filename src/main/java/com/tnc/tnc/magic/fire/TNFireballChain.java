package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ignition projectiles and the bounded, single-cast skyfire rain. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireballChain {
    static final int RAIN_TICKS = 120;
    static final int RAIN_INTERVAL = 5;
    private static final Map<UUID, Rain> RAINS = new HashMap<>();

    private static final class Rain {
        final ServerLevel level;
        final UUID owner;
        final UUID focus;
        final Vec3 center;
        final float range;
        final float radius;
        final long ends;
        long next;
        int shots;

        Rain(ServerPlayer owner, LivingEntity focus, Vec3 center, float range, float radius) {
            this.level = owner.serverLevel();
            this.owner = owner.getUUID();
            this.focus = focus == null ? null : focus.getUUID();
            this.center = center;
            this.range = range;
            this.radius = radius;
            this.next = level.getGameTime();
            this.ends = next + RAIN_TICKS;
        }
    }

    private TNFireballChain() {}

    static float radius(int tier) {
        return switch (tier) {
            case 1 -> .33F;
            case 2 -> .75F;
            case 3, 4 -> 1.65F;
            case 5 -> 1.4F;
            default -> throw new IllegalArgumentException("Invalid ignition tier " + tier);
        };
    }

    static void cast(ServerPlayer caster, int tier, float range) {
        float size = radius(tier) * com.tnc.tnc.arcana.ArcanaBeacon.scale(caster);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        if (tier == 5) {
            LivingEntity focus = acquire(caster, eye, look, range);
            Vec3 end = eye.add(look.scale(Math.min(range, 30)));
            var hit = caster.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster));
            Vec3 center = focus == null ? hit.getLocation() : center(focus);
            var rain = new Rain(caster, focus, center, range, size);
            RAINS.put(caster.getUUID(), rain);
            drop(rain, caster);
            return;
        }
        if (tier < 4) {
            TNFireBoltEntity.launchIgnition(caster, tier, eye, look, range, size);
            return;
        }

        var targets = candidates(caster, eye, look, range);
        var side = FireSpellRules.right(look);
        for (int i = 0; i < 3; i++) {
            var target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3 origin = eye.add(side.scale((i - 1) * .8)).add(0, i == 1 ? .45 : 0, 0);
            // Keep the launch position on the caster's side of any adjacent wall.
            var muzzle = caster.serverLevel().clip(new ClipContext(eye, origin, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster));
            if (muzzle.getType() != HitResult.Type.MISS) origin = eye;
            Vec3 axis = target == null
                    ? Vec3.directionFromRotation(caster.getXRot(), caster.getYRot() + (i - 1) * 12)
                    : center(target).subtract(origin).normalize();
            TNFireBoltEntity.launchIgnition(caster, tier, origin, axis, range, size, target);
        }
    }

    static LivingEntity acquire(ServerPlayer caster, Vec3 origin, Vec3 axis, double range) {
        var targets = candidates(caster, origin, axis, range);
        return targets.isEmpty() ? null : targets.get(0);
    }

    private static List<LivingEntity> candidates(ServerPlayer caster, Vec3 origin, Vec3 axis, double range) {
        return caster.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin, origin).inflate(range), target -> TNFireRework.canDamage(caster, target)
                                && center(target).distanceToSqr(origin) <= range * range
                                && center(target).subtract(origin).normalize().dot(axis) > .15
                                && visible(caster.serverLevel(), origin, center(target), caster))
                .stream().sorted(Comparator.comparingDouble(target ->
                        (1 - center(target).subtract(origin).normalize().dot(axis)) * range
                                + center(target).distanceTo(origin) * .2)).limit(12).toList();
    }

    static Vec3 center(LivingEntity entity) { return entity.position().add(0, entity.getBbHeight() * .55, 0); }

    static boolean visible(ServerLevel level, Vec3 from, Vec3 to, Entity context) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS;
    }

    private static void drop(Rain rain, ServerPlayer caster) {
        Vec3 at = rain.center;
        var followed = rain.focus == null ? null : rain.level.getEntity(rain.focus);
        if (followed instanceof LivingEntity focus && TNFireRework.canDamage(caster, focus)
                && caster.distanceToSqr(focus) <= rain.range * rain.range) at = center(focus);
        Vec3 anchor = at;
        var targets = rain.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(14),
                target -> TNFireRework.canDamage(caster, target) && center(target).distanceToSqr(anchor) <= 14 * 14)
                .stream().sorted(Comparator.comparingDouble(target -> center(target).distanceToSqr(anchor))).limit(12).toList();
        LivingEntity target = targets.isEmpty() ? null : targets.get(rain.shots % targets.size());
        if (target != null) at = center(target);

        double angle = rain.shots * 2.399963;
        double spread = target == null ? 3 + rain.shots % 5 : 2.4;
        Vec3 source = at.add(Math.cos(angle) * spread, 13 + rain.shots % 4, Math.sin(angle) * spread);
        if (rain.level.hasChunkAt(BlockPos.containing(source))) {
            if (source.y > at.y + 1.3 && rain.level.noCollision(new AABB(source, source).inflate(.2))) {
                Vec3 axis = at.subtract(source).normalize();
                float path = Math.min(rain.range, 26);
                if (target != null && !visible(rain.level, source, at, caster)) target = null;
                TNFireBoltEntity.launchIgnition(caster, 5, source, axis, path, rain.radius, target);
            }
        }
        rain.shots++;
        rain.next = rain.level.getGameTime() + RAIN_INTERVAL;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var it = RAINS.entrySet().iterator(); it.hasNext();) {
            var rain = it.next().getValue();
            if (rain.level.getServer() != event.getServer()) continue;
            var caster = rain.level.getEntity(rain.owner) instanceof ServerPlayer player ? player : null;
            if (caster == null || !caster.isAlive() || caster.isSpectator() || caster.level() != rain.level
                    || DownedCombat.isDowned(caster) || rain.level.getGameTime() >= rain.ends) {
                it.remove();
            } else if (rain.level.getGameTime() >= rain.next) {
                drop(rain, caster);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { RAINS.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        RAINS.values().removeIf(rain -> rain.level.getServer() == event.getServer());
    }
}
