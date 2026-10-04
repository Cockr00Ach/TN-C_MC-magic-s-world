package com.tnc.tnc.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * The 3×3×3 forge is anchored at its FRONT middle core, not its hollow centre.
 * Grid rows run back to front, as printed in the in-game manual.
 */
public final class ForgeStructure {
    public static final int OK = 0;
    public static final int WRONG_BLOCK = 1;
    public static final int UNLOADED = 2;
    public static final int FILLED_CHAMBER = 3;
    public static final int WRONG_DIRECTION = 4;
    public static final int SHARED_PART = 5;
    public static final int CELL_COUNT = 27;

    public enum Part { BRICK, INPUT, OUTPUT, FRAME, CORE, HEART, INJECTOR, EXHAUST, AIR }

    public record Scan(int[] cells, int firstBad) {
        public boolean formed() { return firstBad < 0; }
        public int code(int index) { return cells[index]; }
    }

    private ForgeStructure() {}

    public static int index(int layer, int row, int column) { return layer * 9 + row * 3 + column; }

    public static Part expected(int index) {
        int layer = index / 9;
        int row = index % 9 / 3;
        int column = index % 3;
        if (layer == 0) return Part.BRICK;
        if (layer == 2) return row == 1 && column == 1 ? Part.EXHAUST : Part.BRICK;
        if (row == 1 && column == 1) return Part.HEART;
        if (row == 2 && column == 1) return Part.CORE;
        return Part.BRICK;
    }

    public static String expectedName(Part part) {
        return switch (part) {
            case BRICK -> "耐火炉砖";
            case INPUT -> "侧壁投料砖";
            case OUTPUT -> "底层出料砖";
            case FRAME -> "刻铜炉框";
            case CORE -> "炼金炉口";
            case HEART -> "魔力炉核";
            case INJECTOR -> "注能口（朝后）";
            case EXHAUST -> "排烟顶";
            case AIR -> "空炉膛";
        };
    }

    public static String cellName(int index) {
        String layer = new String[]{"底层", "中层", "顶层"}[index / 9];
        String row = new String[]{"后", "中", "前"}[index % 9 / 3];
        String col = new String[]{"左", "中", "右"}[index % 3];
        return layer + row + col;
    }

    /** The front direction points OUT of the door. Right is the viewer's right. */
    public static BlockPos at(BlockPos core, Direction front, int layer, int row, int column) {
        BlockPos chamber = core.relative(front.getOpposite());
        Direction right = front.getCounterClockWise();
        return chamber.relative(right, column - 1).relative(front, row - 1).above(layer - 1);
    }

    public static BlockPos at(BlockPos core, Direction front, int index) {
        return at(core, front, index / 9, index % 9 / 3, index % 3);
    }

    public static Scan scan(Level level, BlockPos core, Direction front) {
        int[] cells = new int[CELL_COUNT];
        int firstBad = -1;
        Set<BlockPos> requiredBlocks = new HashSet<>();
        for (int i = 0; i < CELL_COUNT; i++) {
            Part expected = expected(i);
            BlockPos at = at(core, front, i);
            if (expected != Part.AIR) requiredBlocks.add(at);
            int code;
            if (!level.hasChunkAt(at)) code = UNLOADED;
            else {
                BlockState state = level.getBlockState(at);
                if (expected == Part.AIR) code = state.isAir() ? OK : FILLED_CHAMBER;
                else if (!state.is(blockFor(expected))) code = WRONG_BLOCK;
                else if (expected == Part.INJECTOR
                        && state.hasProperty(ForgeInjectorBlock.FACING)
                        && state.getValue(ForgeInjectorBlock.FACING) != front.getOpposite()) code = WRONG_DIRECTION;
                else if (expected == Part.CORE
                        && state.hasProperty(MagicForgeBlock.FACING)
                        && state.getValue(MagicForgeBlock.FACING) != front) code = WRONG_DIRECTION;
                else code = OK;
            }
            cells[i] = code;
            if (firstBad < 0 && code != OK) firstBad = i;
        }
        // An unloaded edge is a pause, not an accusation that the player's masonry is wrong.
        for (int i = 0; i < CELL_COUNT; i++) if (cells[i] == UNLOADED) {
            firstBad = i;
            break;
        }
        // A second core may not borrow this structure's costly real parts.
        if (firstBad < 0) {
            BlockPos chamber = core.relative(front.getOpposite());
            for (BlockPos probe : BlockPos.betweenClosed(chamber.offset(-3, -2, -3), chamber.offset(3, 2, 3))) {
                if (probe.equals(core) || !level.hasChunkAt(probe)) continue;
                BlockState other = level.getBlockState(probe);
                if (!other.is(MagicForgeContent.FORGE) || !other.hasProperty(MagicForgeBlock.FACING)) continue;
                Direction otherFront = other.getValue(MagicForgeBlock.FACING);
                if (!basicFormed(level, probe, otherFront)) continue;
                for (int j = 0; j < CELL_COUNT; j++) {
                    if (expected(j) == Part.AIR || !requiredBlocks.contains(at(probe, otherFront, j))) continue;
                    for (int i = 0; i < CELL_COUNT; i++) if (at(core, front, i).equals(at(probe, otherFront, j))) {
                        cells[i] = SHARED_PART;
                        if (firstBad < 0) firstBad = i;
                    }
                }
            }
        }
        return new Scan(cells, firstBad);
    }

    /** Checks the other core without recursively asking whether it shares this forge. */
    /** Cheap complete-cell check used before every cached port operation. */
    public static boolean basicFormed(Level level, BlockPos core, Direction front) {
        for (int i = 0; i < CELL_COUNT; i++) {
            BlockPos pos = at(core, front, i);
            if (!level.hasChunkAt(pos)) return false;
            Part part = expected(i);
            BlockState state = level.getBlockState(pos);
            if (part == Part.AIR) {
                if (!state.isAir()) return false;
            } else if (!state.is(blockFor(part))) return false;
            if (part == Part.INJECTOR && state.getValue(ForgeInjectorBlock.FACING) != front.getOpposite())
                return false;
            if (part == Part.CORE && state.getValue(MagicForgeBlock.FACING) != front) return false;
        }
        return true;
    }

    private static Block blockFor(Part part) {
        return switch (part) {
            case BRICK -> MagicForgeContent.FIREBRICK;
            case INPUT -> MagicForgeContent.INPUT_PORT;
            case OUTPUT -> MagicForgeContent.OUTPUT_PORT;
            case FRAME -> MagicForgeContent.COPPER_FRAME;
            case CORE -> MagicForgeContent.FORGE;
            case HEART -> MagicForgeContent.HEART;
            case INJECTOR -> MagicForgeContent.INJECTOR;
            case EXHAUST -> MagicForgeContent.EXHAUST;
            case AIR -> throw new IllegalArgumentException("air has no block item");
        };
    }
}
