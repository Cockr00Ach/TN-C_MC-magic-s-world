package com.tnc.tnc.production;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
/** The same masonry gains input/output roles only in a complete forge. */
public final class ForgeBrickBlock extends BaseEntityBlock{
    public ForgeBrickBlock(){super(BlockBehaviour.Properties.of().strength(3F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS));}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ForgePortBlockEntity(p,s);}
}
