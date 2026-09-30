package com.tnc.tnc.prop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generic scene-prop block: the shape comes from the constructor, so tables / counters /
 * bottles / lamps all share this one class. Props may be larger than one block cell
 * (a 2x1 table, a 3x1 counter) - hence noOcclusion().
 */
public class TNPropBlock extends Block {

    private final VoxelShape shape;

    public TNPropBlock(VoxelShape shape) {
        this(shape, 0);
    }

    public TNPropBlock(VoxelShape shape, int light) {
        super((light > 0
                ? Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> light)
                : Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
        this.shape = shape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }
}