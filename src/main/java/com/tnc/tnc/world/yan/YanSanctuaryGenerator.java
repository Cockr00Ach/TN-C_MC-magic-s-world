package com.tnc.tnc.world.yan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static com.tnc.tnc.world.yan.YanSanctuaryLayout.DRAGON;
import static com.tnc.tnc.world.yan.YanSanctuaryLayout.FLOOR;
import static com.tnc.tnc.world.yan.YanSanctuaryLayout.STRUCTURE_TOP;

/** Flat terrain, altar and blueprint are generated independently in each requested chunk. */
public final class YanSanctuaryGenerator extends ChunkGenerator {
    public static final Codec<YanSanctuaryGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(YanSanctuaryGenerator::getBiomeSource),
                    Codec.BOOL.optionalFieldOf("repeat_altar", false).forGetter(generator -> generator.repeatAltar))
                    .apply(instance, YanSanctuaryGenerator::new));
    private static final int GROUND_DEPTH = 4;
    private static final int DRAGON_HEIGHT = 225;

    private record DragonChunk(long[][] columns) {
        long[] column(int x, int z) { return columns[(x & 15) | ((z & 15) << 4)]; }
    }

    private static final class Dragon {
        static final Map<Long, DragonChunk> CHUNKS = read();

        private static Map<Long, DragonChunk> read() {
            try (var input = YanSanctuaryGenerator.class.getResourceAsStream("/data/tnc/blueprints/aurelian.nbt")) {
                if (input == null) throw new IOException("Missing Aurelian blueprint");
                var nbt = NbtIo.readCompressed(input);
                int[] size = nbt.getIntArray("Size"), blocks = nbt.getIntArray("Blocks");
                var palette = nbt.getList("Palette", 10);
                if (!Arrays.equals(size, new int[]{300, DRAGON_HEIGHT, 289}) || blocks.length == 0 || blocks.length % 4 != 0) {
                    throw new IOException("Invalid Aurelian blueprint dimensions");
                }

                int air = -1;
                for (int i = 0; i < palette.size(); i++) {
                    if (palette.getCompound(i).getString("Name").equals("minecraft:air")) air = i;
                }
                var chunks = new HashMap<Long, DragonChunk>();
                for (int i = 0; i < blocks.length; i += 4) {
                    int x = blocks[i], y = blocks[i + 1], z = blocks[i + 2], material = blocks[i + 3];
                    if (x < 0 || x >= size[0] || y < 0 || y >= size[1] || z < 0 || z >= size[2]
                            || material < 0 || material >= palette.size()) {
                        throw new IOException("Invalid Aurelian blueprint entry");
                    }
                    if (material == air) continue;
                    x += DRAGON.getX();
                    z += DRAGON.getZ();
                    long key = ChunkPos.asLong(x >> 4, z >> 4);
                    var chunk = chunks.computeIfAbsent(key, ignored -> new DragonChunk(new long[256][]));
                    int columnIndex = (x & 15) | ((z & 15) << 4);
                    long[] column = chunk.columns[columnIndex];
                    if (column == null) {
                        column = new long[4];
                        chunk.columns[columnIndex] = column;
                    }
                    column[y >> 6] |= 1L << (y & 63);
                }
                return Map.copyOf(chunks);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot generate the dragon in Yan's sanctuary", e);
            }
        }
    }

    static final int LANE_WIDTH = 512;
    private final boolean repeatAltar;

    public YanSanctuaryGenerator(BiomeSource source, boolean repeatAltar) {
        super(source);
        this.repeatAltar = repeatAltar;
    }

    private int arenaX(int x) {
        return repeatAltar ? Math.floorMod(x + LANE_WIDTH / 2, LANE_WIDTH) - LANE_WIDTH / 2 : x;
    }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 320; }
    @Override public int getMinY() { return 0; }
    @Override public int getSeaLevel() { return 0; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return FLOOR + 1; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState state, long seed) {
        return ChunkGeneratorStructureState.createForFlat(state, seed, getBiomeSource(), Stream.empty());
    }

    @Override public void buildSurface(WorldGenRegion region, StructureManager manager, RandomState state, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes,
                                      StructureManager manager, ChunkAccess chunk, GenerationStep.Carving step) {}

    private static BlockState ground(int x, int y, int z) {
        if (y == 0) return Blocks.BEDROCK.defaultBlockState();
        if (y > 0 && y < FLOOR - GROUND_DEPTH) return Blocks.DEEPSLATE.defaultBlockState();
        if (y > 0 && y < FLOOR) return Blocks.BLACKSTONE.defaultBlockState();
        if (y < FLOOR || y > STRUCTURE_TOP) return Blocks.AIR.defaultBlockState();
        return YanSanctuaryLayout.platform(x, y, z);
    }

    private static long[] dragonColumn(int x, int z) {
        DragonChunk chunk = Dragon.CHUNKS.get(ChunkPos.asLong(x >> 4, z >> 4));
        return chunk == null ? null : chunk.column(x, z);
    }

    private static int top(int x, int z, Heightmap.Types type, long[] dragon) {
        if (dragon != null) {
            for (int i = dragon.length - 1; i >= 0; i--) {
                if (dragon[i] != 0) return DRAGON.getY() + (i << 6) + 64 - Long.numberOfLeadingZeros(dragon[i]);
            }
        }
        if (Math.abs(x) > 37 || z < -45 || z > 56) return FLOOR + 1;
        for (int y = STRUCTURE_TOP; y >= FLOOR; y--) {
            if (type.isOpaque().test(ground(x, y, z))) return y + 1;
        }
        return FLOOR + 1;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            var ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            var pos = new BlockPos.MutableBlockPos();
            int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
            DragonChunk dragon = Dragon.CHUNKS.get(ChunkPos.asLong(arenaX(minX) >> 4, minZ >> 4));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = arenaX(minX + x), wz = minZ + z;
                    long[] column = dragon == null ? null : dragon.column(wx, wz);
                    int surfaceY = top(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, column) - 1;
                    int oceanY = top(wx, wz, Heightmap.Types.OCEAN_FLOOR_WG, column) - 1;
                    for (int y = 0; y <= Math.min(surfaceY, STRUCTURE_TOP); y++) {
                        BlockState state = ground(wx, y, wz);
                        if (!state.isAir()) chunk.setBlockState(pos.set(x, y, z), state, false);
                    }
                    if (column != null) {
                        for (int word = 0; word < column.length; word++) {
                            for (long mask = column[word]; mask != 0; mask &= mask - 1) {
                                int y = DRAGON.getY() + (word << 6) + Long.numberOfTrailingZeros(mask);
                                chunk.setBlockState(pos.set(x, y, z), YanSanctuary.OBSIDIAN.defaultBlockState(), false);
                            }
                        }
                    }
                    surface.update(x, surfaceY, z, column == null ? ground(wx, surfaceY, wz) : YanSanctuary.OBSIDIAN.defaultBlockState());
                    ocean.update(x, oceanY, z, column == null ? ground(wx, oceanY, wz) : YanSanctuary.OBSIDIAN.defaultBlockState());
                }
            }
            return chunk;
        }, executor);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState state) {
        int localX = arenaX(x);
        return top(localX, z, type, dragonColumn(localX, z));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState state) {
        int min = level.getMinBuildHeight();
        var states = new BlockState[level.getHeight()];
        x = arenaX(x);
        var dragon = dragonColumn(x, z);
        for (int i = 0; i < states.length; i++) {
            int y = min + i;
            int local = y - DRAGON.getY();
            boolean statue = dragon != null && local >= 0 && local < DRAGON_HEIGHT && (dragon[local >> 6] & (1L << (local & 63))) != 0;
            states[i] = statue ? YanSanctuary.OBSIDIAN.defaultBlockState() : ground(x, y, z);
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(java.util.List<String> lines, RandomState state, BlockPos pos) {
        lines.add("Yan sanctuary");
    }
}
