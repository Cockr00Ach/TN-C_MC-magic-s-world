package com.tnc.tnc.light;

import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * <b>光系第五条链「光龙」</b>的全部机制 ✓（作者 2026-10-02："然后新增加一条光龙链" ✓）。
 *
 * <p>主角是那条**东方光明龙**（作者手改的 {@code dragon.geo.json} ✓ 23 格长、41 骨骼、
 * 贴图是我按他的 UV 画的"光明龙"✓、动画 {@code idle}/{@code dash} ✓）——
 * 本文只负责"什么时候召唤几条、多大、多少血伤"，龙的 AI/动画在
 * {@link TNDragonEntity}（跟主人 + 低头冲刺 ✓）。
 *
 * <h2>五档（作者没给数值，这张表是我定的 ✓，要改就改这一张表）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>做什么</th></tr>
 *   <tr><td>t1</td><td>光龙吐息</td><td>向前喷一道**粗金白光柱**（复用光线链的实体光柱 ✓ 放大尺寸 ✓）</td></tr>
 *   <tr><td>t2</td><td>光龙鳞甲</td><td>自己 + 10 格内队友：减伤 50% + 速度 +20% + 光伤 +30%，20 秒</td></tr>
 *   <tr><td>t3</td><td>召唤光龙</td><td>召唤 **1 条**（0.30 倍 ≈ 7 格长，300 血 / 冲刺 22 伤，45 秒）</td></tr>
 *   <tr><td>t4</td><td>光龙俯冲</td><td>1 条更大的（0.40 ≈ 9 格，420 血 / 32 伤，50 秒）+ 天上落一道小号圣光</td></tr>
 *   <tr><td>t5</td><td>光龙降世</td><td>**2 条**最大的（0.45 ≈ 10 格，520 血 / 42 伤，60 秒）+ 大号圣光天降</td></tr>
 * </table>
 *
 * <p>召唤位置：主人**正前方** {@link #SPAWN_DISTANCE} 格（龙很长 ✗ 直接放脚下会穿模 ✓），
 * 同档覆盖（再放一次 t3 会把上一批 t3 送走 ✓）、总数上限 {@link #CAP} 条 ✓。
 */
public final class TNLightDragonChain {

    /**
     * 一档光龙法术：法术 id / 档位 / 召唤几条 / 个头 / 血 / 冲刺伤害 / 时长（秒） / 名字 ✓。
     * {@code count == 0} 表示这一档不召唤（吐息 / 鳞甲 ✓）。
     */
    private record Dragon(String path, int tier, int count, double scale, double health,
                          double damage, int seconds, String name) {
    }

    private static final Dragon[] DRAGONS = {
            new Dragon("light_dragon_breath", 1, 0, 0.0D, 0.0D, 0.0D, 0, "光龙吐息"),
            new Dragon("light_dragon_scales", 2, 0, 0.0D, 0.0D, 0.0D, 0, "光龙鳞甲"),
            new Dragon("summon_light_dragon", 3, 1, 0.30D, 300.0D, 22.0D, 45, "召唤光龙"),
            new Dragon("light_dragon_dive", 4, 1, 0.40D, 420.0D, 32.0D, 50, "光龙俯冲"),
            new Dragon("light_dragon_descend", 5, 2, 0.45D, 520.0D, 42.0D, 60, "光龙降世"),
    };

    /** 一个人身上最多留几条龙 ✓（t5 一次两条 ⇒ 上限给 4 够用 ✓）。 */
    private static final int CAP = 4;
    /** 龙放主人前方多远（格 ✓）—— 它 23 格长，贴着放会把自己穿进主人身上 ✗。 */
    private static final double SPAWN_DISTANCE = 7.0D;
    /** t2 鳞甲持续多久 / 影响半径（格 ✓）。 */
    private static final int SCALES_TICKS = 400;
    private static final double SCALES_RADIUS = 10.0D;
    /** t1 吐息：用光线链的「大光线」放大到多粗（1.0 = 原尺寸 ✓）。 */
    private static final double BREATH_SIZE_SCALE = 1.8D;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    private TNLightDragonChain() {
    }

    public static boolean isLightDragonSpell(String path) {
        return find(path) != null;
    }

    private static Dragon find(String path) {
        for (Dragon dragon : DRAGONS) {
            if (dragon.path().equals(path)) {
                return dragon;
            }
        }
        return null;
    }

    /** 释放时调用（入口在 {@code light/TNLightChainMechanics.onSpellCast} ✓）。 */
    public static void onSpellCast(LivingEntity caster, String path) {
        Dragon dragon = find(path);
        if (dragon == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        switch (dragon.tier()) {
            case 1 -> breath(caster, dragon);
            case 2 -> scales(caster, dragon);
            default -> summon(level, caster, dragon);
        }
    }

    /**
     * t1 光龙吐息：从施法者眼睛向前一道**粗金白光柱** ✓ ——
     * 直接复用光线链的实体光柱（{@link TNLightBeamMechanics#onSpellCast} ✓ 会自己索敌 ✓），
     * 只把尺寸放大 ✓（"龙吐出来的比人射的粗"✓）。
     */
    private static void breath(LivingEntity caster, Dragon dragon) {
        TNLightBeamMechanics.onSpellCast(caster, "great_light_beam", 1.0D, BREATH_SIZE_SCALE);
        if (caster.level() instanceof ServerLevel level) {
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getLookAngle();
            for (int i = 1; i <= 6; i++) {
                Vec3 at = eye.add(look.scale(i * 1.6D));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                        at.x, at.y, at.z, 6, 0.3D, 0.3D, 0.3D, 0.02D);
            }
            level.playSound(null, eye.x, eye.y, eye.z,
                    net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.35F);
        }
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(
                    "§e[TN-C] §r光龙吐息 §7（一道粗光柱，会自己索敌）"), true);
        }
    }

    /** t2 光龙鳞甲：自己 + 附近队友挂 {@link TNEffects#LIGHT_DRAGON_SCALES} ✓。 */
    private static void scales(LivingEntity caster, Dragon dragon) {
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 at = caster.position();
        if (!TNEffects.LIGHT_DRAGON_SCALES.isPresent()) {
            LOGGER.warn("TN-C/light: 光龙鳞甲 buff 没注册，{} 只做了表演", dragon.path());
            return;
        }
        int buffed = 0;
        caster.addEffect(new MobEffectInstance(TNEffects.LIGHT_DRAGON_SCALES.get(),
                SCALES_TICKS, 0, false, true, true));
        buffed++;
        for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(SCALES_RADIUS))) {
            if (ally == caster) {
                continue;
            }
            ally.addEffect(new MobEffectInstance(TNEffects.LIGHT_DRAGON_SCALES.get(),
                    SCALES_TICKS, 0, false, true, true));
            buffed++;
        }
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle != null) {
            circle.configure(SCALES_RADIUS * 0.6D, 160, TNMagicCircleEntity.STYLE_LIGHT);
            circle.moveTo(at.x, at.y + 0.04D, at.z, 0.0F, 0.0F);
            level.addFreshEntity(circle);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                at.x, at.y + 1.0D, at.z, 50, 1.2D, 0.8D, 1.2D, 0.05D);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[TN-C] §r光龙鳞甲 §7（"
                    + buffed + " 人 · 减伤 50% / 速度 +20% · " + (SCALES_TICKS / 20) + " 秒）"), true);
        }
        LOGGER.info("TN-C/light: 光龙鳞甲 buffed={}", buffed);
    }

    /** t3 / t4 / t5：把龙放到主人正前方 ✓（t4/t5 再补一道圣光 ✓）。 */
    private static void summon(ServerLevel level, LivingEntity caster, Dragon dragon) {
        Vec3 at = caster.position();
        // 同档覆盖 ✓（再放一次 t3 先把上一批 t3 送走 —— 不然会攒成一队 ✗）
        List<TNDragonEntity> mine = level.getEntitiesOfClass(TNDragonEntity.class,
                new AABB(at, at).inflate(96.0D), d -> caster.getUUID().equals(d.owner()));
        int alive = 0;
        for (TNDragonEntity old : mine) {
            if (old.tier() == dragon.tier()) {
                old.discard();
            } else if (old.isAlive()) {
                alive++;
            }
        }
        int count = Math.min(dragon.count(), Math.max(0, CAP - alive));
        Vec3 dir = caster.getLookAngle();
        for (int i = 0; i < count; i++) {
            // 多条时左右错开一点 ✓（不然两条 23 格的龙会完全重叠 ✗）
            double side = (i - (count - 1) / 2.0D) * 6.0D;
            Vec3 offset = new Vec3(-dir.z, 0.0D, dir.x).normalize().scale(side);
            spawnOne(level, caster, dragon,
                    caster.getX() + dir.x * SPAWN_DISTANCE + offset.x,
                    caster.getY() + 2.0D + i * 0.6D,
                    caster.getZ() + dir.z * SPAWN_DISTANCE + offset.z);
        }
        // t4/t5 的"天上那道圣光"（复用光线链的 t4 ✓ 尺寸缩小：t4 0.35 / t5 0.6 ✓）
        if (dragon.tier() >= 4) {
            TNLightBeamMechanics.onSpellCast(caster, "holy_light_descent", 1.0D,
                    dragon.tier() >= 5 ? 0.60D : 0.35D);
        }
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[TN-C] §r" + dragon.name()
                    + " §7（" + count + " 条 · " + dragon.seconds() + " 秒 · "
                    + (int) dragon.health() + " 血 / 冲刺 " + (int) dragon.damage() + " 伤）"), true);
        }
        LOGGER.info("TN-C/light: 光龙 {} count={} scale={} caster={}",
                dragon.path(), count, dragon.scale(), caster.getName().getString());
    }

    /** 放下一条龙 ✓（个头 / 主人 / 寿命 / 血伤都写进去 ✓）。 */
    private static void spawnOne(ServerLevel level, LivingEntity caster, Dragon dragon,
                                 double x, double y, double z) {
        TNDragonEntity entity = TNOrbEntities.LIGHT_DRAGON.get().create(level);
        if (entity == null) {
            return;
        }
        entity.setTier(dragon.tier());
        entity.setScale(dragon.scale());
        entity.setOwner(caster.getUUID());
        entity.setLifetime(dragon.seconds() * 20);
        entity.setDashDamage(dragon.damage());
        entity.moveTo(x, y, z, caster.getYRot(), 0.0F);
        if (entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null) {
            entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    .setBaseValue(dragon.health());
            entity.setHealth((float) dragon.health());
        }
        if (entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
            entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    .setBaseValue(dragon.damage());
        }
        level.addFreshEntity(entity);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 1.0D, z, 70, 1.2D, 1.0D, 1.2D, 0.06D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                x, y + 1.0D, z, 40, 1.0D, 0.8D, 1.0D, 0.10D);
    }

    /** 供 {@code /tnc} 自检/命令看的摘要 ✓。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder("光龙链：");
        for (Dragon d : DRAGONS) {
            sb.append("\n  t").append(d.tier()).append(' ').append(d.name());
            if (d.count() > 0) {
                sb.append(" ×").append(d.count()).append(" 个头 ").append(d.scale())
                        .append(" 血 ").append((int) d.health()).append(" 伤 ").append((int) d.damage())
                        .append(' ').append(d.seconds()).append("秒");
            }
        }
        return sb.toString();
    }
}
