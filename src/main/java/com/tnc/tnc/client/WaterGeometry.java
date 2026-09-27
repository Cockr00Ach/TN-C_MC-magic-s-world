package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.tnc.tnc.magic.water.WaterSpellRules;

/** Continuous geometry rather than a cloud of disconnected particles. */
final class WaterGeometry {
    private WaterGeometry() {}
    static void vertex(VertexConsumer out,Matrix4f pose,Vec3 p,float r,float g,float b,float a) {
        out.vertex(pose,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,a).endVertex();
    }
    static void quad(VertexConsumer out,Matrix4f pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha) {
        vertex(out,pose,a,r,g,blue,alpha);vertex(out,pose,b,r,g,blue,alpha);
        vertex(out,pose,c,r,g,blue,alpha);vertex(out,pose,d,r,g,blue,alpha);
        vertex(out,pose,d,r,g,blue,alpha);vertex(out,pose,c,r,g,blue,alpha);
        vertex(out,pose,b,r,g,blue,alpha);vertex(out,pose,a,r,g,blue,alpha);
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
    static void ring(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,double phase,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<96;i++) {
            double a=phase+i*Math.PI/48,b=phase+(i+1)*Math.PI/48;
            quad(out,pose,center.add(radial(right,up,a,radius)),center.add(radial(right,up,b,radius)),
                    center.add(radial(right,up,b,radius-width)),center.add(radial(right,up,a,radius-width)),.18F,.8F,1,alpha);
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
}
