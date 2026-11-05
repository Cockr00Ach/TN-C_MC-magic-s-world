package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorHeavyGrowth;
import com.tnc.tnc.warrior.client.WarriorPhantomScales;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorGrowthSyncTest {
    @Test void absolutePointsRoundTripIndependentlyOfTheOldTalentNormalisedScale(){
        for(int points:new int[]{0,200,3000,5000,10000}){
            var out=new FriendlyByteBuf(Unpooled.buffer());
            try{
                var packet=new WarriorPhantomPacket(UUID.randomUUID(),1.9f,points);
                packet.encode(out);assertEquals(packet,WarriorPhantomPacket.decode(out));assertEquals(0,out.readableBytes());
            }finally{out.release();}
        }
    }
    @Test void outOfRangePointsAreRejected(){
        for(int points:new int[]{-1,10001,Integer.MAX_VALUE}){
            var out=new FriendlyByteBuf(Unpooled.buffer());
            try{
                out.writeUUID(UUID.randomUUID());out.writeFloat(1.9f);out.writeVarInt(points);
                assertThrows(DecoderException.class,()->WarriorPhantomPacket.decode(out));
            }finally{out.release();}
        }
    }
    @Test void observersUseEachPlayersOwnPointsAndClearThemOnDisconnect(){
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        try{
            WarriorPhantomScales.accept(first,1.9f,200);
            WarriorPhantomScales.accept(second,1.9f,10000);
            assertEquals(WarriorHeavyGrowth.peak(200),WarriorHeavyGrowth.peak(WarriorPhantomScales.points(first)));
            assertEquals(10,WarriorHeavyGrowth.peak(WarriorPhantomScales.points(second)));
            WarriorPhantomScales.logout(null);assertEquals(0,WarriorPhantomScales.points(second));
        }finally{WarriorPhantomScales.logout(null);}
    }
}
