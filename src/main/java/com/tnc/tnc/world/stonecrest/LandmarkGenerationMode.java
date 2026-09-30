package com.tnc.tnc.world.stonecrest;

import com.tnc.tnc.TNMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.ConcurrentHashMap;

/** Persist the algorithm per world: never mix new chunk placement with an old construction journal. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class LandmarkGenerationMode extends SavedData {
    private static final String ID="tnc_landmark_generation_v2";
    private static final ConcurrentHashMap<ServerLevel,Boolean> MODES=new ConcurrentHashMap<>();
    final boolean nativeChunks;
    LandmarkGenerationMode(boolean enabled) { nativeChunks=enabled; }
    static LandmarkGenerationMode load(CompoundTag tag) { return new LandmarkGenerationMode(tag.getBoolean("NativeChunks")); }
    @Override public CompoundTag save(CompoundTag tag) { tag.putBoolean("NativeChunks",nativeChunks); return tag; }
    static boolean enabled(ServerLevel level) { return MODES.getOrDefault(level,false); }
    @SubscribeEvent public static void loaded(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD) return;
        var mode=level.getDataStorage().computeIfAbsent(LandmarkGenerationMode::load,()->{
            var created=new LandmarkGenerationMode(level.getGameTime()==0
                    && !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer));
            created.setDirty(); return created;
        },ID);
        MODES.put(level,mode.nativeChunks);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        MODES.keySet().removeIf(l->l.getServer()==event.getServer());
        LandmarkSites.clear(event.getServer());
    }
}
