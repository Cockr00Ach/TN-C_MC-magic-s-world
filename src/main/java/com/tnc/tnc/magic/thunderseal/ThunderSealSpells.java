package com.tnc.tnc.magic.thunderseal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 雷锲链的入口（{@code tnc:thunder_seal_cast_1..5} 这个 marker 效果被挂上 ⇒ 落一枚雷楔）。
 *
 * <p>★ 2026-11-05 第三批：作者「参考一下光法的施法前戏和法阵，照猫画虎一下」⇒
 * 落楔的同时铺一座<b>仪式阵</b>（{@link ThunderRitualSequence}）：
 * 主阵立刻出现，小阵按蓄雷进度一颗颗点亮，门环在中段一道道立起来，
 * 蓄雷后半段还有几道从环上往中心收的电 —— 这就是"暴风雨的前夕" ✓。
 *
 * <p>阵是<b>视觉</b>：伤害仍然只由 {@link ThunderSealEntity} 的脉冲结算 ✓（和落雷同一条约定）。
 */
@Mod.EventBusSubscriber(modid = "tnc")
public final class ThunderSealSpells {

    /** 一次性排程：到点了再铺下一颗小阵/门环/电弧 ✓ */
    private record Pending(ServerLevel level, long at, Runnable action) {
    }

    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void release(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (!event.getEntity().level().isClientSide
                && event.getEffectInstance().getEffect() instanceof ThunderSealContent.Marker marker) {
            cast(event.getEntity(), marker.tier);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        for (var it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.level().getGameTime() < pending.at()) {
                continue;
            }
            it.remove();
            try {
                pending.action().run();
            } catch (Throwable ignored) {
                // 世界卸载 / 实体没了：这一颗跳过就行，不能把服务端 tick 弄崩 ✗
            }
        }
    }

    public static ThunderSealEntity cast(LivingEntity owner, int tier) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) {
            return null;
        }
        if (level.getEntitiesOfClass(ThunderSealEntity.class, owner.getBoundingBox().inflate(160),
                seal -> seal.owner() == owner).size() >= 2) {
            return null;
        }
        var target = com.tnc.tnc.magic.wind.WindChainSpells.aim(owner, 72);
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(48));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 point = target != null ? target.position() : hit.getLocation();
        if (target == null && hit.getType() == HitResult.Type.MISS) {
            point = owner.position().add(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(16));
        }
        var pos = net.minecraft.core.BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        if (target == null) {
            var floor = level.clip(new ClipContext(point.add(0, 3, 0), point.add(0, -24, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (floor.getType() == HitResult.Type.BLOCK) {
                point = floor.getLocation();
            }
        }
        var seal = ThunderSealContent.SEAL.create(level);
        if (seal == null) {
            return null;
        }
        seal.configure(owner, tier, point, target);
        if (!level.addFreshEntity(seal)) {
            return null;
        }
        beginRitual(level, point, tier, seal);
        return seal;
    }

    /** 铺仪式阵：主阵 ＋ 逐颗点亮的小阵 ＋ 逐道立起的门环 ＋ 收束电弧 ✓ */
    private static void beginRitual(ServerLevel level, Vec3 point, int tier, ThunderSealEntity seal) {
        int theme = themeFor(tier);
        int charge = ThunderSealRules.charge(tier);
        int finish = ThunderSealRules.finish(tier);
        int life = Math.max(charge, finish) + 10;
        double radius = ThunderSealRules.radius(tier);

        // 主阵：低阶（11）/ 标准（7，带雷网）/ 高阶（12）
        int style = switch (ThunderRitualSequence.mainStyleStep(tier)) {
            case 0 -> com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING_MINOR;
            case 1 -> com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING;
            default -> com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING_GRAND;
        };
        com.tnc.tnc.magic.TnSpellMechanics.spawnMagicCircleAt(level, point, radius * 0.95D, life, style, theme);

        // 小阵：一颗一颗亮（点燃 tick 由规则算，全落在蓄雷期里）
        int satellites = ThunderRitualSequence.satelliteSeals(tier);
        double size = ThunderRitualSequence.satelliteSize(tier);
        for (int i = 0; i < satellites; i++) {
            double ang = ThunderRitualSequence.satelliteAngle(tier, i);
            double orbit = radius * ThunderRitualSequence.satelliteOrbitFraction(i);
            Vec3 at = point.add(Math.cos(ang) * orbit, 0.0D, Math.sin(ang) * orbit);
            int delay = ThunderRitualSequence.ignitionTick(tier, i, charge);
            int smallLife = Math.max(20, life - delay);
            PENDING.add(new Pending(level, level.getGameTime() + delay, () -> {
                if (!seal.isAlive()) {
                    return;
                }
                com.tnc.tnc.magic.TnSpellMechanics.spawnMagicCircleAt(level, at, size, smallLife,
                        com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING_MINOR, theme);
            }));
        }

        // 门环：t3 起，中段一道道立起来（面朝阵心）
        int gates = ThunderRitualSequence.gateRings(tier);
        for (int i = 0; i < gates; i++) {
            double ang = i * (Math.PI * 2.0D / gates) + Math.PI / gates;
            double dist = ThunderRitualSequence.gateRadius(tier);
            Vec3 at = point.add(Math.cos(ang) * dist, 1.25D, Math.sin(ang) * dist);
            float facing = (float) Math.toDegrees(Math.atan2(point.x - at.x, point.z - at.z));
            int delay = ThunderRitualSequence.gateTick(tier, i, charge);
            int gateLife = Math.max(20, life - delay);
            PENDING.add(new Pending(level, level.getGameTime() + delay, () -> {
                if (!seal.isAlive()) {
                    return;
                }
                var circle = com.tnc.tnc.magic.TNOrbEntities.MAGIC_CIRCLE.get().create(level);
                if (circle == null) {
                    return;
                }
                circle.configure(2.2D, gateLife, com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING, theme);
                circle.configure(2.2D, gateLife, com.tnc.tnc.magic.TNMagicCircleEntity.STYLE_LIGHTNING, true, facing);
                circle.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                level.addFreshEntity(circle);
            }));
        }

        // 蓄雷后半段：几道从环上往中心收的电（"要来了"的感觉 ✓）
        for (int i = 0; i < 3; i++) {
            double atProgress = 0.55D + 0.15D * i;
            int delay = Math.max(1, (int) Math.round(charge * atProgress));
            double ang = i * 2.1D + 0.7D;
            Vec3 from = point.add(Math.cos(ang) * radius * 0.9D, 0.1D, Math.sin(ang) * radius * 0.9D);
            Vec3 to = point.add(0.0D, 1.1D + i * 0.5D, 0.0D);
            PENDING.add(new Pending(level, level.getGameTime() + delay, () -> {
                if (!seal.isAlive()) {
                    return;
                }
                var arc = com.tnc.tnc.magic.TNOrbEntities.LIGHTNING_ARC.get().create(level);
                if (arc == null) {
                    return;
                }
                arc.configure(from, to, theme, 6, 0.09F, false);
                level.addFreshEntity(arc);
            }));
        }
    }

    /** 雷楔链的配色：1 金 / 2 蓝 / 3 紫 / 4 蓝 / 5 暗紫（和封印渲染器一致 ✓；0/1/2/3 = 金/蓝/紫/暗） */
    public static int themeFor(int tier) {
        return switch (ThunderSealRules.tier(tier)) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 1;
            default -> 3;
        };
    }

    @SubscribeEvent
    public static void commands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tnc")
                .then(net.minecraft.commands.Commands.literal("thunderseal")
                        .requires(source -> source.hasPermission(2))
                        .then(net.minecraft.commands.Commands.argument("tier",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> cast(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "tier")) == null
                                        ? 0 : 1))));
    }
}
