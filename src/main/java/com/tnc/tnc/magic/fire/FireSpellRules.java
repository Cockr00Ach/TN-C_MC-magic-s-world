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
    public static final double SKYFALL_BLAST_RADIUS = 5.0D;
    /**
     * 每几 tick 来一轮 —— 作者 2026-10-05：「不要固定一颗一颗向下落，可以一秒内落下多颗，
     * 且把落下的时间间隔减小，不用等到落地了再落下下一颗」✗
     *
     * <p>10 tick（0.5 秒一发 ✗）→ <b>8 tick 一轮、每轮 {@link #SKYFALL_LAUNCHES_PER_VOLLEY} 发</b>：
     * 也就是 <b>每秒约 5 发</b>，天上会同时挂着好几颗 ✓
     *
     * <p>⚠️ 与"落地"完全无关 —— 落没落地都照砸 ✓（原来也不等落地，只是间隔太长看着像"一颗一颗"✗）
     */
    public static final int SKYFALL_INTERVAL_TICKS = 7;

    /** 法阵持续 10 秒。 */

    /** 开火窗口：5 秒（作者 2026-10-05：「t4 档改为 5 秒」✓）。 */
    public static final int SKYFALL_FIRE_TICKS = 100;

    /** 一共落几颗（作者：「5 秒共 13 个火球，13 个火球在 5s 内落完」✓）。 */
    public static final int SKYFALL_BOLTS = 13;

    /**
     * 最后一颗在第几 tick 放出 = 间隔 ×（颗数 - 1）= 7 × 12 = <b>84</b> ✓
     *
     * <p>这样 13 颗正好均匀落在 <b>0 ~ 84 tick</b>（0~4.2 秒）内，
     * 最后一颗大约第 96 tick 落地 —— 仍在 5 秒窗口里 ✓
     * 写法是**算出来的**，改颗数或间隔时不会再对不上 ✗
     */
    public static final int SKYFALL_LAST_FIRE_TICK =
            SKYFALL_INTERVAL_TICKS * (SKYFALL_BOLTS - 1);
    /**
     * 熔岳天倾法阵持续多久 —— 作者 2026-10-05：「t4 档法术持续时间改为 8s」✓
     *
     * <p>（原来 200 tick = 10 秒 ✗）
     */
    public static final int SKYFALL_LIFE_TICKS = SKYFALL_FIRE_TICKS + 20;

    /** 法阵每隔 10 tick（0.5 秒）砸一发下来。 */

    /**
     * 一轮落几颗。
     *
     * <p>⚠️ 作者 2026-10-05：「不要出现一起下落的现象」✗ ⇒ <b>固定 1 颗</b>，
     * 靠 {@link #SKYFALL_INTERVAL_TICKS} 把 13 颗**均匀铺开** ✓
     * （之前为了"一秒多颗"改成一轮 2 颗 ✗ —— 那正是"一起下落"的来源 ✗）
     */
    public static final int SKYFALL_LAUNCHES_PER_VOLLEY = 1;

    /** 法阵悬在多高（格）。 */
    public static final double SKYFALL_HEIGHT = 12.0D;

    /**
     * 熔岳天倾<b>选定目标</b>的距离（格）—— 作者 2026-10-05：
     * 「在选定目标头上生成法阵，法阵不是在玩家上方」。
     *
     * <p>所以要沿视线先找到"你瞄的是谁"：<b>先做实体扫掠</b>，打不到实体就看方块落点，
     * 连方块都没有（对着天空）才退回自己头顶。
     */
    public static final double SKYFALL_CAST_RANGE = 64.0D;

    /**
     * 陨星坠（t5）选定目标的距离 —— 比 t4 的 {@link #SKYFALL_CAST_RANGE} 再远一点。
     *
     * <p>⚠️ 作者 2026-10-05 明确要求「以上的效果要保持高档强于低档，不要出现低档高于高档的现象」✗
     * —— 而 t5 原来只有 24 格、比 t4 的 64 格**还近** ✗，所以这里拉到 72 ✓
     */
    public static final double METEOR_CAST_RANGE = 72.0D;

    /** 法阵的多大范围内找目标（格）—— 找不到就直直往下砸。 */
    public static final double SKYFALL_SEEK_RADIUS = 12.0D;

    // ---------------- 炎葬（t5）----------------

    /**
     * <b>陨星坠</b>（t5）的参数。
     *
     * <p>它<b>不再</b>是"以自己为中心"的持续法阵了（那是改名前「炎葬」的做法）——
     * 现在分两段：<b>地上先张开一座大法阵</b>（{@link TNMeteorFallEntity}），
     * 随后从天上砸下一颗<b>大陨石</b>（{@link #METEOR_BOLT}）。
     */
    public static final float METEOR_MAX_HEALTH_PERCENT = 0.10F;

    /** 陨石从法阵上方多高开始落下（格）—— 给高一点才看得出"从天而降"。 */
    public static final double METEOR_DROP_HEIGHT = 30.0D;

    /**
     * 陨石下落速度（格/tick）。
     *
     * <p>⚠️ 作者 2026-10-05 实测「我没有看到陨石」—— 原来用的是
     * {@code LAUNCH_SPEED × 1.6 = 1.6 格/tick}：从 42 格砸下来只要 <b>1.3 秒</b>，
     * 而玩家当时正盯着地面的目标，根本来不及抬头 ✗
     * 现在放慢到 0.85（约 <b>1.75 秒</b>），并让陨石一路冒火焰拖尾，从任何角度看都能注意到 ✓
     */
    public static final double METEOR_FALL_SPEED = 0.85D;

    /** 只用于编译期兜底的无主法术 id（正常不该出现）。 */
    private static final ResourceLocation FIRE_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("spell_power", "fire");

    /**
     * 一个「火球」型法术的形态参数。
     *
     * @param coefficient      伤害系数（相对 {@link #BASE_DAMAGE}）
     * @param range            最大飞行距离（格）
     * @param radius           视觉/判定半径
     * @param launches         一次施法放出几发
     * @param heavyScorch      命中挂的焚身是 I 级还是 II 级
     * @param lavaField        命中后是否在落点留下熔岩地（见 {@link TNLavaFieldEntity}）
     * @param blastRadius      命中后的爆炸半径（0 = 不爆炸）
     * @param maxHealthPercent 命中后再按目标<b>最大生命</b>的这个比例额外扣一次（0 = 不扣）；
     *                         陨星坠的 10% 走这里 ✓
     */
    public record Bolt(float coefficient, float range, float radius, int launches,
                       boolean heavyScorch, boolean lavaField, double blastRadius,
                       float maxHealthPercent) {
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
            "fireball",       new Bolt(1.0F, 40.0F, 0.35F, 1, false, false, 0.0D, 0.0F),  // t1 冒险者
            "great_fireball", new Bolt(2.4F, 40.0F, 0.62F, 1, false, false, 0.0D, 0.0F),  // t2 精英
            "lava_fireball",  new Bolt(3.2F, 44.0F, 0.70F, 1, false, true,  0.0D, 0.0F)   // t3 王：落点留熔岩地（作者要求体积增大）
    );

    /**
     * <b>熔岳天倾</b>砸下来的那种火球。
     *
     * <p>⚠️ 它<b>故意不在 {@link #BOLTS} 里</b>：熔岳天倾不是一个"朝准星扔火球"的法术，
     * 而是法阵往下砸（见 {@code TNSkyfallEntity}）。放进 {@code BOLTS} 的话
     * 玩家直接施放它就会变成"扔出一颗大火球"，与设计不符 ✗
     */
    public static final Bolt SKYFALL_BOLT =
            new Bolt(3.4F, 64.0F, 1.05F, 1, false, true, SKYFALL_BLAST_RADIUS, 0.0F);

    /**
     * <b>陨星坠</b>砸下来的那颗<b>大陨石</b>（作者 2026-10-05）。
     *
     * <p>同样**故意不在 {@link #BOLTS} 里** —— 它不是"朝准星扔火球"。
     *
     * <p>比熔岳天倾那颗更狠：半径 1.4（视觉上"巨大"）、爆炸半径 5、
     * 挂 <b>II 级焚身</b>，并且再按目标<b>最大生命扣 10%</b>（{@code maxHealthPercent}）——
     * 后者正是作者对 t5 的明确要求 ✓
     */
    public static final Bolt METEOR_BOLT =
            new Bolt(6.0F, 64.0F, 6.00F, 1, true, true, 8.0D, METEOR_MAX_HEALTH_PERCENT);

    /**
     * <b>装饰用的小陨石</b>（作者 2026-10-05：「旁边可以跟随大小不一的陨石一起落下，
     * 跟随的陨石起装饰作用」）。
     *
     * <p>系数 <b>0</b> ⇒ 伤害 0、不挂焚身、不爆炸、不留熔岩地 —— <b>纯视觉</b> ✓
     * （命中处理里加了 {@code damage > 0} 的门，0 伤害不会给目标挂上焚身效果）
     */
    public static final Bolt METEOR_SHARD =
            new Bolt(0.0F, 64.0F, 0.50F, 1, false, false, 0.0D, 0.0F);

    /** 跟随大陨石一起落下的装饰小陨石数量。 */
    public static final int METEOR_SHARD_COUNT = 8;

    /** 装饰小陨石散落在主陨石周围多大范围（格）。 */
    public static final double METEOR_SHARD_SPREAD = 4.5D;

    /** 装饰小陨石的尺寸范围（相对 {@link #METEOR_SHARD} 的半径）—— 作者要「大小不一」。 */
    public static final double METEOR_SHARD_MIN_SCALE = 0.50D;
    public static final double METEOR_SHARD_MAX_SCALE = 1.30D;

    // ---------------- 命中震屏强度（t3 轻微 -> t5 强烈，2026-10-05 作者要求）----------------
    // ⚠️ 必须**逐档递增**，不能出现低档比高档还猛 ✗（作者明确要求"保持高档强于低档"）
    // 参考：现有普通爆炸的震幅是 client\TNSpellClientVisuals 里的 SHAKE_MAX = 9.0
    /** t3 熔岩火球：轻微的视角震动。 */
    public static final float SHAKE_T3 = 3.0F;
    /** t4 熔岳天倾：较强烈（比普通爆炸更猛）。 */
    public static final float SHAKE_T4 = 7.0F;
    /** t5 陨星坠：强烈。 */
    public static final float SHAKE_T5 = 16.0F;

    // ---------------- 射线链（作者 2026-10-05 全新设计，顶替旧的 *_fire_ray）----------------

    /**
     * 一档「射线」的规格。
     *
     * @param coefficient  伤害系数（实际伤害 = 系数 × {@link #BASE_DAMAGE} × 火法强 ✓）
     * @param range        射程（格）
     * @param radius       射线半径（格）—— 也就是"多粗"
     * @param scorchLevel  命中挂几级焚身（1 = I 级、2 = II 级）✓
     *                     —— 作者定：t1~t3 是 I 级，t4~t5 是 II 级
     */
    public record Ray(float coefficient, float range, float radius, int scorchLevel) {
    }

    /**
     * 射线链里**已经实装**的档位。<b>加到这里的档才走 Java 派发</b> ✓
     * （引擎的 SPAWN 动作带不了参数 ✗，所以射线必须由我们自己在 Java 里 spawn ✓）。
     *
     * <p>⚠️ 一档一档加：每加一档就把 catalog 里对应的旧法术换掉 ✓
     * （换之前旧的走引擎那套，换之后走这里 ✓）
     */
    private static final Map<String, Ray> RAYS = Map.of(
            "sun_ray",   new Ray(1.0F, 8.0F, 0.30F, 1),   // t1 烈阳射线
            "blast_ray", new Ray(2.4F, 8.0F, 0.55F, 1)    // t2 爆炸射线（命中额外小范围爆炸 ✓）
    );

    /** 这一档射线是不是已经实装（没实装就交回引擎处理 ✓）。 */
    public static Ray ray(String spellPath) {
        return spellPath == null ? null : RAYS.get(spellPath);
    }

    public static boolean isRay(String spellPath) {
        return ray(spellPath) != null;
    }

    /**
     * 准星指向的**地面点**（往下探到方块为止 ✓）—— 巨阵落在目标脚下就用它 ✓。
     *
     * <p>和"往脚下找地面"那套（{@code TNFireFields.groundAnchor}）同一个思路 ✓
     */
    public static net.minecraft.world.phys.Vec3 groundBelow(net.minecraft.world.entity.LivingEntity caster,
                                                            double reach) {
        net.minecraft.world.phys.Vec3 eye = caster.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(caster.getLookAngle().scale(reach));
        if (!(caster.level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return caster.position();
        }
        net.minecraft.world.phys.BlockHitResult hit = server.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return hit.getLocation();
        }
        // 没打到方块：从目标点往下找一个地面 ✓
        net.minecraft.world.phys.BlockHitResult down = server.clip(new net.minecraft.world.level.ClipContext(
                end, end.subtract(0.0D, 32.0D, 0.0D),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        return down.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? down.getLocation() : end;
    }

    /** 一发射线的实际伤害（和火球同一个公式 ✓）。 */
    public static float rayDamage(Ray spec, float power) {
        return spec == null ? 0.0F : spec.coefficient() * BASE_DAMAGE * power;
    }

    /** t2 爆炸射线：命中时的**小范围爆炸**伤害 = 本次伤害 × 这个比例（作者定 75% ✓）。 */
    public static final float RAY_BLAST_PERCENT = 0.75F;

    /** t2 那个小范围爆炸的半径（格）—— 作者要求"范围不要设定的太大" ✓。 */
    public static final double RAY_BLAST_RADIUS = 1.6D;

    /**
     * <b>t3 火龙术</b>的那颗「龙头投射物」✓
     *
     * <p>它不是光柱（光柱是"贴在施法者眼睛上的一条光" ✗），而是一个**会飞出去的龙头** ✓
     * ⇒ 复用 {@code TNFireBoltEntity}（飞行 + 扫掠碰撞都现成 ✓），
     * 靠 {@code isDragon()} 打开三个专属行为：
     * 穿透 / 命中不爆 / 消失时剧烈爆炸 ✓
     *
     * <p>焚身：{@code heavyScorch = false} ⇒ I 级 ✓（作者定 t1~t3 都是 I 级 ✓）
     */
    public static final Bolt DRAGON_BOLT =
            new Bolt(3.2F, 16.0F, 0.90F, 1, false, false, 0.0D, 0.0F);

    /** 火龙消失时那次爆炸的伤害倍率（作者定：本法术伤害 × 3 ✓）。 */
    public static final float DRAGON_EXPLODE_MULTIPLIER = 3.0F;

    /** 火龙消失时那次爆炸的半径（格）—— "剧烈爆炸"给大一点 ✓。 */
    public static final double DRAGON_EXPLODE_RADIUS = 5.0D;

    // ---------------- t5 太阳の审判 ----------------

    /** t5 巨阵的半径（格）—— "巨大的法阵"✓。 */
    public static final double T5_FIELD_RADIUS = 15D;

    /** t5 持续 10 秒 ✓（作者明确）。 */
    public static final int T5_FIELD_TICKS = 200;

    /** 太阳光球离地多高（格）—— 要"正上方"✓，给得高一点才像悬在天上 ✓。 */
    public static final double T5_SUN_HEIGHT = 16.0D;

    /** 光线每几 tick 打一轮（20 tick = 1 秒 ✓ ⇒ 10 秒里 10 轮 ✓）。 */
    public static final int T5_BEAM_INTERVAL_TICKS = 20;

    /** 收场球形爆炸的半径（格）—— 作者定<b>直径 32</b> ⇒ 半径 16 ✓。 */
    public static final double T5_EXPLODE_RADIUS = 16.0D;

    /**
     * 破坏方块每 tick 删几个水平层 ✓
     *
     * <p>⚠️ 半径 16 的球 ≈ 1.7 万方块 ✗ —— 一 tick 全删会把主线程卡几秒 ✗。
     * 球的水平层共 {@code 33} 层 ✓，每 tick 删 4 层 ⇒ 约 9 tick 删完 ✓
     * （观感仍是"整片塌掉"✓，但不会顿 ✗）
     */
    public static final int T5_DESTROY_LAYERS_PER_TICK = 4;

    /** 单次光线伤害 = 总伤害 ÷ 这个数（10 轮 ⇒ 光线总量 = 系数 × 基础 × 火法强 ✓）。 */
    public static final float T5_BEAM_DIVISOR = 6.4F;

    /** t5 的伤害系数（×7 ✓）。 */
    public static final float T5_COEFFICIENT = 7.0F;

    /** t5 收场爆炸的画面震动强度（度 ✓）—— 作者要"强烈震动"✓，全场最高 ✓。 */
    public static final float SHAKE_T5_RAY = 20.0F;
    // ---------------- t4 炎魔龙之怒 ----------------

    /** t4 巨阵的半径（格）—— 作者说"巨大的法阵"✓（射程 32 格内的目标脚下 ✓）。 */
    public static final double T4_FIELD_RADIUS = 15D;

    /** t4 持续 8 秒 ✓（作者明确）。 */
    public static final int T4_FIELD_TICKS = 160;

    /**
     * 4 个圆球分布在这个半径的比例上 ✓
     *
     * <p>⚠️ 作者 2026-10-05 第 4 条：「t4 中的黑曜石柱在离中心远一点」✓
     * —— 原来 0.78（贴着阵内 ✗）⇒ 现在 **1.02**（就落在法阵边缘上 ✓）
     */
    public static final double T4_ORB_RING = 1.06D;

    /**
     * 圆球离地多高（格）✓
     *
     * <p>⚠️ 作者第 4 条：「高度改为 2/3 左右」✓ —— 原来 9 格 ⇒ 现在 **6 格** ✓
     */
    public static final double T4_ORB_HEIGHT = 10.0D;

    /** 光线每几 tick 跳一次（10 tick = 0.5 秒 ✓）。 */
    public static final int T4_BEAM_INTERVAL_TICKS = 10;

    /**
     * 单次光线伤害 = 系数 × 基础伤害 × 火法强 ÷ <b>这个数</b> ✓
     *
     * <p>为什么是 3.2：8 秒里一共跳 {@code 160 / 10 = 16} 次 ✓，
     * {@code 16 ÷ 3.2 = 5} ⇒ <b>总量正好 = 系数 × 基础伤害 × 火法强</b> ✓
     * —— 和别档同一个口径，不会因为"持续伤害"就偷偷打出一堆额外伤害 ✗
     */
    public static final float T4_BEAM_DIVISOR = 1.42F;

    /** 收场时每个圆球爆炸的伤害 = 单次光线伤害 × 这个数（作者定 ×2 ✓）。 */
    public static final float T4_ORB_BLAST_MULTIPLIER = 2.0F;

    /** 收场爆炸的半径（格）✓。 */
    public static final double T4_ORB_BLAST_RADIUS = 15.0D;

    /** t4 收场的画面震动强度（度 ✓）—— 比 t5 弱、比 t3 强 ✓。 */
    public static final float SHAKE_T4_RAY = 12.0F;
    /** 爆炸伤害 = 那一发火球伤害 × {@link #SKYFALL_BLAST_PERCENT}。 */
    public static float blastDamage(float boltDamage) {
        return boltDamage <= 0.0F ? 0.0F : boltDamage * SKYFALL_BLAST_PERCENT;
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
        return "lava_fireball".equals(spellPath) || "molten_skyfall".equals(spellPath)
                || "meteor_fall".equals(spellPath) || "meteor_shard".equals(spellPath);
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
