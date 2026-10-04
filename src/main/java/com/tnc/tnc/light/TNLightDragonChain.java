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
 * <p>★ 第二版（作者同日："<b>不要做成召唤物啊，我要释放出一条巨龙往前冲，触碰造成伤害</b>" ✓）
 * 和第三版（"<b>我要的龙不是怪，你把他怪给我删了，我要的是跟雷球一样，扔出去，一条龙冲出去</b>" ✓）：
 * 龙**既不是宠物、也不是怪** ✗ —— 它是和雷球同一类的**投射物实体**
 * （{@code extends Entity} ✗ 不是 {@code Monster} ✗、不注册属性、没有 AI ✓）。
 * 放出去就**直线往前冲**，路上碰到谁伤谁，撞墙/到点爆开消失 ✓
 * （行为在 {@link TNDragonEntity}，这里只管"放几条、多大、多快、打多疼、飞多远"✓）。
 *
 * <h2>五档（作者没给数值，这张表是我定的 ✓，要改就改这一张表）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>做什么</th></tr>
 *   <tr><td>t1</td><td>光龙吐息</td><td>龙吼 + 前冲的一道火光（**不再借用光线链的光柱** ✓，见作者 2026-10-03 的删除要求）</td></tr>
 *   <tr><td>t2</td><td>光龙鳞甲</td><td>自己 + 10 格内队友：减伤 50% + 速度 +20% + 光伤 +30%，20 秒</td></tr>
 *   <tr><td>t3</td><td>光龙出击</td><td>放出 **1 条**（×5 ≈ 34 格长）向前冲 ≈ 45 格，撞到 24 伤</td></tr>
 *   <tr><td>t4</td><td>光龙俯冲</td><td>**2 条**（×10 · 左右各偏 7°）向前冲 ≈ 60 格，撞到 34 伤</td></tr>
 *   <tr><td>t5</td><td>光龙降世</td><td>**3 条**（×20 · 扇形 −16/0/+16°）向前冲 ≈ 75 格，撞到 44 伤</td></tr>
 * </table>
 *
 * <p>★ t3/t4/t5 **只有龙** ✓ —— 原来 t4/t5 放完龙还会砸一道「圣光天降」（光线链的 t4 ✗），
 * 作者 2026-10-03："我只要龙冲出去好不好，你把其他的都给我删了" ⇒ 已删 ✓。
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
            // ★ 个头倍率（作者 2026-10-03："t3的龙放大五倍，t4的放大十倍，t5 20倍" ✓）：
            //   以基准 0.30 为 1 倍 ⇒ t3 = 0.30×5 = **1.50** / t4 = ×10 = **3.00** / t5 = ×20 = **6.00** ✓
            //   模型原长 23 格 ⇒ 三条龙分别约 **34 / 69 / 138 格长** ✗（t5 已经两个多区块长了 ✓）
            //   预览图 docs/previews/dragon_scale.png 里带了一个玩家大小的参照方块 ✓
            new Dragon("summon_light_dragon", 3, 1, 1.50D, 24.0D, 1.45D, 62, 0.0D, "光龙出击"),
            new Dragon("light_dragon_dive", 4, 2, 3.00D, 34.0D, 1.65D, 74, 7.0D, "光龙俯冲"),
            new Dragon("light_dragon_descend", 5, 3, 6.00D, 44.0D, 1.85D, 84, 16.0D, "光龙降世"),
    };

    /** 龙放主人前方多远（格 ✓）—— 它 23 格长，贴着放会把自己穿进主人身上 ✗。 */
    private static final double SPAWN_DISTANCE = 8.0D;
    /** t2 鳞甲持续多久 / 影响半径（格 ✓）。 */
    private static final int SCALES_TICKS = 400;
    private static final double SCALES_RADIUS = 10.0D;

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
     * t1 光龙吐息：**只留"龙吼 + 前冲的一道火光"** ✓ ——
     * 作者 2026-10-03："怎么光龙的魔法还会有光线的 t4 呢，我只要龙冲出去好不好，你把其他的都给我删了" ✗
     * ⇒ 原来这里直接调了光线链的「大光线」✗（那是别的链的东西 ✗），现在删掉 ✓。
     */
    private static void breath(LivingEntity caster, Dragon dragon) {
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
                    "§e[TN-C] §r光龙吐息 §7（一道火光，不再借用光线链）"), true);
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
     * {@link Dragon#fanDeg} 扇形散开 ✓）直线冲出去 ✓。
     *
     * <p>★ 作者 2026-10-03："我只要龙冲出去好不好，你把其他的都给我删了" ✗
     * ⇒ 原来 t4/t5 放完龙**还额外砸一道「圣光天降」（光线链的 t4）**✗ —— 已删除 ✓，
     * 现在这几档**只有龙** ✓。
     */
    private static void release(ServerLevel level, LivingEntity caster, Dragon dragon) {
        Vec3 look = caster.getLookAngle();
        // ★ 这么大的龙不能贴脸放 ✗ —— 出生点随**体长**往后挪 ✓（t5 是 138 格长 ⇒ 放在 40 格外 ✓）
        double length = TNDragonEntity.MODEL_LENGTH_BLOCKS * dragon.scale();
        double distance = SPAWN_DISTANCE + length * 0.25D;
        Vec3 start = caster.position().add(look.scale(distance));
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        int spawned = 0;
        for (int i = 0; i < dragon.count(); i++) {
            // 扇形：i = 0 时居中；多条时左右分（t4 两条 ⇒ ∓7°，t5 三条 ⇒ -16/0/+16 ✓）
            double offset = (i - (dragon.count() - 1) / 2.0D) * dragon.fanDeg();
            Vec3 dir = rotateY(look, offset);
            // 横向也按体长错开 ✓（不然两条 69 格的龙会完全重叠 ✗）
            Vec3 at = start.add(side.scale((i - (dragon.count() - 1) / 2.0D) * length * 0.45D));
            if (spawnOne(level, caster, dragon, at, dir)) {
                spawned++;
            }
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
