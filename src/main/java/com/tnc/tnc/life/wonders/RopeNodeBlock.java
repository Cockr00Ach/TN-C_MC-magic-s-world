package com.tnc.tnc.life.wonders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

public final class RopeNodeBlock extends BaseEntityBlock {
    public RopeNodeBlock(){super(BlockBehaviour.Properties.copy(Blocks.VINE).noCollission().noOcclusion().noLootTable());}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new RopeNodeEntity(p,s);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(6,0,6,10,16,10);}
}
