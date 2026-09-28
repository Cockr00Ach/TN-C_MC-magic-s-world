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
        int tier=e.tier();double r=e.radius();
        if(tier>=4)WaterGeometry.richCircle(buffers,stack,new Vec3(0,tier==5?8:.08,0),UP,r*(tier==5?1.25:1),age,fade*.7F);
        var out=buffers.getBuffer(RenderType.lightning());Matrix4f pose=stack.last().pose();
        float red=tier==1?.08F:tier==2?.12F:tier==3?.6F:tier==4?1:.95F;
        float green=tier==1?.55F:tier==2?1:tier==3?.84F:tier==4?.82F:.98F;
        float blue=tier==2?.66F:tier==4?.4F:1;
        WaterGeometry.ringColor(out,pose,new Vec3(0,.07,0),UP,r,.08,red,green,blue,fade*.7F);
        // Each tier has a different hallmark, duration/healing remain server-owned.
        if(tier==1) {
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6,y=.4+((age*.04+i*.173)%1.8);
                Vec3 p=new Vec3(Math.cos(a)*r*.65,y,Math.sin(a)*r*.65);
                WaterGeometry.tube(out,pose,p.add(0,-.12,0),p.add(0,.12,0),.06,.25F,.8F,1,fade*.65F);
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
            dome(out,pose,r,3.8,red,green,blue,fade*.5F);
        } else if(tier==4) {
            // Four turquoise columns wrap a gold shield, clearly different from an ordinary shower.
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2;Vec3 p=new Vec3(Math.cos(a)*r*.75,.1,Math.sin(a)*r*.75);
                WaterGeometry.tube(out,pose,p,p.add(0,4.5,0),.14,.18F,.9F,.88F,fade*.38F);
                WaterGeometry.spiral(out,pose,p,UP,.3,4.5,age*.1+i,.65F,1,1,fade*.7F);
            }
            dome(out,pose,r,4.7,red,green,blue,fade*.65F);
        } else {
            for(int i=0;i<4;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,.1+i*.32,0),UP,r*(1-i*.08),.1,red,green,blue,fade*.65F);
            for(int i=0;i<6;i++) {
                double a=i*Math.PI/3+age*.01;Vec3 p=new Vec3(Math.cos(a)*r*.9,0,Math.sin(a)*r*.9);
                WaterGeometry.tube(out,pose,p,p.add(0,8,0),.09,.35F,.85F,1,fade*.35F);
                WaterGeometry.spiral(out,pose,p,UP,.25,8,-age*.06+i,.95F,.98F,1,fade*.45F);
            }
            dome(out,pose,r,7,.85F,.96F,1,fade*.4F);
        }
        // Rain is a supporting layer, colored per tier. Keep a strict finite vertex count.
        int count=16+tier*10;
        for(int i=0;i<count;i++) {
            double a=i*2.399963,dist=r*Math.sqrt(((i*37)%131)/131.0),y=(tier==5?7:4)-((age*.16+i*.413)%(tier==5?7:4));
            Vec3 p=new Vec3(Math.cos(a)*dist,y,Math.sin(a)*dist);
            WaterGeometry.tube(out,pose,p,p.add(0,-.4,0),.015,red,green,blue,fade*.48F);
        }
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
