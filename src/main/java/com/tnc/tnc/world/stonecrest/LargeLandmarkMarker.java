package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class LargeLandmarkMarker extends StructurePiece {
    final String asset;
    final BlockPos origin;
    LargeLandmarkMarker(String asset,BlockPos marker,BlockPos origin) {
        super(TNStructures.LANDMARK_MARKER.get(),0,new BoundingBox(marker)); this.asset=asset; this.origin=origin;
    }
    LargeLandmarkMarker(CompoundTag t) {
        super(TNStructures.LANDMARK_MARKER.get(),t); asset=t.getString("Asset"); origin=BlockPos.of(t.getLong("Origin"));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag t) {
        t.putString("Asset",asset); t.putLong("Origin",origin.asLong());
    }
    @Override public void postProcess(WorldGenLevel l,StructureManager s,ChunkGenerator g,RandomSource r,BoundingBox b,ChunkPos c,BlockPos p) {
        // Dedicated GameTests enqueue their own fixtures. Random worldgen landmarks
        // must not occupy those sites or cast shadows across unrelated test batches.
        if(l.getLevel().getServer() instanceof net.minecraft.gametest.framework.GameTestServer)return;
        if (LandmarkGenerationMode.enabled(l.getLevel())) return;
        LargeLandmarkJobs.request(l.getLevel(),asset,origin);
    }
}
