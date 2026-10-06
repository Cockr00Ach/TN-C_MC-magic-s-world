package com.tnc.tnc.life.pasture;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public final class PastureStaffItem extends Item {
    public PastureStaffItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        var tag=context.getItemInHand().getTag();if(tag==null||!tag.hasUUID("Animal")||!player.serverLevel().dimension().location().toString().equals(tag.getString("Dimension")))return InteractionResult.PASS;
        if(player.serverLevel().getEntity(tag.getUUID("Animal")) instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity sheep)return com.tnc.tnc.life.routes.RouteBellCare.setHome(player,sheep,context.getClickedPos());
        if(!(player.serverLevel().getEntity(tag.getUUID("Animal")) instanceof PastureAnimal animal)||animal.distanceToSqr(player)>4096||!animal.mayCareFor(player)||animal.owner()==null||!animal.owner().equals(player.getUUID()))return InteractionResult.FAIL;
        var pos=context.getClickedPos();if(TownProtection.denied(player,pos))return InteractionResult.FAIL;
        if(context.getLevel().getBlockState(pos).is(PastureRegistry.block("habitat_marker"))&&!animal.speciesId().equals("watch_mantis")){animal.setHome(player,pos.above());return InteractionResult.SUCCESS;}
        return animal.setJob(player,pos)?InteractionResult.SUCCESS:InteractionResult.CONSUME;
    }
}
