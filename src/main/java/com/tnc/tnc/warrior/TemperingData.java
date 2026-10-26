package com.tnc.tnc.warrior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned UUID ledger survives player cloning and dimension changes. */
public final class TemperingData extends SavedData {
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> talents = new HashMap<>();
    public int talent(UUID id) { return talents.getOrDefault(id, 0); }
    public int cap(UUID id) { return WarriorTalentRules.cap(talent(id)); }
    public void setTalent(UUID id, int talent) {
        talents.put(id, WarriorTalentRules.clamp(talent)); points.putIfAbsent(id, 0); setDirty();
    }
    private final Map<UUID, Long> windReady = new HashMap<>();
    public boolean spendWindCooldown(UUID id, long now) {
        if (!enrolled(id) || !WindInfusionRules.ready(now, windReady.getOrDefault(id, 0L))) return false;
        windReady.put(id, now + WindInfusionRules.COOLDOWN_TICKS); setDirty(); return true;
    }
    private final java.util.Set<UUID> guardGraduates = new java.util.HashSet<>();
    public boolean completeGuardDrill(UUID id) {
        if (!enrolled(id) || !guardGraduates.add(id)) return false;
        setDirty(); return true;
    }
    public static TemperingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TemperingData::load, TemperingData::new, "tnc_tempering_v1");
    }
    public boolean enrolled(UUID id) { return points.containsKey(id) && talent(id) > 0; }
    public int points(UUID id) { return Math.min(cap(id), points.getOrDefault(id, 0)); }
    public boolean enroll(UUID id) {
        if (talent(id) == 0) return false;
        if (!points.containsKey(id)) { points.put(id, 0); setDirty(); } return true;
    }
    public void set(UUID id, int value) { points.put(id, TemperingRules.clamp(value)); setDirty(); }
    public static TemperingData load(CompoundTag root) {
        var data = new TemperingData();
        for (Tag entry : root.getList("Players", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag)entry;
            if (tag.hasUUID("Id")) {
                data.points.put(tag.getUUID("Id"), TemperingRules.clamp(tag.getInt("Points")));
                data.talents.put(tag.getUUID("Id"), WarriorTalentRules.clamp(tag.getInt("Talent")));
                if (tag.getBoolean("GuardGraduate")) data.guardGraduates.add(tag.getUUID("Id"));
                data.windReady.put(tag.getUUID("Id"), Math.max(0, tag.getLong("WindReady")));
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        var list = new ListTag();
        points.forEach((id, value) -> {
            var tag = new CompoundTag(); tag.putUUID("Id", id); tag.putInt("Points", value);
            tag.putInt("Talent", talent(id));
            tag.putBoolean("GuardGraduate", guardGraduates.contains(id)); list.add(tag);
            tag.putLong("WindReady", windReady.getOrDefault(id, 0L));
        });
        root.put("Players", list); return root;
    }
}
