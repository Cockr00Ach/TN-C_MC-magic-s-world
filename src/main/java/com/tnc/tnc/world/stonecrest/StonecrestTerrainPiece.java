package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** One chunk of the irregular terrain feather surrounding the fortress. */
final class StonecrestTerrainPiece extends StructurePiece {
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int localX;
    private final int localZ;

    StonecrestTerrainPiece(int originX, int originY, int originZ, int localX, int localZ) {
        super(TNStructures.STONECREST_TERRAIN.get(), 0,
                new BoundingBox(originX + localX, originY - 32, originZ + localZ,
                        originX + localX + 15, originY + 95, originZ + localZ + 15));
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.localX = localX;
        this.localZ = localZ;
    }

    StonecrestTerrainPiece(CompoundTag tag) {
        super(TNStructures.STONECREST_TERRAIN.get(), tag);
        this.originX = tag.getInt("OriginX");
        this.originY = tag.getInt("OriginY");
        this.originZ = tag.getInt("OriginZ");
        this.localX = tag.getInt("LocalX");
        this.localZ = tag.getInt("LocalZ");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OriginX", originX);
        tag.putInt("OriginY", originY);
        tag.putInt("OriginZ", originZ);
        tag.putInt("LocalX", localX);
        tag.putInt("LocalZ", localZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        StonecrestManifest manifest = StonecrestManifest.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int desiredCoreY = originY + manifest.anchorLocal().getY();

        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int lx = localX + dx;
                int lz = localZ + dz;
                int distance = manifest.distanceAt(lx, lz);
                if (distance > manifest.maxBlendDistance()) continue;

                int worldX = originX + lx;
                int worldZ = originZ + lz;
                int currentY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
                float strength = 1.0F - (float) distance / (manifest.maxBlendDistance() + 1.0F);
                strength = strength * strength;
                int targetY = Mth.floor(Mth.lerp(strength, currentY, desiredCoreY) + 0.5D);
                if (targetY == currentY) continue;

                cursor.set(worldX, currentY, worldZ);
                BlockState top = level.getBlockState(cursor);
                BlockState fill = level.getBlockState(cursor.below());
                if (top.isAir() || !top.getFluidState().isEmpty()) top = Blocks.GRASS_BLOCK.defaultBlockState();
                if (fill.isAir() || !fill.getFluidState().isEmpty()) fill = Blocks.DIRT.defaultBlockState();

                if (targetY > currentY) {
                    for (int y = currentY + 1; y < targetY; y++) {
                        cursor.set(worldX, y, worldZ);
                        if (chunkBox.isInside(cursor)) level.setBlock(cursor, fill, 2);
                    }
                } else {
                    for (int y = currentY; y > targetY; y--) {
                        cursor.set(worldX, y, worldZ);
                        if (chunkBox.isInside(cursor)) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                cursor.set(worldX, targetY, worldZ);
                if (chunkBox.isInside(cursor)) level.setBlock(cursor, top, 2);
            }
        }
    }
}
