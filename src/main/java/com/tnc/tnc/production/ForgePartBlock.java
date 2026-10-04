package com.tnc.tnc.production;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Real, independently breakable structure pieces; each drops only itself. */
public final class ForgePartBlock extends Block {
    public ForgePartBlock(boolean copper) {
        super(BlockBehaviour.Properties.of().strength(copper ? 3.5f : 3.0f)
                .requiresCorrectToolForDrops().sound(copper ? SoundType.COPPER : SoundType.DEEPSLATE_BRICKS));
    }
}
