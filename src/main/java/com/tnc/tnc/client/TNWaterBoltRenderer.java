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
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();double radius=e.tier()==2?.4:.27;
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
        WaterGeometry.tube(out,pose,Vec3.ZERO,dir.scale(-1.2),radius*.3,.4F,.9F,1,.3F);
    }
}
