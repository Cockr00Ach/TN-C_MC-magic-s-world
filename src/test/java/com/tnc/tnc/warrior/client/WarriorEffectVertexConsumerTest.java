package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorEffectVertexConsumerTest {
    @Test void colouredQuadProducesCompleteEntityVerticesWithoutLosingAlpha(){
        var builder=new BufferBuilder(1024);
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        var out=new WarriorEffectVertexConsumer(builder);
        for(int i=0;i<4;i++)out.vertex(i,2,3).color(40,80,160,96).endVertex();
        var rendered=builder.end();
        try{
            assertEquals(4,rendered.drawState().vertexCount());
            var bytes=rendered.vertexBuffer();
            for(int i=0;i<4;i++){
                int offset=i*DefaultVertexFormat.NEW_ENTITY.getVertexSize();
                assertEquals(40,Byte.toUnsignedInt(bytes.get(offset+12)));
                assertEquals(96,Byte.toUnsignedInt(bytes.get(offset+15)));
                assertEquals(.5f,bytes.getFloat(offset+16));
                assertEquals(.5f,bytes.getFloat(offset+20));
            }
        }finally{rendered.release();}
    }
}
