package com.tnc.tnc.warrior;

/** Holding may continue indefinitely; combat power reaches its cap after three seconds. */
public final class BowChargeRules {
    private BowChargeRules() {}
    public static float power(long ticks){return Math.max(0,Math.min(60,ticks))/60f;}
    public static int arrows(WarriorMove move){return move==WarriorMove.RIGHT?3:1;}
    public static String name(WarriorMove move){return move==WarriorMove.LEFT?"贯风箭":move==WarriorMove.RIGHT?"散华箭":"震荡箭";}
}
