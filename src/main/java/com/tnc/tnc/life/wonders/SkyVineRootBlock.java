package com.tnc.tnc.life.wonders;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SkyVineRootBlock extends BushBlock implements EntityBlock {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,4);
    public SkyVineRootBlock(){super(BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING).noCollission().noOcclusion());registerDefaultState(stateDefinition.any().setValue(AGE,0).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE,com.tnc.tnc.life.routes.RoutePlantBlock.WILD);}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return l.getBlockState(p.below()).is(BlockTags.DIRT)||l.getBlockState(p.below()).is(Blocks.FARMLAND);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new SkyVineRootEntity(p,s);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity who,ItemStack stack){if(who instanceof Player player&&l.getBlockEntity(p) instanceof SkyVineRootEntity be)be.setOwner(player.getUUID());}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide||type!=WonderContent.VINE_ENTITY?null:(w,p,state,be)->SkyVineRootEntity.tick((net.minecraft.server.level.ServerLevel)w,p,state,(SkyVineRootEntity)be);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server)||TownProtection.denied(server,p)||!(l.getBlockEntity(p) instanceof SkyVineRootEntity be))return InteractionResult.FAIL;
        if(player.getItemInHand(hand).is(Items.SHEARS)&&s.getValue(AGE)==4&&be.harvest(server)){
            com.tnc.tnc.life.routes.RouteProgress.award(server,"harvest/sky_vine");player.getItemInHand(hand).hurtAndBreak(1,player,e->e.broadcastBreakEvent(hand));return InteractionResult.CONSUME;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(be.status()),true);return InteractionResult.CONSUME;
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState s,net.minecraft.world.level.storage.loot.LootParams.Builder c){return java.util.List.of(new ItemStack(WonderContent.SKY_VINE_SEED));}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(s.getBlock()!=next.getBlock()&&l.getBlockEntity(p) instanceof SkyVineRootEntity be)be.retire();super.onRemove(s,l,p,next,moving);}
}
