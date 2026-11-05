package com.tnc.tnc.warrior;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorImpactPacketTest {
    @Test void allImpactKindsRoundTripAndInvalidGeometryIsRejected(){
        for(int kind=0;kind<5;kind++){
            var p=new WarriorImpactPacket(ResourceLocation.tryParse("tnc:skyhaven"),new Vec3(1,2,3),kind,0xFFF8DF,50);
            var b=new FriendlyByteBuf(Unpooled.buffer());try{p.encode(b);assertEquals(p,WarriorImpactPacket.decode(b));}finally{b.release();}
        }
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            new WarriorImpactPacket(ResourceLocation.tryParse("minecraft:overworld"),new Vec3(Double.NaN,0,0),0,0,0).encode(b);
            assertThrows(io.netty.handler.codec.DecoderException.class,()->WarriorImpactPacket.decode(b));
        }finally{b.release();}
    }
}
