package com.tnc.tnc.casino.machine;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteControlsTest {
    @Test void everyFacingMapsTheVisibleButtonsToTheSameBet() {
        BlockPos anchor=new BlockPos(10,20,30);
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            for(int button=0;button<3;button++) {
                double x=2.5,z=-0.75+button*0.85;
                Vec3 world=switch(facing) {
                    case EAST -> new Vec3(14-z,21.025,32+x);
                    case SOUTH -> new Vec3(14-x,21.025,32-z);
                    case WEST -> new Vec3(14+z,21.025,32-x);
                    default -> new Vec3(14+x,21.025,32+z);
                };
                Vec3 local=RouletteControls.local(world,anchor,facing);
                assertEquals(button,RouletteControls.button(local.x,local.z),facing+" button "+button);
            }
        }
    }
    @Test void gapsAndWheelAreNotColourButtons() {
        assertEquals(-1,RouletteControls.button(1.7,2));
        assertEquals(-1,RouletteControls.button(3.25,1.7));
        assertArrayEquals(new int[]{0,6,1},RouletteControls.BETS);
    }
}
