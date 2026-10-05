package com.tnc.tnc.dark;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * <b>暗系第三条链「召唤」</b>的全部机制 ✓（作者 2026-10-04："暗魔法的召唤流没实装吗" ✓）。
 *
 * <p>五个法术（{@code summon_dark / summon_elite / summon_lord / dark_king / evil_god} ✓）
 * 原来挂的是**风系占位效果** ✗（{@code tnc:wind_orb_three} 之类 ✓）—— 现在真的把东西叫出来了 ✓：
 * 召唤物是 {@link TNDarkSummonEntity}（五档 = 五个实体类型 ✓，
 * 小恶魔 / 暗卫 / 暗之统领 / 暗之国王 / 邪神 ✓）。
 *
 * <h2>数值（作者没给，这张表是我定的 ✓，一档一行 ✓）</h2>
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>召唤</th><th>个头</th><th>活多久</th></tr>
 *   <tr><td>t1</td><td>召唤</td><td>1 只小恶魔</td><td>0.9</td><td>25 秒</td></tr>
 *   <tr><td>t2</td><td>召唤精兵</td><td>2 只暗卫</td><td>1.0</td><td>30 秒</td></tr>
 *   <tr><td>t3</td><td>召唤统领</td><td>1 只暗之统领 + 2 只暗卫</td><td>1.0</td><td>40 秒</td></tr>
 *   <tr><td>t4</td><td>暗之国王</td><td>1 只暗之国王 + 2 只暗之统领</td><td>1.0</td><td>50 秒</td></tr>
 *   <tr><td>t5</td><td>邪神</td><td>1 尊邪神 + 2 只暗之国王</td><td>1.0</td><td>70 秒</td></tr>
 * </table>
 *
 * <p>高阶会**带小弟** ✓（这是"召唤流"该有的样子 ✓）；同一个人身上最多
 * {@link #CAP} 只 ✓，超了把最老的送走 ✓。
 *
 * <p>召唤位置：主人周围一圈（半径 {@link #RING} 格 ✓），并就地铺一张**暗系法阵** ✓
 * （用雷法那种线条型样式 ✓ —— 现在只有光和雷两种法阵样式 ✓）。
 */
public final class TNDarkSummonChain {

    /**
     * 一档：法术 id / 档位 / **主召唤几条** / **带几条小弟** / 小弟是哪一档 / 个头 / 时长（秒）/ 名字 ✓。
     */
    private record Summon(String path, int tier, int main, int adds, int addTier,
                          double scale, int seconds, String name) {
    }

    private static final Summon[] SUMMONS = {
            new Summon("summon_dark", 1, 1, 0, 1, 0.90D, 25, "召唤"),
            new Summon("summon_elite", 2, 2, 0, 2, 1.00D, 30, "召唤精兵"),
            new Summon("summon_lord", 3, 1, 2, 2, 1.00D, 40, "召唤统领"),
            new Summon("dark_king", 4, 1, 2, 3, 1.00D, 50, "暗之国王"),
            new Summon("evil_god", 5, 1, 2, 4, 1.00D, 70, "邪神"),
    };

    /** 一个人身上最多留几只 ✓（超了送走最早召唤的 ✓）。 */
    private static final int CAP = 6;
    /** 召唤落点的圈半径（格 ✓）。 */
    private static final double RING = 2.6D;
    /** 法阵半径 / 时长（tick ✓）。 */
    private static final double CIRCLE_RADIUS = 5.0D;
    private static final int CIRCLE_LIFE = 180;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/dark");

    private TNDarkSummonChain() {
    }

    public static boolean isDarkSummonSpell(String path) {
        return find(path) != null;
    }

    private static Summon find(String path) {
        for (Summon summon : SUMMONS) {
            if (summon.path().equals(path)) {
                return summon;
            }
        }
        return null;
    }

    /** 释放时调用（入口在 {@code magic/TnSpellMechanics.onSpellCast} ✓）。 */
    public static void onSpellCast(LivingEntity caster, String path) {
        Summon summon = find(path);
        if (summon == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        // ① 先把超过上限的（最早召唤的）送走 ✓
        List<TNDarkSummonEntity> mine = level.getEntitiesOfClass(TNDarkSummonEntity.class,
                new AABB(caster.position(), caster.position()).inflate(64.0D),
                s -> caster.getUUID().equals(s.owner()));
        int want = summon.main() + summon.adds();
        int over = mine.size() + want - CAP;
        if (over > 0) {
            mine.sort(java.util.Comparator.comparingInt(TNDarkSummonEntity::getId));
            for (int i = 0; i < over && i < mine.size(); i++) {
                mine.get(i).discard();
            }
        }

        // ② 法阵（线条型 ✓ 暗系现在没有专属样式 ✗）
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle != null) {
            circle.configure(CIRCLE_RADIUS, CIRCLE_LIFE, TNMagicCircleEntity.STYLE_STORM);
            circle.moveTo(caster.getX(), caster.getY() + 0.04D, caster.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(circle);
        }

        // ③ 点名 ✓：主召唤 + 小弟，按一圈摆开 ✓
        int spawned = 0;
        spawned += spawnGroup(level, caster, summon.tier(), summon.main(), summon.scale(),
                summon.seconds(), spawned);
        if (summon.adds() > 0) {
            spawned += spawnGroup(level, caster, summon.addTier(), summon.adds(),
                    summon.scale(), summon.seconds(), spawned);
        }

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                caster.getX(), caster.getY() + 1.0D, caster.getZ(), 45, 1.0D, 0.8D, 1.0D, 0.05D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                caster.getX(), caster.getY() + 1.0D, caster.getZ(), 30, 1.2D, 0.7D, 1.2D, 0.04D);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.6F);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§5[TN-C] §r" + summon.name()
                    + " §7（" + spawned + " 只 · " + summon.seconds() + " 秒）"), true);
        }
        LOGGER.info("TN-C/dark: 暗系召唤 {} spawned={} tier={} caster={}",
                summon.path(), spawned, summon.tier(), caster.getName().getString());
    }

    /** 放一组同一档的召唤物 ✓（角度依次错开，围着主人摆一圈 ✓）。 */
    private static int spawnGroup(ServerLevel level, LivingEntity caster, int tier, int count,
                                  double scale, int seconds, int offset) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(caster.getYRot() + 40.0F)
                    + Math.PI * 2.0D * (offset + i) / Math.max(1, CAP);
            double x = caster.getX() + Math.cos(angle) * RING;
            double z = caster.getZ() + Math.sin(angle) * RING;
            if (spawnOne(level, caster, tier, scale, seconds, x, caster.getY() + 0.9D, z)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawnOne(ServerLevel level, LivingEntity caster, int tier, double scale,
                                    int seconds, double x, double y, double z) {
        var type = switch (tier) {
            case 1 -> TNOrbEntities.DARK_IMP.get();
            case 2 -> TNOrbEntities.DARK_GUARD.get();
            case 3 -> TNOrbEntities.DARK_LORD.get();
            case 4 -> TNOrbEntities.DARK_KING.get();
            default -> TNOrbEntities.EVIL_GOD.get();
        };
        Mob mob = type.create(level);
        if (!(mob instanceof TNDarkSummonEntity entity)) {
            return false;
        }
        entity.setScale(scale);
        entity.setOwner(caster.getUUID());
        entity.setLifetime(seconds * 20);
        entity.moveTo(x, y, z, caster.getYRot(), 0.0F);
        level.addFreshEntity(entity);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                x, y + 0.6D, z, 20, 0.4D, 0.5D, 0.4D, 0.03D);
        return true;
    }

    /** 供命令/自检看的摘要 ✓。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder("暗系召唤链：");
        for (Summon s : SUMMONS) {
            sb.append("\n  t").append(s.tier()).append(' ').append(s.name())
                    .append(" ×").append(s.main());
            if (s.adds() > 0) {
                sb.append(" + t").append(s.addTier()).append(" ×").append(s.adds());
            }
            sb.append("  个头 ").append(s.scale()).append(' ').append(s.seconds()).append("秒");
        }
        return sb.toString();
    }
}
