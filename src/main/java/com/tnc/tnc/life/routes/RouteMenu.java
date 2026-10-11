package com.tnc.tnc.life.routes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class RouteMenu extends AbstractContainerMenu {
    public final BlockPos pos;private final Container inventory;private final ContainerData data;
    public RouteMenu(int id,Inventory player,FriendlyByteBuf buf){this(id,player,new SimpleContainer(6),new SimpleContainerData(7),buf.readBlockPos());}
    public RouteMenu(int id,Inventory player,RouteNodeEntity be){this(id,player,be.inventory,be.data,be.getBlockPos());}
    private RouteMenu(int id,Inventory player,Container container,ContainerData data,BlockPos pos){super(RouteContent.MENU.get(),id);this.pos=pos;this.inventory=container;this.data=data;
        for(int i=0;i<4;i++)addSlot(new Slot(container,i,20+i*20,60));addSlot(new Slot(container,4,113,60){public boolean mayPlace(ItemStack stack){return false;}});addSlot(new Slot(container,5,153,60){public boolean mayPlace(ItemStack stack){return false;}});
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(player,c+r*9+9,17+c*18,123+r*18));for(int c=0;c<9;c++)addSlot(new Slot(player,c,17+c*18,181));addDataSlots(data);
    }
    public int value(int i){return data.get(i);}
    public RouteKind kind(){return RouteKind.values()[Math.max(0,Math.min(RouteKind.values().length-1,value(5)))];}
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&(!(p instanceof net.minecraft.server.level.ServerPlayer sp)||sp.serverLevel().getBlockEntity(pos) instanceof RouteNodeEntity be&&be.mayUse(sp));}
    @Override public boolean clickMenuButton(Player p,int id){if(id==0&&p instanceof net.minecraft.server.level.ServerPlayer sp&&stillValid(p)&&sp.serverLevel().getBlockEntity(pos) instanceof RouteNodeEntity be&&be.kind()==RouteKind.INFUSER){be.manualPlayer=p.getUUID();be.manualUntil=p.level().getGameTime()+100;return true;}return false;}
    @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var old=stack.copy();if(index<6){if(!moveItemStackTo(stack,6,slots.size(),true))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,0,4,false))return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return old;}
}
