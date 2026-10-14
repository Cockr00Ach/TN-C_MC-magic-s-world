package com.tnc.tnc.life.ecology;

import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.production.ManaPlantSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public final class VerdantVeinBlock extends BushBlock implements EntityBlock,ManaPlantSource {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,3),GLOW=IntegerProperty.create("glow",0,2);
    public VerdantVeinBlock(){super(BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING).noCollission().lightLevel(s->s.getValue(GLOW)*3));registerDefaultState(stateDefinition.any().setValue(AGE,0).setValue(GLOW,0).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE,GLOW,com.tnc.tnc.life.routes.RoutePlantBlock.WILD);}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){var soil=l.getBlockState(p.below());return soil.is(BlockTags.DIRT)||soil.is(Blocks.FARMLAND);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new VerdantVeinBlockEntity(p,s);}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||t!=EcologyContent.VERDANT_ENTITY?null:(world,pos,state,be)->VerdantVeinBlockEntity.tick((ServerLevel)world,pos,state,(VerdantVeinBlockEntity)be);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,@Nullable LivingEntity who,ItemStack stack){if(who instanceof Player player&&l.getBlockEntity(p) instanceof VerdantVeinBlockEntity be)be.setOwner(player.getUUID());}
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder context){
        var be=context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        boolean wild=be instanceof VerdantVeinBlockEntity plant&&plant.manaOwner()==null;
        return state.getValue(AGE)==3?List.of(new ItemStack(EcologyContent.SEED,wild?2:1),new ItemStack(EcologyContent.BRANCH,1)):List.of(new ItemStack(EcologyContent.SEED));
    }
    @Override public int drawMana(ServerLevel l,BlockPos p,BlockState s,int max){return l.getBlockEntity(p) instanceof VerdantVeinBlockEntity be?be.drawMana(max):0;}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server)||TownProtection.denied(server,p)||!(l.getBlockEntity(p) instanceof VerdantVeinBlockEntity be))return InteractionResult.FAIL;
        if(be.manaOwner()==null&&player.isShiftKeyDown()){be.setOwner(player.getUUID());player.displayClientMessage(Component.literal("绿脉枝已认养。相邻炉口或发电座现在可取它的魔力。"),true);return InteractionResult.CONSUME;}
        if(player.getItemInHand(hand).is(Items.SHEARS)&&s.getValue(AGE)==3){
            Block.popResource(l,p,new ItemStack(EcologyContent.BRANCH,2));if(be.prune()){Block.popResource(l,p,new ItemStack(EcologyContent.SEED));com.tnc.tnc.life.routes.RouteProgress.award(server,"harvest/verdant_vein");}
            player.getItemInHand(hand).hurtAndBreak(1,player,e->e.broadcastBreakEvent(hand));
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal(be.status()),true);return InteractionResult.CONSUME;
    }
}
