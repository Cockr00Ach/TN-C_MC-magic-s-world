package com.tnc.tnc.life.pasture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Four functional slots; all counters originate from the server facility ledger. */
public final class PastureMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final BlockPos position;
    public PastureMenu(int id,Inventory inventory,FriendlyByteBuf buffer){this(id,inventory,new SimpleContainer(4),new SimpleContainerData(4),buffer.readBlockPos());}
    public PastureMenu(int id,Inventory inventory,Container container,ContainerData data,BlockPos position){
        super(PastureRegistry.MENU_TYPE,id);this.container=container;this.data=data;this.position=position.immutable();checkContainerSize(container,4);checkContainerDataCount(data,4);
        for(int index=0;index<4;index++){final int slot=index;addSlot(new Slot(container,index,44+22*index,38){@Override public boolean mayPlace(ItemStack item){return container.canPlaceItem(slot,item);}@Override public int getMaxStackSize(){return 16;}});}
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(inventory,column+row*9+9,8+18*column,89+18*row));
        for(int column=0;column<9;column++)addSlot(new Slot(inventory,column,8+18*column,147));addDataSlots(data);
    }
    public int data(int index){return data.get(index);}
    public BlockPos pos(){return position;}
    @Override public boolean stillValid(Player player){return container.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;ItemStack source=slot.getItem(),original=source.copy();
        if(index<4){if(!moveItemStackTo(source,4,40,true))return ItemStack.EMPTY;}else if(!moveItemStackTo(source,0,4,false))return ItemStack.EMPTY;
        if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(source.getCount()==original.getCount())return ItemStack.EMPTY;slot.onTake(player,source);return original;
    }
}
