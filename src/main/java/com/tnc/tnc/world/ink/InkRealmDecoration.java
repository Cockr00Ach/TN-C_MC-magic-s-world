package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Paints persistent, multi-chunk calligraphic strokes onto the otherwise flat paper floor. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class InkRealmDecoration {
    // V3 invalidates V1/V2 markers. V2 could mark a freshly generated chunk as
    // complete while its heightmap was still empty, leaving the paper untouched.
    private static final String SAVED_KEY = "TNCInkFloorV9ShanshuiOnly";
    private static final int PAINT_RADIUS_CHUNKS = 6;
    private static final int CHUNKS_PER_TICK = 6;
    private static final List<int[]> NEARBY_OFFSETS = nearbyOffsets();
    private static final Set<Long> DECORATED = ConcurrentHashMap.newKeySet();

    private InkRealmDecoration() {}

    @SubscribeEvent
    public static void readMarker(ChunkDataEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk
                && isInkRealm(chunk)
                && event.getData().getBoolean(SAVED_KEY)) {
            DECORATED.add(chunk.getPos().toLong());
        }
    }

    /**
     * Paint only chunks near an actual player, in a small per-tick budget.
     * This avoids both the unreliable generation-time heightmap and a large
     * one-frame spike when a high render distance loads thousands of chunks.
     */
    @SubscribeEvent
    public static void paintNearPlayers(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player.level() instanceof ServerLevel level)
                || !level.dimension().equals(InkRealmTravel.INK_REALM)) return;

        int centerX = event.player.chunkPosition().x;
        int centerZ = event.player.chunkPosition().z;
        int painted = 0;
        for (int[] offset : NEARBY_OFFSETS) {
            int chunkX = centerX + offset[0];
            int chunkZ = centerZ + offset[1];
            long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
            if (DECORATED.contains(key)) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) continue;
            paint(chunk);
            DECORATED.add(key);
            if (++painted >= CHUNKS_PER_TICK) break;
        }
    }

    @SubscribeEvent
    public static void writeMarker(ChunkDataEvent.Save event) {
        if (event.getChunk() instanceof LevelChunk chunk
                && isInkRealm(chunk)
                && DECORATED.contains(chunk.getPos().toLong())) {
            event.getData().putBoolean(SAVED_KEY, true);
        }
    }

    @SubscribeEvent
    public static void clear(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(InkRealmTravel.INK_REALM)) {
            DECORATED.clear();
        }
    }

    private static boolean isInkRealm(LevelChunk chunk) {
        return !chunk.getLevel().isClientSide
                && chunk.getLevel().dimension().equals(InkRealmTravel.INK_REALM);
    }

    static void paint(LevelChunk chunk) {
        if (!isInkRealm(chunk)) return;
        ServerLevel level = (ServerLevel) chunk.getLevel();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                // The flat generator has one bedrock + three stone + one paper
                // layer. Its top block is therefore always minY + 4. Do not use
                // a heightmap here: during first-load events it can still report
                // an uninitialised value, which caused the all-white V2 floor.
                int y = chunk.getMinBuildHeight() + 4;
                position.set(startX + localX, y, startZ + localZ);
                BlockState old = chunk.getBlockState(position);
                if (!isPaperFloor(old)) continue;
                BlockState next = stateFor(InkFloorPattern.shadeAt(position.getX(), position.getZ()));
                if (!old.equals(next)) {
                    // ChunkEvent.Load may run after the initial chunk packet has
                    // reached the player. UPDATE_CLIENTS makes the calligraphy
                    // visible immediately instead of only after a relog/reload.
                    level.setBlock(position, next, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        if (changed) chunk.setUnsaved(true);
    }

    private static List<int[]> nearbyOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -PAINT_RADIUS_CHUNKS; x <= PAINT_RADIUS_CHUNKS; x++) {
            for (int z = -PAINT_RADIUS_CHUNKS; z <= PAINT_RADIUS_CHUNKS; z++) {
                offsets.add(new int[]{x, z});
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
        return List.copyOf(offsets);
    }

    private static boolean isPaperFloor(BlockState state) {
        return state.is(Blocks.WHITE_CONCRETE) || state.is(Blocks.LIGHT_GRAY_CONCRETE)
                || state.is(Blocks.GRAY_CONCRETE) || state.is(Blocks.BLACK_CONCRETE);
    }

    private static BlockState stateFor(InkFloorPattern.Shade shade) {
        return switch (shade) {
            case BLACK -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case GRAY -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case WHITE -> Blocks.WHITE_CONCRETE.defaultBlockState();
        };
    }
}
