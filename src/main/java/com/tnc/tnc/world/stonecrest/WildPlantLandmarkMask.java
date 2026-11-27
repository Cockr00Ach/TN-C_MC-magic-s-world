package com.tnc.tnc.world.stonecrest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class WildPlantLandmarkMask {
    private WildPlantLandmarkMask() {}

    /** The wide authored landscapes extend beyond vanilla's structure-reference radius. */
    public static boolean occupiesTerrain(ServerLevel level, ChunkPos chunk) {
        if (LandmarkGenerationMode.enabled(level))
            return !LandmarkSites.touching(level,chunk,0).isEmpty();
        for (var job : LandmarkData.get(level).jobs.values()) {
            var size = job.manifest().dimensions();
            if (job.origin.getX() <= chunk.getMaxBlockX() && job.origin.getX()+size.getX() > chunk.getMinBlockX()
                    && job.origin.getZ() <= chunk.getMaxBlockZ() && job.origin.getZ()+size.getZ() > chunk.getMinBlockZ()) return true;
        }
        return false;
    }
}
