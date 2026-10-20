package com.tnc.tnc.journey;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;
/** Per-player expedition and household records; independent of inventory, death and MCA marriages. */
public final class JourneyData extends SavedData {
    private CompoundTag players=new CompoundTag();
    public static JourneyData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(t->{var d=new JourneyData();d.players=t.getCompound("Players");return d;},JourneyData::new,"tnc_journeys");}
    public CompoundTag player(UUID id){String k=id.toString();if(!players.contains(k,10))players.put(k,new CompoundTag());return players.getCompound(k);}
    @Override public CompoundTag save(CompoundTag t){t.put("Players",players);return t;}
}
