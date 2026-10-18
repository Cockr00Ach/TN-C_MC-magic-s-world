package com.tnc.tnc.mana.sight;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ManaFlowTest {
 @Test void rejectsUnboundedAndNonFiniteGeometry(){assertFalse(new ManaFlow(Vec3.ZERO,new Vec3(Double.NaN,0,0),1,0,0).valid());assertFalse(new ManaFlow(Vec3.ZERO,new Vec3(257,0,0),1,0,0).valid());assertFalse(new ManaFlow(Vec3.ZERO,Vec3.ZERO,65536,0,0).valid());assertFalse(new ManaFlow(Vec3.ZERO,Vec3.ZERO,0,0,0).valid());assertFalse(new ManaFlow(Vec3.ZERO,Vec3.ZERO,1,0,6).valid());}
 @Test void acceptsDirectedTransfersAndProduction(){for(int kind=0;kind<=5;kind++)assertTrue(new ManaFlow(new Vec3(10,180,30),new Vec3(11,181,30),128,0x80ffff,kind).valid());}
 @Test void packetRoundTripPreservesRealPathAndAmount(){var dimension=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","heroskand");var flow=new ManaFlow(new Vec3(1,180,3),new Vec3(2,181,3),32,0xffcaaa,3);var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{new ManaFlowPacket(dimension,java.util.List.of(flow)).encode(buffer);var decoded=ManaFlowPacket.decode(buffer);assertEquals(dimension,decoded.dimension());assertEquals(java.util.List.of(flow),decoded.flows());}finally{buffer.release();}}
}
