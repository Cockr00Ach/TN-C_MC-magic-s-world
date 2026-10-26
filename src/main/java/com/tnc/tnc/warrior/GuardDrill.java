package com.tnc.tnc.warrior;

import java.util.UUID;

/** Three real blocks followed by a counter against the last attacker within four seconds. */
public final class GuardDrill {
    public static final int REWARD = 120;
    private int blocks;
    private long lastBlock = Long.MIN_VALUE;
    private UUID attacker;
    public int blocks() { return blocks; }
    public int remaining(long tick) {
        if(blocks<3 || tick<lastBlock)return 0;
        return (int)Math.max(0,80-(tick-lastBlock));
    }
    public boolean block(UUID source, long tick) {
        if (source == null || (lastBlock != Long.MIN_VALUE && tick - lastBlock < 10)) return false;
        blocks = Math.min(3, blocks + 1); attacker = source; lastBlock = tick; return true;
    }
    public boolean counter(UUID target, long tick) {
        return attacker != null && attacker.equals(target) && remaining(tick)>0;
    }
}
