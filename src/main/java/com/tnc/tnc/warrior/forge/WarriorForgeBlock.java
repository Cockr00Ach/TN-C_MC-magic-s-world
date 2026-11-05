package com.tnc.tnc.warrior.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class WarriorForgeBlock extends BaseEntityBlock {
    public enum Kind { HEARTH, ANVIL, BATH }
    public final Kind kind;
    public WarriorForgeBlock(Kind kind) {
        super(BlockBehaviour.Properties.of().strength(3, 6).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Block.box(0, 0, 0, 16, kind == Kind.ANVIL ? 13 : 12, 16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new WarriorForgeEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> type) {
        return l.isClientSide ? null : createTickerHelper(type, WarriorForgeContent.ENTITY, WarriorForgeEntity::tick);
    }
    @Override public InteractionResult use(BlockState s, Level l, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // Let the held hammer receive vanilla useOn/start/release packets.
        if (player.getMainHandItem().is(WarriorForgeContent.HAMMER) && !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer p && l.getBlockEntity(pos) instanceof WarriorForgeEntity be) be.interact(p, hit);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof WarriorForgeEntity be)
            be.dropWork();
        super.onRemove(old, level, pos, next, moving);
    }
}
