package com.tnc.tnc.journey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
public final class PreparationMenu extends AbstractContainerMenu {
    private final Container storage;public final int floor;
    public PreparationMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,new SimpleContainer(31),b.readVarInt());}
    public PreparationMenu(int id,Inventory inv,Container storage,int floor){super(com.tnc.tnc.alchemy.AlchemyContent.PREP_MENU,id);this.storage=storage;this.floor=floor;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(storage,r*9+c,22+c*18,61+r*18));
        for(int i=0;i<4;i++)addSlot(new Slot(storage,27+i,211+i*25,71){public boolean mayPlace(ItemStack s){return s.getItem() instanceof com.tnc.tnc.alchemy.ElixirItem;}});
        addSlot(new Slot(inv,39,216,117){public boolean mayPlace(ItemStack s){return s.getEquipmentSlot()==EquipmentSlot.HEAD;}public boolean mayPickup(Player p){return !EnchantmentHelperBinding.bound(getItem())||p.isCreative();}public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(inv,38,251,117){public boolean mayPlace(ItemStack s){return s.getEquipmentSlot()==EquipmentSlot.CHEST;}public boolean mayPickup(Player p){return !EnchantmentHelperBinding.bound(getItem())||p.isCreative();}public int getMaxStackSize(){return 1;}});
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,22+c*18,195+r*18));for(int c=0;c<9;c++)addSlot(new Slot(inv,c,22+c*18,253));
    }
    private static final class EnchantmentHelperBinding{static boolean bound(ItemStack s){return net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(s);}}
    @Override public boolean stillValid(Player p){return !(p instanceof net.minecraft.server.level.ServerPlayer sp)||Preparation.nearLobby(sp)&&floor==Preparation.selectedFloor(sp);}
    @Override public boolean clickMenuButton(Player player,int button){if(!(player instanceof net.minecraft.server.level.ServerPlayer p)||!stillValid(p))return false;
        if(button==0){for(int i=33;i<60;i++){var s=slots.get(i).getItem();if(!s.isEmpty()){moveItemStackTo(s,0,27,false);slots.get(i).setChanged();}}storage.setChanged();return true;}
        if(button==1){for(int i=0;i<27;i++){var s=storage.getItem(i);if(!s.isEmpty()){moveItemStackTo(s,33,69,false);storage.setChanged();}}return true;}
        if(button==2){Preparation.ready(p,floor);return true;}return false;
    }
    @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var old=stack.copy();boolean moved=index<33?moveItemStackTo(stack,33,69,true):stack.getItem() instanceof com.tnc.tnc.alchemy.ElixirItem?moveItemStackTo(stack,27,31,false):moveItemStackTo(stack,0,27,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return old;}
}
