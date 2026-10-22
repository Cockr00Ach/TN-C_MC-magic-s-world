package com.tnc.tnc.world.wind.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.world.wind.CanglanEchoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A bounded luminous current: soft sheath, bright core and two travelling helical strands. */
public final class CanglanManaLinks {
    private static final RenderType FLOW=RenderType.eyes(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/canglan_mana_flow.png"));
    private CanglanManaLinks(){}
    public static void render(CanglanEchoEntity e,float partial,PoseStack pose,MultiBufferSource buffers){
        Vec3 link=e.link();double length=link.length();if(length<.1)return;
        float scale=e.visualScale(),fade=e.opacity(partial);if(fade<=0)return;
        Vec3 axis=link.normalize(),camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.position()).subtract(link.scale(.5));
        Vec3 side=axis.cross(camera).normalize();if(side.lengthSqr()<.01)side=axis.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
        Vec3 up=axis.cross(side).normalize();int segments=Math.min(72,Math.max(12,(int)Math.ceil(length*1.5)));
        float age=e.tickCount+partial;VertexConsumer out=buffers.getBuffer(FLOW);var matrix=pose.last();
        for(int layer=0;layer<4;layer++)for(int i=0;i<segments;i++){
            double a=i/(double)segments,b=(i+1)/(double)segments;
            Vec3 x=point(link,side,up,a,length,age,scale,layer),y=point(link,side,up,b,length,age,scale,layer);
            float width=(layer==0?.22F:layer==1?.055F:.025F)*scale;
            double flow=.7+.3*Math.sin((a*length-age*.13)*2.4);
            int alpha=(int)(255*fade*(layer==0?.17:layer==1?.88:.62)*flow);
            float u=(float)(a*length*.3-age*.065),v=(float)(b*length*.3-age*.065);
            Vec3 edge=side.scale(width);
            vertex(out,matrix,x.subtract(edge),u,0,alpha,layer);
            vertex(out,matrix,y.subtract(edge),v,0,alpha,layer);
            vertex(out,matrix,y.add(edge),v,1,alpha,layer);
            vertex(out,matrix,x.add(edge),u,1,alpha,layer);
        }
    }
    private static Vec3 point(Vec3 line,Vec3 side,Vec3 up,double t,double length,float age,float scale,int layer){
        Vec3 p=line.scale(t).add(0,.85*scale+Math.sin(t*Math.PI)*.18*scale,0);
        if(layer>=2){double angle=t*length*1.8-age*.14+(layer-2)*Math.PI;double radius=.14*scale*Math.sin(Math.PI*t);p=p.add(side.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));}
        return p;
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose m,Vec3 p,float u,float v,int alpha,int layer){
        out.vertex(m.pose(),(float)p.x,(float)p.y,(float)p.z).color(layer==1?190:65,layer==1?255:225,255,alpha)
            .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m.normal(),0,1,0).endVertex();
    }
}
