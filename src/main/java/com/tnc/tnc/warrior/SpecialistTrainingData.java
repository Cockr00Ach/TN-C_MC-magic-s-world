package com.tnc.tnc.warrior;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Lesson completions are practice records, never a second source of growth rewards. */
public final class SpecialistTrainingData extends SavedData {
    private final Map<UUID,CompoundTag> records=new HashMap<>();
    public static SpecialistTrainingData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(SpecialistTrainingData::load,SpecialistTrainingData::new,"tnc_specialist_training_v1");}
    public int count(UUID id,String role){return Math.max(0,records.getOrDefault(id,new CompoundTag()).getInt(role));}
    public void complete(UUID id,String role){var t=records.computeIfAbsent(id,k->new CompoundTag());int n=count(id,role);t.putInt(role,n==Integer.MAX_VALUE?n:n+1);setDirty();}
    public static SpecialistTrainingData load(CompoundTag tag){var d=new SpecialistTrainingData();for(Tag raw:tag.getList("Records",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.hasUUID("Id"))d.records.put(t.getUUID("Id"),t.getCompound("Lessons").copy());}return d;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();records.forEach((id,t)->{var r=new CompoundTag();r.putUUID("Id",id);r.put("Lessons",t.copy());list.add(r);});tag.put("Records",list);return tag;}
}
