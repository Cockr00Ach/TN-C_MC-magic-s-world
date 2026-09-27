package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class TNWaterFieldRenderer extends EntityRenderer<TNWaterFieldEntity> {
    public TNWaterFieldRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNWaterFieldEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public boolean shouldRender(TNWaterFieldEntity e,Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<256*256;}
    @Override public void render(TNWaterFieldEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        double age=e.age()+partial;float fade=(float)Math.min(1,Math.min(age/5,(e.life()-age)/8));if(fade<=0)return;
        var out=buffers.getBuffer(RenderType.lightning());var pose=stack.last().pose();double radius=e.radius();Vec3 up=new Vec3(0,1,0);
        if(e.kind()==1) {
            Vec3 center=e.waveCenter(age).subtract(e.position());
            if(e.tier()==1) {
                for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,new Vec3(0,.1+i*.12,0),up,Math.min(radius,age*.2+i*.3),.09,0,fade);
            } else if(e.tier()==5) {
                // Four full circular walls: the interior is filled by successive moving wave fronts.
                for(int i=0;i<4;i++) {
                    double r=(age*.55+i*5)%20;
                    double height=3+12*Math.sin(Math.PI*r/20);
                    circularWave(out,pose,r,height,age+i*10,fade*.65F);
                }
                WaterGeometry.ring(out,pose,new Vec3(0,.1,0),up,20,.3,0,fade);
            } else {
                Vec3 right=WaterSpellRules.right(e.direction());double h=e.tier()==4?8:e.tier()==3?2.8:2;
                for(int i=0;i<48;i++) {
                    double a=-Math.PI*.48+i*Math.PI*.96/48,b=-Math.PI*.48+(i+1)*Math.PI*.96/48;
                    Vec3 p=center.add(right.scale(Math.sin(a)*radius)).add(e.direction().scale(Math.cos(a)*1.6));
                    Vec3 q=center.add(right.scale(Math.sin(b)*radius)).add(e.direction().scale(Math.cos(b)*1.6));
                    double ha=h*Math.cos(a),hb=h*Math.cos(b);
                    Vec3 topA=p.add(0,ha,0).subtract(e.direction().scale(.4)),topB=q.add(0,hb,0).subtract(e.direction().scale(.4));
                    WaterGeometry.quad(out,pose,p,q,topB,topA,.06F,.55F,.95F,fade*.5F);
                    WaterGeometry.tube(out,pose,topA,topB,.07,.8F,.98F,1,fade);
                }
            }
        } else if(e.kind()==2) {
            if(e.tier()<3) {
                for(int i=0;i<(e.tier()==1?2:6);i++) {
                    double y=e.tier()==1?.2+i*.25:.15+i*.38;
                    WaterGeometry.ring(out,pose,new Vec3(0,y,0),up,e.tier()==1?.65:Math.sqrt(Math.max(.1,1.5-Math.pow(y-1.1,2))),.07,0,fade);
                }
                if(e.tier()==2)for(int i=0;i<8;i++) {
                    double a=i*Math.PI/4+age*.02;
                    WaterGeometry.tube(out,pose,new Vec3(Math.cos(a),.3,Math.sin(a)),new Vec3(Math.cos(a),2,Math.sin(a)),.045,.25F,.85F,1,fade*.5F);
                }
            } else {
                for(int i=0;i<8;i++)WaterGeometry.ring(out,pose,new Vec3(0,.08+i*.2,0),up,radius*(1-i*.07),.05,0,fade*.8F);
                if(e.tier()>=4)circularWave(out,pose,radius, e.tier()==5?8:4,age,fade*.45F);
                if(e.tier()==5)for(int i=0;i<10;i++) {
                    double y=i*.8;
                    WaterGeometry.ring(out,pose,new Vec3(0,y,0),up,radius*Math.sqrt(Math.max(.02,1-y*y/64)),.06,0,fade*.65F);
                }
            }
        } else {
            WaterGeometry.ring(out,pose,new Vec3(0,.05,0),up,radius,.08,0,fade*.6F);
            for(int i=0;i<80+e.tier()*12;i++) {
                double a=i*2.399963, r=radius*Math.sqrt(((i*37)%131)/131.0), y=5-((age*.18+i*.413)%5);
                Vec3 p=new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);
                WaterGeometry.tube(out,pose,p,p.add(0,-.32-e.tier()*.04,0),.012,.38F,.85F,1,fade*.55F);
            }
            for(int i=0;i<5;i++)WaterGeometry.ring(out,pose,new Vec3(Math.sin(i*4)*radius*.5,.06,Math.cos(i*4)*radius*.5),up,((age*.06+i*.23)%1.1),.025,0,fade*.4F);
            if(e.tier()==5) {
                stack.pushPose();stack.translate(0,6,0);WaterGeometry.circle(out,stack.last().pose(),up,radius,age,fade*.45F);stack.popPose();
            }
        }
    }
    private static void circularWave(VertexConsumer out,Matrix4f pose,double radius,double height,double age,float alpha) {
        for(int i=0;i<96;i++) {
            double a=i*Math.PI/48,b=(i+1)*Math.PI/48;
            double h1=height*(.85+.15*Math.sin(a*7+age*.16)),h2=height*(.85+.15*Math.sin(b*7+age*.16));
            Vec3 p=new Vec3(Math.cos(a)*radius,.1,Math.sin(a)*radius),q=new Vec3(Math.cos(b)*radius,.1,Math.sin(b)*radius);
            Vec3 t=new Vec3(Math.cos(a)*(radius-.6),h1,Math.sin(a)*(radius-.6)),u=new Vec3(Math.cos(b)*(radius-.6),h2,Math.sin(b)*(radius-.6));
            WaterGeometry.quad(out,pose,p,q,u,t,.05F,.45F,.95F,alpha*.55F);
            WaterGeometry.tube(out,pose,t,u,.07,.6F,.95F,1,alpha);
        }
    }
}
