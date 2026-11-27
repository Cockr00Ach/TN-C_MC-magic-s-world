package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
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

/** Server-side ignition and detonation chains. All telegraphs resolve at their displayed position. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireRework {
    private static final Set<String> SPELLS = Set.of(
            "sun_ray", "blast_ray", "fire_dragon", "flame_demon_wrath", "solar_judgment",
            "fireball", "great_fireball", "lava_fireball", "molten_skyfall", "meteor_fall");

    private enum Step { SEED, MOLTEN, MARTYR, FOOTPRINT }

    private record Pending(ServerLevel level, UUID owner, long when, Step step,
                           Vec3 at, Vec3 axis, float radius, UUID target, Set<UUID> hit) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    private record Ignition(float range, float impact, float initialHeat,
                            float burnDamage, float burnHeat, int burnSeconds) {}
    private record Burn(ServerLevel level, UUID owner, int tier, long nextPulse, int pulses, long appliedAt) {}
    private static final List<Ignition> IGNITIONS = List.of(
            new Ignition(26, 6, 16, 2.5F, 7, 5),
            new Ignition(30, 8, 18, 3, 8, 5),
            new Ignition(34, 10, 22, 4, 9, 6),
            new Ignition(38, 13, 24, 5, 10, 6),
            new Ignition(46, 17, 28, 6, 12, 7));
    private static final Map<UUID, Burn> BURNING = new HashMap<>();
    private static final ResourceLocation SPELL_FIRE = ResourceLocation.fromNamespaceAndPath("spell_power", "fire");
    private static final float[] SPEND = {.45F, .50F, .45F, .40F, .60F};


    private TNFireRework() {}

    public static boolean handles(ResourceLocation id) {
        return TNMod.MODID.equals(id.getNamespace()) && SPELLS.contains(id.getPath());
    }

    public static boolean cast(ServerPlayer player, ResourceLocation id) {
        if (!handles(id)) return false;
        switch (id.getPath()) {
            case "sun_ray" -> launch(player, 1);
            case "blast_ray" -> launch(player, 2);
            case "fire_dragon" -> launch(player, 3);
            case "flame_demon_wrath" -> launch(player, 4);
            case "solar_judgment" -> launch(player, 5);
            case "fireball" -> detonate(player, false);
            case "great_fireball" -> detonate(player, true);
            case "lava_fireball" -> seed(player);
            case "molten_skyfall" -> moltenCore(player);
            case "meteor_fall" -> martyrBurst(player);
            default -> { return false; }
        }
        return true;
    }

    private static void launch(ServerPlayer player, int tier) {
        Ignition spell = IGNITIONS.get(tier - 1);
        Vec3 eye = player.getEyePosition();
        TNFireBoltEntity.launchIgnitionCast(player, tier, spell.range);
        sound(player, eye, SoundEvents.BLAZE_SHOOT, .82F, 1.3F - tier * .12F);
    }

    static void burnOnHit(ServerPlayer player, LivingEntity target, int tier) {
        Ignition spell = IGNITIONS.get(tier - 1);
        UUID id = target.getUUID();
        Burn old = BURNING.get(id);
        long now = player.serverLevel().getGameTime();
        boolean sameLevel = old != null && old.level == player.level();
        if (sameLevel && old.owner.equals(player.getUUID()) && old.tier == tier && now - old.appliedAt < 5) return;
        damage(player, target, spell.impact);
        if (!target.isAlive()) return;
        FireHeat.add(target, player, spell.initialHeat);
        if (target.isInWaterOrBubble() || (old == null && BURNING.size() >= 512)) return;
        if (sameLevel && old.tier > tier) return;
        long next = sameLevel ? old.nextPulse : now + 20;
        BURNING.put(id, new Burn(player.serverLevel(), player.getUUID(), tier, next, spell.burnSeconds, now));
        showBurn(player, target, tier);
        emit(player, FireReworkEffects.Kind.FIRE_WAVE, target.position().add(0, .15, 0), Vec3.ZERO,
                Math.min(3.4F, Math.max(1.35F + tier * .22F, target.getBbWidth() * .7F)), tier / 5F, 15);
        sound(player, target.position(), SoundEvents.FIRECHARGE_USE, .55F, 1.22F - tier * .1F);
    }

    private static void showBurn(ServerPlayer player, LivingEntity target, int tier) {
        FireReworkEffects.emitAttached(player, target, FireReworkEffects.Kind.SCORCHED,
                Math.max(.4F, Math.min(2F, target.getBbWidth() * .64F)), tier / 5F, 25);
    }

    private static void tickBurns(TickEvent.ServerTickEvent event) {
        for (var it = BURNING.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Burn burn = entry.getValue();
            if (event.getServer().getLevel(burn.level.dimension()) != burn.level
                    || !(burn.level.getEntity(entry.getKey()) instanceof LivingEntity target)
                    || !(com.tnc.tnc.dungeon.mirror.MirrorSpellHand.player(burn.level,burn.owner) instanceof ServerPlayer caster)
                    || !caster.isAlive() || caster.isSpectator() || target.isInWaterOrBubble()
                    || !canDamage(caster, target)) {
                it.remove();
                continue;
            }
            if (burn.level.getGameTime() < burn.nextPulse) continue;
            Ignition spell = IGNITIONS.get(burn.tier - 1);
            damage(caster, target, spell.burnDamage);
            FireHeat.add(target, caster, spell.burnHeat);
            if (burn.pulses <= 1 || !target.isAlive()) it.remove();
            else {
                entry.setValue(new Burn(burn.level, burn.owner, burn.tier, burn.level.getGameTime() + 20, burn.pulses - 1, burn.appliedAt));
                showBurn(caster, target, burn.tier);
            }
        }
    }

    private static void detonate(ServerPlayer player, boolean chain) {
        LivingEntity target = aim(player, 40);
        if (target == null) return;
        Vec3 at = center(target);
        int tier = chain ? 2 : 1;
        float heat = heatFuel(target, tier);
        float damage = Math.max(8, heat * (chain ? 4.1F : 4.0F));
        if (!detonationDamage(player, target, damage)) return;
        spend(player, target, tier);
        if (target.isAlive()) target.setSecondsOnFire(4);
        burst(player, at, chain ? 3.6F : 2.5F, heat, FireReworkEffects.Kind.DETONATE);
        if (!chain || heat <= 0) return;
        for (LivingEntity other : nearby(player, at, 6, 5).stream()
                .filter(e -> e != target && visible(player, at, center(e))).limit(4).toList()) {
            emit(player, FireReworkEffects.Kind.CHAIN, at, center(other).subtract(at), .6F, heat / 100F, 18);
            if (detonationDamage(player, other, damage * .35F)) FireHeat.add(other, player, heat * .75F);
        }
    }

    private static void seed(ServerPlayer player) {
        LivingEntity target = aim(player, 26);
        Vec3 at = target == null ? groundAim(player, 24) : target.position();
        if (!queue(player, Step.SEED, at, Vec3.ZERO, 3.2F, 60, null, null)) return;
        if (target != null) FireHeat.add(target, player, 15);
        emit(player, FireReworkEffects.Kind.SEED, at, Vec3.ZERO, 3.2F, .8F, 60);
        sound(player, at, SoundEvents.FIRECHARGE_USE, .9F, .62F);
    }

    private static void moltenCore(ServerPlayer player) {
        Vec3 at = groundAim(player, 32);
        Vec3 forward = forward(player);
        Vec3 right = FireSpellRules.right(forward);
        for (int i = 0; i < 3; i++) {
            Vec3 offset = at.add(right.scale((i - 1) * 3.8)).add(forward.scale(i == 1 ? 2.1 : -1.1));
            Vec3 surface = ground(player, offset.add(0, .65, 0));
            if (surface == null || !visible(player, at.add(0, .7, 0), surface.add(0, .7, 0))) continue;
            int delay = 19 + i * 9;
            if (queue(player, Step.MOLTEN, surface, Vec3.ZERO, 3.4F, delay, null, null)) {
                emit(player, FireReworkEffects.Kind.MOLTEN, surface, upward(player, surface, 11), 3.4F, .92F, delay);
            }
        }
        sound(player, at, SoundEvents.BLAZE_SHOOT, 1F, .52F);
    }

    private static void martyrBurst(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        nearby(player, eye, 24, 16).stream()
                .filter(target -> FireHeat.of(target) > 0 && visible(player, eye, center(target)))
                .limit(12).forEach(target -> {
                    if (queue(player, Step.MARTYR, target.position(), Vec3.ZERO, 3.8F, 12, target.getUUID(), null)) {
                        emit(player, FireReworkEffects.Kind.MARTYR_DRAW, center(target), Vec3.ZERO, 3.8F,
                                FireHeat.ratio(target), 12);
                    }
                });
    }

    static void footprint(ServerPlayer player, Vec3 at, Vec3 axis) {
        if (queue(player, Step.FOOTPRINT, at, axis, 1.05F, 4, null, new HashSet<>())) {
            emit(player, FireReworkEffects.Kind.FOOTPRINT, at, axis, 1.05F, .45F, 26);
        }
    }

    private static boolean queue(ServerPlayer player, Step step, Vec3 at, Vec3 axis, float radius,
                                 int delay, UUID target, Set<UUID> hit) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(BlockPos.containing(at)) || PENDING.size() >= 512) return false;
        PENDING.add(new Pending(level, player.getUUID(), level.getGameTime() + delay, step, at, axis, radius, target, hit));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickBurns(event);
        if (PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (event.getServer().getLevel(p.level.dimension()) != p.level) return true;
            if (p.level.getGameTime() < p.when) return false;
            due.add(p);
            return true;
        });
        for (Pending p : due) {
            if (com.tnc.tnc.dungeon.mirror.MirrorSpellHand.player(p.level,p.owner) instanceof ServerPlayer caster
                    && caster.isAlive() && !caster.isSpectator()
                    && p.level.hasChunkAt(BlockPos.containing(p.at))) resolve(caster, p);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        PENDING.clear();
        BURNING.clear();
    }

    private static void resolve(ServerPlayer player, Pending p) {
        Vec3 at = p.at;
        switch (p.step) {
            case FOOTPRINT -> {
                for (LivingEntity target : area(player, at, p.radius, 1.7)) {
                    if (p.hit.add(target.getUUID())) ignite(player, target, 1, 8);
                }
                if (p.axis.lengthSqr() > 0) queue(player, Step.FOOTPRINT, at, Vec3.ZERO, p.radius, 12, null, p.hit);
            }
            case SEED, MOLTEN -> {
                boolean seed = p.step == Step.SEED;
                float peak = 0;
                for (LivingEntity target : area(player, at, p.radius, 3)) {
                    int tier = seed ? 3 : 4;
                    float heat = heatFuel(target, tier);
                    if (!detonationDamage(player, target, seed ? 20 + heat * 4.5F : 10 + heat * 3.0F)) continue;
                    spend(player, target, tier);
                    peak = Math.max(peak, heat);
                    if (target.isAlive()) target.setSecondsOnFire(4);
                }
                burst(player, at, p.radius, peak, seed ? FireReworkEffects.Kind.DETONATE : FireReworkEffects.Kind.MOLTEN_IMPACT);
            }
            case MARTYR -> {
                if (!(p.level.getEntity(p.target) instanceof LivingEntity target)
                        || !canDamage(player, target) || !visible(player, player.getEyePosition(), center(target))
                        || player.distanceToSqr(target) > 28 * 28) return;
                float heat = heatFuel(target, 5);
                if (heat <= 0) return;
                float fuel = 1 + (1 - player.getHealth() / player.getMaxHealth()) * 1.5F;
                if (!detonationDamage(player, target, heat * 4.0F * fuel)) return;
                spend(player, target, 5);
                burst(player, center(target), p.radius, heat, FireReworkEffects.Kind.MARTYR);
            }
        }
    }

    static boolean canDamage(ServerPlayer player, LivingEntity target) {
        return TNFlameHandEntity.canDamage(player, target)
                && (!(target instanceof ServerPlayer other) || player.canHarmPlayer(other));
    }

    static List<LivingEntity> area(ServerPlayer player, Vec3 at, double radius, double height) {
        return nearby(player, at, radius, height).stream()
                .filter(target -> visible(player, at.add(0, .2, 0), center(target))).toList();
    }

    private static List<LivingEntity> nearby(ServerPlayer player, Vec3 at, double radius, double height) {
        var box = FireSpellRules.uprightArea(at, radius, height);
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                target -> canDamage(player, target) && center(target).subtract(at).horizontalDistanceSqr() <= radius * radius)
                .stream().sorted(Comparator.comparingDouble(target -> center(target).distanceToSqr(at))).toList();
    }

    static boolean isFire(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.typeHolder().unwrapKey().map(key ->
                "spell_power".equals(key.location().getNamespace()) && "fire".equals(key.location().getPath())).orElse(false);
    }

    static void damage(ServerPlayer player, LivingEntity target, float amount) {
        applyFireDamage(player, target, amount);
    }

    private static boolean applyFireDamage(ServerPlayer player, LivingEntity target, float amount) {
        var fire = player.serverLevel().damageSources().onFire().typeHolder();
        return target.hurt(new DamageSource(fire, player, player), amount * FireSpellRules.power(player));
    }

    static boolean detonationDamage(ServerPlayer player, LivingEntity target, float amount) {
        // Ignition ticks and preceding hits must not consume a separate detonation's damage window.
        if (target.fireImmune() || target.hasEffect(MobEffects.FIRE_RESISTANCE)) return false;
        var level = player.serverLevel();
        var fire = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, SPELL_FIRE))
                .<net.minecraft.core.Holder<DamageType>>map(holder -> holder)
                .orElseGet(() -> level.damageSources().onFire().typeHolder());
        float health = target.getHealth();
        float absorption = target.getAbsorptionAmount();
        int previousInvulnerability = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean hit = target.hurt(new DamageSource(fire, player, player), amount * FireSpellRules.power(player))
                && (target.getHealth() < health || target.getAbsorptionAmount() < absorption || !target.isAlive());
        if (!hit) target.invulnerableTime = previousInvulnerability;
        return hit;
    }

    private static void ignite(ServerPlayer player, LivingEntity target, float damage, float heat) {
        damage(player, target, damage);
        FireHeat.add(target, player, heat);
    }

    private static float heatFuel(LivingEntity target, int tier) {
        return FireHeat.of(target) * SPEND[tier - 1];
    }

    private static void spend(ServerPlayer player, LivingEntity target, int tier) {
        FireHeatFeedback.watch(player, target);
        float fraction = SPEND[tier - 1];
        FireHeat.spend(target, fraction * FireHeat.costMultiplier(FireHeat.control(player)));
    }

    private static LivingEntity aim(ServerPlayer player, double range) {
        Vec3 from = player.getEyePosition();
        Vec3 end = clip(player, from, from.add(player.getLookAngle().scale(range)));
        LivingEntity best = null;
        double distance = from.distanceToSqr(end);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, end).inflate(1), entity -> canDamage(player, entity))) {
            var hit = target.getBoundingBox().inflate(.24).clip(from, end);
            if (hit.isEmpty()) continue;
            double candidate = from.distanceToSqr(hit.get());
            if (candidate < distance && visible(player, from, center(target))) {
                distance = candidate;
                best = target;
            }
        }
        return best;
    }

    private static Vec3 groundAim(ServerPlayer player, double range) {
        LivingEntity target = aim(player, range);
        if (target != null) {
            Vec3 surface = ground(player, target.position().add(0, .45, 0));
            return surface == null ? target.position() : surface;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 at = clip(player, eye, eye.add(player.getLookAngle().scale(range)));
        Vec3 ground = ground(player, at.subtract(player.getLookAngle().scale(.18)));
        return ground == null ? player.position() : ground;
    }

    private static Vec3 ground(ServerPlayer player, Vec3 point) {
        if (!player.serverLevel().hasChunkAt(BlockPos.containing(point))) return null;
        var hit = player.serverLevel().clip(new ClipContext(point.add(0, .8, 0), point.add(0, -18, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(0, .035, 0) : null;
    }

    private static Vec3 upward(ServerPlayer player, Vec3 at, double height) {
        Vec3 start = at.add(0, .2, 0);
        return clip(player, start, start.add(0, height, 0)).subtract(at);
    }

    private static Vec3 clip(ServerPlayer player, Vec3 from, Vec3 end) {
        return player.serverLevel().clip(new ClipContext(from, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
    }

    static boolean visible(ServerPlayer player, Vec3 from, Vec3 end) {
        return clip(player, from, end).distanceToSqr(end) < .01;
    }

    private static Vec3 forward(ServerPlayer player) {
        return Vec3.directionFromRotation(0, player.getYRot());
    }

    private static Vec3 center(LivingEntity target) {
        return target.getBoundingBox().getCenter();
    }

    static void burst(ServerPlayer player, Vec3 at, float radius, float heat, FireReworkEffects.Kind kind) {
        emit(player, kind, at, Vec3.ZERO, radius, .34F + .66F * Math.min(1, heat / 100), 28);
        sound(player, at, SoundEvents.GENERIC_EXPLODE, 1F, .8F + radius * .06F);
    }

    static void emit(ServerPlayer player, FireReworkEffects.Kind kind, Vec3 at, Vec3 direction,
                     float radius, float intensity, int lifetime) {
        FireReworkEffects.emit(player.serverLevel(), kind, at, direction, radius, intensity, lifetime, player);
    }

    private static void sound(ServerPlayer player, Vec3 at, SoundEvent event, float volume, float pitch) {
        player.serverLevel().playSound(null, at.x, at.y, at.z, event, SoundSource.PLAYERS, volume, pitch);
    }
}
