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

final class AbyssMarkerPiece extends StructurePiece {
    private final BlockPos origin;
    AbyssMarkerPiece(BlockPos marker, BlockPos origin) {
        super(TNStructures.ABYSS_MARKER.get(),0,new BoundingBox(marker));
        this.origin = origin;
    }
    AbyssMarkerPiece(CompoundTag tag) {
        super(TNStructures.ABYSS_MARKER.get(),tag);
        origin = BlockPos.of(tag.getLong("Origin"));
    }
    BlockPos origin() { return origin; }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c, CompoundTag tag) {
        tag.putLong("Origin",origin.asLong());
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        // No SavedData/chunk access from a worldgen worker thread.
        AbyssCitadelJobs.request(level.getLevel(),origin);
    }
}
