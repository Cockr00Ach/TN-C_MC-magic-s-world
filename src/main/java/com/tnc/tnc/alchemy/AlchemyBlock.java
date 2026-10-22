package com.tnc.tnc.alchemy;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class AlchemyBlock extends BaseEntityBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
    public static final BooleanProperty CLOSED=BooleanProperty.create("closed");
    public AlchemyBlock(){super(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK).strength(2f,6f).sound(SoundType.METAL).noOcclusion().lightLevel(s->5));registerDefaultState(stateDefinition.any().setValue(PART,0).setValue(CLOSED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(PART,CLOSED);}
    public static BlockPos anchor(BlockPos p,BlockState s){int part=s.getValue(PART);return p.offset(-(part%2),0,-(part/2));}
    public static AlchemyEntity master(BlockGetter l,BlockPos p){var s=l.getBlockState(p);if(!(s.getBlock() instanceof AlchemyBlock))return null;return l.getBlockEntity(anchor(p,s)) instanceof AlchemyEntity be?be:null;}
    public static void close(Level l,BlockPos p,boolean closed){if(l==null)return;for(int i=0;i<4;i++){var at=p.offset(i%2,0,i/2);var s=l.getBlockState(at);if(s.getBlock() instanceof AlchemyBlock&&anchor(at,s).equals(p)&&s.getValue(CLOSED)!=closed)l.setBlock(at,s.setValue(CLOSED,closed),3);}}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){var l=c.getLevel();for(int i=0;i<4;i++){var p=c.getClickedPos().offset(i%2,0,i/2);if(!l.hasChunkAt(p)||!l.getBlockState(p).canBeReplaced(c)||c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp&&com.tnc.tnc.home.TownProtection.denied(sp,p))return null;}return defaultBlockState();}
    @Override public RenderShape getRenderShape(BlockState s){return s.getValue(PART)==0?RenderShape.MODEL:RenderShape.INVISIBLE;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){int x=s.getValue(PART)%2,z=s.getValue(PART)/2;var all=Shapes.or(Shapes.box(.12,0,.12,1.88,.25,1.88),Shapes.box(.08,.25,.08,.25,.96,1.92),Shapes.box(1.75,.25,.08,1.92,.96,1.92),Shapes.box(.25,.25,.08,1.75,.96,.25),Shapes.box(.25,.25,1.75,1.75,.96,1.92));if(s.getValue(CLOSED))all=Shapes.or(all,Shapes.box(.08,.91,.08,1.92,1,1.92));return Shapes.join(all,Shapes.box(x,0,z,x+1,1,z+1),BooleanOp.AND).move(-x,0,-z);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(PART)==0?new AlchemyEntity(p,s):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||s.getValue(PART)!=0?null:createTickerHelper(t,AlchemyContent.ENTITY,AlchemyEntity::tick);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){var be=master(l,pos);if(be==null)return InteractionResult.PASS;if(player instanceof net.minecraft.server.level.ServerPlayer p)be.interact(p,hand);return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity actor,net.minecraft.world.item.ItemStack stack){if(l.isClientSide||s.getValue(PART)!=0)return;for(int i=1;i<4;i++)l.setBlock(p.offset(i%2,0,i/2),defaultBlockState().setValue(PART,i).setValue(CLOSED,s.getValue(CLOSED)),3);if(l.getBlockEntity(p) instanceof AlchemyEntity be){be.owner=actor==null?null:actor.getUUID();close(l,p,be.sealed);be.setChanged();}}
    @Override public void playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){var be=master(l,p);if(!l.isClientSide&&be!=null&&player.isCreative()){be.getPersistentData().putBoolean("DismantleWithoutDrop",true);Containers.dropContents(l,be.getBlockPos(),be.inventory);be.inventory.clearContent();}super.playerWillDestroy(l,p,s,player);}
    @Override public void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){if(old.getBlock()!=next.getBlock()&&!l.isClientSide){var root=anchor(p,old);if(old.getValue(PART)!=0){var master=l.getBlockState(root);if(master.getBlock()==this){var be=l.getBlockEntity(root);if(be!=null&&!be.getPersistentData().getBoolean("DismantleWithoutDrop"))for(var drop:Block.getDrops(master,(net.minecraft.server.level.ServerLevel)l,root,be))Block.popResource(l,p,drop);l.removeBlock(root,false);}}for(int i=1;i<4;i++){var at=root.offset(i%2,0,i/2);if(at.equals(p))continue;var s=l.getBlockState(at);if(s.getBlock()==this&&anchor(at,s).equals(root))l.removeBlock(at,false);}}super.onRemove(old,l,p,next,moving);}
}