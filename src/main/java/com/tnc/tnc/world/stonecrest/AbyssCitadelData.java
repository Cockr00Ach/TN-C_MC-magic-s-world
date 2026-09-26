package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;

/** One durable checkpoint per occurrence, including finished occurrences (never regenerated). */
final class AbyssCitadelData extends SavedData {
    final Map<Long, Job> jobs = new LinkedHashMap<>();
    static final int TILE_COUNT = (AbyssPitPlan.WIDTH / 16) * (AbyssPitPlan.DEPTH / 16);
    static AbyssCitadelData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AbyssCitadelData::load,AbyssCitadelData::new,"tnc_abyss_citadels_v1");
    }
    static final class Job {
        final BlockPos origin;
        // 0 preflight, 1 terrain, 2 templates, 3 lookout, 4 done, -1 error.
        int phase, tile, cursor, piece;
        int lookoutY;
        int[] originalSurfaces = new int[TILE_COUNT*256];
        String error = "";
        boolean waitingForPlayers;
        boolean initialized;
        Job(BlockPos origin) { this.origin = origin; }
        BlockPos center() { return origin.offset(122,0,70); }
        BlockPos tileOrigin() {
            return new BlockPos(center().getX()-208+(tile%26)*16,-64,center().getZ()-160+(tile/26)*16);
        }
    }
    static AbyssCitadelData load(CompoundTag tag) {
        var data = new AbyssCitadelData();
        for (Tag raw : tag.getList("Jobs",Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var j = new Job(BlockPos.of(t.getLong("Origin")));
            j.phase=t.getInt("Phase"); j.tile=t.getInt("Tile"); j.cursor=t.getInt("Cursor"); j.piece=t.getInt("Piece");
            j.lookoutY=t.getInt("LookoutY");
            var heights=t.getIntArray("OriginalSurfaces");
            if (heights.length==TILE_COUNT*256) j.originalSurfaces=heights;
            j.error=t.getString("Error");
            // Completion may have reached SavedData before the final chunk snapshot.
            // Verify all persisted completed jobs, even when their southern anchor is unloaded.
            if (j.phase==4) j.phase=1;
            data.jobs.put(j.origin.asLong(),j);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        for (var j : jobs.values()) {
            var t = new CompoundTag();
            t.putLong("Origin",j.origin.asLong()); t.putInt("Phase",j.phase); t.putInt("Tile",j.tile);
            t.putInt("Cursor",j.cursor); t.putInt("Piece",j.piece); t.putIntArray("OriginalSurfaces",j.originalSurfaces);
            t.putInt("LookoutY",j.lookoutY);
            t.putString("Error",j.error); list.add(t);
        }
        tag.put("Jobs",list);
        return tag;
    }
}
