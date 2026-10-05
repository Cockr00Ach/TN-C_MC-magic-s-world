package com.tnc.tnc.magic.fire;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * 火球链（火系第 1 条链）的共享数值与判据。
 *
 * <h2>为什么要自己算伤害</h2>
 * 这条链已经从「引擎驱动的 PROJECTILE」改成<b>自有实体</b>（同 {@code TNWaterBoltEntity}），
 * 因为焚身要求命中时<b>精确知道「是哪个法术、打了多少」</b> —— 引擎不会把法术 id 告诉
 * {@code LivingHurtEvent}，靠时间窗猜会误伤。
 *
 * <p>代价是伤害得自己算：引擎原来是用 {@code spell_power_coefficient} × 它自己的基准，
 * 我们没有那个基准，所以这里用一个<b>可见可调的绝对基准</b>
 * （{@link #BASE_DAMAGE}）—— 想整体调强/调弱就改那一个数。
 */
public final class FireSpellRules {

    /**
     * 绝对基准伤害：<b>系数 1.0 等于多少点伤害</b>。
     *
     * <p>取 5.0 是和现有水系自研实体对齐的量级（{@code WaterSpellRules.damage(1) = 4}），
     * 略高一点是因为设计文档把「火球耗 50 魔力」定成了强度基准。
     * <b>整体手感不对就只改这一个数</b>，各法术之间的比例会跟着一起变。
     */
    public static final float BASE_DAMAGE = 5.0F;

    /** 只用于编译期兜底的无主法术 id（正常不该出现）。 */
    private static final ResourceLocation FIRE_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("spell_power", "fire");

    /**
     * 一个「火球」型法术的形态参数。
     *
     * @param coefficient 伤害系数（相对 {@link #BASE_DAMAGE}）
     * @param range       最大飞行距离（格）
     * @param radius      视觉/判定半径
     * @param launches    一次施法放出几发（连珠 = 3）
     * @param heavyScorch 命中挂的焚身是 I 级还是 II 级
     */
    public record Bolt(float coefficient, float range, float radius, int launches, boolean heavyScorch) {
    }

    /**
     * 这条链里所有「扔出去的火球」。<b>唯一的一份表</b> —— 加新火球只改这里。
     *
     * <p>t2 是**二选一分支**（大火球术 / 连珠火球术），所以这里 t2 有两行 ——
     * 现有链进度是线性的（{@code tier <= 进度+1}），两个都学得动，天然支持「自由选择」。
     */
    private static final Map<String, Bolt> BOLTS = Map.of(
            "fireball",         new Bolt(1.0F, 40.0F, 0.35F, 1, false),   // t1 冒险者
            "great_fireball",   new Bolt(2.4F, 40.0F, 0.62F, 1, false),   // t2 精英（大）
            "fireball_barrage", new Bolt(1.0F, 40.0F, 0.35F, 3, false),   // t2 精英（连珠）
            "lava_fireball",    new Bolt(3.2F, 44.0F, 0.50F, 1, false)    // t3 王
    );

    private FireSpellRules() {
    }

    /** 这个法术是不是「火球型」（由自有实体负责）。不是就返回 null。 */
    public static Bolt bolt(String path) {
        return path == null ? null : BOLTS.get(path);
    }

    public static boolean isBolt(String path) {
        return bolt(path) != null;
    }

    /** 一发火球的实际伤害 = 系数 × 绝对基准 × 施法者的火法强。 */
    public static float damage(Bolt bolt, float power) {
        return bolt == null ? 0.0F : bolt.coefficient() * BASE_DAMAGE * power;
    }

    /**
     * 只打「敌人」：不碰玩家、盟友、已驯服宠物。
     *
     * <p>判据和 {@code WaterSpellRules.enemy} 保持一致 —— 打到自己人是最容易出的纰漏。
     */
    public static boolean enemy(LivingEntity caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isAlliedTo(caster) || caster.isAlliedTo(target)
                || target instanceof Player
                || target instanceof TamableAnimal pet && pet.isTame()) {
            return false;
        }
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == caster;
    }

    /**
     * 施法者的火法强倍率。
     *
     * <p>⚠️ 和 {@code WaterSpellRules.powerMultiplier} 同一条教训：真实整合包里
     * {@code spell_power:fire} <b>默认是 0</b>。直接乘会把伤害变成 0（只剩击退），
     * 所以 0 要当「没有装备加成」= 1 倍处理。
     */
    public static float power(LivingEntity caster) {
        var attribute = ForgeRegistries.ATTRIBUTES.getValue(FIRE_ATTRIBUTE);
        if (attribute == null || caster.getAttribute(attribute) == null) {
            return 1.0F;
        }
        return powerMultiplier(caster.getAttributeValue(attribute));
    }

    /** 抽成纯函数便于自检：0/负数/NaN 一律当 1 倍。 */
    public static float powerMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0F;
        }
        return (float) Math.max(1.0D, Math.min(10000.0D, value));
    }

    /** 一发火球的出生点：施法者眼睛稍前下方，免得从头顶飞出去。 */
    public static Vec3 muzzle(LivingEntity caster, double forward) {
        Vec3 look = caster.getLookAngle();
        return caster.getEyePosition().add(look.scale(forward)).add(0.0D, -0.15D, 0.0D);
    }
}
