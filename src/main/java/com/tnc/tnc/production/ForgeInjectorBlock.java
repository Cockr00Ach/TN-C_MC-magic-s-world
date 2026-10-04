package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Rear-facing, owner-checked mana inlet; never stores a second inventory. */
public final class ForgeInjectorBlock extends HorizontalDirectionalBlock {
    public ForgeInjectorBlock() {
        super(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.COPPER).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        if(player.getItemInHand(hand).getItem() instanceof com.tnc.tnc.life.pasture.ManaBottleItem bottle)
            return bottle.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
        Direction outward = state.getValue(FACING);
        BlockPos corePos = pos.relative(outward.getOpposite(), 2);
        if (!(level.getBlockEntity(corePos) instanceof MagicForgeBlockEntity forge)) {
            server.displayClientMessage(Component.literal("注能口尚未连上正面的炉芯。"), true);
            return InteractionResult.CONSUME;
        }
        String result;
        if (player.getItemInHand(hand).is(TNMod.MANA_ROOT_CORE.get()))
            result = forge.useRootCore(server, player.getItemInHand(hand), pos);
        else if (player.getItemInHand(hand).isEmpty()) result = forge.injectMana(server, pos);
        else result = "拿着蓄魔根芯右键注能口，或空手注入自己的魔力。";
        server.displayClientMessage(Component.literal(result), true);
        return InteractionResult.CONSUME;
    }
}
