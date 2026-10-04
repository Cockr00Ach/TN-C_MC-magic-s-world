package com.tnc.tnc.life.pasture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public final class PastureCageItem extends Item {
    public PastureCageItem(){super(new Properties().stacksTo(1));}
    public InteractionResult capture(ServerPlayer player,ItemStack cage,PastureAnimal animal){
        if(cage.getOrCreateTag().hasUUID("Ticket")||animal.owner()==null||!animal.owner().equals(player.getUUID())||!animal.mayCareFor(player)||animal.isVehicle())return InteractionResult.FAIL;
        CompoundTag saved=new CompoundTag();animal.save(saved);var ticket=PastureCageLedger.get(player.server).store(saved);if(ticket==null)return InteractionResult.FAIL;
        cage.getOrCreateTag().putUUID("Ticket",ticket);cage.getOrCreateTag().putString("Species",animal.speciesId());animal.discard();PastureAnimal.message(player,"异兽已由永久UUID托管；右键合法空地放出。");return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        var tag=context.getItemInHand().getTag();if(tag==null||!tag.hasUUID("Ticket"))return InteractionResult.PASS;
        var ledger=PastureCageLedger.get(player.server);var ticket=tag.getUUID("Ticket");CompoundTag saved=ledger.peek(ticket);if(saved==null){PastureAnimal.message(player,"这张搬迁凭据已经使用，没有第二只动物。");return InteractionResult.FAIL;}
        if(!saved.hasUUID("PastureOwner")||!saved.getUUID("PastureOwner").equals(player.getUUID()))return InteractionResult.FAIL;
        BlockPos pos=context.getClickedPos().relative(context.getClickedFace());if(!PastureAnimal.mayOperate(player.serverLevel(),pos,player.getUUID())||!context.getLevel().getBlockState(pos).isAir()||!context.getLevel().getBlockState(pos.above()).isAir())return InteractionResult.FAIL;
        if(saved.hasUUID("UUID"))for(var level:player.server.getAllLevels())if(level.getEntity(saved.getUUID("UUID"))!=null)return InteractionResult.FAIL;
        Entity entity=EntityType.loadEntityRecursive(saved,player.serverLevel(),e->{e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,player.getYRot(),0);return e;});
        if(!(entity instanceof PastureAnimal)||!player.serverLevel().addFreshEntity(entity))return InteractionResult.FAIL;
        ledger.consume(ticket);tag.remove("Ticket");tag.remove("Species");PastureAnimal.message(player,"同一只异兽已放出，物品库存与取产冷却保留。");return InteractionResult.SUCCESS;
    }
}
