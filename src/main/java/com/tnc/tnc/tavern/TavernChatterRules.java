package com.tnc.tnc.tavern;

/** Cosmetic timing only; never writes chat messages or quest/dialogue state. */
public final class TavernChatterRules {
    public static final int MAX_ACTIVE=2,DURATION=100,COOLDOWN=600,RANGE=12;
    public static float alpha(long age){return Math.max(0,Math.min(1,Math.min(age/10f,(DURATION-age)/15f)));}
    public static String compact(String line){return line.codePointCount(0,line.length())<=22?line:line.substring(0,line.offsetByCodePoints(0,21))+"…";}
    private TavernChatterRules(){}
}
