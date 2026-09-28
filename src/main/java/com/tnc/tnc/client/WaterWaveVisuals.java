package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Curved water volume and white foam lip, inspired by the visual layering of Alex's Caves waves. */
final class WaterWaveVisuals {
    static void crescent(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double r,double time,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<40;i++) {
            double a=-Math.PI*.55+i*Math.PI*1.1/40,b=-Math.PI*.55+(i+1)*Math.PI*1.1/40;
            Vec3 p=center.add(WaterGeometry.radial(right,up,a,r)),q=center.add(WaterGeometry.radial(right,up,b,r));
            double innerA=r*(.78+.18*Math.pow(Math.abs(a)/(Math.PI*.55),2)),innerB=r*(.78+.18*Math.pow(Math.abs(b)/(Math.PI*.55),2));
            Vec3 u=center.add(WaterGeometry.radial(right,up,b,innerB)),v=center.add(WaterGeometry.radial(right,up,a,innerA));
            Vec3 depth=dir.scale(.3);
            WaterGeometry.quad(out,pose,p,q,u,v,.05F,.5F,.95F,alpha*.6F);
            WaterGeometry.quad(out,pose,p.subtract(depth),v.subtract(depth),u.subtract(depth),q.subtract(depth),.12F,.75F,1,alpha*.45F);
            WaterGeometry.quad(out,pose,p,q,q.subtract(depth),p.subtract(depth),.6F,.95F,1,alpha*.85F);
            WaterGeometry.tube(out,pose,p,q,.055,.85F,.98F,1,alpha);
            if(i%4==0) {
                Vec3 foam=p.add(dir.scale(.08+Math.sin(time*.3+i)*.08));
                WaterGeometry.tube(out,pose,foam,foam.add(up.scale(.16)),.045,1,1,1,alpha*.65F);
            }
        }
    }
    static void rollingWave(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=new Vec3(0,1,0);
        for(int i=0;i<40;i++) {
            double a=-Math.PI*.48+i*Math.PI*.96/40,b=-Math.PI*.48+(i+1)*Math.PI*.96/40;
            for(int j=0;j<6;j++) {
                Vec3 p=profile(center,dir,right,up,a,j/6.0,radius,height,time),q=profile(center,dir,right,up,b,j/6.0,radius,height,time);
                Vec3 v=profile(center,dir,right,up,b,(j+1)/6.0,radius,height,time),u=profile(center,dir,right,up,a,(j+1)/6.0,radius,height,time);
                WaterGeometry.quad(out,pose,p,q,v,u,.05F,.4F+j*.07F,.93F,alpha*(.2F+j*.05F));
                if(j==5)WaterGeometry.tube(out,pose,u,v,.07,.88F,.98F,1,alpha);
            }
        }
    }
    private static Vec3 profile(Vec3 center,Vec3 dir,Vec3 right,Vec3 up,double a,double t,double r,double h,double time) {
        double crest=h*Math.cos(a)*(1+.04*Math.sin(a*9+time*.15));
        return center.add(right.scale(Math.sin(a)*r)).add(up.scale(.05+crest*Math.sin(t*Math.PI*.55)))
                .add(dir.scale(Math.cos(a)*1.6+Math.sin(t*Math.PI*1.5)*.8));
    }
    static void circularWave(VertexConsumer out,Matrix4f pose,double radius,double height,double age,float alpha) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
            for(int j=0;j<6;j++) {
                Vec3 p=circularProfile(a,j/6.0,radius,height,age),q=circularProfile(b,j/6.0,radius,height,age);
                Vec3 v=circularProfile(b,(j+1)/6.0,radius,height,age),u=circularProfile(a,(j+1)/6.0,radius,height,age);
                WaterGeometry.quad(out,pose,p,q,v,u,.04F,.35F+j*.07F,.95F,alpha*(.25F+j*.055F));
                if(j==5)WaterGeometry.tube(out,pose,u,v,.09,.9F,.99F,1,alpha);
            }
        }
    }
    private static Vec3 circularProfile(double a,double t,double radius,double height,double age) {
        double r=Math.max(.1,radius+Math.sin(t*Math.PI*1.5)*.9);
        return new Vec3(Math.cos(a)*r,.1+height*Math.sin(t*Math.PI*.55)*(.9+.1*Math.sin(a*7+age*.16)),Math.sin(a)*r);
    }
}
