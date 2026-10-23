package com.tnc.tnc.magic.thunderseal;
public final class ThunderSealRules {
 public static int tier(int t){return Math.max(1,Math.min(5,t));}
 public static int count(int t){return new int[]{0,1,2,3,6,9}[tier(t)];}
 public static double radius(int t){return new double[]{0,2.5,4,6,9,13}[tier(t)];}
 public static int charge(int t){return 22+tier(t)*3;}
 public static int pulses(int t){return tier(t);}
 public static int finish(int t){return charge(t)+12*pulses(t);}
 public static int lifetime(int t){return finish(t)+24;}
 public static double height(int t){return 2.8+tier(t)*.9;}
 private ThunderSealRules(){}
}
