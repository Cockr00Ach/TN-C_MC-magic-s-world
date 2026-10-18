package com.tnc.tnc.dungeon;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
public final class DungeonBookItem extends Item {
    public DungeonBookItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){if(p instanceof ServerPlayer sp)DungeonNetwork.open(sp,"guide");return InteractionResultHolder.sidedSuccess(p.getItemInHand(h),l.isClientSide);}
}
