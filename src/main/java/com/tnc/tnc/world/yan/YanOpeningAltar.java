package com.tnc.tnc.world.yan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The raised, octagonal stage used only in the playable prologue. */
final class YanOpeningAltar {
    static final int EDGE = 42;
    static final int WALK_LIMIT = 35;
    static final int FLOOR = YanSanctuaryLayout.FLOOR;
    static final int FEET = YanSanctuaryLayout.ALTAR_HEIGHT + 1;

    private YanOpeningAltar() {}

    static double gauge(double x, double z) {
        return Math.max(Math.max(Math.abs(x), Math.abs(z)), .75 * (Math.abs(x) + Math.abs(z)));
    }

    static Vec3 limit(Vec3 destination, BlockPos center) {
        double x = destination.x - (center.getX() + .5);
        double z = destination.z - (center.getZ() + .5);
        double radius = gauge(x, z);
        if (radius <= WALK_LIMIT) return destination;
        double scale = WALK_LIMIT / radius;
        return new Vec3(center.getX() + .5 + x * scale, destination.y, center.getZ() + .5 + z * scale);
    }

    static boolean floorPoint(Vec3 point, BlockPos center) {
        return gauge(point.x - center.getX() - .5, point.z - center.getZ() - .5) <= WALK_LIMIT + 1.e-6
                && Math.abs(point.y - FEET) < .1;
    }

    static BlockState block(int x, int y, int z) {
        BlockState old = YanSanctuaryLayout.platform(x, y, z);
        int height = y - FLOOR;
        int distance = (int)Math.ceil(gauge(x, z));
        if (height < 0 || height > 3 || distance > EDGE) return old;
        // Preserve the central altar, original inlays and raised braziers.
        if (distance <= 21 || !old.isAir() && height > 0) return old;
        int stepEdge = EDGE - height * 2;
        if (distance > stepEdge) return old;
        if (distance == stepEdge || height == 3 && (distance == 30 || distance == 33)) {
            return YanSanctuary.OBSIDIAN.defaultBlockState();
        }
        if (height == 0) return old;
        return height == 3 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    /** Upgrade a previously generated stage. Leave every player-modified block in place. */
    static void upgradeChunk(ServerLevel level, ChunkPos chunk, int laneX) {
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.max(-EDGE, chunk.getMinBlockX() - laneX); x <= Math.min(EDGE, chunk.getMaxBlockX() - laneX); x++) {
            for (int z = Math.max(-EDGE, chunk.getMinBlockZ()); z <= Math.min(EDGE, chunk.getMaxBlockZ()); z++) {
                if (gauge(x, z) <= 21 || gauge(x, z) > EDGE) continue;
                for (int y = FLOOR; y <= FLOOR + 3; y++) {
                    var expected = block(x, y, z);
                    var previous = YanSanctuaryLayout.platform(x, y, z);
                    cursor.set(x + laneX, y, z);
                    if (expected != previous && level.getBlockState(cursor) == previous) {
                        level.setBlock(cursor, expected, 2 | 16);
                    }
                }
            }
        }
    }
}
