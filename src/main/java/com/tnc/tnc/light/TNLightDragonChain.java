package com.tnc.tnc.light;

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
 * <b>光系第五条链「光龙」</b>的全部机制 ✓（作者 2026-10-02："新增加一条光龙链" ✓）。
 *
 * <p>★ 第二版（作者同日："<b>不要做成召唤物啊，我要释放出一条巨龙往前冲，触碰造成伤害</b>" ✓）：
 * 龙**不是宠物** ✗ —— 放出去就**直线往前冲**，路上碰到谁伤谁，撞墙/到点爆开消失 ✓
 * （行为在 {@link TNDragonEntity}，这里只管"放几条、多大、多快、打多疼、飞多远"✓）。
 *
 * <h2>五档（作者没给数值，这张表是我定的 ✓，要改就改这一张表）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>做什么</th></tr>
 *   <tr><td>t1</td><td>光龙吐息</td><td>向前喷一道**粗金白光柱**（复用光线链的实体光柱 ✓ 会自己索敌 ✓）</td></tr>
 *   <tr><td>t2</td><td>光龙鳞甲</td><td>自己 + 10 格内队友：减伤 50% + 速度 +20% + 光伤 +30%，20 秒</td></tr>
 *   <tr><td>t3</td><td>光龙出击</td><td>放出 **1 条**（0.30 倍 ≈ 7 格长）向前冲 ≈ 45 格，撞到 24 伤</td></tr>
 *   <tr><td>t4</td><td>光龙俯冲</td><td>**2 条**（0.40 倍 · 左右各偏 7°）向前冲 ≈ 60 格，撞到 34 伤 + 天上落一道小圣光</td></tr>
 *   <tr><td>t5</td><td>光龙降世</td><td>**3 条**（0.45 倍 · 扇形 −16/0/+16°）向前冲 ≈ 75 格，撞到 44 伤 + 大号圣光天降</td></tr>
 * </table>
 *
 * <p>放出来的位置：施法者**正前方** {@link #SPAWN_DISTANCE} 格、朝准星方向 ✓
 * （贴着放会把自己穿进去 ✗）；穿过掩体/墙会直接爆开 ✓（见 {@code TNDragonEntity.aiStep} ✓）。
 */
public final class TNLightDragonChain {

    /**
     * 一档光龙法术：法术 id / 档位 / 放几条 / 个头 / 撞伤 / 每 tick 飞多快（格）/ 飞多久（tick）/
     * 扇形散角（度）/ 名字 ✓。{@code count == 0} 表示这一档不冲（吐息 / 鳞甲 ✓）。
     */
    private record Dragon(String path, int tier, int count, double scale, double damage,
                          double speed, int ticks, double fanDeg, String name) {

        /** 大概能飞多远（格 ✓）—— 只是给提示/日志看的 ✓。 */
        double range() {
            return speed * ticks;
        }
    }

    private static final Dragon[] DRAGONS = {
            new Dragon("light_dragon_breath", 1, 0, 0.0D, 0.0D, 0.0D, 0, 0.0D, "光龙吐息"),
            new Dragon("light_dragon_scales", 2, 0, 0.0D, 0.0D, 0.0D, 0, 0.0D, "光龙鳞甲"),
            // ★ 个头是作者 2026-10-02 定的倍率：t3 = 1 倍 / t4 = 3 倍 / t5 = 10 倍 ✓
            //   （以 t3 的 0.30 为 1 倍 ⇒ 0.30 / 0.90 / 3.00 ✓）
            //   模型原长 23 格 ⇒ 三条龙分别是 **≈7 / ≈21 / ≈69 格长** ✗（t5 就是这么夸张 ✓）
            //   预览图 docs/previews/dragon_scale.png 里带了一个玩家大小的参照方块 ✓
            new Dragon("summon_light_dragon", 3, 1, 0.30D, 24.0D, 1.45D, 62, 0.0D, "光龙出击"),
            new Dragon("light_dragon_dive", 4, 2, 0.90D, 34.0D, 1.65D, 74, 7.0D, "光龙俯冲"),
            new Dragon("light_dragon_descend", 5, 3, 3.00D, 44.0D, 1.85D, 84, 16.0D, "光龙降世"),
    };

    /** 龙放主人前方多远（格 ✓）—— 它 23 格长，贴着放会把自己穿进主人身上 ✗。 */
    private static final double SPAWN_DISTANCE = 8.0D;
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
            default -> release(level, caster, dragon);
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

    /**
     * t3 / t4 / t5：**放龙** ✓ —— 在施法者正前方生成，每一条朝准星方向（多条按
     * {@link Dragon#fanDeg} 扇形散开 ✓）直线冲出去 ✓，t4/t5 再补一道圣光 ✓。
     */
    private static void release(ServerLevel level, LivingEntity caster, Dragon dragon) {
        Vec3 look = caster.getLookAngle();
        // ★ 这么大的龙不能贴脸放 ✗ —— 出生点随**体长**往后挪 ✓（t5 是 69 格长 ⇒ 放在 25 格外 ✓）
        double length = TNDragonEntity.MODEL_LENGTH_BLOCKS * dragon.scale();
        double distance = SPAWN_DISTANCE + length * 0.25D;
        Vec3 start = caster.position().add(look.scale(distance));
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        int spawned = 0;
        for (int i = 0; i < dragon.count(); i++) {
            // 扇形：i = 0 时居中；多条时左右分（t4 两条 ⇒ ∓7°，t5 三条 ⇒ -16/0/+16 ✓）
            double offset = (i - (dragon.count() - 1) / 2.0D) * dragon.fanDeg();
            Vec3 dir = rotateY(look, offset);
            // 横向也按体长错开 ✓（不然两条 21 格的龙会完全重叠 ✗）
            Vec3 at = start.add(side.scale((i - (dragon.count() - 1) / 2.0D) * length * 0.45D));
            if (spawnOne(level, caster, dragon, at, dir)) {
                spawned++;
            }
        }
        // t4/t5 的"天上那道圣光"（复用光线链的 t4 ✓ 尺寸缩小：t4 0.35 / t5 0.6 ✓）
        if (dragon.tier() >= 4) {
            TNLightBeamMechanics.onSpellCast(caster, "holy_light_descent", 1.0D,
                    dragon.tier() >= 5 ? 0.60D : 0.35D);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 1.15F);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[TN-C] §r" + dragon.name()
                    + " §7（" + spawned + " 条 · 向前冲约 " + (int) dragon.range() + " 格 · 撞到 "
                    + (int) dragon.damage() + " 伤）"), true);
        }
        LOGGER.info("TN-C/light: 光龙冲锋 {} count={} scale={} speed={} ticks={} caster={}",
                dragon.path(), spawned, dragon.scale(), dragon.speed(), dragon.ticks(),
                caster.getName().getString());
    }

    /** 把方向绕 Y 轴转 degree 度 ✓（扇形散开用 ✓）。 */
    private static Vec3 rotateY(Vec3 v, double degree) {
        double r = Math.toRadians(degree);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos).normalize();
    }

    private static boolean spawnOne(ServerLevel level, LivingEntity caster, Dragon dragon,
                                    Vec3 at, Vec3 dir) {
        TNDragonEntity entity = TNOrbEntities.LIGHT_DRAGON.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.setTier(dragon.tier());
        entity.setScale(dragon.scale());
        entity.setOwner(caster.getUUID());
        entity.moveTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
        entity.charge(dir, dragon.speed(), dragon.ticks(), dragon.damage());
        level.addFreshEntity(entity);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                at.x, at.y + 1.0D, at.z, 60, 1.2D, 1.0D, 1.2D, 0.06D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                at.x, at.y + 1.0D, at.z, 40, 1.0D, 0.8D, 1.0D, 0.10D);
        return true;
    }

    /** 供命令/自检看的摘要 ✓。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder("光龙链：");
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
