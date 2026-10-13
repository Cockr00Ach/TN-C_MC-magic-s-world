package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class RouteNodeBlock extends Block implements EntityBlock {
    public final RouteKind kind;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT=net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    public RouteNodeBlock(RouteKind kind){super(BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK).strength(2).noOcclusion().lightLevel(s->s.getValue(LIT)?kind==RouteKind.LAMP?11:kind==RouteKind.BRIGHT_LAMP?15:0:0));this.kind=kind;registerDefaultState(stateDefinition.any().setValue(LIT,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(LIT);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new RouteNodeEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&t==RouteContent.NODE_ENTITY.get()?(world,p,state,be)->RouteNodeEntity.tick((net.minecraft.server.level.ServerLevel)world,p,(RouteNodeEntity)be):null;}
    @Override public boolean isSignalSource(BlockState s){return kind==RouteKind.ALARM;}
    @Override public int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return kind==RouteKind.ALARM&&s.getValue(LIT)?15:0;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return kind.wire()?Block.box(5,0,5,11,4,11):Block.box(1,0,1,15,12,15);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity actor,ItemStack item){if(actor!=null&&l.getBlockEntity(p) instanceof RouteNodeEntity be){be.owner=actor.getUUID();be.setChanged();}}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){if(l.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof net.minecraft.server.level.ServerPlayer sp)||!(l.getBlockEntity(p) instanceof RouteNodeEntity be)||!be.mayUse(sp))return InteractionResult.FAIL;
        if(kind==RouteKind.COLOR_TARGET&&player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.DyeItem dye){be.targetColor=dye.getDyeColor().getId();be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal("色靶已调为 "+dye.getDyeColor().getName()+"；染料不消耗"),true);return InteractionResult.CONSUME;}
        if(player.getItemInHand(hand).getItem() instanceof RouteMeterItem)return InteractionResult.PASS;
        if(kind==RouteKind.ANIMAL_COLLECTOR&&player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()){be.animalCharging=!be.animalCharging;be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal(be.animalCharging?"集息器：向绑定异兽充能":"集息器：从绑定异兽取魔"),true);return InteractionResult.CONSUME;}
        if(kind==RouteKind.INFUSER&&player.getItemInHand(hand).isEmpty()&&!player.isShiftKeyDown()){be.manualPlayer=player.getUUID();be.manualUntil=l.getGameTime()+10;player.displayClientMessage(net.minecraft.network.chat.Component.literal("保持空手右键注能，每秒5点；松开停止。潜行右键打开容器面板。"),true);return InteractionResult.CONSUME;}
        if(kind.storage()&&player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()){be.reserve=be.reserve==0?kind.capacity/4:be.reserve==kind.capacity/4?kind.capacity/2:0;be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal("储池保留 "+be.reserve+" 魔力；空手潜行右键切换0/25%/50%"),true);return InteractionResult.CONSUME;}if(kind==RouteKind.VALVE&&player.isShiftKeyDown()){be.priority=(be.priority+1)%3;be.setChanged();player.displayClientMessage(net.minecraft.network.chat.Component.literal("分流优先："+new String[]{"照明 → 机器 → 储池","机器 → 照明 → 储池","轮流供给"}[be.priority]),true);return InteractionResult.CONSUME;}
        if(!kind.wire())net.minecraftforge.network.NetworkHooks.openScreen(sp,be,p);return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){if(old.getBlock()!=next.getBlock()&&l.getBlockEntity(p) instanceof RouteNodeEntity be)net.minecraft.world.Containers.dropContents(l,p,be.inventory);super.onRemove(old,l,p,next,moving);}
}
