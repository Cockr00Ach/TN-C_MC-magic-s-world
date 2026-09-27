package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class LandmarkData extends SavedData {
    final Map<String,Job> jobs=new LinkedHashMap<>();
    static final class Job {
        final String asset;
        final BlockPos origin;
        // 0 validate/site survey, 1 terrain, 2 templates, 3 complete, -1 stopped.
        int phase,tile,piece,cursor,validated;
        String error="";
        boolean initialized,waiting;
        int[] surfaces,grounds,materials;
        FloatingFootprint floatingFootprint;
        final PreservedSite preserved=new PreservedSite();
        final ArrayList<BlockState> palette=new ArrayList<>();
        Job(String asset,BlockPos origin) { this.asset=asset; this.origin=origin; }
        String key() { return asset+"_"+Long.toHexString(origin.asLong()); }
        StonecrestManifest manifest() { return StonecrestManifest.get(asset); }
        int columns() { return manifest().dimensions().getX()*manifest().dimensions().getZ(); }
        int tilesX() { return (manifest().dimensions().getX()+15)/16; }
        int tileCount() { return tilesX()*((manifest().dimensions().getZ()+15)/16); }
        BlockPos tilePos() { return origin.offset((tile%tilesX())*16,0,(tile/tilesX())*16); }
    }
    static LandmarkData get(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(LandmarkData::load,LandmarkData::new,"tnc_landmarks_v1");
    }
    static LandmarkData load(CompoundTag root) {
        var d=new LandmarkData();
        for (Tag raw:root.getList("Jobs",Tag.TAG_COMPOUND)) {
            var t=(CompoundTag)raw; var j=new Job(t.getString("Asset"),BlockPos.of(t.getLong("Origin")));
            j.phase=t.getInt("Phase"); j.error=t.getString("Error");
            if (root.getInt("PreservedSiteVersion")<1 && j.phase==-1
                    && j.error.startsWith("phase=0 ")
                    && j.error.contains("Protected block entity in pending write volume")) {
                j.phase=0; j.error="";
            }
            // Legacy bounding-box false positive: phase 0 has never written any world blocks.
            // Only retry this exact preflight error once; never revive a partial write or protected-container stop.
            if (root.getInt("FloatingSurveyVersion")<1 && j.phase==-1 && j.manifest().floating()
                    && j.error.startsWith("phase=0 ")
                    && j.error.contains("Floating island would intersect existing terrain")) {
                j.phase=0; j.error="";
            }
            // Reconcile chunk-local commit bits, never trust a saved global cursor.
            if (j.phase>0) j.phase=1;
            d.jobs.put(j.key(),j);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("FloatingSurveyVersion",1);
        root.putInt("PreservedSiteVersion",1);
        var list=new ListTag();
        for (var j:jobs.values()) {
            var t=new CompoundTag(); t.putString("Asset",j.asset); t.putLong("Origin",j.origin.asLong());
            t.putInt("Phase",j.phase); t.putString("Error",j.error); list.add(t);
        }
        root.put("Jobs",list); return root;
    }
}
