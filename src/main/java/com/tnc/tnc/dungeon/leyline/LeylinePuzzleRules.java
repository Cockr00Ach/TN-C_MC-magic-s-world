package com.tnc.tnc.dungeon.leyline;

/** Deterministic room rules; all counters are saved by the room ledger. */
public final class LeylinePuzzleRules {
    public static final int[] NOTES = {1, 3, 6};
    public static final int[] NOTE_COLORS = {0xfb957c,0x77c6ff,0x7fe4b0,0xffdc8d,0x95a8ef,0xe49cda,0xbd8cf0,0xe6f1d5};
    public static int nextNote(int progress, int note) {
        return nextNote(NOTES,progress,note);
    }
    public static int nextNote(int[] sequence,int progress,int note) { return sequence.length==3&&progress>=0&&progress<3&&note==sequence[progress]?progress+1:0; }
    public static int[] notes(long seed) { var random=new java.util.Random(seed); return new int[]{random.nextInt(3),3+random.nextInt(2),5+random.nextInt(3)}; }
    public static int treasureMask(long seed) { var random=new java.util.Random(seed); int first=random.nextInt(6),second=random.nextInt(5); if(second>=first)second++;return (1<<first)|(1<<second); }
    public static int nextHour(int hour, boolean clockwise) { return Math.floorMod(hour + (clockwise ? 1 : -1), 4); }
    public static boolean temperatureSafe(int temperature) { return temperature >= 360 && temperature <= 620; }
    public static boolean mayThaw(int melted, int index) { return index >= 0 && index < 6 && Integer.bitCount(melted) < 2 && (melted & 1 << index) == 0; }
    public static int moveMirror(int cell, int dx, int dz) {
        int x = cell % 5 + Integer.signum(dx), z = cell / 5 + Integer.signum(dz);
        return x < 0 || x > 4 || z < 0 || z > 4 ? cell : z * 5 + x;
    }
    public static boolean lensCovers(int lensColumn, int lensRow, int coreIndex) {
        return Math.abs(coreIndex % 15 - lensColumn) <= 1 && Math.abs(coreIndex / 15 - lensRow) <= 1;
    }
    public static int scaleToxicity(int seconds) { return Math.max(0, seconds) * 5; }
    /** A high-toxicity wager improves the rare roll without ever guaranteeing it. */
    public static boolean rarePawn(int toxicity, int roll) { return roll >= 0 && roll < Math.min(85, 10 + Math.max(0, toxicity)); }
    private LeylinePuzzleRules() {}
}
