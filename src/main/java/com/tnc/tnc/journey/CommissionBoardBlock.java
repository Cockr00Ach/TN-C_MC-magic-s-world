package com.tnc.tnc.journey;

import com.tnc.tnc.adventure.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class CommissionBoardBlock extends HorizontalDirectionalBlock {
    public CommissionBoardBlock(){super(Properties.copy(Blocks.DARK_OAK_PLANKS).strength(2).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(FACING).getAxis()==Direction.Axis.Z?Block.box(0,0,5,16,16,11):Block.box(5,0,0,11,16,16);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
        if(p instanceof ServerPlayer player&&TownServices.atBoard(player))CommissionService.open(player);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
