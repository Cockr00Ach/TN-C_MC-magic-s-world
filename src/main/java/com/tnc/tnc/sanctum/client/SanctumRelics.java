package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Low-poly chipped blades, broken shell plates and a widening black seam use the regular entity material. */
final class SanctumRelics {
    private static final RenderType STONE=RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/white_concrete.png"));
    private static final int LIGHT=LightTexture.pack(15,7);
    private SanctumRelics(){}
    private record Point(float x,float y,float z){}
    private static void triangle(VertexConsumer out,Matrix4f m,Point a,Point b,Point c,int color){
        var ab=new Vector3f(b.x-a.x,b.y-a.y,b.z-a.z);
        var ac=new Vector3f(c.x-a.x,c.y-a.y,c.z-a.z);
        var normal=ab.cross(ac);
        if(normal.lengthSquared()<1e-8)return;
        m.transformDirection(normal).normalize();
        vertex(out,m,a,.16F,.16F,color,normal);vertex(out,m,b,.82F,.22F,color,normal);
        vertex(out,m,c,.61F,.86F,color,normal);vertex(out,m,c,.61F,.86F,color,normal);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Point p,float u,float v,int c,Vector3f normal){
        out.vertex(m,p.x,p.y,p.z).color(c>>16&255,c>>8&255,c&255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LIGHT).normal(normal.x,normal.y,normal.z).endVertex();
    }
    private static final float[][] LEFT_FINGERS={{1.1f,-.1f,4.1f, 2.8f,-5.1f,8.65f,1.4f,-4.4f,10.0f},{1.8f,-.1f,4.1f,4.3f,-6.65f,8.75f,3.7f,-6.1f,9.9f},{2.7f,-.1f,4.1f,5.8f,-6.85f,8.9f,5.8f,-6.4f,9.85f}};
    private static final float[][] RIGHT_FINGERS={{-2.2f,-2.3f,.7f,-4.3f,-7.1f,3.0f,-5,-7.3f,5.5f},{-1.3f,-2.1f,.5f,-3,-9.5f,3.9f,-3.6f,-9.1f,5.5f},{-.4f,-2.5f,.9f,-1.2f,-9.7f,3.9f,-2.2f,-9.6f,5.5f}};
    static void embers(MultiBufferSource buffers,Matrix4f m,String bone){
        if(bone.equals("demon_orbits")){
            var out=buffers.getBuffer(SanctumProjectedBlade.EDGE);
            for(int side=-1;side<=1;side+=2){
                var a=new Point(side*1.58F,-.08F,1.54F);var b=new Point(side*3.53F,.45F,1.38F);
                var c=new Point(side*2.5F,.47F,1.65F);var d=new Point(side*2.48F,-.47F,1.7F);
                triangle(out,m,a,b,c,0xf19c43);triangle(out,m,a,d,b,0xc94430);
            }
        }
        boolean left=bone.equals("hand_-1");
        if(!left&&!bone.equals("hand_1"))return;
        var out=buffers.getBuffer(SanctumProjectedBlade.EDGE);
        for(var finger:left?LEFT_FINGERS:RIGHT_FINGERS){
            var a=new Point(finger[0],finger[1],finger[2]);
            var b=new Point(finger[3]-.18F,finger[4],finger[5]);
            var c=new Point(finger[3]+.19F,finger[4]+.05F,finger[5]+.1F);
            var d=new Point(finger[6],finger[7],finger[8]);
            triangle(out,m,a,b,c,0xb56c58);triangle(out,m,b,d,c,0xde9a78);
        }
    }
    static void rupture(MultiBufferSource buffers,Matrix4f base,float strain,float split,float gap){
        var out=buffers.getBuffer(STONE);
        float y = SanctumRupture.CENTER_Y, h = SanctumRupture.HALF_HEIGHT;
        var center = new Point(0, y, 1.84F);
        var inside = new Point[]{new Point(-gap*.54F,y-h,1.84F),new Point(gap*.54F,y-h,1.84F),
                new Point(gap,y,1.84F),new Point(gap*.54F,y+h,1.84F),
                new Point(-gap*.54F,y+h,1.84F),new Point(-gap,y,1.84F)};
        for (int i = 0; i < inside.length; i++)
            triangle(out, base, center, inside[i], inside[(i+1)%inside.length], 0x200A11);
        if (gap < 1.4F) {
            var bottom=new Point(.12F,y-h,5.46F);
            var top=new Point(-.17F,y+h,5.15F);
            var left=new Point(-gap,y,5.52F+strain*.08F);
            var right=new Point(gap,y+.25F,5.52F+strain*.08F);
            triangle(out,base,bottom,right,left,0x280C16);
            triangle(out,base,left,right,top,0x170A10);
        }
        if(split<=0)return;
        for(int i=0;i<14;i++){
            int side=i%2==0?-1:1;float r=.52F+(i%4)*.28F;
            float dx=side*(.75F+(5+i%5)*split);
            float dy=25.1F+(i%7)*1.55F+(2+i%4)*split-7*split*split;
            var piece=new Matrix4f(base).translate(dx,dy,5+(4+i%6)*split)
                    .rotateZ(side*split*(.9F+i*.19F)).rotateY(split*(1+i%4));
            var a=new Point(-r,-r*.65F,0);var b=new Point(r,-r*.3F,0);
            var c=new Point(r*.24F,r*1.5F,0);var d=new Point(r*.08F,0,r*.64F);
            triangle(out,piece,a,b,d,0x4A3132);triangle(out,piece,b,c,d,0xD4C4A9);triangle(out,piece,c,a,d,0xA38B78);
        }
    }
}
