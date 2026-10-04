package com.tnc.tnc.production;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Input and output bricks are the same brick family, each with a real sided port. */
public final class ForgePortBlock extends BaseEntityBlock {
    private final boolean output;

    public ForgePortBlock(boolean output) {
        super(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS));
        this.output = output;
    }

    public boolean output() { return output; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgePortBlockEntity(pos, state);
    }
}
