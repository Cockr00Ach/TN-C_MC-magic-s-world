package com.tnc.tnc.production;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Copper rune posts become visibly active only when all 26 pieces are present. */
public final class ForgeFrameBlock extends Block {
    public static final BooleanProperty GLOW = BooleanProperty.create("glow");

    public ForgeFrameBlock() {
        super(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()
                .sound(SoundType.COPPER).noOcclusion().lightLevel(state -> state.getValue(GLOW) ? 3 : 0));
        registerDefaultState(stateDefinition.any().setValue(GLOW, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }
}
