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
    private static final ConcurrentHashMap<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager,Boolean> PALACES=new ConcurrentHashMap<>();
    final boolean nativeChunks;
    final boolean fullHeroskand;
    LandmarkGenerationMode(boolean enabled) { this(enabled,enabled); }
    private LandmarkGenerationMode(boolean enabled,boolean full) { nativeChunks=enabled; fullHeroskand=full; }
    static LandmarkGenerationMode load(CompoundTag tag) {
        return new LandmarkGenerationMode(tag.getBoolean("NativeChunks"),tag.getBoolean("FullHeroskand"));
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("NativeChunks",nativeChunks); tag.putBoolean("FullHeroskand",fullHeroskand); return tag;
    }
    static boolean fullPalace(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
        return PALACES.getOrDefault(manager,false);
    }
    static boolean enabled(ServerLevel level) { return MODES.getOrDefault(level,false); }
    @SubscribeEvent public static void loaded(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD) return;
        var mode=level.getDataStorage().computeIfAbsent(LandmarkGenerationMode::load,()->{
            var created=new LandmarkGenerationMode(level.getGameTime()==0
                    && !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer));
            created.setDirty(); return created;
        },ID);
        PALACES.put(level.getStructureManager(), mode.fullHeroskand
                || level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
        MODES.put(level,mode.nativeChunks);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        MODES.keySet().removeIf(l->{
            if (l.getServer()!=event.getServer()) return false;
            PALACES.remove(l.getStructureManager()); return true;
        });
        LandmarkSites.clear(event.getServer());
    }
}
