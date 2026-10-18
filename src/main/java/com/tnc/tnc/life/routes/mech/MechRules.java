package com.tnc.tnc.life.routes.mech;
public final class MechRules {
    public static final int CAPACITY=12000,MAX_HEAT=1000;
    public static final String[] ELEMENTS={"水","火","雷","风","土","光","暗"};
    public static final int[] COLORS={0x4ddff5,0xff7539,0xb8a1ff,0x70f0b5,0xe8bd69,0xffec9e,0xd276f3};
    public static int shotCost(boolean overdrive){return overdrive?18:12;}
    public static int shotPeriod(boolean overdrive){return overdrive?1:2;}
    public static int nextHeat(int current,boolean overdrive){return Math.min(MAX_HEAT,current+(overdrive?18:7));}
}
