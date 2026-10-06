package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;

/** Two small magical habitats with different care rhythms: stillness and rain. */
public final class UncommonMagicPlants {
    public static Block HUSHCAP;
    public static Block RAINLETTER;
    public static Item HUSHCAP_SPORE;
    public static Item HUSHCAP_SLICE;
    public static Item HUSHCAP_BROTH;
    public static Item RAINLETTER_SEED;
    public static Item RAINLETTER_BERRY;
    public static Item RAINLETTER_CORDIAL;

    private UncommonMagicPlants() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    /** Called once for each registry on TNMod's MOD event bus. No auto-subscriber. */
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            if (HUSHCAP != null) return;
            HUSHCAP = new HushcapBlock();
            RAINLETTER = new RainletterBlock();
            helper.register(id("hushcap_mushroom"), HUSHCAP);
            helper.register(id("rainletter_bush"), RAINLETTER);
        });
        event.register(Registries.ITEM, helper -> {
            if (HUSHCAP_SPORE != null) return;
            HUSHCAP_SPORE = new PlantingItem(HUSHCAP, "种在苔藓、菌丝或洞石上；安静一会儿才会舒展生长。");
            HUSHCAP_SLICE = new Item(new Item.Properties());
            HUSHCAP_BROTH = new ReturnContainerFood(new Item.Properties().stacksTo(1)
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(5)
                            .saturationMod(0.6F).effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 60, 0), 1.0F)
                            .build()), Items.BOWL, UseAnim.EAT,
                    "暖胃的洞行蕈汤，短暂看清暗处；不是隐身术。");
            RAINLETTER_SEED = new PlantingItem(RAINLETTER,
                    "种在泥土或耕地；雨水会育果，晴天可用清水瓶浇灌。");
            RAINLETTER_BERRY = new ItemNameBlockItem(RAINLETTER,new Item.Properties().food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationMod(0.35F).build()));
            RAINLETTER_CORDIAL = new ReturnContainerFood(new Item.Properties().stacksTo(16)
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2)
                            .saturationMod(0.25F).alwaysEat()
                            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.WATER_BREATHING, 20 * 30, 0), 1.0F)
                            .build()), Items.GLASS_BOTTLE, UseAnim.DRINK,
                    "雨书莓露让旅人在水下多呼吸一会儿。饮后留下空瓶。");
            helper.register(id("hushcap_spore"), HUSHCAP_SPORE);
            helper.register(id("hushcap_slice"), HUSHCAP_SLICE);
            helper.register(id("hushcap_broth"), HUSHCAP_BROTH);
            helper.register(id("rainletter_seed"), RAINLETTER_SEED);
            helper.register(id("rainletter_berry"), RAINLETTER_BERRY);
            helper.register(id("rainletter_cordial"), RAINLETTER_CORDIAL);
        });
    }

    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(TNMod.TNC_TAB.getKey())) return;
        event.accept(HUSHCAP_SPORE);
        event.accept(HUSHCAP_SLICE);
        event.accept(HUSHCAP_BROTH);
        event.accept(RAINLETTER_SEED);
        event.accept(RAINLETTER_BERRY);
        event.accept(RAINLETTER_CORDIAL);
    }

    private static boolean permitted(Player player, BlockPos pos) {
        return !(player instanceof ServerPlayer serverPlayer) || !TownProtection.denied(serverPlayer, pos);
    }

    /** The cap folds shut when a living creature moves nearby, then needs quiet time to reopen. */
    public static final class HushcapBlock extends BushBlock {
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
        public static final IntegerProperty QUIET = IntegerProperty.create("quiet", 0, 3);
        private static final VoxelShape[] SHAPES = {
                Block.box(5, 0, 5, 11, 5, 11), Block.box(4, 0, 4, 12, 8, 12),
                Block.box(3, 0, 3, 13, 11, 13), Block.box(2, 0, 2, 14, 13, 14)
        };
        public HushcapBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM).noCollission().randomTicks()
                    .lightLevel(state -> state.getValue(AGE) == 3 && state.getValue(QUIET) == 3 ? 4 : 0));
            registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(QUIET, 0).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AGE, QUIET, com.tnc.tnc.life.routes.RoutePlantBlock.WILD);
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(AGE)];
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockState substrate = level.getBlockState(pos.below());
            return substrate.is(BlockTags.DIRT) || substrate.is(Blocks.FARMLAND) || substrate.is(Blocks.MUD) || substrate.is(Blocks.MYCELIUM) || substrate.is(Blocks.PODZOL)
                    || substrate.is(Blocks.MOSS_BLOCK) || substrate.is(Blocks.STONE)
                    || substrate.is(Blocks.DEEPSLATE) || substrate.is(Blocks.COBBLED_DEEPSLATE)
                    || substrate.is(Blocks.TUFF);
        }
        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
            if (!level.isClientSide && !state.is(previous.getBlock())) level.scheduleTick(pos, this, 20);
        }
        static boolean movingNearby(ServerLevel level, BlockPos pos) {
            return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(4), entity ->
                    entity.isAlive() && !(entity instanceof Player player && player.isSpectator())
                            && entity.getDeltaMovement().lengthSqr() > 0.0025).isEmpty();
        }
        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int quiet = movingNearby(level, pos) ? 0 : Math.min(3, state.getValue(QUIET) + 1);
            if (quiet != state.getValue(QUIET)) level.setBlock(pos, state.setValue(QUIET, quiet), 3);
            level.scheduleTick(pos, this, 20);
        }
        @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(AGE) < 3 && random.nextInt(4) == 0)
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if (state.getValue(AGE) < 3) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!permitted(player, pos)) return InteractionResult.FAIL;
            Block.popResource(level, pos, new ItemStack(HUSHCAP_SLICE, 2));
            if(player instanceof ServerPlayer p)com.tnc.tnc.life.routes.RouteProgress.award(p,"harvest/hushcap_mushroom");
            level.setBlock(pos, state.setValue(AGE, 1).setValue(QUIET, 0), 3);
            return InteractionResult.CONSUME;
        }
    }

    /** A berry bush whose next growth step requires rain or one hand-poured water bottle. */
    public static final class RainletterBlock extends BushBlock {
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
        public static final BooleanProperty WET = BooleanProperty.create("wet");
        private static final VoxelShape[] SHAPES = {
                Block.box(5, 0, 5, 11, 5, 11), Block.box(4, 0, 4, 12, 8, 12),
                Block.box(2, 0, 2, 14, 12, 14), Block.box(1, 0, 1, 15, 15, 15)
        };
        public RainletterBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.SWEET_BERRY_BUSH).noCollission().randomTicks());
            registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(WET, false).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AGE, WET, com.tnc.tnc.life.routes.RoutePlantBlock.WILD);
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(AGE)];
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockState substrate = level.getBlockState(pos.below());
            return substrate.is(BlockTags.DIRT) || substrate.is(Blocks.FARMLAND);
        }
        @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(AGE) >= 3) return;
            boolean raining = level.isRainingAt(pos.above());
            {
                if (random.nextInt(3) == 0)
                    level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1).setValue(WET, false), 2);
                else if (raining && !state.getValue(WET)) level.setBlock(pos, state.setValue(WET, true), 2);
            }
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            ItemStack held = player.getItemInHand(hand);
            boolean waterBottle = held.is(Items.POTION) && PotionUtils.getPotion(held) == Potions.WATER;
            if (state.getValue(AGE) < 3 && waterBottle) {
                if (state.getValue(WET)) return InteractionResult.CONSUME;
                if (level.isClientSide) return InteractionResult.SUCCESS;
                if (!permitted(player, pos)) return InteractionResult.FAIL;
                level.setBlock(pos, state.setValue(WET, true), 3);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
                    if (held.isEmpty()) player.setItemInHand(hand, empty);
                    else if (!player.getInventory().add(empty)) player.drop(empty, false);
                }
                return InteractionResult.CONSUME;
            }
            if (state.getValue(AGE) < 3) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!permitted(player, pos)) return InteractionResult.FAIL;
            Block.popResource(level, pos, new ItemStack(RAINLETTER_BERRY, 2));
            if(player instanceof ServerPlayer p)com.tnc.tnc.life.routes.RouteProgress.award(p,"harvest/rainletter_bush");
            level.setBlock(pos, state.setValue(AGE, 1).setValue(WET, false), 3);
            return InteractionResult.CONSUME;
        }
    }

    public static final class PlantingItem extends ItemNameBlockItem {
        private final String hint;
        public PlantingItem(Block block, String hint) { super(block, new Item.Properties()); this.hint = hint; }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal(hint));
        }
    }

    public static final class ReturnContainerFood extends Item {
        private final Item container;
        private final UseAnim animation;
        private final String hint;
        public ReturnContainerFood(Item.Properties properties, Item container, UseAnim animation, String hint) {
            super(properties);
            this.container = container;
            this.animation = animation;
            this.hint = hint;
        }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return animation; }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal(hint));
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof Player player && !player.getAbilities().instabuild) {
                ItemStack empty = new ItemStack(container);
                if (result.isEmpty()) return empty;
                if (!level.isClientSide && !player.getInventory().add(empty)) player.drop(empty, false);
            }
            return result;
        }
    }
}
