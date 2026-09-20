package com.tnc.tnc.world.stonecrest;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Fixed-layout, terrain-aware worldgen structure for the Stonecrest fortress. */
public final class StonecrestStructure extends Structure {
    public static final Codec<StonecrestStructure> CODEC = simpleCodec(StonecrestStructure::new);
    private static final int GROUND_LOCAL_Y = 10;
    private static final int MAX_HEIGHT_SPREAD = 22;

    public StonecrestStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StonecrestManifest manifest = StonecrestManifest.get();
        ChunkPos start = context.chunkPos();
        int originX = (start.x - 8) << 4;
        int originZ = (start.z - 8) << 4;
        List<Integer> heights = new ArrayList<>();
        int wetSamples = 0;

        for (int z = 8; z < manifest.dimensions().getZ(); z += 32) {
            for (int x = 8; x < manifest.dimensions().getX(); x += 32) {
                if (!manifest.terrainAt(x, z)) continue;
                int worldX = originX + x;
                int worldZ = originZ + z;
                int height = context.chunkGenerator().getFirstOccupiedHeight(worldX, worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                heights.add(height - 1);
                NoiseColumn column = context.chunkGenerator().getBaseColumn(worldX, worldZ,
                        context.heightAccessor(), context.randomState());
                BlockState surface = column.getBlock(Math.max(context.heightAccessor().getMinBuildHeight(), height - 1));
                if (!surface.getFluidState().isEmpty()) wetSamples++;
            }
        }
        if (heights.size() < 16 || wetSamples * 10 > heights.size()) return Optional.empty();
        int min = Collections.min(heights);
        int max = Collections.max(heights);
        if (max - min > MAX_HEIGHT_SPREAD) return Optional.empty();
        Collections.sort(heights);
        int median = heights.get(heights.size() / 2);
        int originY = median - GROUND_LOCAL_Y;
        if (originY < context.heightAccessor().getMinBuildHeight() + 16
                || originY + manifest.dimensions().getY() >= context.heightAccessor().getMaxBuildHeight() - 4) {
            return Optional.empty();
        }

        BlockPos locator = new BlockPos(originX + manifest.anchorLocal().getX(), median,
                originZ + manifest.anchorLocal().getZ());
        return Optional.of(new GenerationStub(locator,
                builder -> addPieces(builder, context, manifest, originX, originY, originZ)));
    }

    private static void addPieces(StructurePiecesBuilder builder, GenerationContext context,
                                  StonecrestManifest manifest, int originX, int originY, int originZ) {
        for (int z = 0; z < manifest.dimensions().getZ(); z += 16) {
            for (int x = 0; x < manifest.dimensions().getX(); x += 16) {
                boolean used = false;
                for (int dz = 0; dz < 16 && !used; dz++) {
                    for (int dx = 0; dx < 16; dx++) {
                        if (manifest.terrainAt(x + dx, z + dz)) {
                            used = true;
                            break;
                        }
                    }
                }
                if (used) builder.addPiece(new StonecrestTerrainPiece(originX, originY, originZ, x, z));
            }
        }
        BlockPos origin = new BlockPos(originX, originY, originZ);
        for (StonecrestManifest.Piece piece : manifest.pieces()) {
            builder.addPiece(new StonecrestTemplatePiece(context.structureTemplateManager(), piece.resource(),
                    origin.offset(piece.offset())));
        }
    }

    @Override
    public StructureType<?> type() {
        return TNStructures.STONECREST.get();
    }
}
