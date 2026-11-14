package com.tnc.tnc.journey;

import com.tnc.tnc.magic.Element;

/**
 * 开局配点规则：<b>元素亲和 + 战士天赋</b>（2026-11-15 作者口述）。
 *
 * <h2>规则</h2>
 * <ol>
 *   <li>元素亲和一共 <b>{@value #TOTAL}</b> 点，分摊到 7 系（每系至少 {@value #MIN}，最多 {@value #MAX}）</li>
 *   <li><b>最多只有一个元素</b>能到 {@value #MAX}（6）—— 想当单系大师就得放弃别的</li>
 *   <li>战士天赋 0~{@value #TALENT_MAX}</li>
 *   <li><b>最高的那一项亲和 + 战士天赋 ≤ {@value #BUDGET}</b>（6）：要么魔法专精，要么武艺专精</li>
 * </ol>
 *
 * <p>为什么是纯函数类：这套规则要在客户端（按钮边框、按钮能不能按）和服务端（最终裁决）
 * 各用一次，写成一份才不可能对不上 ✓
 */
public final class JourneyProfileRules {

    public static final int TOTAL = 12;
    public static final int MIN = 1;
    public static final int MAX = 6;
    public static final int TALENT_MAX = 6;
    public static final int BUDGET = 6;

    private JourneyProfileRules() {
    }

    /** 默认分配：五项 2 点、两项 1 点 = 12；天赋 2（2+2=4 ≤ 6）。 */
    public static int[] defaults() {
        return new int[]{2, 2, 2, 2, 2, 1, 1};
    }

    public static int defaultTalent() {
        return 2;
    }

    /** 亲和总和。 */
    public static int sum(int[] affinities) {
        int n = 0;
        for (int a : affinities) {
            n += a;
        }
        return n;
    }

    /** 最高的一项亲和。 */
    public static int max(int[] affinities) {
        int m = 0;
        for (int a : affinities) {
            m = Math.max(m, a);
        }
        return m;
    }

    /** 有几个元素到了 6。 */
    public static int sixes(int[] affinities) {
        int n = 0;
        for (int a : affinities) {
            if (a >= MAX) {
                n++;
            }
        }
        return n;
    }

    /**
     * @return 不合法时返回一句给玩家看的话；合法返回 {@code null}
     */
    public static String validate(int[] affinities, int talent) {
        if (affinities == null || affinities.length != Element.values().length) {
            return "亲和数据不完整";
        }
        for (int a : affinities) {
            if (a < MIN || a > MAX) {
                return "每系亲和必须在 " + MIN + "~" + MAX + " 之间";
            }
        }
        if (sum(affinities) != TOTAL) {
            return "亲和点必须刚好分配完（" + sum(affinities) + " / " + TOTAL + "）";
        }
        if (sixes(affinities) > 1) {
            return "最多只能有一个元素达到 " + MAX + " 点亲和";
        }
        if (talent < 0 || talent > TALENT_MAX) {
            return "战士天赋必须在 0~" + TALENT_MAX + " 之间";
        }
        if (max(affinities) + talent > BUDGET) {
            return "最高亲和(" + max(affinities) + ") + 战士天赋(" + talent + ") 不能超过 " + BUDGET;
        }
        return null;
    }

    /** 给界面用的一句话总结（永远显示当前状态，玩家不用自己算）。 */
    public static String summary(int[] affinities, int talent) {
        int s = sum(affinities);
        int m = max(affinities);
        return "亲和 " + s + "/" + TOTAL + " · 最高 " + m + " + 天赋 " + talent + " = " + (m + talent) + " / " + BUDGET;
    }
}
