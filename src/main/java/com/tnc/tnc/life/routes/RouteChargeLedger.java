package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
/** Item NBT is only a synced display. Duplicated item identities share a balance. */
public final class RouteChargeLedger extends SavedData {
    private record Balance(int capacity,int mana){}
    private final Map<UUID,Balance> balances=new LinkedHashMap<>();
    public static RouteChargeLedger get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(RouteChargeLedger::load,RouteChargeLedger::new,"tnc_accessory_charge");}
    public int amount(ItemStack s){return s.hasTag()&&s.getTag().hasUUID("ChargeId")&&balances.containsKey(s.getTag().getUUID("ChargeId"))?balances.get(s.getTag().getUUID("ChargeId")).mana:0;}
    public int deposit(ItemStack s,int capacity,int offered){if(s.getCount()!=1||offered<=0)return 0;var tag=s.getOrCreateTag();UUID id=tag.hasUUID("ChargeId")?tag.getUUID("ChargeId"):UUID.randomUUID();tag.putUUID("ChargeId",id);var b=balances.computeIfAbsent(id,key->new Balance(capacity,0));int got=Math.max(0,Math.min(offered,Math.min(capacity,b.capacity)-b.mana));balances.put(id,new Balance(b.capacity,b.mana+got));sync(s);setDirty();return got;}
    public boolean spend(ItemStack s,int cost){if(cost<0||amount(s)<cost)return false;var b=balances.get(s.getTag().getUUID("ChargeId"));if(b==null)return cost==0;balances.put(s.getTag().getUUID("ChargeId"),new Balance(b.capacity,b.mana-cost));sync(s);setDirty();return true;}
    public void sync(ItemStack s){s.getOrCreateTag().putInt("ChargedMana",amount(s));}
    public static RouteChargeLedger load(CompoundTag t){var out=new RouteChargeLedger();for(Tag raw:t.getList("Balances",10)){var row=(CompoundTag)raw;if(row.hasUUID("Id")){int cap=Math.max(0,Math.min(6000,row.getInt("Capacity")));out.balances.put(row.getUUID("Id"),new Balance(cap,Math.max(0,Math.min(cap,row.getInt("Mana")))));}}return out;}
    @Override public CompoundTag save(CompoundTag t){var list=new ListTag();balances.forEach((id,b)->{var row=new CompoundTag();row.putUUID("Id",id);row.putInt("Capacity",b.capacity);row.putInt("Mana",b.mana);list.add(row);});t.put("Balances",list);return t;}
}
