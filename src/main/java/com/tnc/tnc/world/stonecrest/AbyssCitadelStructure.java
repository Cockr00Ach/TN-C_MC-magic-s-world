package com.tnc.tnc.world.stonecrest;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/** Native locate/distribution anchor; the large excavation is a resumable server job. */
public final class AbyssCitadelStructure extends Structure {
    public static final Codec<AbyssCitadelStructure> CODEC = simpleCodec(AbyssCitadelStructure::new);
    public AbyssCitadelStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c) {
        if (c.heightAccessor().getMinBuildHeight() != -64 || c.heightAccessor().getMaxBuildHeight() != 320)
            return Optional.empty();
        int x = c.chunkPos().getMinBlockX() + 8;
        int z = c.chunkPos().getMinBlockZ() + 8;
        int cx = x, cz = z - 168;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dz = -144; dz <= 144; dz += 48) for (int dx = -192; dx <= 192; dx += 48) {
            int h = c.chunkGenerator().getFirstOccupiedHeight(cx + dx, cz + dz,
                    Heightmap.Types.WORLD_SURFACE_WG, c.heightAccessor(), c.randomState());
            var block = c.chunkGenerator().getBaseColumn(cx + dx, cz + dz,
                    c.heightAccessor(), c.randomState()).getBlock(h);
            if (!block.getFluidState().isEmpty() || h < 60 || h > 140) return Optional.empty();
            min = Math.min(min, h); max = Math.max(max, h);
        }
        if (max - min > 40) return Optional.empty();
        int y = c.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                c.heightAccessor(), c.randomState()) + 1;
        BlockPos lookout = new BlockPos(x,y,z);
        BlockPos origin = new BlockPos(cx - 122, -64, cz - 70);
        return Optional.of(new GenerationStub(lookout, b -> b.addPiece(new AbyssMarkerPiece(lookout, origin))));
    }

    @Override public StructureType<?> type() { return TNStructures.ABYSS_CITADEL.get(); }
}
