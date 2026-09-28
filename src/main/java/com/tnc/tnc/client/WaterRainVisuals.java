package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.TNWaterFieldEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Five silhouettes, not five copies of the same rainfall. Local geometry only, no weather/block edits. */
final class WaterRainVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    static void render(TNWaterFieldEntity e,double age,float fade,PoseStack stack,MultiBufferSource buffers) {
        int tier=e.tier();double r=e.radius(),h=e.height();
        if(tier>=4)WaterGeometry.richCircle(buffers,stack,new Vec3(0,tier==5?h:.08,0),UP,r*(tier==5?1.25:1),age,fade);
        if(tier==5) {
            WaterGeometry.richCircle(buffers,stack,new Vec3(0,h+4,0),UP,r*.82,-age,fade*.95F);
            WaterGeometry.richCircle(buffers,stack,new Vec3(0,.06,0),UP,r,age*.5,fade*.9F);
        }
        var out=buffers.getBuffer(WaterRenderTypes.geometry());Matrix4f pose=stack.last().pose();
        float red=tier==4?.48F:.015F;
        float green=tier==1?.32F:tier==2?.85F:tier==3?.48F:tier==4?.16F:.3F;
        float blue=tier==2?.3F:tier==4?.95F:1;
        WaterGeometry.ringColor(out,pose,new Vec3(0,.07,0),UP,r,.16+r*.003,.14F+tier*.055F,.12F,.72F+tier*.05F,fade*.95F);
        // Each tier has a different hallmark, duration/healing remain server-owned.
        if(tier==1) {
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6,y=.4+((age*.04+i*.173)%1.8);
                Vec3 p=new Vec3(Math.cos(a)*r*.65,y,Math.sin(a)*r*.65);
                WaterGeometry.tube(out,pose,p.add(0,-.2,0),p.add(0,.2,0),.09,.02F,.38F,1,fade*.85F);
                WaterGeometry.ringColor(out,pose,new Vec3(p.x,.08,p.z),UP,((age*.025+i*.3)%1),.03,.2F,.7F,1,fade*.5F);
            }
        } else if(tier==2) {
            for(int i=0;i<3;i++)WaterGeometry.spiral(out,pose,Vec3.ZERO,UP,r*.72,3,age*.04+i*Math.PI*2/3,red,green,blue,fade*.7F);
            WaterGeometry.ringColor(out,pose,new Vec3(0,1.5,0),UP,r*.6,.1,.6F,1,.85F,fade*.5F);
        } else if(tier==3) {
            // Eight open water-lotus petals, with a pale-blue dome meridian cage.
            for(int i=0;i<8;i++)for(int j=0;j<12;j++) {
                double a=i*Math.PI/4+age*.003,t=j/12.0,q=(j+1)/12.0;
                Vec3 p=petal(a,t,r),v=petal(a,q,r);
                WaterGeometry.tube(out,pose,p,v,.055,red,green,blue,fade*.8F);
            }
            dome(out,pose,r,Math.min(10,h*.5),red,green,blue,fade*.7F);
        } else if(tier==4) {
            // Four turquoise columns wrap an indigo shield, distinct from the water columns.
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2;Vec3 p=new Vec3(Math.cos(a)*r*.75,.1,Math.sin(a)*r*.75);
                WaterGeometry.tube(out,pose,p,p.add(0,h*.75,0),.18,.01F,.5F,.8F,fade*.5F);
                WaterGeometry.spiral(out,pose,p,UP,.5,h*.75,age*.1+i,.02F,.5F,1,fade*.9F);
            }
            dome(out,pose,r,h*.75,red,green,blue,fade*.8F);
        } else {
            for(int i=0;i<4;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,.12+i*.35,0),UP,r*(1-i*.08),.18,.28F+i*.12F,.12F,1,fade*.9F);
            // Twelve peripheral cataracts connect the sky seal to the whole healing territory.
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6;Vec3 p=new Vec3(Math.cos(a)*r*.87,0,Math.sin(a)*r*.87);
                WaterGeometry.tube(out,pose,p,p.add(0,h,0),.3,.005F,.2F,.8F,fade*.5F);
                WaterGeometry.spiral(out,pose,p,UP,.65,h,-age*.065+i,.025F,.55F,1,fade*.9F);
                WaterGeometry.ringColor(out,pose,p.add(0,.2,0),UP,1.8,.18,.58F,.2F,1,fade*.9F);
            }
            // Eight luminous arches rise from an open central water crown, never a solid screen-covering dome.
            for(int i=0;i<8;i++)for(int j=0;j<24;j++) {
                double a=i*Math.PI/4+age*.005,t=j/24.0,q=(j+1)/24.0;
                Vec3 p=arch(a,t,r,h),v=arch(a,q,r,h);
                WaterGeometry.tube(out,pose,p,v,.075,.01F,.4F,1,fade*.86F);
            }
            for(int i=0;i<3;i++)WaterGeometry.spiral(out,pose,Vec3.ZERO,UP,r*.15,h*.65,age*.035+i*Math.PI*2/3,.01F,.35F,.92F,fade*.75F);
            dome(out,pose,r,h,.28F,.12F,.85F,fade*.65F);
        }
        // Rain is a supporting layer, colored per tier. Keep a strict finite vertex count.
        int count=16+tier*10;
        for(int i=0;i<count;i++) {
            double a=i*2.399963,dist=r*Math.sqrt(((i*37)%131)/131.0),rainHeight=tier==5?h:Math.min(10,h),y=rainHeight-((age*.25+i*.413)%rainHeight);
            Vec3 p=new Vec3(Math.cos(a)*dist,y,Math.sin(a)*dist);
            WaterGeometry.tube(out,pose,p,p.add(0,-.65,0),.025,red,green,blue,fade*.75F);
        }
    }
    private static Vec3 arch(double a,double t,double r,double h) {
        double distance=r*(.15+.72*t);
        return new Vec3(Math.cos(a)*distance,Math.sin(t*Math.PI)*h*.7,Math.sin(a)*distance);
    }
    private static Vec3 petal(double angle,double t,double r) {
        double distance=r*(.1+.9*Math.sin(t*Math.PI*.7));
        return new Vec3(Math.cos(angle)*distance,Math.sin(t*Math.PI)*2.5,Math.sin(angle)*distance);
    }
    private static void dome(VertexConsumer out,Matrix4f pose,double r,double h,float red,float green,float blue,float alpha) {
        for(int i=0;i<8;i++)for(int j=0;j<20;j++) {
            double a=i*Math.PI/4,t=j*Math.PI/40,q=(j+1)*Math.PI/40;
            Vec3 p=new Vec3(Math.cos(a)*r*Math.cos(t),h*Math.sin(t),Math.sin(a)*r*Math.cos(t));
            Vec3 v=new Vec3(Math.cos(a)*r*Math.cos(q),h*Math.sin(q),Math.sin(a)*r*Math.cos(q));
            WaterGeometry.tube(out,pose,p,v,.03,red,green,blue,alpha);
        }
        for(int j=1;j<=3;j++)WaterGeometry.ringColor(out,pose,new Vec3(0,h*j*.22,0),UP,
                r*Math.sqrt(1-Math.pow(j*.22,2)),.035,red,green,blue,alpha*.75F);
    }
}
