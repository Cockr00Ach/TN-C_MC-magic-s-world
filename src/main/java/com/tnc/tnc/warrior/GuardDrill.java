package com.tnc.tnc.warrior;

import java.util.UUID;

/** Three real blocks followed by a counter within the selected difficulty's window. */
public final class GuardDrill {
    public static final int REWARD = 120;
    private int blocks;
    private long lastBlock = Long.MIN_VALUE;
    private UUID attacker;
    private final int mode;
    private int misses;
    public GuardDrill(){this(0);}
    public GuardDrill(int mode){this.mode=PracticeDifficulty.clamp(mode);}
    public int mode(){return mode;}
    public void miss(){if(misses<Integer.MAX_VALUE)misses++;}
    public int misses(){return misses;}
    public int reaction(long tick){return remaining(tick)>0?(int)(tick-lastBlock):PracticeDifficulty.window(mode);}
    public int blocks() { return blocks; }
    public int remaining(long tick) {
        if(blocks<3 || tick<lastBlock)return 0;
        return (int)Math.max(0,PracticeDifficulty.window(mode)-(tick-lastBlock));
    }
    public boolean block(UUID source, long tick) {
        if (source == null || (lastBlock != Long.MIN_VALUE && tick - lastBlock < 10)) return false;
        blocks = Math.min(3, blocks + 1); attacker = source; lastBlock = tick; return true;
    }
    public boolean counter(UUID target, long tick) {
        return attacker != null && attacker.equals(target) && remaining(tick)>0;
    }
}
