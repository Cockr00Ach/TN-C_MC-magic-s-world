package com.tnc.tnc.world.eartharena;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Version marker is saved in the same chunk NBT snapshot as the upgraded terrain. */
@Mod.EventBusSubscriber(modid="tnc")
public final class EarthArenaChunkLedger {
    private static final Map<ServerLevel,Map<Long,Integer>> VERSIONS=new WeakHashMap<>();
    public static synchronized int version(LevelChunk chunk){return VERSIONS.computeIfAbsent((ServerLevel)chunk.getLevel(),l->new HashMap<>()).getOrDefault(chunk.getPos().toLong(),0);}
    public static synchronized void mark(LevelChunk chunk,int version){VERSIONS.computeIfAbsent((ServerLevel)chunk.getLevel(),l->new HashMap<>()).put(chunk.getPos().toLong(),version);chunk.setUnsaved(true);}
    @SubscribeEvent public static synchronized void load(ChunkDataEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level&&level.dimension().equals(EarthArena.KEY))VERSIONS.computeIfAbsent(level,l->new HashMap<>()).put(event.getChunk().getPos().toLong(),event.getData().getInt(EarthArenaLayout.CHUNK_VERSION));
    }
    @SubscribeEvent public static synchronized void save(ChunkDataEvent.Save event){
        if(event.getLevel() instanceof ServerLevel level&&level.dimension().equals(EarthArena.KEY))event.getData().putInt(EarthArenaLayout.CHUNK_VERSION,VERSIONS.computeIfAbsent(level,l->new HashMap<>()).getOrDefault(event.getChunk().getPos().toLong(),0));
    }
    @SubscribeEvent public static synchronized void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){VERSIONS.clear();}
}
