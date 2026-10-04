package com.tnc.tnc.life.botanical;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
public final class BotanicalSegment extends Block {
    public static final IntegerProperty HEIGHT=IntegerProperty.create("segment",1,3);
    private final boolean climb;
    public BotanicalSegment(boolean climb){super(BlockBehaviour.Properties.copy(Blocks.VINE).noCollission().noOcclusion());this.climb=climb;registerDefaultState(stateDefinition.any().setValue(HEIGHT,1));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(HEIGHT);}
    @Override public boolean isLadder(BlockState s,LevelReader l,BlockPos p,LivingEntity entity){var base=l.getBlockState(p.below(s.getValue(HEIGHT)));return climb&&base.is(BotanicalContent.BLOCKS.get("ladder_vine"));}
}
