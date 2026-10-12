package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import java.util.Arrays;
import java.util.function.BiConsumer;

/** Records emission until the water body is complete: custom RenderTypes share the fallback buffer. */
final class WaterVisualBuffers {
    private static final Emission EMISSION=new Emission();
    static void draw(MultiBufferSource buffers,BiConsumer<VertexConsumer,VertexConsumer> draw) {
        draw(buffers.getBuffer(WaterRenderTypes.geometry()),() -> buffers.getBuffer(WaterRenderTypes.glow()),draw);
    }
    static void draw(VertexConsumer body,java.util.function.Supplier<VertexConsumer> glow,
                     BiConsumer<VertexConsumer,VertexConsumer> draw) {
        EMISSION.count=0;
        draw.accept(body,EMISSION);
        if(EMISSION.count>0)EMISSION.flush(glow.get());
    }
    private static final class Emission implements VertexConsumer {
        private float[] positions=new float[4096*3];
        private int[] colors=new int[4096*4];
        private int count;
        public VertexConsumer vertex(double x,double y,double z) {
            if(count*3>=positions.length) {
                positions=Arrays.copyOf(positions,positions.length*2);
                colors=Arrays.copyOf(colors,colors.length*2);
            }
            positions[count*3]=(float)x;positions[count*3+1]=(float)y;positions[count*3+2]=(float)z;
            return this;
        }
        public VertexConsumer color(int r,int g,int b,int a) {
            colors[count*4]=r;colors[count*4+1]=g;colors[count*4+2]=b;colors[count*4+3]=a;return this;
        }
        public void endVertex(){count++;}
        public VertexConsumer uv(float u,float v){return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
        private void flush(VertexConsumer out) {
            for(int i=0;i<count;i++)out.vertex(positions[i*3],positions[i*3+1],positions[i*3+2])
                    .color(colors[i*4],colors[i*4+1],colors[i*4+2],colors[i*4+3]).endVertex();
        }
    }
}
