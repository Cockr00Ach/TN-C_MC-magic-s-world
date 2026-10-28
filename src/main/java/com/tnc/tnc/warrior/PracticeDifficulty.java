package com.tnc.tnc.warrior;

/** Sparring difficulty changes timing, never talent or repeatable growth rewards. */
public final class PracticeDifficulty {
    private PracticeDifficulty(){}
    public static int clamp(int mode){return Math.max(0,Math.min(2,mode));}
    public static String name(int mode){return switch(clamp(mode)){case 1->"进阶";case 2->"试炼";default->"入门";};}
    public static int period(int mode){return 50-10*clamp(mode);}
    public static int windup(int mode){return new int[]{15,12,10}[clamp(mode)];}
    public static int window(int mode){return 80-20*clamp(mode);}
    public static int score(int misses,int reactionTicks){return (int)Math.max(0,100L-15L*Math.max(0,misses)-Math.max(0,reactionTicks)/2);}
    public static String grade(int score){return score>=90?"炉火纯青":score>=75?"稳健":score>=50?"合格":"仍需磨练";}
}
