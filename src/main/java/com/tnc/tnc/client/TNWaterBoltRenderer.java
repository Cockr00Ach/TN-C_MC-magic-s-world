package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class TNWaterBoltRenderer extends EntityRenderer<TNWaterBoltEntity> {
    public TNWaterBoltRenderer(EntityRendererProvider.Context ctx){super(ctx);shadowRadius=0;}
    @Override public boolean shouldRender(TNWaterBoltEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<128*128 && f.isVisible(e.getBoundingBox().inflate(4*e.spellScale()));}
    @Override public ResourceLocation getTextureLocation(TNWaterBoltEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public void render(TNWaterBoltEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        buffers=WaterTransparencyPass.route(buffers);
        stack.pushPose();
        stack.scale(e.spellScale(),e.spellScale(),e.spellScale());
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        Vec3 dir=e.getDeltaMovement().normalize();if(dir.lengthSqr()<.5)dir=new Vec3(0,0,1);
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();double radius=WaterSpellRules.boltRadius(e.tier());
        double age=e.tickCount+partial;
        sphere(out,pose,dir,right,up,radius,.12F,.72F,1,.75F);
        WaterGeometry.ring(out,pose,Vec3.ZERO,dir,radius*1.15,.045,(e.tickCount+partial)*.25,1,32);
        WaterGeometry.ringColor(out,pose,Vec3.ZERO,right,radius*1.25,.035,.7F,.97F,1,.8F,32);
        WaterGeometry.ringColor(out,pose,Vec3.ZERO,up,radius*1.2,.03,.18F,.8F,1,.6F,32);
        double tail=e.tier()==2?3.8:2.2;
        for(int strand=0;strand<3;strand++)for(int i=0;i<16;i++) {
            double t=i/16.0,q=(i+1)/16.0,a=age*.35+t*Math.PI*3+strand*Math.PI*2/3,b=age*.35+q*Math.PI*3+strand*Math.PI*2/3;
            Vec3 p=dir.scale(-tail*t).add(WaterGeometry.radial(right,up,a,radius*(1-t)*.9));
            Vec3 r=dir.scale(-tail*q).add(WaterGeometry.radial(right,up,b,radius*(1-q)*.9));
            WaterGeometry.tube(out,pose,p,r,e.tier()==2?.045:.03,.55F,.96F,1,(float)(.7*(1-t)));
        }
        WaterGeometry.tube(out,pose,Vec3.ZERO,dir.scale(-tail),radius*.2,.65F,.98F,1,.4F);
        if(e.tier()==2)for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,dir.scale(-.5-i*.7),dir,radius*(1-i*.15),.025,0,.6F-i*.15F);
        /* ---- 加色发光层（2026-10-07 特效增强）----
           同一套几何再画一遍，但走 WaterRenderTypes.glow() 的加色混合 ✓：
           水滴核心、外圈光环、出膛光面会被推亮成"发光"，而不是单纯盖一层浅蓝 ✓。
           呼吸脉冲（pulse）让它不是死板的静态球 ✗。 */
        var glow=buffers.getBuffer(WaterRenderTypes.glow());var gpose=stack.last().pose();
        float pulse=(float)(.78+.22*Math.sin(age*.55));
        sphere(glow,gpose,dir,right,up,radius*.62,1,.99F,1,(e.tier()==2?.34F:.26F)*pulse);
        WaterGeometry.ringColor(glow,gpose,Vec3.ZERO,dir,radius*1.32,.055,.55F,.95F,1,.50F*pulse,32);
        WaterGeometry.disk(glow,gpose,Vec3.ZERO,dir,radius*1.02,.45F,.90F,1,.34F*pulse,24);
        WaterGeometry.tube(glow,gpose,Vec3.ZERO,dir.scale(-tail*.85),radius*.30,.72F,1,1,.30F*pulse);
        if(e.tier()==2)WaterGeometry.ringColor(glow,gpose,dir.scale(-.5),dir,radius*1.2,.06,.60F,1,1,.40F*pulse);
        stack.popPose();
    }
    /** 水球本体：一张经线×纬线的封闭球面 ✓（原来是内联在 render 里的一大段 ✗）。 */
    private static void sphere(VertexConsumer out,Matrix4f pose,Vec3 dir,Vec3 right,Vec3 up,double radius,float r,float g,float b,float alpha) {
        for(int j=0;j<6;j++) {
            double a=-Math.PI/2+j*Math.PI/6,bb=a+Math.PI/6;
            for(int i=0;i<12;i++) {
                double u=i*Math.PI/6,v=(i+1)*Math.PI/6;
                Vec3 p=dir.scale(Math.sin(a)*radius).add(WaterGeometry.radial(right,up,u,Math.cos(a)*radius));
                Vec3 q=dir.scale(Math.sin(a)*radius).add(WaterGeometry.radial(right,up,v,Math.cos(a)*radius));
                Vec3 s=dir.scale(Math.sin(bb)*radius).add(WaterGeometry.radial(right,up,v,Math.cos(bb)*radius));
                Vec3 t=dir.scale(Math.sin(bb)*radius).add(WaterGeometry.radial(right,up,u,Math.cos(bb)*radius));
                WaterGeometry.quad(out,pose,p,q,s,t,r,g,b,alpha);
            }
        }
    }
}
