package com.tnc.tnc.world.stonecrest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

/** Data-selected buildings using the already-tested Stonecrest terrain/template pipeline. */
public final class ImportedBuildingStructure extends Structure {
    public static final Codec<ImportedBuildingStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("asset").forGetter(s -> s.asset)
    ).apply(instance, ImportedBuildingStructure::new));
    private final String asset;

    public ImportedBuildingStructure(StructureSettings settings, String asset) {
        super(settings);
        this.asset = asset;
        StonecrestManifest.get(asset); // Fail during resource loading, not halfway through worldgen.
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var manifest = StonecrestManifest.get(asset);
        int ox = (context.chunkPos().x - manifest.dimensions().getX() / 32) << 4;
        int oz = (context.chunkPos().z - manifest.dimensions().getZ() / 32) << 4;
        var heights = new ArrayList<Integer>();
        for (int z = 4; z < manifest.dimensions().getZ(); z += 8) {
            for (int x = 4; x < manifest.dimensions().getX(); x += 8) {
                if (!manifest.buildingAt(x, z)) continue;
                int h = context.chunkGenerator().getFirstOccupiedHeight(ox + x, oz + z,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                var surface = context.chunkGenerator().getBaseColumn(ox + x, oz + z,
                        context.heightAccessor(), context.randomState()).getBlock(h);
                if (!surface.getFluidState().isEmpty()) return Optional.empty();
                heights.add(h);
            }
        }
        if (heights.isEmpty()) return Optional.empty();
        Collections.sort(heights);
        if (heights.get(heights.size() - 1) - heights.get(0) > 10) return Optional.empty();
        int oy = heights.get(heights.size() / 2) - manifest.anchorLocal().getY();
        if (oy < context.heightAccessor().getMinBuildHeight() + 16
                || oy + manifest.dimensions().getY() >= context.heightAccessor().getMaxBuildHeight() - 4)
            return Optional.empty();
        var origin = new BlockPos(ox, oy, oz);
        return Optional.of(new GenerationStub(origin.offset(manifest.anchorLocal()), builder -> {
            for (int z = 0; z < manifest.dimensions().getZ(); z += 16) {
                for (int x = 0; x < manifest.dimensions().getX(); x += 16) {
                    boolean used = false;
                    for (int dz = 0; dz < 16 && !used; dz++)
                        for (int dx = 0; dx < 16; dx++)
                            if (manifest.terrainAt(x + dx, z + dz)) { used = true; break; }
                    if (used) builder.addPiece(new StonecrestTerrainPiece(asset, ox, oy, oz, x, z));
                }
            }
            for (var piece : manifest.pieces()) builder.addPiece(new StonecrestTemplatePiece(
                    context.structureTemplateManager(), piece.resource(), origin.offset(piece.offset())));
        }));
    }

    @Override public StructureType<?> type() { return TNStructures.IMPORTED_BUILDING.get(); }
}
