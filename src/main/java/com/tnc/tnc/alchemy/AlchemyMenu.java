package com.tnc.tnc.alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Read-only field guide. No inventory slots or fire/production button packets. */
public final class AlchemyMenu extends AbstractContainerMenu {
    public final BlockPos pos;private AlchemyEntity bound;
    public AlchemyMenu(int id,Inventory inv,FriendlyByteBuf b){super(AlchemyContent.MENU,id);pos=b.readBlockPos();}
    public AlchemyMenu(int id,Inventory inv,AlchemyEntity be){super(AlchemyContent.MENU,id);pos=be.getBlockPos();bound=be;}
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64&&(!(p instanceof net.minecraft.server.level.ServerPlayer sp)||bound!=null&&!bound.isRemoved()&&sp.serverLevel().getBlockEntity(pos)==bound&&bound.mayUse(sp));}
    @Override public boolean clickMenuButton(Player p,int id){return false;}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
}
