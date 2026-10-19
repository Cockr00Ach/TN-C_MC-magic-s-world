package com.tnc.tnc.world.eartharena;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class EarthArenaEncounterState extends SavedData {
    public UUID boss;
    public boolean defeated;
    public static EarthArenaEncounterState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(EarthArenaEncounterState::load,EarthArenaEncounterState::new,"tnc_earth_arena_encounter");
    }
    public static EarthArenaEncounterState load(CompoundTag tag) {
        var state=new EarthArenaEncounterState();if(tag.hasUUID("Boss"))state.boss=tag.getUUID("Boss");state.defeated=tag.getBoolean("Defeated");return state;
    }
    @Override public CompoundTag save(CompoundTag tag) { if(boss!=null)tag.putUUID("Boss",boss);tag.putBoolean("Defeated",defeated);return tag; }
}
