package com.tnc.tnc.world.stonecrest;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Commit markers stored in the SAME chunk NBT snapshot as the affected blocks. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class AbyssChunkLedger {
    private static final String KEY="tnc_abyss_v1";
    private static final Map<ServerLevel,Map<Long,CompoundTag>> LEDGERS=new WeakHashMap<>();
    private static CompoundTag entry(ServerLevel level,ChunkPos chunk) {
        return LEDGERS.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(chunk.toLong(),k->new CompoundTag());
    }
    static synchronized boolean complete(ServerLevel level,List<ChunkPos> chunks,BlockPos origin,String phase,int index) {
        String key=Long.toHexString(origin.asLong());
        for (var c:chunks) if (!BitSet.valueOf(entry(level,c).getCompound(key).getLongArray(phase)).get(index)) return false;
        return true;
    }
    static synchronized void mark(ServerLevel level,List<ChunkPos> chunks,BlockPos origin,String phase,int index) {
        String key=Long.toHexString(origin.asLong());
        for (var c:chunks) {
            var root=entry(level,c); var tag=root.getCompound(key);
            var bits=BitSet.valueOf(tag.getLongArray(phase)); bits.set(index);
            tag.putLongArray(phase,bits.toLongArray()); root.put(key,tag);
            level.getChunk(c.x,c.z).setUnsaved(true);
        }
    }
    static synchronized void invalidateTemplates(ServerLevel level,List<ChunkPos> chunks,BlockPos origin) {
        invalidate(level,chunks,origin,"P");
    }
    static synchronized void invalidate(ServerLevel level,List<ChunkPos> chunks,BlockPos origin,String phase) {
        String key=Long.toHexString(origin.asLong());
        for (var c:chunks) {
            var root=entry(level,c); var tag=root.getCompound(key);
            tag.remove(phase); root.put(key,tag); level.getChunk(c.x,c.z).setUnsaved(true);
        }
    }
    @SubscribeEvent public static void load(ChunkDataEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var tag=event.getData().getCompound(KEY).copy();
        synchronized (AbyssChunkLedger.class) {
            var map=LEDGERS.computeIfAbsent(level,k->new HashMap<>());
            if (tag.isEmpty()) map.remove(event.getChunk().getPos().toLong());
            else map.put(event.getChunk().getPos().toLong(),tag);
        }
    }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Vanilla already persists the marker piece in StructureStart, including in
        // ProtoChunks whose ChunkDataEvent.Load has no world. Re-admit from that source
        // when promoted/reloaded; never perform level/chunk operations in this callback.
        for (var start:event.getChunk().getAllStarts().values())
            if (start.getStructure() instanceof AbyssCitadelStructure)
                for (var piece:start.getPieces()) if (piece instanceof AbyssMarkerPiece marker)
                    AbyssCitadelJobs.request(level,marker.origin());
        for (var start:event.getChunk().getAllStarts().values())
            for (var piece:start.getPieces()) if (piece instanceof LargeLandmarkMarker marker)
                LargeLandmarkJobs.request(level,marker.asset,marker.origin);
    }
    @SubscribeEvent public static synchronized void save(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var map=LEDGERS.get(level);
        var tag=map==null ? null : map.get(event.getChunk().getPos().toLong());
        if (tag!=null && !tag.isEmpty()) event.getData().put(KEY,tag.copy());
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) {
        LEDGERS.keySet().removeIf(l->l.getServer()==event.getServer());
    }
}
