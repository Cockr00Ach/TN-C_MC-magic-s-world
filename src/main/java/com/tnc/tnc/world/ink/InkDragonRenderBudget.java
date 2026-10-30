package com.tnc.tnc.world.ink;

/** Bound CPU submission cost without changing the editable, full-detail dragon. */
public final class InkDragonRenderBudget {
    private InkDragonRenderBudget() {}

    public static int lod(String group, double headDistance, boolean phantom) {
        if (phantom) return headDistance < 192 ? 1 : 2;
        // Preserve the complete visible scales and filaments. Shadow detail is
        // selected separately, never by deleting details from the camera mesh.
        return headDistance < 192 ? 0 : headDistance < 320 ? 1 : 2;
    }
}
