package com.tnc.tnc.life.wonders;

import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.life.botanical.BotanicalContent;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A tuned note becomes a real redstone pulse; a bell chip also allows ringing by hand. */
public final class SoundRelayBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED=BooleanProperty.create("powered");
    public SoundRelayBlock(){super(Properties.copy(Blocks.NOTE_BLOCK).strength(2).lightLevel(s->s.getValue(POWERED)?7:0));registerDefaultState(stateDefinition.any().setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(POWERED);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new SoundRelayEntity(p,s);}
    @Override public boolean isSignalSource(BlockState s){return true;}
    @Override public int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return s.getValue(POWERED)?15:0;}
    @Override public int getDirectSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return getSignal(s,l,p,d);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity who,ItemStack stack){if(who instanceof Player player&&l.getBlockEntity(p) instanceof SoundRelayEntity be)be.setOwner(player.getUUID());}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server)||TownProtection.denied(server,p)||!(l.getBlockEntity(p) instanceof SoundRelayEntity be)||!be.mayUse(server))return InteractionResult.FAIL;
        ItemStack held=player.getItemInHand(hand);
        if(held.is(BotanicalContent.PRODUCTS.get("companion_bell_chip"))&&!be.hasBell()){
            be.installBell();if(!player.isCreative())held.shrink(1);server.displayClientMessage(Component.literal("陪伴铃片装好了：空手右键响铃；潜行右键调音。"),true);
        }else if(held.is(Items.SHEARS)&&be.hasBell()){
            be.removeBell();Block.popResource(l,p,new ItemStack(BotanicalContent.PRODUCTS.get("companion_bell_chip")));held.hurtAndBreak(1,player,a->a.broadcastBreakEvent(hand));
        }else if(held.isEmpty()){
            if(player.isShiftKeyDown()||!be.hasBell()){be.tune();server.displayClientMessage(Component.literal("节律继电器音高 "+be.pitch()+" / 24；六格内同音符会输出半秒红石，冷却五秒。"),true);}
            else be.pulse((ServerLevel)l);
        }else return InteractionResult.PASS;
        return InteractionResult.CONSUME;
    }
    @Override public void tick(BlockState state,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource r){if(l.getBlockEntity(p) instanceof SoundRelayEntity be)be.endPulse(l);}
    @Override public void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){if(old.getBlock()!=next.getBlock()&&l.getBlockEntity(p) instanceof SoundRelayEntity be&&be.hasBell())Block.popResource(l,p,new ItemStack(BotanicalContent.PRODUCTS.get("companion_bell_chip")));super.onRemove(old,l,p,next,moving);}
}
