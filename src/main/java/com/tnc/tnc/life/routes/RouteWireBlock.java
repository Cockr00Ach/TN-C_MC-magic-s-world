package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;
public final class RouteWireBlock extends RouteNodeBlock {
 public static final java.util.Map<Direction,BooleanProperty> ARMS=java.util.Map.of(Direction.DOWN,BooleanProperty.create("down"),Direction.UP,BooleanProperty.create("up"),Direction.NORTH,BooleanProperty.create("north"),Direction.SOUTH,BooleanProperty.create("south"),Direction.WEST,BooleanProperty.create("west"),Direction.EAST,BooleanProperty.create("east"));
 public RouteWireBlock(RouteKind kind){super(kind);var state=defaultBlockState();for(var arm:ARMS.values())state=state.setValue(arm,false);registerDefaultState(state);}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);ARMS.values().forEach(b::add);}
 @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return connections(defaultBlockState(),c.getLevel(),c.getClickedPos());}
 /** Two endpoints, chosen from six possible orientations. No fiber junctions. */
 private boolean accepts(BlockGetter l,BlockPos p,Direction d){var q=p.relative(d);if(!RouteConnections.compatible(l,p,q))return false;var neighbour=l.getBlockState(q);if(!(neighbour.getBlock() instanceof RouteWireBlock))return true;
  if(neighbour.getValue(ARMS.get(d.getOpposite())))return true;int degree=0;for(var side:Direction.values())if(neighbour.getValue(ARMS.get(side))&&RouteConnections.compatible(l,q,q.relative(side))){var back=l.getBlockState(q.relative(side));if(!(back.getBlock() instanceof RouteWireBlock)||back.getValue(ARMS.get(side.getOpposite())))degree++;}return degree<2;
 }
 private BlockState connections(BlockState s,BlockGetter l,BlockPos p){var chosen=java.util.EnumSet.noneOf(Direction.class);for(var d:Direction.values())if(s.getValue(ARMS.get(d))&&accepts(l,p,d)&&chosen.size()<2)chosen.add(d);for(var d:Direction.values())if(chosen.size()<2&&accepts(l,p,d))chosen.add(d);for(var d:Direction.values())s=s.setValue(ARMS.get(d),chosen.contains(d));return s;}
 public void refresh(Level l,BlockPos p){if(l.isClientSide)return;var old=l.getBlockState(p);if(old.getBlock()!=this)return;var next=connections(old,l,p);if(next!=old)l.setBlock(p,next,3);}
 @Override public BlockState updateShape(BlockState s,Direction d,BlockState other,LevelAccessor l,BlockPos p,BlockPos q){return connections(s,l,p);}
 @Override public void neighborChanged(BlockState s,Level l,BlockPos p,Block b,BlockPos q,boolean moving){refresh(l,p);}
 @Override public void tick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos p,net.minecraft.util.RandomSource r){refresh(l,p);}
 @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){VoxelShape shape=Block.box(6,6,6,10,10,10);for(var d:Direction.values())if(s.getValue(ARMS.get(d))){var min=new BlockPos(6,6,6);var max=new BlockPos(10,10,10);min=d.getAxisDirection()==Direction.AxisDirection.NEGATIVE?min.relative(d,6):min;max=d.getAxisDirection()==Direction.AxisDirection.POSITIVE?max.relative(d,6):max;shape=Shapes.or(shape,Block.box(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ()));}return shape;}
}
