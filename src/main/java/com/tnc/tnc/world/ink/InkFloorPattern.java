package com.tnc.tnc.world.ink;

/** Clean paper beneath the mountain/cloud decals; legacy strokes are no longer sampled. */
public final class InkFloorPattern {
    public enum Shade { WHITE, LIGHT_GRAY, GRAY, BLACK }


    private InkFloorPattern() {}

    public static Shade shadeAt(int worldX, int worldZ) {
        return Shade.WHITE;
    }

}
