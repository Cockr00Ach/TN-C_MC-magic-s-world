package com.tnc.tnc.magic;

public final class DarkSiphonRules {
    private static final double[] RANGES={5,7,10,14,18};
    private static final int[] TARGETS={2,3,4,5,6},STRANDS={1,2,3,4,5};
    public static final int BEADS=8;
    private DarkSiphonRules(){}
    private static int index(int tier){return Math.max(1,Math.min(5,tier))-1;}
    public static double range(int tier){return RANGES[index(tier)];}
    public static int targets(int tier){return TARGETS[index(tier)];}
    public static int strands(int tier){return STRANDS[index(tier)];}
    public static double flow(double age,int bead){return (age*.045+bead/(double)BEADS)%1;}
}
