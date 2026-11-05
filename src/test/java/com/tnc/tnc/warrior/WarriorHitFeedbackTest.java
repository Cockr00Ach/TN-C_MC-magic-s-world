package com.tnc.tnc.warrior;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorHitFeedbackTest {
    @Test void recoilGrowsWithTargetSizeAndHeavyWeightButIsBounded() {
        double previous=0;
        for(double size:new double[]{.3,1,1.8,3,6,12,30,128}) {
            double strength=WarriorHitFeedback.strength(size,false);
            assertTrue(strength>=previous);previous=strength;
            assertTrue(WarriorHitFeedback.strength(size,true)>=strength);
            assertTrue(WarriorHitFeedback.strength(size,true)<=2.2);
            double duration=WarriorHitFeedback.duration(size);
            assertTrue(duration<=6.5);
            assertEquals(0,WarriorHitFeedback.envelope(0,duration));
            assertTrue(WarriorHitFeedback.envelope(.25,duration)>WarriorHitFeedback.envelope(duration*.8,duration));
            assertEquals(0,WarriorHitFeedback.envelope(duration,duration));
        }
        assertEquals(0,WarriorHitFeedback.strength(Double.NaN,true));
    }
    @Test void contactLiesJustOutsideTallWideAndSmallTargetsEvenWhenAttackerIsInside() {
        for(var box:new AABB[]{new AABB(-.3,0,-.3,.3,1.8,.3),new AABB(-5,0,-5,5,20,5),new AABB(-.2,0,-.2,.2,.4,.2)})
            for(var eye:new Vec3[]{new Vec3(0,1.6,4),new Vec3(3,10,0),box.getCenter()}) {
                var at=WarriorHitFeedback.contact(box,eye);
                assertFalse(box.contains(at));assertTrue(box.inflate(.036).contains(at));
            }
    }
    @Test void contactFragmentsHaveDepthAndVariationAndStaySmall() {
        for(int kind:new int[]{0,3})for(boolean metal:new boolean[]{false,true}) {
            var mesh=WarriorImpactShape.build(kind,.3f,0x88AAFF,42,metal);
            assertEquals(mesh,WarriorImpactShape.build(kind,.3f,0x88AAFF,42,metal));
            assertNotEquals(mesh,WarriorImpactShape.build(kind,.3f,0x88AAFF,43,metal));
            assertTrue(mesh.size()<=26);
            assertTrue(mesh.stream().anyMatch(q->q.a().z!=0));
            for(var q:mesh)for(var p:new Vec3[]{q.a(),q.b(),q.c(),q.d()})assertTrue(p.length()<1);
            assertTrue(WarriorImpactShape.build(kind,1,0,42,metal).isEmpty());
        }
    }
    @Test void extendedImpactPacketRoundTripsAndRejectsUnsafeFrames() {
        var dim=ResourceLocation.tryParse("tnc:skyhaven");
        var p=new WarriorImpactPacket(dim,new Vec3(1,2,3),3,0xABCDEF,42,123,12,new Vec3(1,0,0),789,true);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try{p.encode(b);assertEquals(p,WarriorImpactPacket.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        for(float size:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,129}) {
            var bad=new WarriorImpactPacket(dim,Vec3.ZERO,0,0,0,1,size,new Vec3(0,0,1),0,false);
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try{bad.encode(buf);assertThrows(io.netty.handler.codec.DecoderException.class,()->WarriorImpactPacket.decode(buf));}finally{buf.release();}
        }
    }
}
