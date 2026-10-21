package com.tnc.tnc.arcana;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
/** Death receipts remain valid when a tower is unloaded or carried as an item. */
public final class ArcanaDisasters extends SavedData {
 private CompoundTag killed=new CompoundTag();
 public static ArcanaDisasters get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(t->{var d=new ArcanaDisasters();d.killed=t.getCompound("Killed");return d;},ArcanaDisasters::new,"tnc_arcana_disasters");}
 public void mark(UUID id){killed.putBoolean(id.toString(),true);setDirty();}
 public boolean dead(UUID id){return killed.getBoolean(id.toString());}
 @Override public CompoundTag save(CompoundTag t){t.put("Killed",killed);return t;}
}
