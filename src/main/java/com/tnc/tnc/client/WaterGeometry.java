package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.tnc.tnc.magic.water.WaterSpellRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;

/** Continuous geometry rather than a cloud of disconnected particles. */
final class WaterGeometry {
    private WaterGeometry() {}
    static void vertex(VertexConsumer out,Matrix4f pose,Vec3 p,float r,float g,float b,float a) {
        out.vertex(pose,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,a).endVertex();
    }
    static void quad(VertexConsumer out,Matrix4f pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha) {
        vertex(out,pose,a,r,g,blue,alpha);vertex(out,pose,b,r,g,blue,alpha);
        vertex(out,pose,c,r,g,blue,alpha);vertex(out,pose,d,r,g,blue,alpha);
    }
    static Vec3 radial(Vec3 right,Vec3 up,double angle,double radius) {
        return right.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
    }
    static void tube(VertexConsumer out,Matrix4f pose,Vec3 start,Vec3 end,double radius,float r,float g,float b,float a) {
        Vec3 axis=end.subtract(start).normalize(); if(axis.lengthSqr()<.5)return;
        Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
        int sides=radius<.1?4:24;
        for(int i=0;i<sides;i++) {
            Vec3 v=radial(right,up,i*Math.PI*2/sides,radius),w=radial(right,up,(i+1)*Math.PI*2/sides,radius);
            quad(out,pose,start.add(v),end.add(v),end.add(w),start.add(w),r,g,b,a);
        }
    }
    /** Filled aperture, encoded as degenerate quads for the shared QUADS / NO_CULL buffer. */
    static void disk(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,float r,float g,float b,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<64;i++) {
            Vec3 p=center.add(radial(right,up,i*Math.PI/32,radius));
            Vec3 q=center.add(radial(right,up,(i+1)*Math.PI/32,radius));
            quad(out,pose,center,p,q,center,r,g,b,alpha);
        }
    }
    static void ring(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,double phase,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<96;i++) {
            double a=phase+i*Math.PI/48,b=phase+(i+1)*Math.PI/48;
            quad(out,pose,center.add(radial(right,up,a,radius)),center.add(radial(right,up,b,radius)),
                    center.add(radial(right,up,b,radius-width)),center.add(radial(right,up,a,radius-width)),.015F,.32F,1,alpha);
        }
    }
    static void circle(VertexConsumer out,Matrix4f pose,Vec3 dir,double radius,double time,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        ring(out,pose,Vec3.ZERO,dir,radius,.055,0,alpha);
        ring(out,pose,Vec3.ZERO,dir,radius*.92,.035,0,alpha);
        ring(out,pose,dir.scale(.025),dir,radius*.65,.05,0,alpha);
        ring(out,pose,Vec3.ZERO,dir,radius*.25,.035,0,alpha);
        // Twelve moving angular glyphs, and a counter-rotating inner star.
        for(int i=0;i<12;i++) {
            double a=time*.009+i*Math.PI/6;
            Vec3 center=radial(right,up,a,radius*.79);
            Vec3 v=radial(right,up,a,radius*.055),w=radial(right,up,a+Math.PI/2,radius*.045);
            tube(out,pose,center.subtract(v),center.add(w),.025,.7F,.94F,1,alpha);
            tube(out,pose,center.add(w),center.add(v),.025,.7F,.94F,1,alpha);
            double star=-time*.012+i*Math.PI/6;
            tube(out,pose,radial(right,up,star,radius*.6),radial(right,up,star+Math.PI*2/3,radius*.6),.022,.25F,.7F,1,alpha*.65F);
        }
    }
    static void ringColor(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,
                          float r,float g,float b,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,c=(i+1)*Math.PI/32;
            quad(out,pose,center.add(radial(right,up,a,radius)),center.add(radial(right,up,c,radius)),
                    center.add(radial(right,up,c,radius-width)),center.add(radial(right,up,a,radius-width)),r,g,b,alpha);
        }
    }
    /** Layered indigo/violet water sigil. Geometry colors are independent of the cyan water body. */
    static void richCircle(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,double radius,double time,float alpha) {
        sigil(buffers,stack,center,dir,radius,time*.016,alpha*.96F);
        sigil(buffers,stack,center.add(dir.scale(.035)),dir,radius*.59,-time*.024,alpha*.84F);
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        int satellites=radius>=6?6:3;
        for(int i=0;i<satellites;i++) {
            double a=time*.006+i*Math.PI*2/satellites;
            sigil(buffers,stack,center.add(radial(right,up,a,radius*1.1)).add(dir.scale(.06)),dir,radius*.14,-time*.02,alpha*.94F);
        }
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        ringColor(out,pose,center,dir,radius*1.04,radius*.028,.005F,.10F,.48F,alpha*.9F);
        ringColor(out,pose,center.add(dir.scale(.03)),dir,radius*1.03,radius*.018,.34F,.18F,1,alpha*.95F);
        ringColor(out,pose,center.add(dir.scale(.07)),dir,radius*.73,radius*.024,.58F,.3F,1,alpha*.9F);
        for(int i=0;i<24;i++) {
            double a=-time*.018+i*Math.PI/12;
            Vec3 p=center.add(radial(right,up,a,radius*.84));
            Vec3 v=radial(right,up,a,radius*.045),w=radial(right,up,a+Math.PI/2,radius*.018);
            tube(out,pose,p.subtract(v),p.add(w),radius*.006,.6F,.22F,1,alpha*.96F);
            tube(out,pose,p.add(w),p.add(v),radius*.006,.6F,.22F,1,alpha*.96F);
        }
    }
    private static void sigil(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,double radius,double spin,float alpha) {
        var out=buffers.getBuffer(WaterRenderTypes.geometry());
        var pose=stack.last().pose();
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        ringColor(out,pose,center,dir,radius,radius*.013,.12F,.08F,.65F,alpha);
        ringColor(out,pose,center,dir,radius*.94,radius*.01,.48F,.22F,1,alpha);
        ringColor(out,pose,center,dir,radius*.61,radius*.015,.28F,.18F,.92F,alpha);
        ringColor(out,pose,center,dir,radius*.28,radius*.018,.7F,.4F,1,alpha);
        for(int i=0;i<12;i++) {
            double a=spin+i*Math.PI/6;
            Vec3 p=center.add(radial(right,up,a,radius*.6));
            Vec3 q=center.add(radial(right,up,a+Math.PI*2/3,radius*.6));
            tube(out,pose,p,q,radius*.004,.38F,.18F,.9F,alpha*.8F);
            Vec3 c=center.add(radial(right,up,a,radius*.79));
            Vec3 v=radial(right,up,a,radius*.065),w=radial(right,up,a+Math.PI/2,radius*.025);
            tube(out,pose,c.subtract(v),c.add(w),radius*.006,.62F,.3F,1,alpha);
            tube(out,pose,c.add(w),c.add(v),radius*.006,.62F,.3F,1,alpha);
            tube(out,pose,c.add(v),c.subtract(w),radius*.006,.62F,.3F,1,alpha);
            tube(out,pose,c.subtract(w),c.subtract(v),radius*.006,.62F,.3F,1,alpha);
        }
    }
    static void spiral(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 axis,double radius,double height,
                       double time,float red,float green,float blue,float alpha) {
        Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
        for(int i=0;i<48;i++) {
            double t=i/48.0,q=(i+1)/48.0,a=time+t*Math.PI*4,b=time+q*Math.PI*4;
            Vec3 p=center.add(axis.scale(height*t)).add(radial(right,up,a,radius));
            Vec3 r=center.add(axis.scale(height*q)).add(radial(right,up,b,radius));
            tube(out,pose,p,r,.045,red,green,blue,alpha);
        }
    }
}
