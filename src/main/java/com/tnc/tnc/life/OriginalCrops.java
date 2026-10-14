package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The first living specimens of the travel-and-home collection. Registration is
 * performed on the MOD bus so this class can remain independent of TNMod's
 * central registry and of the author's quest book.
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class OriginalCrops {
    private static final Set<Long> SCOUTED_CHUNKS = new HashSet<>();
    public static Block ROAD_BELL_CROP;
    public static Block NIGHT_GOURD_CROP;
    public static Block TIDE_REED_CROP;
    public static Block WILD_ROAD_BELL;
    public static Block WILD_NIGHT_GOURD;
    public static Block WILD_TIDE_REED;
    public static Item ROAD_BELL_SEED;
    public static Item ROAD_BELL_EAR;
    public static Item NIGHT_GOURD_SEED;
    public static Item NIGHT_GOURD;
    public static Item TIDE_REED_SEED;
    public static Item TIDE_REED_STEM;
    public static Item ROAD_BELL_WILD_SAMPLE;
    public static Item NIGHT_GOURD_WILD_SAMPLE;
    public static Item TIDE_REED_WILD_SAMPLE;
    public static Item ROAD_BELL_TRAVEL_BREAD;

    private OriginalCrops() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    /**
     * The first seeds are found by looking closely at an appropriate habitat.
     * It never consumes an original loot drop and does not depend on a quest.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void findWildSeeds(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null || player.isCreative() || player.isSpectator()) return;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && com.tnc.tnc.home.TownProtection.denied(serverPlayer, event.getPos())) return;
        BlockPos pos = event.getPos();
        Block block = event.getState().getBlock();
        if ((block == Blocks.GRASS || block == Blocks.FERN) && nearPath(level, pos)
                && level.random.nextInt(12) == 0) {
            Block.popResource(level, pos, ROAD_BELL_SEED.getDefaultInstance());
        } else if (block == Blocks.BROWN_MUSHROOM && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) < 8
                && level.random.nextInt(8) == 0) {
            Block.popResource(level, pos, NIGHT_GOURD_SEED.getDefaultInstance());
        } else if ((block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS)
                && level.random.nextInt(10) == 0) {
            Block.popResource(level, pos, TIDE_REED_SEED.getDefaultInstance());
        }
    }

    private static boolean nearPath(ServerLevel level, BlockPos pos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (level.getBlockState(pos.offset(dx, -1, dz)).is(Blocks.DIRT_PATH)) return true;
            }
        }
        return false;
    }

    /**
     * A sparse field encounter rather than a biome modifier: a biome modifier
     * cannot distinguish plains on the authored sky island from natural plains.
     * The island anchor and a 900-block buffer are mandatory before any write.
     */
    @SubscribeEvent
    public static void scoutWildPlants(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 80 != 0) return;
        ServerLevel level = server.overworld();
        BlockPos town = com.tnc.tnc.world.SkyIslandSavedData.get(level).anchorPos("CENTER");
        if (town == null) return;
        for (var player : level.players()) {
            if (player.isSpectator() || outsideTown(player.blockPosition(), town) == false) continue;
            int cx = player.chunkPosition().x + level.random.nextInt(5) - 2;
            int cz = player.chunkPosition().z + level.random.nextInt(5) - 2;
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            // Place visible wild specimens only on newly visited wilderness.
            // An inhabited or block-entity-bearing chunk may already contain a
            // player's imported build even though its air and soil look natural.
            if (chunk == null || chunk.getInhabitedTime() > 1200
                    || !chunk.getBlockEntitiesPos().isEmpty()) continue;
            BlockPos mid = new BlockPos(cx * 16 + 8, level.getSeaLevel(), cz * 16 + 8);
            ResourceLocation biome = level.getBiome(mid).unwrapKey().map(key -> key.location()).orElse(null);
            Block specimen = wildForBiome(biome);
            if (specimen == null) continue;
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
            if (SCOUTED_CHUNKS.contains(chunkKey)) continue;
            int planted = 0;
            for (int attempt = 0; attempt < 48 && planted < 1; attempt++) {
                int x = cx * 16 + level.random.nextInt(16);
                int z = cz * 16 + level.random.nextInt(16);
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, 0, z));
                BlockState plantedState = wildState(specimen);
                if (!outsideTown(pos, town)
                        || com.tnc.tnc.home.TownProtection.hazard(level, pos)
                        || com.tnc.tnc.home.HousingService.plotAt(level, pos) != null
                        || !level.getBlockState(pos).isAir()
                        || !plantedState.canSurvive(level, pos)) continue;
                level.setBlock(pos, plantedState, 3);
                planted++;
            }
            // A dry swamp patch or crowded terrain can be retried while still
            // newly explored. Never consume a chunk's one encounter on a miss.
            if (planted > 0) SCOUTED_CHUNKS.add(chunkKey);
        }
    }

    private static boolean outsideTown(BlockPos pos, BlockPos town) {
        long dx = (long) pos.getX() - town.getX();
        long dz = (long) pos.getZ() - town.getZ();
        return dx * dx + dz * dz > 900L * 900L;
    }

    private static Block wildForBiome(ResourceLocation biome) {
        if (biome == null || !biome.getNamespace().equals("minecraft")) return null;
        return switch (biome.getPath()) {
            case "plains", "meadow", "sunflower_plains" -> WILD_ROAD_BELL;
            case "dark_forest", "old_growth_pine_taiga" -> WILD_NIGHT_GOURD;
            case "swamp", "mangrove_swamp" -> WILD_TIDE_REED;
            case "mushroom_fields", "old_growth_spruce_taiga" -> UncommonMagicPlants.HUSHCAP;
            case "jungle", "sparse_jungle", "bamboo_jungle" -> UncommonMagicPlants.RAINLETTER;
            case "forest", "birch_forest" -> com.tnc.tnc.life.ecology.EcologyContent.VERDANT;
            default -> null;
        };
    }

    private static BlockState wildState(Block block) {
        if(block==com.tnc.tnc.life.ecology.EcologyContent.VERDANT)return block.defaultBlockState().setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true).setValue(com.tnc.tnc.life.ecology.VerdantVeinBlock.AGE,3);
        if (block == UncommonMagicPlants.HUSHCAP)
            return block.defaultBlockState().setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true).setValue(UncommonMagicPlants.HushcapBlock.AGE, 3)
                    .setValue(UncommonMagicPlants.HushcapBlock.QUIET, 3);
        if (block == UncommonMagicPlants.RAINLETTER)
            return block.defaultBlockState().setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,true).setValue(UncommonMagicPlants.RainletterBlock.AGE, 3)
                    .setValue(UncommonMagicPlants.RainletterBlock.WET, true);
        return block.defaultBlockState();
    }

    @SubscribeEvent public static void clearScouting(ServerStoppedEvent event) { SCOUTED_CHUNKS.clear(); }

    private static BlockBehaviour.Properties cropProperties() {
        return BlockBehaviour.Properties.copy(Blocks.WHEAT).noCollission().randomTicks();
    }

    public static final class RoadBellCrop extends CropBlock {
        public RoadBellCrop() { super(cropProperties()); }
        @Override protected Item getBaseSeedId() { return ROAD_BELL_SEED; }
        @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return l.getBlockState(p.below()).is(BlockTags.DIRT)||l.getBlockState(p.below()).is(Blocks.FARMLAND);}
        @Override public void randomTick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
            if(!isMaxAge(s)&&r.nextInt(6)==0)l.setBlock(p,s.setValue(AGE,getAge(s)+1),2);
        }
    }

    /** This gourd can live on normal farmland, but grows only after dusk or under a roof. */
    public static final class NightGourdCrop extends CropBlock {
        public NightGourdCrop() {
            super(cropProperties().lightLevel(state -> state.getValue(AGE) >= 6 ? 5 : 0));
        }
        @Override protected Item getBaseSeedId() { return NIGHT_GOURD_SEED; }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).is(BlockTags.DIRT)||level.getBlockState(pos.below()).is(Blocks.FARMLAND);
        }
        static boolean darkEnough(int skyLight, boolean day) {
            return skyLight < 8 || !day;
        }
        private static boolean darkEnough(LevelReader level, BlockPos pos) {
            return darkEnough(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos),
                    !(level instanceof Level world) || world.isDay());
        }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (isMaxAge(state)) return;
            // Vanilla CropBlock demands light level 9 even after dusk, which
            // would make a true night crop permanently sterile. Keep fertile
            // farmland useful without that unrelated daylight requirement.
            BlockState soil = level.getBlockState(pos.below());
            int interval = soil.is(Blocks.FARMLAND) && soil.getValue(FarmBlock.MOISTURE) > 0 ? 5 : 9;
            if (random.nextInt(interval) == 0) {
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
            }
        }
        @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
            return super.isValidBonemealTarget(level, pos, state, isClient);
        }
    }

    /** A shore crop: plant on damp ground with neighbouring water, never on farmland. */
    public static final class TideReedCrop extends BushBlock {
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
        private static final VoxelShape[] SHAPES = {
                Block.box(5, 0, 5, 11, 5, 11),
                Block.box(4, 0, 4, 12, 9, 12),
                Block.box(3, 0, 3, 13, 14, 13),
                Block.box(2, 0, 2, 14, 16, 14)
        };
        public TideReedCrop() {
            super(BlockBehaviour.Properties.copy(Blocks.FERN).noCollission().randomTicks());
            registerDefaultState(stateDefinition.any().setValue(AGE, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AGE);
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(AGE)];
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            var soil=level.getBlockState(pos.below());return soil.is(BlockTags.DIRT)||soil.is(Blocks.FARMLAND)||soil.is(Blocks.SAND)||soil.is(Blocks.CLAY)||soil.is(Blocks.MUD);
        }
        @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(AGE) < 3 && random.nextInt(level.isRainingAt(pos.above()) ? 3 : 6) == 0) {
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
            }
        }
    }

    private static boolean isWetShore(LevelReader level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        if (!(soil.is(BlockTags.DIRT) || soil.is(Blocks.SAND) || soil.is(Blocks.RED_SAND)
                || soil.is(Blocks.CLAY) || soil.is(Blocks.MUD))) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            FluidState beside = level.getFluidState(pos.below().relative(direction));
            if (beside.is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private enum Habitat { GRASSLAND, WOODLAND, WET_SHORE }

    /** A visible, harvestable wild parent; the farm version has its own growth rules. */
    public static final class WildSpecimen extends BushBlock {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
        private final Habitat habitat;
        private WildSpecimen(Habitat habitat) {
            super(BlockBehaviour.Properties.copy(Blocks.FERN).noCollission()
                    .lightLevel(state -> habitat == Habitat.WOODLAND ? 5 : 0));
            this.habitat = habitat;
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            if (habitat == Habitat.WET_SHORE) return isWetShore(level,pos);
            return level.getBlockState(pos.below()).is(BlockTags.DIRT);
        }
    }

    public static final class Registration {
        private Registration() {}
        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> {
                ROAD_BELL_CROP = new RoadBellCrop();
                NIGHT_GOURD_CROP = new NightGourdCrop();
                TIDE_REED_CROP = new TideReedCrop();
                WILD_ROAD_BELL = new WildSpecimen(Habitat.GRASSLAND);
                WILD_NIGHT_GOURD = new WildSpecimen(Habitat.WOODLAND);
                WILD_TIDE_REED = new WildSpecimen(Habitat.WET_SHORE);
                helper.register(id("road_bell_crop"), ROAD_BELL_CROP);
                helper.register(id("night_gourd_crop"), NIGHT_GOURD_CROP);
                helper.register(id("tide_reed_crop"), TIDE_REED_CROP);
                helper.register(id("wild_road_bell"), WILD_ROAD_BELL);
                helper.register(id("wild_night_gourd"), WILD_NIGHT_GOURD);
                helper.register(id("wild_tide_reed"), WILD_TIDE_REED);
            });
            event.register(Registries.ITEM, helper -> {
                ROAD_BELL_SEED = new ItemNameBlockItem(ROAD_BELL_CROP, new Item.Properties());
                ROAD_BELL_EAR = new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(3).saturationMod(0.35F).build()));
                NIGHT_GOURD_SEED = new ItemNameBlockItem(NIGHT_GOURD_CROP, new Item.Properties());
                NIGHT_GOURD = new ItemNameBlockItem(NIGHT_GOURD_CROP,new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(4).saturationMod(0.45F).build()));
                TIDE_REED_SEED = new ItemNameBlockItem(TIDE_REED_CROP, new Item.Properties());
                TIDE_REED_STEM = new Item(new Item.Properties());
                ROAD_BELL_WILD_SAMPLE = new Item(new Item.Properties().stacksTo(16));
                NIGHT_GOURD_WILD_SAMPLE = new Item(new Item.Properties().stacksTo(16));
                TIDE_REED_WILD_SAMPLE = new Item(new Item.Properties().stacksTo(16));
                ROAD_BELL_TRAVEL_BREAD = new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(10).saturationMod(0.8F)
                        .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 * 30, 0), 1.0F)
                        .build()));
                helper.register(id("road_bell_seed"), ROAD_BELL_SEED);
                helper.register(id("road_bell_ear"), ROAD_BELL_EAR);
                helper.register(id("night_gourd_seed"), NIGHT_GOURD_SEED);
                helper.register(id("night_gourd"), NIGHT_GOURD);
                helper.register(id("tide_reed_seed"), TIDE_REED_SEED);
                helper.register(id("tide_reed_stem"), TIDE_REED_STEM);
                helper.register(id("road_bell_wild_sample"), ROAD_BELL_WILD_SAMPLE);
                helper.register(id("night_gourd_wild_sample"), NIGHT_GOURD_WILD_SAMPLE);
                helper.register(id("tide_reed_wild_sample"), TIDE_REED_WILD_SAMPLE);
                helper.register(id("road_bell_travel_bread"), ROAD_BELL_TRAVEL_BREAD);
            });
        }
        @SubscribeEvent
        public static void creative(BuildCreativeModeTabContentsEvent event) {
            if (!event.getTabKey().equals(TNMod.TNC_TAB.getKey())) return;
            event.accept(ROAD_BELL_SEED);
            event.accept(ROAD_BELL_EAR);
            event.accept(NIGHT_GOURD_SEED);
            event.accept(NIGHT_GOURD);
            event.accept(TIDE_REED_SEED);
            event.accept(TIDE_REED_STEM);
            event.accept(ROAD_BELL_WILD_SAMPLE);
            event.accept(NIGHT_GOURD_WILD_SAMPLE);
            event.accept(TIDE_REED_WILD_SAMPLE);
            event.accept(ROAD_BELL_TRAVEL_BREAD);
        }
    }
}
