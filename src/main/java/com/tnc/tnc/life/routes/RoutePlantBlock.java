package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
public final class RoutePlantBlock extends BushBlock implements EntityBlock {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,3);
    public static final BooleanProperty WILD=BooleanProperty.create("wild");
    public final NewPlantKind kind;
    public RoutePlantBlock(NewPlantKind kind){super(BlockBehaviour.Properties.copy(Blocks.FERN).noCollission().noOcclusion().lightLevel(s->s.getValue(AGE)==3?2:0));this.kind=kind;registerDefaultState(stateDefinition.any().setValue(AGE,0).setValue(WILD,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE,WILD);}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return s.getValue(WILD)?l.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)||l.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD):MagicSoilBlock.isSoil(l.getBlockState(p.below()));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new RoutePlantEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&t==RouteContent.PLANT_ENTITY.get()?(world,p,state,be)->RoutePlantEntity.tick((ServerLevel)world,p,(RoutePlantEntity)be):null;}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity actor,ItemStack stack){if(actor!=null&&l.getBlockEntity(p) instanceof RoutePlantEntity be){be.owner=actor.getUUID();be.setChanged();}}
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder params){var seed=new ItemStack(RouteContent.item(kind.id+"_seed"));return s.getValue(AGE)==3?List.of(seed,new ItemStack(RouteContent.item(kind.product),2)):List.of(seed);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){if(l.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer sp)||com.tnc.tnc.home.TownProtection.denied(sp,p)||!(l.getBlockEntity(p) instanceof RoutePlantEntity be))return InteractionResult.FAIL;
        if(be.feed(sp,player.getItemInHand(hand)))return InteractionResult.CONSUME;
        if(s.getValue(AGE)==3&&player.getItemInHand(hand).is(Items.SHEARS)){Block.popResource(l,p,new ItemStack(RouteContent.item(kind.id+"_seed")));be.growth=6000;l.setBlock(p,s.setValue(AGE,1),3);be.setChanged();return InteractionResult.CONSUME;}
        if(s.getValue(AGE)==3&&player.getItemInHand(hand).isEmpty()){Block.popResource(l,p,new ItemStack(RouteContent.item(kind.product),2));be.growth=6000;be.mana=0;l.setBlock(p,s.setValue(AGE,1),3);be.setChanged();RouteProgress.award(sp,"harvest/"+kind.id);return InteractionResult.CONSUME;}
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(kind.name+" · "+be.mana+"魔力 · "+kind.help),true);return InteractionResult.CONSUME;}
}
