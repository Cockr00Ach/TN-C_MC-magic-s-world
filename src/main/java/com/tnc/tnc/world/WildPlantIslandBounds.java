package com.tnc.tnc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/** Keep legacy authored terrain clear without projecting a separate island onto the Overworld. */
public final class WildPlantIslandBounds {
    private WildPlantIslandBounds() {}

    public static boolean allows(ServerLevel level, ChunkPos chunk) {
        if (SkyIslandWorlds.separate(level.getServer())) return true;
        var island = SkyIslandSavedData.get(level);
        // The first-login router always puts a fresh IDLE save in Skyhaven. Spawn terrain is
        // generated before that login, so it is safe to populate these initial Overworld chunks too.
        if (island.version == 0 && island.phase == SkyIslandSavedData.Phase.IDLE) return true;
        BlockPos town = island.anchorPos("CENTER");
        if (town == null) return false;
        long dx = (long)town.getX() - Mth.clamp(town.getX(), chunk.getMinBlockX(), chunk.getMaxBlockX());
        long dz = (long)town.getZ() - Mth.clamp(town.getZ(), chunk.getMinBlockZ(), chunk.getMaxBlockZ());
        return dx * dx + dz * dz > 900L * 900L;
    }
}
