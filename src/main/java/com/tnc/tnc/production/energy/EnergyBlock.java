package com.tnc.tnc.production.energy;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.life.pasture.ManaBottleItem;
import com.tnc.tnc.life.pasture.PastureProductItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Industrial outdoor device; residential decorative lamps remain merchant furniture. */
public final class EnergyBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FOCUSED = BooleanProperty.create("focused");
    private final EnergyBlockEntity.Kind kind;

    EnergyBlock(EnergyBlockEntity.Kind kind) {
        super(BlockBehaviour.Properties.of().strength(kind == EnergyBlockEntity.Kind.CABLE ? 1.5f : 2.5f)
                .sound(kind == EnergyBlockEntity.Kind.LAMP ? SoundType.LANTERN : SoundType.COPPER)
                .lightLevel(state -> kind == EnergyBlockEntity.Kind.LAMP && state.getValue(LIT)
                        ? state.getValue(FOCUSED) ? 15 : 12 : 0));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FOCUSED, false));
    }

    EnergyBlockEntity.Kind kind() { return kind; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FOCUSED);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyBlockEntity(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != EnergyContent.ENERGY_ENTITY || kind == EnergyBlockEntity.Kind.CABLE
                ? null : (world, pos, current, be) -> EnergyBlockEntity.serverTick(world, pos, current, (EnergyBlockEntity) be);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof EnergyBlockEntity node)
            node.claim(EnergyPermissions.ownerForPlacement(player, pos));
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof EnergyBlockEntity node)) return InteractionResult.PASS;
        if (TownProtection.denied(serverPlayer, pos) || !node.mayUse(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("这台装置属于其他人或受保护区域。"), true);
            return InteractionResult.CONSUME;
        }
        if (node.owner() == null) node.claim(EnergyPermissions.ownerForPlacement(serverPlayer, pos));
        if (player.getItemInHand(hand).getItem() instanceof ManaBottleItem bottle)
            return bottle.useOn(new UseOnContext(player, hand, hit));
        if (player.getItemInHand(hand).getItem() instanceof PastureProductItem product
                && product.productId().equals("storm_crystal"))
            return product.useOn(new UseOnContext(player, hand, hit));
        String message = node.interact(serverPlayer, player.getItemInHand(hand), player.isShiftKeyDown());
        serverPlayer.displayClientMessage(Component.literal(message), true);
        return InteractionResult.CONSUME;
    }

    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof EnergyBlockEntity node) {
            ItemStack dropped = new ItemStack(kind.item());
            node.writePortableState(dropped);
            popResource(level, pos, dropped);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
