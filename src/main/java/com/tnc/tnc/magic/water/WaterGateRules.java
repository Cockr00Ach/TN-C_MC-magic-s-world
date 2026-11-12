package com.tnc.tnc.magic.water;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 水球链 t4/t5「法阵群 → 齐射」的<b>纯规则</b>（无 Minecraft 运行时依赖 ⇒ 单测直接跑 ✓）。
 *
 * <h2>2026-11-09 作者要给的设计（《Fate》金闪闪那种）</h2>
 * <blockquote>
 * 直接射一个太单调了……在释放 t4 之后，玩家身边会有两个小法阵围绕，然后当玩家攻击了某个敌人之后，
 * 玩家身边就会出现几个法阵并且像光线那样快速射出我们的这个新水波，记得要先出现法阵，
 * 法阵全部出现完之后再一起发射水波；t5 就是法阵和水波变大，然后攻击结束之后几个法阵汇聚到一起
 * 射出一个巨大的水波。这个水波可以就不用原来那样持久，就跟光线一样就行了。
 * </blockquote>
 *
 * <p>所以一条时间线：
 * <pre>
 *   IDLE（20 秒）：两个小法阵绕着玩家转
 *        │ 玩家打中一个敌人（每次攻击都触发，内置 2 秒冷却）
 *        ▼
 *   OPEN：N 个法阵一个接一个亮起（每个间隔 {@link #APPEAR_STAGGER} tick）
 *        └─ 全部亮完再等 {@link #CHARGE_TICKS} tick（"先出阵、再齐射"✗ 作者特意强调 ✓）
 *        ▼
 *   FIRE：N 道水波<b>同时</b>射出（每道从一个法阵里出来，各自瞄准目标 ✓）
 *        ├─ t4：回到 IDLE
 *        └─ t5：CONVERGE → GIANT（几个法阵汇到身前一点，射出一道巨大的水波）→ IDLE
 * </pre>
 *
 * <p>为什么这些数字放在这里而不是散在实体里：**两侧都要用** ✗ ——
 * 服务端拿它决定"第几 tick 该生成哪道水波 / 打谁"，客户端拿它决定"这个法阵现在多大、朝哪" ✗。
 * 两边各写一套常数，表现和判定就会错开（本仓水法已经踩过一次"环半径没跟着板走"✗）。
 */
public final class WaterGateRules {

    private WaterGateRules() {
    }

    // ------------------------------------------------------------------
    //  时间线（tick）
    // ------------------------------------------------------------------

    /** 施法后法阵存在多久（20 秒 ✓ 作者 2026-11-09 选的）。 */
    public static final int GATE_TICKS = 20 * 20;

    /** 两次齐射之间至少隔多久（2 秒 ✓ "每次攻击都齐射"的内置冷却）。 */
    public static final int VOLLEY_COOLDOWN = 40;

    /** 相邻两个法阵亮起之间隔几 tick。 */
    public static final int APPEAR_STAGGER = 2;

    /** 单个法阵从无到亮起来要几 tick。 */
    public static final int APPEAR_TICKS = 4;

    /** 全部亮完之后再憋几 tick 才齐射（作者："法阵全部出现完之后再一起发射"✓）。 */
    public static final int CHARGE_TICKS = 6;

    /** 齐射那一下的姿态持续几 tick（此时法阵最亮、往外喷）。 */
    public static final int FIRE_TICKS = 8;

    /**
     * 齐射<b>之后</b>法阵还要残留几 tick —— 作者 2026-11-10：
     * 「射完激光之后法阵还要残留半秒」⇒ **10 tick = 0.5 秒** ✓。
     *
     * <p>为什么不能直接拿 FIRE 的最后几 tick 当残留 ✗：齐射是"阵最亮 + 往外喷"的姿态，
     * 它必须和"水波刚出去"那几 tick 对齐 ✓；残留是另一件事 ——
     * 水波飞走了、阵还挂在那里淡出 ✗（不然六座阵会"啪"一下同时消失 ✗）。
     */
    public static final int LINGER_TICKS = 10;

    /** t5：几个法阵汇聚到一起要几 tick。 */
    public static final int CONVERGE_TICKS = 10;

    /** t5：汇成一点之后，那道巨大水波持续几 tick（比普通水波长一点 ✓）。 */
    public static final int GIANT_TICKS = 14;

    /** 法阵一共几个（t4 六个 / t5 八个 ✓ 越大越多）。 */
    public static int portals(int tier) {
        return tier >= 5 ? 8 : 6;
    }

    /** 从触发到齐射一共等几 tick（OPEN 阶段长度 ✓）。 */
    public static int openTicks(int tier) {
        return portals(tier) * APPEAR_STAGGER + CHARGE_TICKS;
    }

    /** 一次完整动作（齐射 + 残留 + t5 的汇聚 + 大波）多长 —— 冷却至少要盖住它，别让两个动作叠在一起。 */
    public static int cycleTicks(int tier) {
        return openTicks(tier) + FIRE_TICKS + LINGER_TICKS
                + (giant(tier) ? CONVERGE_TICKS + GIANT_TICKS : 0);
    }

    // ------------------------------------------------------------------
    //  几何（服务端判定 / 客户端表现共用 ✓）
    // ------------------------------------------------------------------

    /**
     * 法阵围成的圆半径（格 ✓ t5 更大、阵之间要拉开）。
     *
     * <p>★ 2026-11-10 第二版：作者「法阵出现的位置可以再拉开一点，并且离敌人远点」✗ ——
     * 半径 3.2 / 4.4 → **4.2 / 5.8**，并且改成**只排 240° 的扇面**（见 {@link #portalPos}）✓。
     */
    public static double ringRadius(int tier) {
        return tier >= 5 ? 5.8D : 4.2D;
    }

    /** 扇面张角（度 ✓）：240° ⇒ 朝着敌人的那 120° 一律不放阵 ✓ */
    public static final double ARC_DEGREES = 240.0D;

    /** 法阵/环绕阵的高度（玩家脚底往上多少格 ✓ = 胸口）。 */
    public static final double RING_HEIGHT = 1.15D;

    /** 法阵本身的半径（格 ✓ 就是那圈水色阵的半径，和齐射的水波半径是两回事 ✗）。 */
    public static double sealRadius(int tier) {
        return tier >= 5 ? 0.95D : 0.62D;
    }

    /**
     * t5 收尾那个「汇聚成的大阵」的半径 —— 作者 2026-11-10：
     * <b>「t5 汇聚成的大小要是小的的 5 倍大」</b>✗ ⇒ 严格 5 倍 ✓
     * （改这一个数，渲染器和测试同时跟着走 ✓）。
     */
    public static double giantSealRadius(int tier) {
        return sealRadius(tier) * 5.0D;
    }

    /**
     * 第 {@code index} 个法阵的位置（齐射时用 ✓）。
     *
     * <p>★ 2026-11-10 作者：「法阵出现的位置可以再拉开一点，并且离敌人远点」✗：
     * <ul>
     *   <li><b>拉开</b>：半径 4.2 / 5.8 格（原来 3.2 / 4.4 ✓）；</li>
     *   <li><b>离敌人远点</b>：不再绕玩家一整圈 ✗，只排<b>背对敌人的 240° 扇面</b> ✓ ——
     *       {@code awayYaw} 是"从敌人指向玩家"的朝向 ✓，扇面以它为中心 ⇒ 朝着敌人的那 120°
     *       一个阵都没有 ✓，水波从玩家身后射出去、飞一段才打到人 ✓。</li>
     * </ul>
     */
    public static Vec3 portalPos(Vec3 anchor, int tier, int index, int count, float awayYaw) {
        int n = Math.max(1, count);
        double offset = n <= 1 ? 0.0D : -ARC_DEGREES * 0.5D + ARC_DEGREES * index / (n - 1.0D);
        Vec3 dir = Vec3.directionFromRotation(0.0F, awayYaw + (float)offset);
        return anchor.add(dir.x * ringRadius(tier), RING_HEIGHT + (index % 3) * 0.16D,
                dir.z * ringRadius(tier));
    }

    /** IDLE 阶段两个小法阵的位置（绕着玩家慢慢转 ✓）。 */
    public static Vec3 orbitPos(Vec3 base, int index, int count, double age) {
        double a = age * 0.045D + Math.PI * 2.0D * index / Math.max(1, count);
        double r = 1.15D;
        return base.add(Math.cos(a) * r, RING_HEIGHT + Math.sin(age * 0.06D + index) * 0.18D,
                Math.sin(a) * r);
    }

    /**
     * t5 汇聚的那个点 —— 在<b>玩家和敌人之间</b>（身前 2 格 ✓）；
     *
     * <p>{@code awayYaw} 是"从敌人指向玩家"的朝向 ⇒ {@code -away} 就是朝着敌人的方向 ✓
     * （法阵是从背后收拢到身前、再朝敌人打出去的 ✓）。
     */
    public static Vec3 convergePos(Vec3 anchor, float awayYaw) {
        Vec3 toward = Vec3.directionFromRotation(0.0F, awayYaw + 180.0F);
        return anchor.add(toward.x * 2.2D, RING_HEIGHT + 0.25D, toward.z * 2.2D);
    }

    /** 法阵要朝向哪：朝着目标 ✓（阵面正对敌人，水波从阵心射出去）。 */
    public static Vec3 facing(Vec3 from, Vec3 target) {
        Vec3 dir = target.subtract(from);
        return dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
    }

    /** 某个法阵"现在亮了几分"（0 = 还没出现，1 = 完全亮起 ✓）。 */
    public static float appear(int index, double phaseTick) {
        double start = index * APPEAR_STAGGER;
        return (float)Mth.clamp((phaseTick - start) / APPEAR_TICKS, 0.0D, 1.0D);
    }

    /** 齐射那一刻，法阵"闪一下"的强度（0..1 ✓）。 */
    public static float flash(double phaseTick) {
        return (float)Mth.clamp(1.0D - phaseTick / Math.max(1.0D, FIRE_TICKS), 0.0D, 1.0D);
    }

    /**
     * 齐射之后那半秒，法阵"还剩多少"（1 → 0 ✓ 作者 2026-11-10：「射完激光之后法阵还要残留半秒」）。
     *
     * <p>用一条<b>先慢后快</b>的曲线（{@code (1-t)²}）而不是直线 ✗：
     * 直线淡出看起来像"被谁按着灯关掉"，二次曲线才是"阵自己散掉"✓。
     */
    public static float linger(double phaseTick) {
        double t = Mth.clamp(phaseTick / (double)Math.max(1, LINGER_TICKS), 0.0D, 1.0D);
        double remain = 1.0D - t;
        return (float)(remain * remain);
    }

    // ------------------------------------------------------------------
    //  水波本体（伤害 / 粗细）
    // ------------------------------------------------------------------

    /** 齐射那一道道水波要不要"巨大版"（只有 t5 的收尾 ✓）。 */
    public static boolean giant(int tier) {
        return tier >= 5;
    }

    /** 一道水波多粗（格 ✓ t5 比 t4 粗一倍多，作者："t5 就是法阵和水波变大"）。 */
    public static double lanceRadius(int tier) {
        return tier >= 5 ? 2.2D : 0.9D;
    }

    /**
     * t5 收尾那道「巨大水波」多粗（格）。
     *
     * <p>★ 2026-11-10：作者要求"汇聚成的大小要是小的 5 倍大"⇒ 那道水波就从大阵里射出来，
     * 所以它<b>和汇聚成的大阵一样粗</b> ✓（小水波 2.2 格 ⇒ 巨大水波 4.75 格，
     * 正好是大阵半径 {@link #giantSealRadius} ✓ —— 水波"填满"阵口才好看 ✗）。
     */
    public static double giantRadius(int tier) {
        return giantSealRadius(tier);
    }

    /**
     * 一道水波打多少伤害（原始值 ✓，调用方再乘法术强度）。
     *
     * <p>⚠️ 这只是<b>一道</b>的伤害 ✗ —— 一次齐射是 {@link #portals(int)} 道<b>同时</b>命中 ✓，
     * 所以单道必须比原来那根"持续 12 秒的水柱"低一个量级 ✗，否则一秒就能把 Boss 打空 ✗。
     */
    public static float lanceDamage(int tier) {
        return tier >= 5 ? 7.0F : 4.0F;
    }

    /** t5 收尾的巨大水波：作者选的"比普通高一档 ≈ 2 倍"✓。 */
    public static float giantDamage(int tier) {
        return lanceDamage(tier) * 2.0F;
    }

    /** 一道水波活几 tick（作者："不用原来那样持久，就跟光线一样"✓）。 */
    public static int lanceTicks(int tier) {
        return tier >= 5 ? 12 : 10;
    }

    /** 齐射的时候每个法阵最多打多远（格 ✓ 够到准星那边的敌人就行）。 */
    public static final double LANCE_RANGE = 48.0D;
}
