package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
                terrainBox(originX, originY, originZ, localX, localZ));
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.localX = localX;
        this.localZ = localZ;
    }

    private static BoundingBox terrainBox(int originX, int originY, int originZ, int localX, int localZ) {
        int structureTopY = originY + StonecrestManifest.get().dimensions().getY() - 1;
        return new BoundingBox(originX + localX, originY - 32, originZ + localZ,
                originX + localX + 15, structureTopY, originZ + localZ + 15);
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
        int structureTopY = originY + manifest.dimensions().getY() - 1;

        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int lx = localX + dx;
                int lz = localZ + dz;
                int distance = manifest.distanceAt(lx, lz);
                if (distance > manifest.maxBlendDistance()) continue;

                int worldX = originX + lx;
                int worldZ = originZ + lz;
                int currentY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
                StonecrestTerrainPlanner.ColumnPlan plan = StonecrestTerrainPlanner.plan(
                        currentY, desiredCoreY, distance, manifest.maxBlendDistance(),
                        manifest.buildingAt(lx, lz), originY, structureTopY);
                int targetY = plan.targetY();

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

                // Terrain pieces are registered before every template piece.
                // Clear the complete source volume, including underground
                // rooms and tunnels, then let the compact non-air templates
                // reproduce every solid source block.  Avoid writing air on
                // positions that are already empty.
                if (plan.clearsTemplateVolume()) {
                    for (int y = plan.clearFromY(); y <= plan.clearToY(); y++) {
                        cursor.set(worldX, y, worldZ);
                        if (chunkBox.isInside(cursor) && !level.getBlockState(cursor).isAir()) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
}
