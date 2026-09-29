package com.tnc.tnc.adventure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Plan on copies, then commit once on the server thread. Full inventory never consumes inputs. */
public final class InventoryTransaction {
    private final Inventory inventory;
    private final List<ItemStack> slots = new ArrayList<>();
    public InventoryTransaction(Inventory inventory) {
        this.inventory=inventory;
        for(int i=0;i<36;i++) slots.add(inventory.getItem(i).copy());
    }
    public static boolean matches(ItemStack s, ContractCatalog.Material m) {
        var id=ResourceLocation.tryParse(m.id());
        return id!=null && !s.isEmpty() && (m.tag()?s.is(TagKey.create(Registries.ITEM,id)):s.is(ForgeRegistries.ITEMS.getValue(id)));
    }
    public static int count(Inventory inv, ContractCatalog.Material m) {
        int count=0;for(int i=0;i<36;i++)if(matches(inv.getItem(i),m))count+=inv.getItem(i).getCount();return count;
    }
    public boolean take(List<ContractCatalog.Material> materials, boolean returnContainers) {
        List<ItemStack> containers=new ArrayList<>();
        for(var m:materials) {
            int remaining=m.count();
            for(int i=0;i<slots.size()&&remaining>0;i++) {
                var s=slots.get(i);if(!matches(s,m))continue;
                int n=Math.min(remaining,s.getCount());
                var remainder=returnContainers?com.tnc.tnc.life.FoodContainers.remainder(s):ItemStack.EMPTY;
                if(!remainder.isEmpty()) {
                    for(int j=0;j<n;j++)containers.add(remainder.copy());
                }
                s.shrink(n);remaining-=n;
            }
            if(remaining>0)return false;
        }
        for(var s:containers)if(!add(s))return false;
        return true;
    }
    public boolean add(ItemStack offered) {
        var remaining=offered.copy();
        for(var slot:slots) {
            if(!slot.isEmpty()&&ItemStack.isSameItemSameTags(slot,remaining)) {
                int n=Math.min(remaining.getCount(),Math.min(slot.getMaxStackSize(),64)-slot.getCount());
                if(n>0){slot.grow(n);remaining.shrink(n);}if(remaining.isEmpty())return true;
            }
        }
        for(int i=0;i<slots.size();i++)if(slots.get(i).isEmpty()) {
            int n=Math.min(remaining.getCount(),Math.min(remaining.getMaxStackSize(),64));
            slots.set(i,remaining.copyWithCount(n));remaining.shrink(n);if(remaining.isEmpty())return true;
        }
        return remaining.isEmpty();
    }
    public boolean takeFromSlot(int slot,int amount) {
        if(slot<0||slot>=36||amount<=0||slots.get(slot).getCount()<amount)return false;
        slots.get(slot).shrink(amount);return true;
    }
    public void commit() {
        for(int i=0;i<36;i++)inventory.setItem(i,slots.get(i));
        inventory.setChanged();inventory.player.inventoryMenu.broadcastChanges();
    }
}
