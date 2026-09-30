package com.tnc.tnc.equipment;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.ResourceLocation;
import com.mojang.datafixers.util.Pair;
/** Vanilla armor permissions and equip callback, with a two-slot position and custom ghost icon. */
public final class GearSlot extends Slot {
    private final Player player;private final EquipmentSlot gear;private final Slot original;
    public GearSlot(Slot original,int y,Player p,EquipmentSlot gear){super(original.container,original.getContainerSlot(),original.x,y);this.original=original;this.player=p;this.gear=gear;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean mayPlace(ItemStack s){return s.canEquip(gear,player);}
    @Override public boolean mayPickup(Player p){return (getItem().isEmpty()||p.isCreative()||!EnchantmentHelper.hasBindingCurse(getItem()))&&super.mayPickup(p);}
    @Override public void setByPlayer(ItemStack s){original.setByPlayer(s);}
    @Override public Pair<ResourceLocation,ResourceLocation> getNoItemIcon(){return Pair.of(InventoryMenu.BLOCK_ATLAS,ResourceLocation.fromNamespaceAndPath("tnc",gear==EquipmentSlot.HEAD?"item/gear/empty_magic_hat":"item/gear/empty_full_outfit"));}
}
