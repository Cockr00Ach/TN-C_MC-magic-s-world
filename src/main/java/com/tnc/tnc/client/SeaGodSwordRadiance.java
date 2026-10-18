package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Narrow blue-gold light sheaths, independent of the blade's translucent body. */
final class SeaGodSwordRadiance {
    static void draw(VertexConsumer out,Matrix4f pose,float alpha) {
        double[] ys={5,12,43.5,48},xs={1.8,4,4,3.2};
        for(int side:new int[]{-1,1})for(int face:new int[]{-1,1})for(int j=0;j<ys.length-1;j++) {
            Vec3 a=new Vec3(side*xs[j]/48,ys[j]/48,face*2.05/48);
            Vec3 b=new Vec3(side*xs[j+1]/48,ys[j+1]/48,face*2.05/48);
            for(int layer=0;layer<3;layer++) {
                double width=new double[]{.85,.38,.12}[layer]/48;
                float opacity=new float[]{.10F,.25F,.8F}[layer]*alpha;
                WaterGeometry.stroke(out,pose,new Vec3(0,0,1),a,b,width,
                        layer==2?.95F:.12F,layer==2?.73F:.62F,layer==2?.24F:1,opacity);
            }
        }
    }
}
