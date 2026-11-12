package com.tnc.tnc.magic.wind;

/** Shared palette of real projectile forms; the summon chain retains its existing damage. */
public final class WindArsenalRules {
    public static final int MAX_PROJECTILES=16,LIFE=32;
    private WindArsenalRules(){}
    public static int blades(int tier){return tier<=1?4:6;}
    public static int escorts(int tier){return Math.max(0,Math.min(3,tier-2));}
    public static int weapon(int tier,int slot,long cycle){return tier<=2?0:tier==3?1:tier==4?2:1+Math.floorMod(slot+(int)(cycle%3),3);}
    public static double radius(int tier){return 5.5+Math.max(1,Math.min(5,tier))*.5;}
    public static double scale(int tier){return .85+Math.max(1,Math.min(5,tier))*.12;}

    // ------------------------------------------------------------------
    //  2026-11-11 作者「风灵再改改」—— 按档位给不同的出手方式 ✓
    // ------------------------------------------------------------------

    /** 一次出手打几个目标 ✓（t1/t2 单发、t3/t4 双发、t5 三发）。 */
    public static int shots(int tier) {
        int t = Math.max(1, Math.min(5, tier));
        return t <= 2 ? 1 : t <= 4 ? 2 : 3;
    }

    /** 每几次出手来一轮「风环齐射」✓（只有 t5 有；0 = 不齐射）。 */
    public static int volleyEvery(int tier) {
        return Math.max(1, Math.min(5, tier)) >= 5 ? 5 : 0;
    }

    /** 齐射时这一只风灵打几发 ✓。 */
    public static int volleyShots(int tier) {
        return Math.max(1, Math.min(5, tier)) >= 5 ? 4 : 0;
    }

    /**
     * 单发伤害系数 ✓ —— 多发时按 **1/√n** 分摊，而不是 1/n ✗。
     *
     * <p>为什么不用 1/n：那样"从单发变三发"总伤害完全不变，玩家感觉不到升档 ✓；
     * 为什么不用 1：三发就是三倍伤害，档位之间会断层 ✗。
     * {@code 1/√n} ⇒ 三发的总伤害是单发的 <b>1.73 倍</b>，升档有感觉但不爆炸 ✓。
     */
    public static float shotDamageFactor(int tier) {
        return (float) (1.0D / Math.sqrt(Math.max(1, shots(tier))));
    }

    /** 命中炸开半径（格）✓ —— 让"打中了"有一圈风，而不是只掉一格血 ✗。 */
    public static double burstRadius(int tier) {
        return 1.0D + 0.12D * Math.max(1, Math.min(5, tier));
    }

    /** 炸开伤害占主伤害的比例 ✓（0.35 ⇒ 擦到边也有反馈，但抢不走主伤害）。 */
    public static float burstDamageShare() {
        return 0.35F;
    }

    /** 出手间隔（tick）✓（每只风灵各打各的，间隔不变）。 */
    public static int zapInterval() {
        return 10;
    }
}