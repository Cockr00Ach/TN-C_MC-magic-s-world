package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloatingFootprintTest {
    private static CompoundTag template(int state,int y) {
        var n=new CompoundTag(); var palette=new ListTag();
        for (String name:new String[]{"minecraft:stone","minecraft:air","minecraft:structure_void"}) {
            var p=new CompoundTag(); p.putString("Name",name); palette.add(p);
        }
        n.put("palette",palette); var blocks=new ListTag(); var block=new CompoundTag();
        var pos=new ListTag(); pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(y));pos.add(IntTag.valueOf(0));
        block.put("pos",pos); block.putInt("state",state); blocks.add(block);n.put("blocks",blocks);return n;
    }
    @Test void raisedColumnUsesActualUndersideNotBoundingBox() {
        var f=new FloatingFootprint(4,4,100); f.include(template(0,11),new BlockPos(1,64,1));
        assertEquals(75,f.bottomAt(1,1));
        assertFalse(f.intersects(170,132,1,1));
        assertFalse(f.intersects(202,132,1,1));
        assertTrue(f.intersects(203,132,1,1));
    }
    @Test void emptyColumnsAndAirAreNotFloatingTerrain() {
        var f=new FloatingFootprint(4,4,100); f.include(template(1,0),BlockPos.ZERO);
        f.include(template(2,0),new BlockPos(1,0,1));
        assertFalse(f.occupied(0,0)); assertFalse(f.occupied(1,1));
        assertFalse(f.intersects(319,132,0,0)); assertFalse(f.occupied(-1,0));
    }
    @Test void lowestOfMultiplePiecesWinsRegardlessOfOrder() {
        var f=new FloatingFootprint(4,4,100);
        f.include(template(0,8),new BlockPos(1,64,1));
        f.include(template(0,8),new BlockPos(1,16,1));
        f.include(template(0,8),new BlockPos(1,48,1));
        assertEquals(24,f.bottomAt(1,1));
    }
}
