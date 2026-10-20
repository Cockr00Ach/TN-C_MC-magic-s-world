package com.tnc.tnc.world.eartharena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tnc.tnc.building.ElementBossBlocks;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Solid runestone mountains with no ocean, imported structures, ores or ticking scenery. */
public final class EarthArenaChunkGenerator extends ChunkGenerator {
    public static final Codec<EarthArenaChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(EarthArenaChunkGenerator::getBiomeSource),
            Codec.LONG.optionalFieldOf("terrain_seed", EarthArenaTerrain.DEFAULT_SEED).forGetter(g -> g.terrainSeed)
    ).apply(instance, EarthArenaChunkGenerator::new));
    private final long terrainSeed;
    private static volatile BlockState[] cachedPalette;

    public EarthArenaChunkGenerator(BiomeSource biomes, long seed) { super(biomes); terrainSeed = seed; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return EarthArenaTerrain.HEIGHT; }
    @Override public int getMinY() { return EarthArenaTerrain.MIN_Y; }
    @Override public int getSeaLevel() { return getMinY(); }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return EarthArenaTerrain.height(8, 8, terrainSeed) + 1; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, getBiomeSource(), Stream.empty());
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}

    private static BlockState[] palette() {
        if (cachedPalette != null) return cachedPalette;
        BlockState[] states = new BlockState[8];
        for (int i = 0; i < 8; i++) states[i] = ElementBossBlocks.block("earth", i + 1).defaultBlockState();
        cachedPalette = states;
        return states;
    }

    public static BlockState material(int x, int y, int z, int top, int offset) {
        if (EarthArenaLayout.platform(x,z) && y >= EarthArenaLayout.FLOOR - 6)
            return y >= EarthArenaLayout.FLOOR - 2 ? platformSurface(x,z) : EarthArena.PLATFORM.defaultBlockState();
        return palette()[EarthArenaTerrain.shade(y,top,offset)-1];
    }

    private static BlockState platformSurface(int x,int z) {
        return (EarthArenaLayout.blackTile(x,z) ? EarthArena.DARK_PLATFORM : EarthArena.PLATFORM).defaultBlockState();
    }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            BlockState[] states = palette();
            Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int top = Math.min(chunk.getMaxBuildHeight() - 1, EarthArenaTerrain.height(startX + x, startZ + z, terrainSeed));
                int offset = EarthArenaTerrain.layerOffset(startX + x, startZ + z, terrainSeed);
                boolean platform = EarthArenaLayout.platform(startX+x,startZ+z);
                BlockState tile = platform ? platformSurface(startX+x,startZ+z) : null;
                for (int y = chunk.getMinBuildHeight(); y <= top; y++)
                    chunk.setBlockState(cursor.set(x, y, z), platform && y>=EarthArenaLayout.FLOOR-6 ? y>=EarthArenaLayout.FLOOR-2 ? tile : EarthArena.PLATFORM.defaultBlockState() : states[EarthArenaTerrain.shade(y,top,offset)-1], false);
                BlockState cap = material(startX+x,top,startZ+z,top,offset);
                floor.update(x, top, z, cap);
                surface.update(x, top, z, cap);
            }
            return chunk;
        }, executor);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), EarthArenaTerrain.height(x, z, terrainSeed) + 1));
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = palette(), column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        int top = EarthArenaTerrain.height(x, z, terrainSeed), offset = EarthArenaTerrain.layerOffset(x, z, terrainSeed);
        for (int y = level.getMinBuildHeight(); y <= Math.min(top, level.getMaxBuildHeight() - 1); y++)
            column[y - level.getMinBuildHeight()] = material(x,y,z,top,offset);
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        lines.add("Earth arena: mountain height=" + EarthArenaTerrain.height(pos.getX(), pos.getZ(), terrainSeed));
    }
}
