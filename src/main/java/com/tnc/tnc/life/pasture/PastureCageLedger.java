package com.tnc.tnc.life.pasture;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Ticket refers to exactly one persistent entity UUID. Cloned cage NBT spends the same ticket. */
public final class PastureCageLedger extends SavedData {
    private final Map<UUID,CompoundTag> entries=new HashMap<>();
    public static PastureCageLedger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(PastureCageLedger::load,PastureCageLedger::new,"tnc_pasture_cages_v1");}
    public UUID store(CompoundTag entity){if(entries.size()>=4096)return null;UUID ticket=UUID.randomUUID();entries.put(ticket,entity.copy());setDirty();return ticket;}
    public CompoundTag peek(UUID ticket){CompoundTag t=entries.get(ticket);return t==null?null:t.copy();}
    public boolean consume(UUID ticket){if(entries.remove(ticket)==null)return false;setDirty();return true;}
    public static PastureCageLedger load(CompoundTag tag){PastureCageLedger data=new PastureCageLedger();for(Tag entry:tag.getList("Entries",10)){CompoundTag e=(CompoundTag)entry;if(data.entries.size()<4096&&e.hasUUID("Ticket"))data.entries.put(e.getUUID("Ticket"),e.getCompound("Animal").copy());}return data;}
    @Override public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();entries.forEach((id,animal)->{CompoundTag e=new CompoundTag();e.putUUID("Ticket",id);e.put("Animal",animal.copy());list.add(e);});tag.put("Entries",list);return tag;}
}
