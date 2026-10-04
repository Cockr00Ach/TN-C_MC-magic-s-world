package com.tnc.tnc.production;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Front centre control block of the hollow three-layer forge. */
public final class MagicForgeBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public MagicForgeBlock() {
        super(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.COPPER).lightLevel(state -> state.getValue(LIT) ? 8 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MagicForgeBlockEntity forge)
            forge.setOwner(player.getUUID());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicForgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != MagicForgeContent.FORGE_ENTITY ? null
                : (world, pos, blockState, entity) -> MagicForgeBlockEntity.serverTick(world, pos, blockState, (MagicForgeBlockEntity) entity);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof MagicForgeBlockEntity forge)) return InteractionResult.PASS;
        if (!forge.canOperate(serverPlayer, true)) {
            serverPlayer.displayClientMessage(Component.literal("这座炉台不属于你，或当前位置禁止使用。"), true);
            return InteractionResult.CONSUME;
        }
        if (player.getItemInHand(hand).is(com.tnc.tnc.TNMod.MANA_ROOT_CORE.get())) {
            serverPlayer.displayClientMessage(Component.literal(forge.useRootCore(serverPlayer, player.getItemInHand(hand), pos)), true);
            return InteractionResult.CONSUME;
        }
        if (player.getItemInHand(hand).getItem() instanceof com.tnc.tnc.life.pasture.ManaBottleItem bottle)
            return bottle.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit));
        NetworkHooks.openScreen(serverPlayer, forge, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof MagicForgeBlockEntity forge) {
            if (!level.isClientSide) Containers.dropContents(level, pos, forge);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MagicForgeBlockEntity forge ? forge.comparatorSignal() : 0;
    }
}
