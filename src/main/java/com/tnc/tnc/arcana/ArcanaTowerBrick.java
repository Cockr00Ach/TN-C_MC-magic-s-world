package com.tnc.tnc.arcana;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
public final class ArcanaTowerBrick extends Block {
 public static final BooleanProperty FORMED=BooleanProperty.create("formed");
 public ArcanaTowerBrick(){super(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_BRICKS).strength(5).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FORMED,false));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FORMED);}
 @Override public RenderShape getRenderShape(BlockState s){return s.getValue(FORMED)?RenderShape.INVISIBLE:RenderShape.MODEL;}
 public static void shell(net.minecraft.world.level.Level l,net.minecraft.core.BlockPos core,boolean formed){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++){var p=core.offset(x,y,z);var s=l.getBlockState(p);if(s.is(ArcanaContent.TOWER_BRICK)&&s.getValue(FORMED)!=formed)l.setBlock(p,s.setValue(FORMED,formed),3);}}
}
