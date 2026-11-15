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
import net.minecraft.world.level.block.Block;
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
    private static final int DRAGON_WIDTH = 300;
    private static final int DRAGON_LENGTH = 289;
    private static final int MATERIAL_BITS = 4;
    private static final int MATERIAL_MASK = (1 << MATERIAL_BITS) - 1;

    private enum Stone {
        SHADOW(Blocks.POLISHED_BLACKSTONE),
        RIDGE(Blocks.BASALT),
        SCALES(Blocks.POLISHED_DEEPSLATE),
        ASH(Blocks.ANDESITE),
        MEMBRANE(Blocks.SMOOTH_STONE),
        IVORY(Blocks.CALCITE),
        VIOLET(Blocks.PURPLE_TERRACOTTA),
        CRYSTAL(Blocks.AMETHYST_BLOCK),
        VEIN(YanSanctuary.OBSIDIAN);

        final BlockState state;

        Stone(Block block) { state = block.defaultBlockState(); }

        // Translate the blueprint's original material regions into a single stone and violet palette.
        static Stone fromSource(String name) {
            return switch (name) {
                case "blackstone", "polished_blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
                        "coal_ore", "deepslate_coal_ore", "netherite_block", "black_terracotta", "black_wool",
                        "black_concrete_powder", "sculk", "sculk_catalyst" -> SHADOW;
                case "soul_sand", "gray_glazed_terracotta", "gray_concrete", "gray_wool", "gray_concrete_powder",
                        "gray_terracotta", "smooth_basalt", "polished_basalt", "basalt" -> RIDGE;
                case "smooth_stone", "andesite", "polished_andesite", "light_gray_wool", "light_gray_glazed_terracotta",
                        "light_gray_concrete", "light_gray_concrete_powder", "lodestone", "stone" -> ASH;
                case "diorite", "polished_diorite", "white_concrete", "white_concrete_powder", "white_wool",
                        "white_glazed_terracotta", "iron_block", "birch_wood" -> MEMBRANE;
                case "smooth_quartz", "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block",
                        "bone_block", "snow_block", "calcite" -> IVORY;
                case "diamond_ore", "deepslate_diamond_ore", "deepslate_lapis_ore", "iron_ore",
                        "waxed_exposed_cut_copper", "light_blue_terracotta" -> VIOLET;
                case "pearlescent_froglight", "orange_glazed_terracotta", "gold_ore" -> CRYSTAL;
                case "lapis_ore", "deepslate_gold_ore", "gilded_blackstone", "deepslate_redstone_ore" -> VEIN;
                default -> SCALES;
            };
        }
    }

    private record DragonChunk(short[][] columns) {
        short[] column(int x, int z) { return columns[(x & 15) | ((z & 15) << 4)]; }
    }

    private static final class Dragon {
        static final Stone[] STONES = Stone.values();
        static final Map<Long, DragonChunk> CHUNKS = read();

        private static Map<Long, DragonChunk> read() {
            try (var input = YanSanctuaryGenerator.class.getResourceAsStream("/data/tnc/blueprints/aurelian.nbt")) {
                if (input == null) throw new IOException("Missing Aurelian blueprint");
                var nbt = NbtIo.readCompressed(input);
                int[] size = nbt.getIntArray("Size"), blocks = nbt.getIntArray("Blocks");
                var palette = nbt.getList("Palette", 10);
                if (!Arrays.equals(size, new int[]{DRAGON_WIDTH, DRAGON_HEIGHT, DRAGON_LENGTH})
                        || blocks.length == 0 || blocks.length % 4 != 0) {
                    throw new IOException("Invalid Aurelian blueprint dimensions");
                }

                var materials = new byte[palette.size()];
                for (int i = 0; i < palette.size(); i++) {
                    String name = palette.getCompound(i).getString("Name");
                    if (!name.equals("minecraft:air")) {
                        materials[i] = (byte) (Stone.fromSource(name.substring(name.indexOf(':') + 1)).ordinal() + 1);
                    }
                }
                var chunks = new HashMap<Long, byte[][]>();
                for (int i = 0; i < blocks.length; i += 4) {
                    int x = blocks[i], y = blocks[i + 1], z = blocks[i + 2], material = blocks[i + 3];
                    if (x < 0 || x >= size[0] || y < 0 || y >= size[1] || z < 0 || z >= size[2]
                            || material < 0 || material >= palette.size()) {
                        throw new IOException("Invalid Aurelian blueprint entry");
                    }
                    if (materials[material] == 0) continue;
                    x += DRAGON.getX();
                    z += DRAGON.getZ();
                    long key = ChunkPos.asLong(x >> 4, z >> 4);
                    var chunk = chunks.computeIfAbsent(key, ignored -> new byte[256][]);
                    int columnIndex = (x & 15) | ((z & 15) << 4);
                    if (chunk[columnIndex] == null) chunk[columnIndex] = new byte[DRAGON_HEIGHT];
                    chunk[columnIndex][y] = materials[material];
                }
                var packed = new HashMap<Long, DragonChunk>();
                chunks.forEach((key, columns) -> {
                    var chunk = new short[256][];
                    var columnBuffer = new short[DRAGON_HEIGHT];
                    for (int i = 0; i < columns.length; i++) {
                        byte[] column = columns[i];
                        if (column == null) continue;
                        int count = 0;
                        for (int y = 0; y < column.length; y++) {
                            if (column[y] != 0) columnBuffer[count++] = (short) ((y << MATERIAL_BITS) | (column[y] - 1));
                        }
                        chunk[i] = Arrays.copyOf(columnBuffer, count);
                    }
                    packed.put(key, new DragonChunk(chunk));
                });
                return Map.copyOf(packed);
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

    private DragonChunk dragonChunk(int x, int z) {
        x = arenaX(x);
        if ((x >> 4) < (DRAGON.getX() >> 4) || x >= DRAGON.getX() + DRAGON_WIDTH
                || (z >> 4) < (DRAGON.getZ() >> 4) || z >= DRAGON.getZ() + DRAGON_LENGTH) return null;
        return Dragon.CHUNKS.get(ChunkPos.asLong(x >> 4, z >> 4));
    }

    short[] dragonColumn(int x, int z) {
        var chunk = dragonChunk(x, z);
        return chunk == null ? null : chunk.column(x, z);
    }

    boolean hasDragon(ChunkPos chunk) { return dragonChunk(chunk.getMinBlockX(), chunk.getMinBlockZ()) != null; }

    static int dragonY(short packed) { return DRAGON.getY() + (packed >> MATERIAL_BITS); }

    static BlockState dragonMaterial(short packed) { return Dragon.STONES[packed & MATERIAL_MASK].state; }

    private static int top(int x, int z, Heightmap.Types type, short[] dragon) {
        if (dragon != null) return dragonY(dragon[dragon.length - 1]) + 1;
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
            DragonChunk dragon = dragonChunk(minX, minZ);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = arenaX(minX + x), wz = minZ + z;
                    short[] column = dragon == null ? null : dragon.column(wx, wz);
                    int surfaceY = top(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, column) - 1;
                    int oceanY = top(wx, wz, Heightmap.Types.OCEAN_FLOOR_WG, column) - 1;
                    for (int y = 0; y <= Math.min(surfaceY, STRUCTURE_TOP); y++) {
                        BlockState state = ground(wx, y, wz);
                        if (!state.isAir()) chunk.setBlockState(pos.set(x, y, z), state, false);
                    }
                    if (column != null) {
                        for (short block : column) chunk.setBlockState(pos.set(x, dragonY(block), z), dragonMaterial(block), false);
                    }
                    var highestStone = column == null ? null : dragonMaterial(column[column.length - 1]);
                    surface.update(x, surfaceY, z, highestStone == null ? ground(wx, surfaceY, wz) : highestStone);
                    ocean.update(x, oceanY, z, highestStone == null ? ground(wx, oceanY, wz) : highestStone);
                }
            }
            return chunk;
        }, executor);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState state) {
        int localX = arenaX(x);
        return top(localX, z, type, dragonColumn(x, z));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState state) {
        int min = level.getMinBuildHeight();
        var states = new BlockState[level.getHeight()];
        var dragon = dragonColumn(x, z);
        int localX = arenaX(x);
        for (int i = 0; i < states.length; i++) states[i] = ground(localX, min + i, z);
        if (dragon != null) {
            for (short block : dragon) {
                int index = dragonY(block) - min;
                if (index >= 0 && index < states.length) states[index] = dragonMaterial(block);
            }
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(java.util.List<String> lines, RandomState state, BlockPos pos) {
        lines.add("Yan sanctuary");
    }
}
