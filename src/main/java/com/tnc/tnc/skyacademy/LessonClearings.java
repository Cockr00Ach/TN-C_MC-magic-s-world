package com.tnc.tnc.skyacademy;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.adventure.TownServiceNpc;
import com.tnc.tnc.life.botanical.BotanicalPlantEntity;
import com.tnc.tnc.life.routes.RouteNodeEntity;
import com.tnc.tnc.world.SkyIslandWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** The shipped starter contains these clearings. Upgrade matching blocks in earlier saves as their chunks load. */
@Mod.EventBusSubscriber(modid = "tnc")
public final class LessonClearings {
    private static final String MARK = "TncLessonClearingsV1";
    private static final Map<MinecraftServer, Boolean> HIGHLANDS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Layout> LAYOUTS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<Long>> COMPLETE = new WeakHashMap<>();
    private static final Map<String, BlockPos> OLD_MENTORS = Map.of(
            "academy_wind", new BlockPos(88,107,190),
            "academy_farm", new BlockPos(93,92,320),
            "academy_workshop", new BlockPos(420,93,369));

    private record Change(BlockPos pos, BlockState before, BlockState after) {}
    private record Layout(Map<Long, List<Change>> chunks) {}
    private LessonClearings() {}

    static boolean highlands(MinecraftServer server) {
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) return true;
        return HIGHLANDS.computeIfAbsent(server, s -> Files.isRegularFile(s.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions/tnc/skyhaven/.tnc-highlands-lod-v1")));
    }

    /** Marks travel in the same NBT as the edited blocks; a partial chunk can safely be replayed. */
    @SubscribeEvent public static synchronized void load(ChunkDataEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getData().getBoolean(MARK)) {
            COMPLETE.computeIfAbsent(level, k -> new HashSet<>()).add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent public static synchronized void save(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && marked(level, event.getChunk().getPos().toLong())) {
            event.getData().putBoolean(MARK, true);
        }
    }

    private static synchronized boolean marked(ServerLevel level, long chunk) {
        return COMPLETE.getOrDefault(level, Set.of()).contains(chunk);
    }

    private static synchronized void finish(ServerLevel level, long chunk) {
        COMPLETE.computeIfAbsent(level, k -> new HashSet<>()).add(chunk);
    }

    private static Layout read(ServerLevel level, BlockPos origin) {
        String file = highlands(level.getServer()) ? "lesson_clearings.nbt" : "lesson_clearings_legacy.nbt";
        try (var in = LessonClearings.class.getResourceAsStream("/data/tnc/sky_academy/" + file)) {
            var nbt = NbtIo.readCompressed(Objects.requireNonNull(in));
            if (nbt.getInt("Version") != 1) throw new IOException("Unknown lesson terrain version");
            var states = new ArrayList<BlockState>();
            var registry = level.registryAccess().registryOrThrow(Registries.BLOCK);
            for (var entry : nbt.getList("Palette", 10)) {
                var tag = (CompoundTag)entry;
                // Optional decorative rock types may be absent from a developer's minimal installation.
                states.add(registry.containsKey(ResourceLocation.parse(tag.getString("Name")))
                        ? NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag) : null);
            }
            int[] rows = nbt.getIntArray("Changes");
            if (rows.length % 5 != 0 || rows.length > 100_000) throw new IOException("Invalid lesson patch length");
            Map<Long,List<Change>> chunks = new HashMap<>();
            for (int i=0; i<rows.length; i+=5) {
                BlockPos pos = origin.offset(rows[i], rows[i+1], rows[i+2]);
                var before = states.get(rows[i+3]);
                var after = states.get(rows[i+4]);
                if (before != null && after != null) {
                    chunks.computeIfAbsent(new ChunkPos(pos).toLong(), k -> new ArrayList<>()).add(new Change(pos,before,after));
                }
            }
            return new Layout(chunks);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取教学场地 " + file, failure);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 4 != 0) return;
        var server = event.getServer();
        if (server instanceof net.minecraft.gametest.framework.GameTestServer || !SkyAcademyWorld.available(server)) return;
        var level = SkyIslandWorlds.island(server);
        var origin = SkyAcademyWorld.origin(server);
        if (level.players().isEmpty() || origin == null) return;
        var layout = LAYOUTS.computeIfAbsent(server, s -> read(level, origin));
        if (server.getTickCount() % 80 == 0) retireMentors(level, origin);
        // At most one already-loaded chunk per pass. Never force-load a player's world.
        for (var entry : layout.chunks.entrySet()) {
            long key = entry.getKey();
            if (marked(level,key)) continue;
            var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            int conflicts = 0;
            boolean obstructed = false;
            for (var c : entry.getValue()) {
                var actual = level.getBlockState(c.pos);
                if (actual.equals(c.after)) continue;
                if (!actual.equals(c.before) || level.getBlockEntity(c.pos) != null) {
                    conflicts++;
                    continue;
                }
                if (!c.after.isAir() && level.players().stream().anyMatch(p -> !p.isSpectator()
                        && p.getBoundingBox().intersects(new AABB(c.pos)))) {
                    obstructed = true;
                    continue;
                }
                level.setBlock(c.pos, c.after, 18);
            }
            if (!obstructed) {
                if (conflicts > 0) LogUtils.getLogger().info("[Sky Academy] Preserved {} changed blocks in {}", conflicts, chunk.getPos());
                finish(level,key);
                chunk.setUnsaved(true);
            }
            // Other chunks can still finish if a player stands in this one.
            if (!obstructed) break;
        }
    }

    private static void retireMentors(ServerLevel level, BlockPos origin) {
        for (var entry : OLD_MENTORS.entrySet()) {
            var old = origin.offset(entry.getValue());
            if (!level.hasChunkAt(old)) continue;
            for (var npc : level.getEntitiesOfClass(TownServiceNpc.class, new AABB(old).inflate(7),
                    n -> entry.getKey().equals(n.role()))) npc.discard();
        }
    }

    /** Even a protected player build on top of a former clearing must never be overwritten by lesson setup. */
    public static boolean ready(MinecraftServer server, String site) {
        if (!site.equals("wind") && !site.equals("farm") && !site.equals("workshop")) return true;
        var level = SkyIslandWorlds.island(server);
        var npc = SkyAcademyWorld.at(server,site);
        if (npc == null || !level.hasChunkAt(npc) || !floor(level,npc)
                || !level.getBlockState(npc).getCollisionShape(level,npc).isEmpty()
                || !level.getBlockState(npc.above()).getCollisionShape(level,npc.above()).isEmpty()) return false;
        if (site.equals("wind")) {
            for (int i=0; i<3; i++) {
                var pos=SkyAcademyWorld.at(server,"rune"+i);
                if (!level.hasChunkAt(pos)) return false;
                for (int section=0; section<3; section++)
                    if (!level.getBlockState(pos.above(section)).equals(WindChimePillar.state(i,section))) return false;
            }
            return true;
        }
        var base = AcademyStations.base(server,site);
        boolean farm = site.equals("farm");
        for (int x=0; x<5; x++) {
            var pos=base.east(x);
            if (!level.hasChunkAt(pos) || !floor(level,pos)) return false;
            var block=level.getBlockEntity(pos);
            boolean tutorialNode=block instanceof RouteNodeEntity
                    && (block.getPersistentData().getBoolean("SkyAcademy") || block.getPersistentData().hasUUID("AcademyPlacedBy"));
            boolean tutorialFlower=farm && x==0 && block instanceof BotanicalPlantEntity && block.getPersistentData().getBoolean("SkyAcademy");
            if (!level.getBlockState(pos).canBeReplaced() && !tutorialNode && !tutorialFlower) return false;
        }
        if (farm) {
            // The lesson also sets the flower's soil, source fluid and its four containment blocks.
            for (var offset : List.of(base.below(),base.offset(0,-1,1),base.offset(-1,-1,1),
                    base.offset(1,-1,1),base.offset(0,-1,2),base.offset(0,-2,1))) {
                if (level.getBlockEntity(offset)!=null) return false;
            }
        }
        return true;
    }

    private static boolean floor(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP);
    }

    @SubscribeEvent public static synchronized void stop(ServerStoppedEvent event) {
        HIGHLANDS.remove(event.getServer());
        LAYOUTS.remove(event.getServer());
        COMPLETE.keySet().removeIf(l -> l.getServer()==event.getServer());
    }
}
