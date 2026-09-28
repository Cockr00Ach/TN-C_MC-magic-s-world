package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class TNWaterBoltRenderer extends EntityRenderer<TNWaterBoltEntity> {
    public TNWaterBoltRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=0;}
    @Override public boolean shouldRender(TNWaterBoltEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<128*128;}
    @Override public ResourceLocation getTextureLocation(TNWaterBoltEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public void render(TNWaterBoltEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        var out=buffers.getBuffer(RenderType.lightning());var pose=stack.last().pose();
        Vec3 dir=e.getDeltaMovement().normalize();if(dir.lengthSqr()<.5)dir=new Vec3(0,0,1);
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();double radius=e.tier()==2?.5:.32;
        double age=e.tickCount+partial;
        for(int j=0;j<10;j++) {
            double a=-Math.PI/2+j*Math.PI/10,b=a+Math.PI/10;
            for(int i=0;i<20;i++) {
                double u=i*Math.PI/10,v=(i+1)*Math.PI/10;
                Vec3 p=dir.scale(Math.sin(a)*radius).add(WaterGeometry.radial(right,up,u,Math.cos(a)*radius));
                Vec3 q=dir.scale(Math.sin(a)*radius).add(WaterGeometry.radial(right,up,v,Math.cos(a)*radius));
                Vec3 r=dir.scale(Math.sin(b)*radius).add(WaterGeometry.radial(right,up,v,Math.cos(b)*radius));
                Vec3 s=dir.scale(Math.sin(b)*radius).add(WaterGeometry.radial(right,up,u,Math.cos(b)*radius));
                WaterGeometry.quad(out,pose,p,q,r,s,.12F,.72F,1,.75F);
            }
        }
        WaterGeometry.ring(out,pose,Vec3.ZERO,dir,radius*1.15,.045,(e.tickCount+partial)*.25,1);
        WaterGeometry.ringColor(out,pose,Vec3.ZERO,right,radius*1.25,.035,.7F,.97F,1,.8F);
        WaterGeometry.ringColor(out,pose,Vec3.ZERO,up,radius*1.2,.03,.18F,.8F,1,.6F);
        double tail=e.tier()==2?3.8:2.2;
        for(int strand=0;strand<3;strand++)for(int i=0;i<16;i++) {
            double t=i/16.0,q=(i+1)/16.0,a=age*.35+t*Math.PI*3+strand*Math.PI*2/3,b=age*.35+q*Math.PI*3+strand*Math.PI*2/3;
            Vec3 p=dir.scale(-tail*t).add(WaterGeometry.radial(right,up,a,radius*(1-t)*.9));
            Vec3 r=dir.scale(-tail*q).add(WaterGeometry.radial(right,up,b,radius*(1-q)*.9));
            WaterGeometry.tube(out,pose,p,r,e.tier()==2?.045:.03,.55F,.96F,1,(float)(.7*(1-t)));
        }
        WaterGeometry.tube(out,pose,Vec3.ZERO,dir.scale(-tail),radius*.2,.65F,.98F,1,.4F);
        if(e.tier()==2)for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,dir.scale(-.5-i*.7),dir,radius*(1-i*.15),.025,0,.6F-i*.15F);
    }
}
