package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Arrays;

/** Actual lowest written voxel in each column; empty bounding-box space is not island terrain. */
final class FloatingFootprint {
    private final int width, depth, height;
    private final int[] bottom;
    private final int[] top;

    FloatingFootprint(int width, int depth, int height) {
        this.width=width; this.depth=depth; this.height=height;
        bottom=new int[width*depth]; Arrays.fill(bottom,Integer.MAX_VALUE);
        top=new int[width*depth]; Arrays.fill(top,Integer.MIN_VALUE);
    }

    void include(CompoundTag template, BlockPos offset) {
        var palette=template.getList("palette",Tag.TAG_COMPOUND);
        boolean[] written=new boolean[palette.size()];
        for (int i=0;i<written.length;i++) {
            String name=palette.getCompound(i).getString("Name");
            written[i]=!name.equals("minecraft:air") && !name.equals("minecraft:cave_air")
                    && !name.equals("minecraft:void_air") && !name.equals("minecraft:structure_void");
        }
        for (Tag raw:template.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)raw; int state=b.getInt("state");
            if (state<0 || state>=written.length) throw new IllegalStateException("Invalid floating template palette");
            if (!written[state]) continue;
            var p=b.getList("pos",Tag.TAG_INT);
            int x=offset.getX()+p.getInt(0),y=offset.getY()+p.getInt(1),z=offset.getZ()+p.getInt(2);
            if (x<0 || z<0 || y<0 || x>=width || z>=depth || y>=height)
                throw new IllegalStateException("Floating template writes outside manifest bounds");
            bottom[z*width+x]=Math.min(bottom[z*width+x],y);
            top[z*width+x]=Math.max(top[z*width+x],y);
        }
    }

    int bottomAt(int x,int z) {
        return x<0 || z<0 || x>=width || z>=depth ? Integer.MAX_VALUE : bottom[z*width+x];
    }

    boolean occupied(int x,int z) { return bottomAt(x,z)!=Integer.MAX_VALUE; }
    int topAt(int x,int z) { return top[z*width+x]; }

    boolean intersects(int surface,int originY,int x,int z) {
        return occupied(x,z) && surface>=originY+bottomAt(x,z)-4;
    }
}
