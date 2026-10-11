package com.tnc.tnc.life.botanical;

import com.tnc.tnc.home.TownProtection;
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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;
import java.util.List;

/** A retained living root, with genuine stateful harvesting rather than a renamed flower. */
public final class BotanicalBlock extends BushBlock implements EntityBlock {
    public static final IntegerProperty AGE=IntegerProperty.create("age",0,3),MODE=IntegerProperty.create("mode",0,3);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WILD=net.minecraft.world.level.block.state.properties.BooleanProperty.create("wild");
    public final BotanicalSpecies species;
    public BotanicalBlock(BotanicalSpecies species){super(BlockBehaviour.Properties.copy(Blocks.FERN).noCollission().noOcclusion().lightLevel(s->s.getValue(AGE)==3&&(species==BotanicalSpecies.STAR_DEW||species==BotanicalSpecies.STAR_REST||species==BotanicalSpecies.WISH_PUFF)?3:0));this.species=species;registerDefaultState(stateDefinition.any().setValue(AGE,0).setValue(MODE,0).setValue(WILD,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE,MODE,WILD);}
    @Override public VoxelShape getShape(BlockState s,net.minecraft.world.level.BlockGetter l,BlockPos p,CollisionContext c){return species==BotanicalSpecies.MIRROR_LOTUS?Block.box(1,0,1,15,6,15):Block.box(2,0,2,14,5+s.getValue(AGE)*3,14);}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){var soil=l.getBlockState(p.below());if(!s.getValue(WILD))return soil.is(BlockTags.DIRT)||soil.is(Blocks.FARMLAND)||soil.is(Blocks.MUD)||soil.is(Blocks.MOSS_BLOCK)||species==BotanicalSpecies.MIRROR_LOTUS&&l.getFluidState(p.below()).getType()==Fluids.WATER||species==BotanicalSpecies.STONE_FERN&&soil.is(BlockTags.BASE_STONE_OVERWORLD);if(species==BotanicalSpecies.MIRROR_LOTUS)return l.getFluidState(p.below()).isSource()&&l.getFluidState(p.below()).getType()==Fluids.WATER&&(l.getBlockState(p.below(2)).is(BlockTags.DIRT)||l.getBlockState(p.below(2)).is(Blocks.CLAY));if(species==BotanicalSpecies.ECHO_BEAN)return soil.is(Blocks.FARMLAND)||s.getValue(WILD)&&soil.is(BlockTags.DIRT);if(species==BotanicalSpecies.STONE_FERN||species==BotanicalSpecies.SALT_INK)return soil.is(BlockTags.BASE_STONE_OVERWORLD)||soil.is(BlockTags.STONE_BRICKS)||soil.is(Blocks.MUD)||soil.is(Blocks.MOSS_BLOCK);return soil.is(BlockTags.DIRT)||soil.is(Blocks.FARMLAND);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new BotanicalPlantEntity(p,s);}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide||type!=BotanicalContent.PLANT_ENTITY?null:(world,p,state,be)->BotanicalPlantEntity.tick((ServerLevel)world,p,state,(BotanicalPlantEntity)be);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,@Nullable LivingEntity who,ItemStack stack){if(l.getBlockEntity(p) instanceof BotanicalPlantEntity be&&who instanceof Player player){be.plantedBy(player.getUUID());if(stack.hasTag()&&stack.getTag().hasUUID("BotanicalOwner"))be.owner=stack.getTag().getUUID("BotanicalOwner");}}
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder context){var seed=new ItemStack(BotanicalContent.SEEDS.get(species.id));var raw=context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(raw instanceof BotanicalPlantEntity be){if(be.owner!=null&&species==BotanicalSpecies.DANCE_BELL)seed.getOrCreateTag().putUUID("BotanicalOwner",be.owner);if(s.getValue(AGE)==3){seed.setCount(2);return List.of(seed,new ItemStack(BotanicalContent.PRODUCTS.get(species.product)));}}return List.of(seed);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(l.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer sp)||TownProtection.denied(sp,p)||!(l.getBlockEntity(p) instanceof BotanicalPlantEntity be))return InteractionResult.FAIL;
        var held=player.getItemInHand(hand);
        if(com.tnc.tnc.life.routes.RouteSources.feedBotanical(be,sp,held))return InteractionResult.CONSUME;
        if(s.getValue(AGE)==3&&held.is(Items.SHEARS)){Block.popResource(l,p,new ItemStack(BotanicalContent.SEEDS.get(species.id)));be.harvest();return InteractionResult.CONSUME;}
        if(held.is(BotanicalContent.TOOLS.get("rain_watering_flask"))||held.is(BotanicalContent.TOOLS.get("pollination_brush"))||held.is(BotanicalContent.TOOLS.get("plant_sample_clip"))||held.is(BotanicalContent.TOOLS.get("field_tuning_bell")))return InteractionResult.PASS;
        if(species==BotanicalSpecies.PAPER_TREE&&held.is(Items.BOOK)){be.bookStudied=true;be.changed();player.displayClientMessage(Component.literal("墨信树记住了书的符号，未读取任何文字。"),true);return InteractionResult.CONSUME;}
        if(species==BotanicalSpecies.ECHO_BEAN&&held.is(Items.NOTE_BLOCK)){player.displayClientMessage(Component.literal("在三格内敲响音符盒三次；植株会记录最后一音。"),true);return InteractionResult.CONSUME;}
        if(player.isShiftKeyDown()&&held.isEmpty()&&species==BotanicalSpecies.DANCE_BELL&&be.owner!=null&&be.owner.equals(player.getUUID())){player.displayClientMessage(Component.literal("移植时绑定会保存在种子中；把种子交给朋友，由朋友潜行浇水即可转让。"),true);return InteractionResult.CONSUME;}
        if(s.getValue(AGE)==3&&be.canHarvest()){
            if(species.bottled()&&(!held.is(Items.GLASS_BOTTLE)||held.getCount()<species.count)){player.displayClientMessage(Component.literal("请拿"+species.count+"个空玻璃瓶来收取；瓶会成为成品容器。"),true);return InteractionResult.CONSUME;}
            if(species.bottled()&&!player.isCreative())held.shrink(species.count);
            ItemStack product=new ItemStack(BotanicalContent.PRODUCTS.get(species.product),species.count);
            if(species==BotanicalSpecies.STONE_FERN||species==BotanicalSpecies.SHADOW_CUT||species==BotanicalSpecies.ECHO_BEAN){var tag=product.getOrCreateTag();tag.putInt("Pattern",be.pattern);tag.putString("Dimension",l.dimension().location().toString());tag.putLong("SamplePosition",p.asLong());tag.putUUID("Sampler",player.getUUID());}
            if(!player.getInventory().add(product))player.drop(product,false);
            if(be.wild&&!be.wildHarvested){Block.popResource(l,p,new ItemStack(BotanicalContent.SEEDS.get(species.id),2));be.wildHarvested=true;}
            else if(++be.harvests>0)Block.popResource(l,p,new ItemStack(BotanicalContent.SEEDS.get(species.id)));
            com.tnc.tnc.life.routes.RouteProgress.award(sp,"harvest/"+species.id);be.harvest();l.playSound(null,p,net.minecraft.sounds.SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,net.minecraft.sounds.SoundSource.BLOCKS,.6F,1);return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal(be.status()),true);return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){if(old.getBlock()!=next.getBlock()&&l instanceof ServerLevel server&&l.getBlockEntity(p) instanceof BotanicalPlantEntity be)be.removeSprites(server);super.onRemove(old,l,p,next,moving);}
}
