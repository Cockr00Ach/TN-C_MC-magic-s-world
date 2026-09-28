package com.tnc.tnc.magic.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Isolation exists only during one already-authorized bore removal, on its server thread. */
public final class WaterBoreEditGuard {
    public interface Installed {}
    private static final ThreadLocal<Edit> ACTIVE = new ThreadLocal<>();
    private static final class Edit {
        final Level level; final BlockPos pos; boolean entered;
        Edit(Level level, BlockPos pos) { this.level=level;this.pos=pos.immutable(); }
    }
    private WaterBoreEditGuard() {}
    public static boolean active() { return ACTIVE.get()!=null; }
    /** A callback cannot consume another edit, even to the originally authorized position. */
    public static boolean allowSetBlock(Level level, BlockPos pos) {
        Edit edit=ACTIVE.get();
        if(edit==null)return true;
        if(edit.entered||edit.level!=level||!edit.pos.equals(pos))return false;
        edit.entered=true;return true;
    }
    public static boolean remove(Level level, BlockPos pos) {
        // Missing injection must fail closed, not silently execute unsafe callbacks.
        if(!(level instanceof Installed)||active())return false;
        ACTIVE.set(new Edit(level,pos));
        try { return level.setBlock(pos,Blocks.AIR.defaultBlockState(),2|16|64); }
        finally { ACTIVE.remove(); }
    }
}
