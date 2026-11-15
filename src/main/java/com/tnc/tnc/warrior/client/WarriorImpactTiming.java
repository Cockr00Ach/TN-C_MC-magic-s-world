package com.tnc.tnc.warrior.client;
/** Two visual pose ticks of impact hold, caught up by tick five. Simulation never pauses. */
public final class WarriorImpactTiming {
 private WarriorImpactTiming(){}
 public static double lag(double age){if(!Double.isFinite(age)||age<0||age>=5)return 0;return age<2?age:2*(5-age)/3;}
}
