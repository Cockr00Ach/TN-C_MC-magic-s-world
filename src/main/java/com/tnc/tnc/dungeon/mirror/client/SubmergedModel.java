package com.tnc.tnc.dungeon.mirror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Cuts original player, armor and attachment quads at a single world-height plane during liquid return. */
final class SubmergedModel implements MultiBufferSource {
    private final MultiBufferSource buffers;
    private final Map<RenderType,VertexConsumer> writers=new IdentityHashMap<>();
    private final float px,py,pz,pw;
    SubmergedModel(MultiBufferSource buffers,PoseStack pose,float height,boolean keepBelow) {
        this.buffers=buffers;
        Matrix4f inverse=new Matrix4f(pose.last().pose()).invert();
        float s=keepBelow?1:-1;
        px=inverse.m01()*s;py=inverse.m11()*s;pz=inverse.m21()*s;pw=(inverse.m31()-height)*s;
    }
    @Override public VertexConsumer getBuffer(RenderType type) {
        // Vanilla's immediate buffer may flush when another type is requested: always request a fresh delegate.
        VertexConsumer out=buffers.getBuffer(type);
        if(type.mode()!=VertexFormat.Mode.QUADS) return out;
        Cutter c=(Cutter)writers.computeIfAbsent(type,k->new Cutter());
        c.out=out;return c;
    }
    private final class Cutter implements VertexConsumer {
        private final Point[] vertices={new Point(),new Point(),new Point(),new Point()};
        private final Point[] scratch={new Point(),new Point(),new Point(),new Point(),new Point(),new Point()};
        private int count;
        private VertexConsumer out;
        @Override public VertexConsumer vertex(double x,double y,double z){var p=vertices[count];p.x=(float)x;p.y=(float)y;p.z=(float)z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){var p=vertices[count];p.r=r;p.g=g;p.b=b;p.a=a;return this;}
        @Override public VertexConsumer uv(float u,float v){var p=vertices[count];p.u=u;p.v=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){var p=vertices[count];p.ou=u;p.ov=v;return this;}
        @Override public VertexConsumer uv2(int u,int v){var p=vertices[count];p.lu=u;p.lv=v;return this;}
        @Override public VertexConsumer normal(float x,float y,float z){var p=vertices[count];p.nx=x;p.ny=y;p.nz=z;return this;}
        @Override public void defaultColor(int r,int g,int b,int a){for(var p:vertices){p.r=r;p.g=g;p.b=b;p.a=a;}}
        @Override public void unsetDefaultColor() {}
        private float distance(Point p){return p.x*px+p.y*py+p.z*pz+pw;}
        @Override public void endVertex() {
            if(++count<4) return;
            count=0;int n=0;Point a=vertices[3];float da=distance(a);
            for(Point b:vertices){
                float db=distance(b);
                if((da<=0)!=(db<=0)) scratch[n++].set(a,b,da/(da-db));
                if(db<=0) scratch[n++].set(b,b,0);
                a=b;da=db;
            }
            if(n==4) {
                for(int i=0;i<4;i++) scratch[i].emit(out);
            } else for(int i=1;i+1<n;i++){
                scratch[0].emit(out);scratch[i].emit(out);scratch[i+1].emit(out);scratch[i+1].emit(out);
            }
        }
    }
    private static final class Point {
        float x,y,z,r=255,g=255,b=255,a=255,u,v,nx,ny,nz;
        int ou,ov,lu,lv;
        private static float mix(float a,float b,float t){return a+(b-a)*t;}
        void set(Point p,Point q,float t) {
            x=mix(p.x,q.x,t);y=mix(p.y,q.y,t);z=mix(p.z,q.z,t);
            r=mix(p.r,q.r,t);g=mix(p.g,q.g,t);b=mix(p.b,q.b,t);a=mix(p.a,q.a,t);
            u=mix(p.u,q.u,t);v=mix(p.v,q.v,t);
            nx=mix(p.nx,q.nx,t);ny=mix(p.ny,q.ny,t);nz=mix(p.nz,q.nz,t);
            ou=p.ou;ov=p.ov;lu=(int)mix(p.lu,q.lu,t);lv=(int)mix(p.lv,q.lv,t);
        }
        void emit(VertexConsumer vtx) {
            vtx.vertex(x,y,z).color((int)r,(int)g,(int)b,(int)a).uv(u,v).overlayCoords(ou,ov)
                .uv2(lu,lv).normal(nx,ny,nz).endVertex();
        }
    }
}
