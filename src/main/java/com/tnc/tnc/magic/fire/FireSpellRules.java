package com.tnc.tnc.magic.fire;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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

    // ---------------- 熔岳天倾（t4）----------------

    /**
     * 熔岳天倾的火球命中后的爆炸伤害 = <b>那一发火球伤害的一半</b>（作者 2026-10-05 定）。
     *
     * <p>（原先按字面理解成"等同于灼烧伤害"= 10%，作者改成了 50% —— 一个常数的事。）
     */
    public static final float SKYFALL_BLAST_PERCENT = 0.50F;

    /** 爆炸半径（格）。 */
    public static final double SKYFALL_BLAST_RADIUS = 3.5D;

    /** 法阵持续 10 秒。 */
    public static final int SKYFALL_LIFE_TICKS = 200;

    /** 法阵每隔 10 tick（0.5 秒）砸一发下来。 */
    public static final int SKYFALL_INTERVAL_TICKS = 10;

    /** 法阵悬在多高（格）。 */
    public static final double SKYFALL_HEIGHT = 8.0D;

    /** 法阵的多大范围内找目标（格）—— 找不到就直直往下砸。 */
    public static final double SKYFALL_SEEK_RADIUS = 12.0D;

    // ---------------- 炎葬（t5）----------------

    /**
     * 炎葬给的重度灼烧的"基准伤害"。
     *
     * <p>它不是一个"命中"，所以没有命中伤害可用 —— 用系数 × {@link #BASE_DAMAGE} 算出来：
     * 6.0 × 5.0 = 30 → II 级焚身每秒 30 × 20% = <b>6.0 点</b>，持续 10 秒（合计 60）。
     * 这是这一档唯一的"自定数值"，要调手感改这里。
     */
    public static final float BURIAL_SCORCH_COEFFICIENT = 6.0F;

    /** 炎葬法阵持续 10 秒。 */
    public static final int BURIAL_LIFE_TICKS = 200;

    /** 炎葬每秒结算一次。 */
    public static final int BURIAL_TICK_INTERVAL = 20;

    /** 炎葬法阵半径（格）—— 作者：「自身周围」= 一颗烈焰灵珠的爆炸范围。 */
    public static final double BURIAL_RADIUS = 6.0D;

    /** 炎葬每秒扣<b>目标最大生命</b>的百分比（作者定：1%）。 */
    public static final float BURIAL_MAX_HEALTH_PERCENT = 0.01F;

    /** 只用于编译期兜底的无主法术 id（正常不该出现）。 */
    private static final ResourceLocation FIRE_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("spell_power", "fire");

    /**
     * 一个「火球」型法术的形态参数。
     *
     * @param coefficient 伤害系数（相对 {@link #BASE_DAMAGE}）
     * @param range       最大飞行距离（格）
     * @param radius      视觉/判定半径
     * @param launches    一次施法放出几发
     * @param heavyScorch 命中挂的焚身是 I 级还是 II 级
     * @param lavaField   命中后是否在落点留下熔岩地（见 {@link TNLavaFieldEntity}）
     * @param blastRadius 命中后的爆炸半径（0 = 不爆炸）
     */
    public record Bolt(float coefficient, float range, float radius, int launches,
                       boolean heavyScorch, boolean lavaField, double blastRadius) {
    }

    /**
     * 这条链里所有「扔出去的火球」。<b>唯一的一份表</b> —— 加新火球只改这里。
     *
     * <p>⚠️ 这里**每档只有一个**，和 {@code SpellCatalogStructureTest} 的
     * 「每条链每档恰好一个」保持一致：曾经想给 t2 加第二个选项「连珠火球术」，
     * 但同档两个会让 {@code SpellCatalog.isChainTop} 的"一边学一边进"判定算错，
     * 作者 2026-10-05 决定**只保留大火球术** ✓
     * （那套"一次放多发"的代码仍留在 {@link Bolt#launches} 与 {@code TNFireBoltEntity.cast}
     * 的循环里，将来真要加同档选项时可以直接用。）
     */
    private static final Map<String, Bolt> BOLTS = Map.of(
            "fireball",       new Bolt(1.0F, 40.0F, 0.35F, 1, false, false, 0.0D),   // t1 冒险者
            "great_fireball", new Bolt(2.4F, 40.0F, 0.62F, 1, false, false, 0.0D),   // t2 精英
            "lava_fireball",  new Bolt(3.2F, 44.0F, 0.50F, 1, false, true,  0.0D)    // t3 王：落点留熔岩地
    );

    /**
     * <b>熔岳天倾</b>砸下来的那种火球。
     *
     * <p>⚠️ 它<b>故意不在 {@link #BOLTS} 里</b>：熔岳天倾不是一个"朝准星扔火球"的法术，
     * 而是头顶法阵往下砸（见 {@code TNSkyfallEntity}）。放进 {@code BOLTS} 的话
     * 玩家直接施放它就会变成"扔出一颗大火球"，与设计不符 ✗
     */
    public static final Bolt SKYFALL_BOLT =
            new Bolt(3.4F, 64.0F, 0.50F, 1, false, true, SKYFALL_BLAST_RADIUS);

    /** 爆炸伤害 = 那一发火球伤害 × {@link #SKYFALL_BLAST_PERCENT}。 */
    public static float blastDamage(float boltDamage) {
        return boltDamage <= 0.0F ? 0.0F : boltDamage * SKYFALL_BLAST_PERCENT;
    }

    /** 炎葬的 II 级焚身该以多少为基数（系数 × 绝对基准 × 火法强）。 */
    public static float burialScorchBase(float power) {
        return BURIAL_SCORCH_COEFFICIENT * BASE_DAMAGE * power;
    }

    // ---------------- 熔岩地 ----------------

    /**
     * 熔岩地每秒烧多少 = 那一发火球的伤害 × 这个比率。
     *
     * <p>取 10% 和焚身的比率一致。作者明确要求<b>熔岩地与焚身可以同时触发</b>
     * （"此伤害与自身携带的灼烧并不冲突"）—— 所以站在熔岩地里又中了焚身，
     * 就是每秒吃两份 ✓ 这不是 bug。
     */
    public static final float LAVA_FIELD_PERCENT = 0.10F;

    /** 熔岩地持续 5 秒。 */
    public static final int LAVA_FIELD_LIFE_TICKS = 100;

    /** 熔岩地每秒结算一次（和焚身同节奏）。 */
    public static final int LAVA_FIELD_TICK_INTERVAL = 20;

    /** 熔岩地半径（格）。 */
    public static final double LAVA_FIELD_RADIUS = 3.0D;

    /** 熔岩地每秒该烧多少（绝对值）。 */
    public static float lavaFieldPerSecond(float boltDamage) {
        return boltDamage <= 0.0F ? 0.0F : boltDamage * LAVA_FIELD_PERCENT;
    }

    /**
     * 这一发的火球是不是<b>熔岩岩球</b>外观（作者 2026-10-05 给参照图定稿）：
     * <b>黑炭岩板 + 亮橙熔岩裂缝</b>，而不是彗星焰尾。
     *
     * <p>会留下熔岩地的那两档就是它：{@code lava_fireball}（t3）与
     * {@code molten_skyfall}（t4 法阵往下砸的也是熔岩火球）——
     * 「会不会留熔岩地」和「长什么样」在这里正好是同一批，所以并成一个判据，
     * 免得以后加档位时两处各改一次、改漏一处 ✗
     *
     * <p>渲染器读的是实体的<b>同步字段</b> {@code spellPath()}，所以客户端拿得到 ✓
     */
    public static boolean isLavaRock(String spellPath) {
        return "lava_fireball".equals(spellPath) || "molten_skyfall".equals(spellPath);
    }

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
     * 火球能打中谁 —— <b>所有生物</b>（作者 2026-10-05 定），唯一的例外是施法者自己。
     *
     * <h2>演进过程（两轮作者实测反馈）</h2>
     * <ol>
     *   <li>最初抄的是 {@code WaterSpellRules.enemy}：「必须是 {@code Enemy} 或正在打你」
     *       → <b>试验假人打不中</b>（假人不敌对），火球直接从它身上穿过去 ✗</li>
     *   <li>第二版改成「不是自己人就能打」（排除玩家 / 宠物 / 队友 / 自家 NPC）
     *       → 假人能打了，但作者要的是更彻底的「<b>对所有生物造成伤害</b>」</li>
     *   <li>所以现在就是<b>所有活着的生物</b>，只把<b>施法者自己</b>排除掉 —— 不再有任何白名单</li>
     * </ol>
     *
     * <h2>⚠️ 这条判据现在的实际含义（改之前先读）</h2>
     * 除了施法者自己，以下<b>全部</b>会被打中：
     * <ul>
     *   <li><b>其他玩家</b> —— 法术现在可以 PVP</li>
     *   <li><b>队友</b> —— 不再看 {@code isAlliedTo}</li>
     *   <li><b>已驯服的宠物</b> —— 自己的猫狗也会被自己的炎葬烧到</li>
     *   <li><b>自己的召唤物</b> —— 光系召唤的天使 / 光龙</li>
     *   <li>村民、动物、<b>试验假人</b>……一切活物</li>
     * </ul>
     * 这是作者明确要的语义，所以<b>故意没有再保留任何白名单</b> ——
     * 别再"顺手"把玩家/宠物/NPC 加回来，那会让试验假人又打不中。
     * 真玩下来觉得误伤自己人太难受时，在这一条里加排除条件即可（一行的事）。
     *
     * <p>唯一保留的例外是<b>施法者自己</b>：炎葬是"以自身为中心"的法阵，
     * 若连自己一起烧，一放就自焚 ✗
     * （这不代表"自焚"不能做成机制 —— 只是那该是独立设计，不该是范围伤害的副作用。）
     *
     * <p>注意：水系 {@code WaterSpellRules.enemy} <b>仍是原判据，本次没动</b> ——
     * 那是另一条链的手感，不该顺手改。
     *
     * @param caster 施法者，<b>可为 null</b>（熔岩地 / 炎葬的主人已下线或换了维度时）——
     *               这时"排除自己"自动失效，其余照旧，区域不会整个失效 ✓
     */
    public static boolean hittable(LivingEntity caster, LivingEntity target) {
        return target != null
                && target != caster
                && target.isAlive()
                && !target.isSpectator();
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

    /**
     * 射向的"右手边"单位向量 —— 连珠要在横向错开，不能叠成一根。
     *
     * <p>视线几乎垂直时必须换参考轴，否则叉乘退化成零向量、连珠会全叠在一起。
     */
    public static Vec3 right(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > 0.95D ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        return direction.cross(reference).normalize();
    }

    /** 以某点为中心的"直立圆柱"包围盒（熔岩地这类贴地区域用它选人，不用球形免得选到天上）。 */
    public static AABB uprightArea(Vec3 center, double radius, double halfHeight) {
        return new AABB(center.x - radius, center.y - halfHeight, center.z - radius,
                center.x + radius, center.y + halfHeight, center.z + radius);
    }
}
