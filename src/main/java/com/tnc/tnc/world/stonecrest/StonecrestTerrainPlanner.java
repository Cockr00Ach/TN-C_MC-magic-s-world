package com.tnc.tnc.world.stonecrest;

/** Pure terrain-column planning kept separate so the destructive bounds are testable. */
final class StonecrestTerrainPlanner {
    private StonecrestTerrainPlanner() {
    }

    static ColumnPlan plan(int currentY, int desiredCoreY, int distance, int maxBlendDistance,
                           boolean buildingColumn, int structureBottomY, int structureTopY) {
        double strength = 1.0D - (double) distance / (maxBlendDistance + 1.0D);
        strength = Math.max(0.0D, Math.min(1.0D, strength));
        strength *= strength;
        int targetY = (int) Math.floor(currentY + strength * (desiredCoreY - currentY) + 0.5D);
        return buildingColumn
                ? new ColumnPlan(targetY, structureBottomY, structureTopY)
                : new ColumnPlan(targetY, 1, 0);
    }

    record ColumnPlan(int targetY, int clearFromY, int clearToY) {
        boolean clearsTemplateVolume() {
            return clearFromY <= clearToY;
        }
    }
}
