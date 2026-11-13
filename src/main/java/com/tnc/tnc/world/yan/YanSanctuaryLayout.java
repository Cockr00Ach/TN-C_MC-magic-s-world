package com.tnc.tnc.world.yan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Coordinates for the flat sanctuary. The approach is on the south side. */
final class YanSanctuaryLayout {
    static final int FLOOR = 64;
    static final int ALTAR_HEIGHT = FLOOR + 3;
    static final BlockPos ARRIVAL = new BlockPos(0, FLOOR + 1, 58);
    static final BlockPos ALTAR = new BlockPos(0, ALTAR_HEIGHT + 1, -9);
    static final BlockPos BOSS = new BlockPos(0, ALTAR_HEIGHT + 1, -17);
    static final BlockPos DRAGON = new BlockPos(-149, FLOOR + 1, -374);
    static final int STRUCTURE_TOP = FLOOR + 18;

    private YanSanctuaryLayout() {}

    static BlockState platform(int x, int y, int z) {
        int distance = octagon(x, z);
        if (y == FLOOR) {
            if (distance <= 37 && (distance == 37 || distance == 32 || distance == 29)) return rune();
            if (z > 28 && z <= 69 && Math.abs(x) <= 7) {
                return Math.abs(x) == 7 || Math.abs(x) == 3 ? rune() : polished();
            }
            if (z >= -88 && z < -33 && Math.abs(x) <= 15) {
                return Math.abs(x) == 15 || (z % 8 == 0 && Math.abs(x) <= 12) ? rune() : polished();
            }
            if (distance > 40 && (Math.floorMod(x, 64) == 32 || Math.floorMod(z, 64) == 32)) {
                return Math.floorMod(x + z, 7) < 4 ? rune() : polished();
            }
            return distance < 38 ? polished() : Blocks.OBSIDIAN.defaultBlockState();
        }

        if (y < FLOOR || y > STRUCTURE_TOP) return Blocks.AIR.defaultBlockState();
        if (x == ALTAR.getX() && y == ALTAR.getY() && z == ALTAR.getZ()) return YanSanctuary.ALTAR.defaultBlockState();
        int height = y - FLOOR;
        if (distance <= 27 && height <= 3) {
            int limit = 28 - height * 2;
            if (distance <= limit) {
                if (distance == limit || (height == 3 && (distance == 18 || distance == 13)
                        && Math.min(Math.abs(x), Math.abs(z)) >= 2)) return rune();
                return height == 3 ? polished() : Blocks.DEEPSLATE_TILES.defaultBlockState();
            }
        }
        int pylon = pylon(x, z);
        if (pylon >= 0) return brazier(x, z, height, pylon);
        return Blocks.AIR.defaultBlockState();
    }

    private static int octagon(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        return Math.max(Math.max(ax, az), (ax + az + 1) * 3 / 4);
    }

    private static int pylon(int x, int z) {
        int az = Math.abs(x);
        if (Math.abs(az - 26) <= 2 && Math.abs(z + 30) <= 2) return 14;
        if (Math.abs(az - 16) <= 2 && Math.abs(z + 43) <= 2) return 10;
        if (Math.abs(az - 24) <= 2 && Math.abs(z - 19) <= 2) return 6;
        if (Math.abs(az - 10) <= 2 && Math.abs(z - 39) <= 2) return 4;
        if (Math.abs(az - 8) <= 2 && Math.abs(z - 54) <= 2) return 3;
        return -1;
    }

    private static BlockState brazier(int x, int z, int height, int shaft) {
        int centerX = switch (shaft) { case 14 -> 26; case 10 -> 16; case 6 -> 24; case 4 -> 10; default -> 8; };
        int centerZ = switch (shaft) { case 14 -> -30; case 10 -> -43; case 6 -> 19; case 4 -> 39; default -> 54; };
        int dx = Math.abs(Math.abs(x) - centerX), dz = Math.abs(z - centerZ);
        int radius = Math.max(dx, dz);
        if (height == 1 && radius <= 2 && dx + dz <= 3) return polished();
        if (height == 2 && radius <= 1) return rune();
        if (height > 2 && height < shaft + 1 && radius <= 1) {
            return (dx == 0 || dz == 0) && (height % 4 == 0 || radius == 0) ? rune() : polished();
        }
        if (height == shaft + 1 && radius <= 2 && dx + dz <= 3) return rune();
        if (height == shaft + 2 && radius <= 2) {
            if (radius == 2 && dx + dz == 3) return Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
            if (radius <= (shaft >= 10 ? 1 : 0)) return YanSanctuary.FLAME.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState rune() {
        return YanSanctuary.OBSIDIAN.defaultBlockState();
    }

    private static BlockState polished() {
        return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }
}
