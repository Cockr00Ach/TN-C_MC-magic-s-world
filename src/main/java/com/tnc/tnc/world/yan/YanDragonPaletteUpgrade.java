package com.tnc.tnc.world.yan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Retints the original statue in loaded chunks, using the same palette as new terrain. */
@Mod.EventBusSubscriber(modid = "tnc")
public final class YanDragonPaletteUpgrade {
    private static final String MARKER = "TncYanDragonPaletteV1";
    private static final int BLOCKS_PER_TICK = 768;
    private static final long NANOS_PER_TICK = 1_000_000;
    // Chunk IO may read and save markers concurrently with the server tick.
    private static final Map<LevelChunk, Boolean> COMPLETE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, Map<LevelChunk, Recolor>> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private YanDragonPaletteUpgrade() {}

    private static boolean inYan(ServerLevel level) {
        return level.dimension().equals(YanSanctuary.KEY) || level.dimension().equals(YanPrologue.WORLD);
    }

    @SubscribeEvent
    public static void readMarker(ChunkDataEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk && event.getLevel() instanceof ServerLevel level
                && inYan(level) && event.getData().getBoolean(MARKER)) COMPLETE.put(chunk, true);
    }

    @SubscribeEvent
    public static void saveMarker(ChunkDataEvent.Save event) {
        if (event.getChunk() instanceof LevelChunk chunk && COMPLETE.containsKey(chunk)) event.getData().putBoolean(MARKER, true);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inYan(level)
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !(level.getChunkSource().getGenerator() instanceof YanSanctuaryGenerator generator)) return;
        if (!generator.hasDragon(chunk.getPos())) return;
        if (event.isNewChunk()) {
            complete(chunk);
        } else if (!COMPLETE.containsKey(chunk)) {
            var server = level.getServer();
            if (server.isSameThread()) queue(server, generator, chunk);
            else server.execute(() -> queue(server, generator, chunk));
        }
    }

    private static void queue(MinecraftServer server, YanSanctuaryGenerator generator, LevelChunk chunk) {
        PENDING.computeIfAbsent(server, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(chunk, ignored -> new Recolor(generator, chunk));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk && event.getLevel() instanceof ServerLevel level && inYan(level)) {
            var server = level.getServer();
            if (server.isSameThread()) dequeue(server, chunk);
            else server.execute(() -> dequeue(server, chunk));
        }
    }

    private static void dequeue(MinecraftServer server, LevelChunk chunk) {
        var queue = PENDING.get(server);
        if (queue != null) queue.remove(chunk);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var queue = PENDING.get(event.getServer());
        if (queue == null || queue.isEmpty()) return;
        long start = System.nanoTime();
        int remaining = BLOCKS_PER_TICK;
        var jobs = queue.entrySet().iterator();
        while (remaining > 0 && jobs.hasNext() && System.nanoTime() - start < NANOS_PER_TICK) {
            var entry = jobs.next();
            var chunk = entry.getKey();
            var level = (ServerLevel) chunk.getLevel();
            var pos = chunk.getPos();
            // Full chunks can arrive before their holder is visible; leave those jobs for the next tick.
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) continue;
            while (remaining-- > 0 && System.nanoTime() - start < NANOS_PER_TICK) {
                if (entry.getValue().step()) {
                    jobs.remove();
                    complete(chunk);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
    }

    private static void complete(LevelChunk chunk) {
        COMPLETE.put(chunk, true);
        chunk.setUnsaved(true);
    }

    static final class Recolor {
        private final YanSanctuaryGenerator generator;
        private final LevelChunk chunk;
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private final int startX;
        private final int startZ;
        private int columnIndex;
        private int blockIndex;
        private short[] column;

        Recolor(YanSanctuaryGenerator generator, LevelChunk chunk) {
            this.generator = generator;
            this.chunk = chunk;
            startX = chunk.getPos().getMinBlockX();
            startZ = chunk.getPos().getMinBlockZ();
        }

        boolean step() {
            if (columnIndex >= 256) return true;
            int x = startX + (columnIndex & 15);
            int z = startZ + (columnIndex >> 4);
            if (blockIndex == 0) column = generator.dragonColumn(x, z);
            if (column != null) {
                short block = column[blockIndex++];
                var stone = YanSanctuaryGenerator.dragonMaterial(block);
                at.set(x, YanSanctuaryGenerator.dragonY(block), z);
                if (!stone.is(YanSanctuary.OBSIDIAN) && chunk.getBlockState(at).is(YanSanctuary.OBSIDIAN)
                        && chunk.setBlockState(at, stone, false) != null) {
                    ((ServerLevel) chunk.getLevel()).getChunkSource().blockChanged(at);
                }
                if (blockIndex < column.length) return false;
            }
            columnIndex++;
            blockIndex = 0;
            return columnIndex == 256;
        }
    }
}
