package com.tnc.tnc.dark;

import com.tnc.tnc.light.TNDragonEntity;
import com.tnc.tnc.light.TNDragonOrbitConfig;
import com.tnc.tnc.light.TNDragonOrbitMath;
import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>暗系链「暗龙」</b> ✓（作者 2026-10-02：<b>"复制一下光龙，生成一个暗龙"</b> ✓）。
 *
 * <p>它就是把 {@code light/TNLightDragonChain} 那一套**原样搬过来换皮** ✓：
 * <ul>
 *   <li>龙用的是**同一个实体类**（{@link TNDragonEntity} ✓）—— 暗龙只是**另一个实体类型**
 *       {@code tnc:dark_dragon} ＋ 另一张贴图（{@code dragon_dark_bedrock.png} ✓，
 *       见 {@code dark/client/TNDarkDragonGeoModel} ✓）；</li>
 *   <li>行为完全一致 ✓：t1 一条小龙冲出去、t2 三条小龙绕着自己转 + 鳞甲 buff、
 *       t3/t4/t5 一条/两条/三条大龙往前冲 ✓（个头倍率、速度、时长、扇形角都照抄光龙那张表 ✓）；</li>
 *   <li>只有"皮"不同：粒子换成灵魂火/黑烟（{@code entity.setDark(true)} ✓）、
 *       法阵用雷法那种线条型（{@code STYLE_STORM} ✓ 因为现在只有光和雷两种样式 ✓）、
 *       龙吟压低一档 ✓、buff 换成 {@link TNEffects#DARK_DRAGON_SCALES}（暗属性伤害 = {@code spell_power:soul} ✓）。</li>
 * </ul>
 *
 * <p>⚠️ t2 的环绕参数（个数 / 半径 / 转速 / 方向 / 环高）**和光龙共用同一个配置文件** ✓
 * （{@code config/tnc/dragon_orbit.json} ✓）—— 想给暗龙单独一套，说一声就拆开 ✓。
 *
 * <h2>五档（数值 = 光龙那张表 ✓）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>做什么</th></tr>
 *   <tr><td>t1</td><td>暗龙吐息</td><td>一条最小的龙（0.30 倍 ≈ 6.8 格）往前冲 ≈54 格，撞到 14 伤</td></tr>
 *   <tr><td>t2</td><td>暗龙鳞甲</td><td>三条小龙绕着自己转（咬到 6 伤）+ 减伤 50% / 速度 +20% / 暗伤 +30%，20 秒</td></tr>
 *   <tr><td>t3</td><td>暗龙出击</td><td>一条大龙（1.50 倍 ≈ 34 格）冲 ≈90 格，撞到 24 伤</td></tr>
 *   <tr><td>t4</td><td>暗龙俯冲</td><td>两条（3.00 倍 ≈ 68 格 · 左右各偏 7°）冲 ≈122 格，撞到 34 伤</td></tr>
 *   <tr><td>t5</td><td>暗龙降世</td><td>三条（6.00 倍 ≈ 137 格 · 扇形 ∓16°）冲 ≈155 格，撞到 44 伤</td></tr>
 * </table>
 */
public final class TNDarkDragonChain {

    /** 一档暗龙法术：法术 id / 档位 / 放几条 / 个头 / 撞伤 / 速度（格/tick）/ 时长（tick）/ 扇形角 / 名字 ✓。 */
    private record Dragon(String path, int tier, int count, double scale, double damage,
                          double speed, int ticks, double fanDeg, String name) {

        double range() {
            return speed * ticks;
        }
    }

    /**
     * 数值表 ✓ —— 和光龙那张**逐字一样** ✓（作者要的是"复制"✓；要单独调就改这里 ✓）。
     *
     * <p>★ 作者 2026-10-04："龙存在的时间太短了" ✗ ⇒ **飞行时长整体翻倍** ✓
     * （速度一个没动 ✓ ⇒ 看着飞得一样快，就是**能多看一倍时间** ✓，航程也跟着翻倍 ✓）。
     */
    private static final Dragon[] DRAGONS = {
            new Dragon("dark_dragon_breath", 1, 1, 0.30D, 14.0D, 0.24D, 450, 0.0D, "暗龙吐息"),
            new Dragon("dark_dragon_scales", 2, 3, 0.30D, 6.0D, 0.0D, 800, 0.0D, "暗龙鳞甲"),
            new Dragon("dark_dragon_charge", 3, 1, 1.50D, 24.0D, 0.29D, 620, 0.0D, "暗龙出击"),
            new Dragon("dark_dragon_dive", 4, 2, 3.00D, 34.0D, 0.33D, 740, 7.0D, "暗龙俯冲"),
            new Dragon("dark_dragon_descend", 5, 3, 6.00D, 44.0D, 0.37D, 840, 16.0D, "暗龙降世"),
    };

    /** 龙放主人前方多远（格 ✓）。 */
    private static final double SPAWN_DISTANCE = 8.0D;
    /** t2 鳞甲持续多久 / 影响半径（格 ✓）。 */
    private static final int SCALES_TICKS = 400;
    private static final double SCALES_RADIUS = 10.0D;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/dark");

    private TNDarkDragonChain() {
    }

    public static boolean isDarkDragonSpell(String path) {
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

    /** 释放时调用（入口在 {@code magic/TnSpellMechanics.onSpellCast} ✓）。 */
    public static void onSpellCast(LivingEntity caster, String path) {
        Dragon dragon = find(path);
        if (dragon == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        switch (dragon.tier()) {
            case 1 -> release(level, caster, dragon);                       // 一条小龙冲出去 ✓
            case 2 -> {                                                     // 鳞甲 + 三条小龙环绕 ✓
                scales(caster, dragon);
                orbit(level, caster, dragon);
            }
            default -> release(level, caster, dragon);
        }
    }

    /** t2 的三条小龙绕着主人转 ✓（参数与光龙共用 {@code config/tnc/dragon_orbit.json} ✓）。 */
    private static void orbit(ServerLevel level, LivingEntity caster, Dragon dragon) {
        TNDragonOrbitConfig.reload();
        int n = Math.max(1, TNDragonOrbitConfig.count);
        double radius = TNDragonOrbitMath.ringRadius(dragon.scale(), n) * TNDragonOrbitConfig.radiusFactor;
        double degPerTick = TNDragonOrbitConfig.degPerTick * TNDragonOrbitConfig.direction;
        int ticks = TNDragonOrbitConfig.ticks;
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            TNDragonEntity entity = TNOrbEntities.DARK_DRAGON.get().create(level);
            if (entity == null) {
                continue;
            }
            double angle = 360.0D * i / n;
            entity.setTier(dragon.tier());
            entity.setScale(dragon.scale());
            entity.setOwner(caster.getUUID());
            entity.setDark(true);                       // ★ 只有皮不同：灵魂火 + 黑烟 ✓
            entity.orbit(caster.getUUID(), caster.getId(), radius, degPerTick,
                    TNDragonOrbitConfig.height, ticks, dragon.damage(), angle);
            entity.placeOnRing(caster, angle);          // 立刻摆到环上 ✓（不然第一帧会闪在脚下 ✗）
            level.addFreshEntity(entity);
            spawned++;
        }
        LOGGER.info("TN-C/dark: 暗龙环绕 count={} radius={} deg/tick={} ticks={} caster={} | {}",
                spawned, String.format(java.util.Locale.ROOT, "%.3f", radius), degPerTick, ticks,
                caster.getName().getString(), TNDragonOrbitConfig.describe());
    }

    /** t2 暗龙鳞甲：自己 + 附近队友挂 {@link TNEffects#DARK_DRAGON_SCALES} ✓。 */
    private static void scales(LivingEntity caster, Dragon dragon) {
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 at = caster.position();
        if (!TNEffects.DARK_DRAGON_SCALES.isPresent()) {
            LOGGER.warn("TN-C/dark: 暗龙鳞甲 buff 没注册，{} 只做了表演", dragon.path());
            return;
        }
        int buffed = 0;
        caster.addEffect(new MobEffectInstance(TNEffects.DARK_DRAGON_SCALES.get(),
                SCALES_TICKS, 0, false, true, true));
        buffed++;
        for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(SCALES_RADIUS))) {
            if (ally == caster) {
                continue;
            }
            ally.addEffect(new MobEffectInstance(TNEffects.DARK_DRAGON_SCALES.get(),
                    SCALES_TICKS, 0, false, true, true));
            buffed++;
        }
        // 法阵：现在只有"雷法线条型"和"光耀型"两种样式 ✓ —— 暗龙用线条型（更配黑紫 ✓）
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle != null) {
            circle.configure(SCALES_RADIUS * 0.6D, 160, TNMagicCircleEntity.STYLE_STORM);
            circle.moveTo(at.x, at.y + 0.04D, at.z, 0.0F, 0.0F);
            level.addFreshEntity(circle);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                at.x, at.y + 1.0D, at.z, 50, 1.2D, 0.8D, 1.2D, 0.05D);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§5[TN-C] §r暗龙鳞甲 §7（"
                    + buffed + " 人 · 减伤 50% / 速度 +20% · " + (SCALES_TICKS / 20) + " 秒）"), true);
        }
        LOGGER.info("TN-C/dark: 暗龙鳞甲 buffed={}", buffed);
    }

    /** t1 / t3 / t4 / t5：**放龙** ✓ —— 在施法者正前方生成，一条/两条/三条朝准星方向冲出去 ✓。 */
    private static void release(ServerLevel level, LivingEntity caster, Dragon dragon) {
        Vec3 look = caster.getLookAngle();
        double length = TNDragonEntity.MODEL_LENGTH_BLOCKS * dragon.scale();
        double distance = SPAWN_DISTANCE + length * 0.25D;
        Vec3 start = caster.position().add(look.scale(distance));
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        int spawned = 0;
        for (int i = 0; i < dragon.count(); i++) {
            double offset = (i - (dragon.count() - 1) / 2.0D) * dragon.fanDeg();
            Vec3 dir = rotateY(look, offset);
            Vec3 at = start.add(side.scale((i - (dragon.count() - 1) / 2.0D) * length * 0.45D));
            if (spawnOne(level, caster, dragon, at, dir)) {
                spawned++;
            }
        }
        // 龙吟压低一档 ✓（暗龙听起来更沉 ✓）
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.72F);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§5[TN-C] §r" + dragon.name()
                    + " §7（" + spawned + " 条 · 向前冲约 " + (int) dragon.range() + " 格 · 撞到 "
                    + (int) dragon.damage() + " 伤）"), true);
        }
        LOGGER.info("TN-C/dark: 暗龙冲锋 {} count={} scale={} speed={} ticks={} caster={}",
                dragon.path(), spawned, dragon.scale(), dragon.speed(), dragon.ticks(),
                caster.getName().getString());
    }

    private static Vec3 rotateY(Vec3 v, double degree) {
        double r = Math.toRadians(degree);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos).normalize();
    }

    private static boolean spawnOne(ServerLevel level, LivingEntity caster, Dragon dragon,
                                    Vec3 at, Vec3 dir) {
        TNDragonEntity entity = TNOrbEntities.DARK_DRAGON.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.setTier(dragon.tier());
        entity.setScale(dragon.scale());
        entity.setOwner(caster.getUUID());
        entity.setDark(true);                           // ★ 灵魂火 + 黑烟 ✓
        entity.moveTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
        entity.charge(dir, dragon.speed(), dragon.ticks(), dragon.damage());
        level.addFreshEntity(entity);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                at.x, at.y + 1.0D, at.z, 60, 1.2D, 1.0D, 1.2D, 0.06D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                at.x, at.y + 1.0D, at.z, 40, 1.0D, 0.8D, 1.0D, 0.10D);
        return true;
    }

    /** 供命令/自检看的摘要 ✓。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder("暗龙链：");
        for (Dragon d : DRAGONS) {
            sb.append("\n  t").append(d.tier()).append(' ').append(d.name());
            if (d.count() > 0) {
                sb.append(" ×").append(d.count()).append(" 个头 ").append(d.scale())
                        .append(" 撞伤 ").append((int) d.damage())
                        .append(" 速度 ").append(d.speed()).append(" 飞 ").append(d.ticks())
                        .append("t（约 ").append((int) d.range()).append(" 格）");
            }
        }
        return sb.toString();
    }
}
