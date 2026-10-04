package com.tnc.tnc.life;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Personal equipment and meals belong to UUID accounts, never to copyable pouch NBT. */
public final class LifeSavedData extends SavedData {
    final Map<UUID,Account> accounts=new LinkedHashMap<>();
    public static LifeSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(LifeSavedData::load,LifeSavedData::new,"tnc_life_v1");}
    public Account account(UUID id){return accounts.computeIfAbsent(id,ignored->{var a=new Account();a.bag.addListener(c->setDirty());setDirty();return a;});}
    public static final class Account {
        public final SimpleContainer bag=new SimpleContainer(7);
        public final Set<String> tasted=new LinkedHashSet<>(),delicacies=new LinkedHashSet<>();
        public int farmTier;
        public long farmCooldown;
        public long nextMealTick;
        public String campDimension="";
        public long campPosition;
        public long campBloomDay=-1;
        public int campBloomCount;
        public final List<CompoundTag> homewardFlowers=new ArrayList<>();
    }
    public static LifeSavedData load(CompoundTag root) {
        var d=new LifeSavedData();for(Tag v:root.getList("Accounts",10)) {
            var t=(CompoundTag)v;if(!t.hasUUID("UUID"))continue;var a=d.account(t.getUUID("UUID"));
            for(Tag slot:t.getList("Bag",10)){var s=(CompoundTag)slot;int index=s.getInt("Slot");if(index>=0&&index<7)a.bag.setItem(index,ItemStack.of(s));}
            for(Tag s:t.getList("Tasted",8))a.tasted.add(s.getAsString());for(Tag s:t.getList("Delicacies",8))a.delicacies.add(s.getAsString());
            a.farmTier=Math.max(0,Math.min(3,t.getInt("FarmTier")));a.farmCooldown=Math.max(0,t.getLong("FarmCooldown"));
            a.nextMealTick=Math.max(0,t.getLong("NextMealTick"));
            a.campDimension=t.getString("CampDimension");a.campPosition=t.getLong("CampPosition");
            a.campBloomDay=t.contains("CampBloomDay")?t.getLong("CampBloomDay"):-1;
            a.campBloomCount=Math.max(0,Math.min(3,t.getInt("CampBloomCount")));
            for(Tag entry:t.getList("HomewardFlowers",10))if(a.homewardFlowers.size()<256)a.homewardFlowers.add(((CompoundTag)entry).copy());
        }return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        var list=new ListTag();accounts.forEach((id,a)->{
            var t=new CompoundTag();t.putUUID("UUID",id);var slots=new ListTag();for(int i=0;i<7;i++)if(!a.bag.getItem(i).isEmpty()){var s=a.bag.getItem(i).save(new CompoundTag());s.putInt("Slot",i);slots.add(s);}t.put("Bag",slots);
            var tasted=new ListTag();a.tasted.forEach(s->tasted.add(StringTag.valueOf(s)));t.put("Tasted",tasted);
            var delicacies=new ListTag();a.delicacies.forEach(s->delicacies.add(StringTag.valueOf(s)));t.put("Delicacies",delicacies);
            t.putInt("FarmTier",a.farmTier);t.putLong("FarmCooldown",a.farmCooldown);t.putLong("NextMealTick",a.nextMealTick);
            t.putString("CampDimension",a.campDimension);t.putLong("CampPosition",a.campPosition);
            t.putLong("CampBloomDay",a.campBloomDay);t.putInt("CampBloomCount",a.campBloomCount);
            var flowers=new ListTag();a.homewardFlowers.forEach(f->flowers.add(f.copy()));t.put("HomewardFlowers",flowers);list.add(t);
        });root.put("Accounts",list);return root;
    }
}
