package com.tnc.tnc.prop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 4×2 赌博桌（可放置的方块道具）。
 *
 * <h2>尺寸契约（与 {@code tools/gen_gamble_table.py} 头部一致，改模型必须同步这里 ✗）</h2>
 * <ul>
 *   <li>占地 <b>4.0 × 2.0 格</b>（模型 x 0..64、z 0..32 单位）；总高 <b>0.9375 格</b>（y 0..15 单位）</li>
 *   <li>碰撞箱就是整张桌子 ✓（{@link #SHAPE}）—— 比一个方块格大是<b>故意</b>的：
 *       4 格宽的桌子塞不进 1×1 的格子里 ✗，所以它一个方块渲染 4×2 的形状 ✓，
 *       放置时模型从"被放下的那一格"向 <b>+x / +z</b> 展开 ✓</li>
 *   <li>{@code noOcclusion()}：形状超出格子，不这样设会剔掉相邻面 ✗</li>
 * </ul>
 */
public class TNGambleTableBlock extends Block {

    /** 4.0 × 0.9375 × 2.0 格（单位：格）。 */
    private static final VoxelShape SHAPE =
            Shapes.box(0.0D, 0.0D, 0.0D, 4.0D, 0.9375D, 2.0D);

    public TNGambleTableBlock() {
        super(Properties.of()
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }
}
