package com.tnc.tnc.life.pasture;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** NBT is a display, never the authority. Copies of one bottle share one balance. */
public final class PastureBottleLedger extends SavedData {
    private record Balance(int capacity,int mana){}
    private final Map<UUID,Balance> accounts=new LinkedHashMap<>();
    public static PastureBottleLedger get(ServerLevel level){var world=level.getServer().overworld();return world.getDataStorage().computeIfAbsent(PastureBottleLedger::load,PastureBottleLedger::new,"tnc_mana_bottles");}
    public int amount(ItemStack stack){var t=stack.getTag();if(t==null||!t.hasUUID("BottleId"))return 0;var a=accounts.get(t.getUUID("BottleId"));return a==null?0:a.mana;}
    public int capacity(ItemStack stack,int nominal){var t=stack.getTag();if(t==null||!t.hasUUID("BottleId"))return nominal;var a=accounts.get(t.getUUID("BottleId"));return a==null?nominal:Math.min(nominal,a.capacity);}
    private UUID identity(ItemStack stack,int capacity){var t=stack.getOrCreateTag();UUID id=t.hasUUID("BottleId")?t.getUUID("BottleId"):UUID.randomUUID();if(!accounts.containsKey(id)){accounts.put(id,new Balance(capacity,0));t.putUUID("BottleId",id);setDirty();}return id;}
    public int deposit(ItemStack stack,int capacity,int offered){
        if(stack.getCount()!=1||offered<=0)return 0;
        UUID id=identity(stack,capacity);var b=accounts.get(id);int received=Math.min(offered,Math.max(0,Math.min(capacity,b.capacity)-b.mana));
        if(received>0){accounts.put(id,new Balance(b.capacity,b.mana+received));setDirty();}
        sync(stack);return received;
    }
    public int withdraw(ItemStack stack,int requested){
        int held=amount(stack),removed=Math.min(Math.max(0,requested),held);
        if(removed>0){UUID id=stack.getTag().getUUID("BottleId");var b=accounts.get(id);accounts.put(id,new Balance(b.capacity,b.mana-removed));setDirty();}
        sync(stack);return removed;
    }
    public void sync(ItemStack stack){stack.getOrCreateTag().putInt("StoredMana",amount(stack));}
    public static PastureBottleLedger load(CompoundTag tag){var ledger=new PastureBottleLedger();for(Tag raw:tag.getList("Bottles",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.hasUUID("Id")){int cap=t.getInt("Capacity")==72?72:24;ledger.accounts.put(t.getUUID("Id"),new Balance(cap,Math.max(0,Math.min(cap,t.getInt("Mana")))));}}return ledger;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();accounts.forEach((id,b)->{var t=new CompoundTag();t.putUUID("Id",id);t.putInt("Capacity",b.capacity);t.putInt("Mana",b.mana);list.add(t);});tag.put("Bottles",list);return tag;}
}
