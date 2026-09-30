package com.tnc.tnc.equipment;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
/** Maintains vanilla menu indices while removing the two unused equipment targets. */
public final class InactiveGearSlot extends Slot {
    public InactiveGearSlot(Container container,int slot){super(container,slot,-10000,-10000);}
    @Override public boolean mayPlace(ItemStack stack){return false;}
}
