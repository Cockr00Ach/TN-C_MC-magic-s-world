package com.tnc.tnc.bronze;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One authoritative bond per owner, independent of item stacks, dimensions and chunk loading. */
public final class PuppetBondData extends SavedData {
    public static final int LIFETIME=12000,COOLDOWN=2400,FADE=30;
    public static final double ATTACK=12;
    public record Bond(UUID puppet,long disappearsAt,long readyAt){}
    private final Map<UUID,Bond> bonds=new HashMap<>();
    public static PuppetBondData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(PuppetBondData::load,PuppetBondData::new,"tnc_bronze_puppets_v1");}
    public Bond bond(UUID owner){return bonds.get(owner);}
    public boolean owns(UUID owner,UUID puppet){var b=bond(owner);return b!=null&&puppet.equals(b.puppet);}
    public boolean available(UUID owner,long now){var b=bond(owner);return b==null||b.puppet==null&&now>=b.readyAt;}
    public boolean reserve(UUID owner,UUID puppet,long now){
        if(!available(owner,now))return false;bonds.put(owner,new Bond(puppet,now+LIFETIME,0));setDirty();return true;
    }
    public void fading(UUID owner,UUID puppet,long end){if(owns(owner,puppet)){var b=bond(owner);bonds.put(owner,new Bond(puppet,Math.min(b.disappearsAt,end),0));setDirty();}}
    public void vanished(UUID owner,UUID puppet,long now){if(owns(owner,puppet)){bonds.put(owner,new Bond(null,0,now+COOLDOWN));setDirty();}}
    public void failed(UUID owner,UUID puppet){if(owns(owner,puppet)){bonds.remove(owner);setDirty();}}
    public List<Map.Entry<UUID,Bond>> expired(long now){return bonds.entrySet().stream().filter(e->e.getValue().puppet!=null&&now>=e.getValue().disappearsAt).map(e->Map.entry(e.getKey(),e.getValue())).toList();}
    public static float health(float ownerMaximum){return Math.max(1,ownerMaximum)*10;}
    public static PuppetBondData load(CompoundTag tag){
        var data=new PuppetBondData();for(var raw:tag.getList("Bonds",10)){var row=(CompoundTag)raw;if(!row.hasUUID("Owner"))continue;
            data.bonds.put(row.getUUID("Owner"),new Bond(row.hasUUID("Puppet")?row.getUUID("Puppet"):null,row.getLong("DisappearsAt"),row.getLong("ReadyAt")));}return data;
    }
    @Override public CompoundTag save(CompoundTag tag){var rows=new ListTag();bonds.forEach((owner,bond)->{var row=new CompoundTag();row.putUUID("Owner",owner);if(bond.puppet!=null)row.putUUID("Puppet",bond.puppet);row.putLong("DisappearsAt",bond.disappearsAt);row.putLong("ReadyAt",bond.readyAt);rows.add(row);});tag.put("Bonds",rows);return tag;}
}
