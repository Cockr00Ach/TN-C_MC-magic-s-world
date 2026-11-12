package com.tnc.tnc.boss;

import net.minecraft.world.entity.LivingEntity;

/**
 * <b>黑魔法师的招式表</b> —— ★ 2026-11-07 作者：
 * "你做一个黑魔法师，通身黑暗 …… 然后把现在的暗系 boss 的法术绑到它身上" ✓。
 *
 * <h2>为什么这里一条法术都不另写</h2>
 * 作者要的是"<b>把现在的暗系 boss 的法术绑到它身上</b>"✓ —— 不是"再做一套像的"✗。
 * 所以这里<b>不复制任何数值</b>：招式表、档位、权重、连招顺序全部直接读
 * {@link TNDarkGiantSpells} ✓（{@code MOVES} 只有一份 ✓）。
 * 以后改巨兽人的招式/权重，法师<b>同步生效</b> ✓ —— 只有一个地方要改 ✓。
 *
 * <p>两边唯一不同的是<b>身体尺寸</b>：巨兽人领主肩宽 4 格、高 6 格，它的召唤物是 ×10 的庞然大物、
 * 摆在 14 格外 ✓（它的式子算出来 24+14=38 格 ✓）；黑魔法师只有 1 格宽、3.9 格高，照抄会把三个召唤物摆到
 * 15 格开外、而且每个都比法师本人还大 ✗ ⇒ 这里只覆盖
 * {@link #SUMMON_SCALE}（2.6）和 {@link #SUMMON_RADIUS}（4.5 格）两个数 ✓，法术本身一模一样 ✓。
 *
 * <p>★ 作者后续把巨兽人领主拿去做<b>战士系</b> boss 时（2026-11-07 原话：
 * "现在有的这个我想到时候拿去做战士那边的 boss"），如果那时候想让两边<b>分开</b>进化，
 * 就先把 {@code MOVES} 拆成两份再各自加招式 —— 现在共用是<b>故意</b>的 ✓（作者说"绑到它身上"✓）。
 */
public final class TNDarkMageSpells {

    /** 召唤物体型（1.0 = 原样）—— 法师召出来的暗系召唤物只放大到 2.6 倍 ✓（巨人那边是 10 ✓）。 */
    public static final double SUMMON_SCALE = 2.6D;

    /**
     * 召唤物落点半径（格）—— 公式仍是 {@code caster.getBbWidth() + 本值} ✓（**公式没改** ✓）。
     * 巨人是 14 ⇒ 24+14=38 格（和以前一模一样 ✓）；法师 4.5 ⇒ 1.1+4.5≈<b>5.6 格</b> ✓（围在他身边 ✓）。
     */
    public static final double SUMMON_RADIUS = 4.5D;

    /** 施法距离 / 索敌距离：和巨人共用同一组常量 ✓（法术的行为必须一致 ✓）。 */
    public static final double CAST_RANGE = TNDarkGiantSpells.CAST_RANGE;
    public static final double FOLLOW_RANGE = TNDarkGiantSpells.FOLLOW_RANGE;

    private TNDarkMageSpells() {
    }

    /** 招式总数（= 巨人那张表 ✓）。 */
    public static int count() {
        return TNDarkGiantSpells.count();
    }

    /** 第 index 招（越界自动夹到合法范围 ✓）。 */
    public static TNDarkGiantSpells.Move move(int index) {
        return TNDarkGiantSpells.move(index);
    }

    /** 起手要播的动作名 ✓ —— 模型必须提供这些片段（见 {@code dark_mage.animation.json} ✓）。 */
    public static String animation(TNDarkGiantSpells.Move move) {
        return TNDarkGiantSpells.animation(move);
    }

    /**
     * 选招 ✓ —— 前 3 招还是照巨人的连招顺序（{@link TNDarkGiantSpells#pick} ✓），
     * 但**每第 3 招改成黑雾**（2026-11-07 第三轮：分身是从黑雾里出来的 ✓）。
     *
     * <p>★ 为什么要在法师这边改节奏：黑雾在共用的权重表里只占 2/21 ✗，
     * 再叠上"每三招有两招是定死的"，实际大约 <b>3%</b> 的出手会是黑雾 ——
     * 平均两分钟才见一次分身 ⇒ 作者很可能以为"没做" ✗。
     * 这里只改**法师自己的出手顺序** ✓，招式表/权重/巨人那边一个字节都没动 ✓。
     */
    public static int pick(net.minecraft.util.RandomSource random, int completedCasts, int previous) {
        int mist = indexOf("devour_light");
        if (mist >= 0 && completedCasts % MIST_EVERY_NTH_CAST == MIST_AT) {
            return mist;
        }
        return TNDarkGiantSpells.pick(random, completedCasts, previous);
    }

    /** 每几招必放一次黑雾 ✓（分身跟着它出来 ✓）。 */
    public static final int MIST_EVERY_NTH_CAST = 5;
    /** 在这一组里的第几招放 ✓（第 3 招，也就是开打后大约 20 秒第一次见分身 ✓）。 */
    public static final int MIST_AT = 2;

    /** 真正放出去 ✓ —— 只把"召唤物多大 / 摆多远"换成法师的尺寸 ✓，其余全走巨人那条链 ✓。 */
    public static boolean cast(LivingEntity caster, int index) {
        return TNDarkGiantCasting.cast(caster, TNDarkGiantSpells.move(index), SUMMON_SCALE, SUMMON_RADIUS);
    }

    /** 招式名列表（日志/自检用 ✓）。 */
    public static String[] paths() {
        return TNDarkGiantSpells.paths();
    }

    /**
     * 按路径找招式索引（−1 = 表里没有这一招）✓。
     *
     * <p>分身齐射要按名字定位 {@code night_embrace} ✓ —— 写死下标会在以后给表里插一招时**静默错位** ✗。
     */
    public static int indexOf(String path) {
        String[] all = paths();
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(path)) {
                return i;
            }
        }
        return -1;
    }

    /** 供日志/自检看的一行摘要 ✓。 */
    public static String describe() {
        return "黑魔法师招式（与巨兽人领主同一套，共 " + count() + " 招）"
                + java.util.Arrays.toString(paths())
                + " | 召唤物 ×" + SUMMON_SCALE + " · 落点半径 " + SUMMON_RADIUS + " 格";
    }
}
