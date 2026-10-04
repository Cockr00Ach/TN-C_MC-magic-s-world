package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.home.HousingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** A stone-clinging crop and a household alarm made from its renewable fronds. */
public final class WarningMoss {
    public static Block MOSS;
    public static Block LANTERN;
    public static Item SPORE;
    public static Item FLAKE;
    public static Item LANTERN_ITEM;
    public static BlockEntityType<LanternEntity> LANTERN_ENTITY;
    static final int MOSS_RADIUS = 8;
    static final int LANTERN_RADIUS = 8;
    static final int UPGRADED_LANTERN_RADIUS = 12;
    private static final int CHECK_TICKS = 30;

    private WarningMoss() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    /** Entity indexing sees loaded mobs only; neither alarm accesses nor loads distant chunks. */
    static boolean hostileNearby(ServerLevel level, BlockPos pos, int radius) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius),
                mob -> mob instanceof Enemy && mob.isAlive() && !mob.isRemoved()).isEmpty();
    }

    public static final class MossBlock extends HorizontalDirectionalBlock {
        public static final BooleanProperty LIT = BooleanProperty.create("lit");
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
        private static final VoxelShape NORTH_SHAPE = Block.box(2, 2, 14, 14, 14, 16);
        private static final VoxelShape SOUTH_SHAPE = Block.box(2, 2, 0, 14, 14, 2);
        private static final VoxelShape EAST_SHAPE = Block.box(0, 2, 2, 2, 14, 14);
        private static final VoxelShape WEST_SHAPE = Block.box(14, 2, 2, 16, 14, 14);

        public MossBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.MOSS_CARPET).noCollission().randomTicks()
                    .lightLevel(state -> state.getValue(LIT) ? 9 : 0));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                    .setValue(LIT, false).setValue(AGE, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, LIT, AGE);
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return switch (state.getValue(FACING)) {
                case NORTH -> NORTH_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                default -> WEST_SHAPE;
            };
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction clickedFace = context.getClickedFace();
            if (clickedFace.getAxis().isHorizontal()) {
                BlockState clicked = defaultBlockState().setValue(FACING, clickedFace);
                if (clicked.canSurvive(context.getLevel(), context.getClickedPos())) return clicked;
            }
            for (Direction facing : context.getNearestLookingDirections()) {
                if (!facing.getAxis().isHorizontal()) continue;
                BlockState candidate = defaultBlockState().setValue(FACING, facing);
                if (candidate.canSurvive(context.getLevel(), context.getClickedPos())) return candidate;
            }
            return null;
        }
        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockState host = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
            // A planted warning mat needs a solid wall; players can add a stone or brick backing to a wooden home.
            return host.is(Blocks.STONE) || host.is(Blocks.COBBLESTONE)
                    || host.is(Blocks.MOSSY_COBBLESTONE) || host.is(Blocks.STONE_BRICKS)
                    || host.is(Blocks.MOSSY_STONE_BRICKS) || host.is(Blocks.DEEPSLATE)
                    || host.is(Blocks.COBBLED_DEEPSLATE) || host.is(Blocks.BRICKS);
        }
        @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                                net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
            return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                    ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }
        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
            if (!level.isClientSide && !state.is(previous.getBlock())) level.scheduleTick(pos, this, CHECK_TICKS);
        }
        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean danger = hostileNearby(level, pos, MOSS_RADIUS);
            if (state.getValue(LIT) != danger) level.setBlock(pos, state.setValue(LIT, danger), 3);
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
        @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(AGE) < 3 && random.nextInt(3) == 0)
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if (state.getValue(AGE) < 3) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && TownProtection.denied(serverPlayer, pos)) return InteractionResult.FAIL;
            Block.popResource(level, pos, new ItemStack(FLAKE));
            level.setBlock(pos, state.setValue(AGE, 1), 3);
            return InteractionResult.CONSUME;
        }
        @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }
        @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    public static final class LanternBlock extends BaseEntityBlock {
        public static final BooleanProperty LIT = BooleanProperty.create("lit");
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);
        public LanternBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.LANTERN).lightLevel(state -> state.getValue(LIT) ? 13 : 0));
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LanternEntity(pos, state); }
        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && placer instanceof Player player
                    && level.getBlockEntity(pos) instanceof LanternEntity lamp) lamp.claim(player.getUUID());
        }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
            if (!level.isClientSide && !state.is(previous.getBlock())) level.scheduleTick(pos, this, CHECK_TICKS);
        }
        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int range = level.getBlockEntity(pos) instanceof LanternEntity lamp ? lamp.range() : LANTERN_RADIUS;
            boolean danger = hostileNearby(level, pos, range);
            if (state.getValue(LIT) != danger) level.setBlock(pos, state.setValue(LIT, danger), 3);
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer server) || !(level.getBlockEntity(pos) instanceof LanternEntity lamp)) return InteractionResult.PASS;
            if (TownProtection.denied(server, pos) || !lamp.mayUse(server)) {
                server.displayClientMessage(Component.literal("这盏警路灯属于其他人或受保护区域。"), true);
                return InteractionResult.CONSUME;
            }
            if (lamp.owner == null) lamp.claim(player.getUUID());
            server.displayClientMessage(Component.literal(lamp.interact(server, player.getItemInHand(hand), player.isShiftKeyDown())), true);
            level.scheduleTick(pos, this, 1);
            return InteractionResult.CONSUME;
        }
        @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
            if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LanternEntity lamp)
                lamp.dropLens();
            super.onRemove(state, level, pos, next, moving);
        }
    }

    /** The removable lens is real inventory; normal lamps continue to detect loaded mobs. */
    public static final class LanternEntity extends BlockEntity {
        private UUID owner;
        private ItemStack lens = ItemStack.EMPTY;
        public LanternEntity(BlockPos pos, BlockState state) { super(LANTERN_ENTITY, pos, state); }
        public void claim(UUID id) { if (owner == null) { owner = id; setChanged(); } }
        public boolean mayUse(ServerPlayer player) {
            return owner == null || owner.equals(player.getUUID()) || HousingService.mayDecorate(player, worldPosition);
        }
        private static boolean validLens(ItemStack stack) {
            if (stack.isEmpty()) return false;
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null && key.getNamespace().equals(TNMod.MODID) && key.getPath().equals("warning_lens");
        }
        public int range() { return validLens(lens) ? UPGRADED_LANTERN_RADIUS : LANTERN_RADIUS; }
        public String interact(ServerPlayer player, ItemStack held, boolean sneaking) {
            if (validLens(held)) {
                if (!lens.isEmpty()) return "已装巡灯镜片；真实预警范围12格。";
                lens = held.copyWithCount(1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                setChanged();
                return "装入巡灯镜片：预警8→12格；潜行空手可拆。";
            }
            if (held.isEmpty() && sneaking && !lens.isEmpty()) {
                ItemStack removed = lens; lens = ItemStack.EMPTY;
                player.getInventory().add(removed);
                if (!removed.isEmpty()) Block.popResource(player.serverLevel(), worldPosition, removed);
                setChanged();
                return "巡灯镜片已拆下，预警范围恢复8格。";
            }
            return "警路灯 · " + range() + "格内有敌对生物时亮起；巡灯镜片可扩至12格。";
        }
        public void dropLens() {
            if (!lens.isEmpty() && level != null) {
                ItemStack removed = lens; lens = ItemStack.EMPTY;
                Block.popResource(level, worldPosition, removed); setChanged();
            }
        }
        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (owner != null) tag.putUUID("Owner", owner);
            if (!lens.isEmpty()) tag.put("WarningLens", lens.save(new CompoundTag()));
        }
        @Override public void load(CompoundTag tag) {
            super.load(tag);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            lens = tag.contains("WarningLens") ? ItemStack.of(tag.getCompound("WarningLens")) : ItemStack.EMPTY;
            if (!validLens(lens)) lens = ItemStack.EMPTY;
            else lens.setCount(1);
        }
    }

    public static final class MossSporeItem extends BlockItem {
        public MossSporeItem(Block block) { super(block, new Item.Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal("贴在石墙或砖墙上；附近八格有敌对生物时亮起。"));
            text.add(Component.literal("长成后右键采苔片，警戒植株会继续生长。"));
        }
    }

    public static final class WarningLanternItem extends BlockItem {
        public WarningLanternItem(Block block) { super(block, new Item.Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal("八格内有敌对生物时亮起；装巡灯镜片扩到十二格，潜行空手可拆。"));
        }
    }

    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> {
                MOSS = new MossBlock();
                LANTERN = new LanternBlock();
                helper.register(id("warning_moss"), MOSS);
                helper.register(id("warning_moss_lantern"), LANTERN);
            });
            event.register(Registries.ITEM, helper -> {
                SPORE = new MossSporeItem(MOSS);
                FLAKE = new Item(new Item.Properties());
                LANTERN_ITEM = new WarningLanternItem(LANTERN);
                helper.register(id("warning_moss_spore"), SPORE);
                helper.register(id("warning_moss_flake"), FLAKE);
                helper.register(id("warning_moss_lantern"), LANTERN_ITEM);
            });
            event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
                LANTERN_ENTITY = BlockEntityType.Builder.of(LanternEntity::new, LANTERN).build(null);
                helper.register(id("warning_lantern"), LANTERN_ENTITY);
            });
        }
        @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
            if (!event.getTabKey().equals(TNMod.TNC_TAB.getKey())) return;
            event.accept(SPORE);
            event.accept(FLAKE);
            event.accept(LANTERN_ITEM);
        }
    }
}
