package com.tnc.tnc.production;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The open crown above the hollow chamber. */
public final class ForgeExhaustBlock extends Block {
    public ForgeExhaustBlock() {
        super(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.COPPER).noOcclusion());
    }
}
