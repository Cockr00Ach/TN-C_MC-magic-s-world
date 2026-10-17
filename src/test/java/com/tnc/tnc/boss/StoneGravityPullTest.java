package com.tnc.tnc.boss;

import com.tnc.tnc.boss.earth.StoneGravityPull;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoneGravityPullTest {
    @Test void predictionRecoversHighSpeedAfterAVanillaClampedPacket(){
        var direction=new Vec3(340,85,0);
        var packet=new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(1,new Vec3(12,5,0));
        var clamped=new Vec3(packet.getXa()/8000.0,packet.getYa()/8000.0,packet.getZa()/8000.0);
        assertTrue(clamped.x<=3.9);
        var predicted=StoneGravityPull.motion(clamped,direction,false,30);
        assertTrue(predicted.x>3.9&&predicted.y>0&&predicted.length()<=12.001);
    }
    @Test void theCoreSlowsVictimsAtItsCentreWithoutNaNsOrOvershoot(){
        var near=StoneGravityPull.motion(new Vec3(12,0,0),new Vec3(.1,.1,0),false,60);
        assertTrue(near.length()<=.16001);
        var centre=StoneGravityPull.motion(Vec3.ZERO,Vec3.ZERO,true,60);
        assertEquals(Vec3.ZERO,centre);
    }
}
