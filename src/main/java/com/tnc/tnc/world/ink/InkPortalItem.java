package com.tnc.tnc.world.ink;

import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;

public final class InkPortalItem extends Item {
    public InkPortalItem(){super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        var gate=InkBossRegistration.INK_PORTAL.create(player.serverLevel());if(gate==null)return InteractionResult.FAIL;
        var at=context.getClickedPos().relative(context.getClickedFace());
        if(!player.mayUseItemAt(at,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        gate.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,player.getYRot(),0);
        if(!context.getLevel().getWorldBorder().isWithinBounds(gate.getBoundingBox())||!context.getLevel().noCollision(gate,gate.getBoundingBox()))return InteractionResult.FAIL;
        if(!context.getLevel().addFreshEntity(gate))return InteractionResult.FAIL;
        if(!player.isCreative())context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
