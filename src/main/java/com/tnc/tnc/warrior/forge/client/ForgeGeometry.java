package com.tnc.tnc.warrior.forge.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.tnc.tnc.warrior.forge.WarriorForgeContent;

/** Small procedural placeholder geometry; replacement art does not own the process rules. */
final class ForgeGeometry {
    private ForgeGeometry(){}
    static void vertex(VertexConsumer v,PoseStack p,float x,float y,float z,int color){
        v.vertex(p.last().pose(),x,y,z).color(color>>16&255,color>>8&255,color&255,color>>>24).uv(0,0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal(p.last().normal(),0,1,0).endVertex();
    }
    static VertexConsumer buffer(MultiBufferSource b){return b.getBuffer(RenderType.entityTranslucentEmissive(WarriorForgeContent.id("textures/misc/warrior_forge_white.png")));}
    static void box(PoseStack p,MultiBufferSource b,float x,float y,float z,float w,float h,float d,int color){
        var v=buffer(b);float X=x+w,Y=y+h,Z=z+d;
        float[][] q={{x,y,z,X,y,z,X,Y,z,x,Y,z},{X,y,Z,x,y,Z,x,Y,Z,X,Y,Z},{x,y,Z,x,y,z,x,Y,z,x,Y,Z},
            {X,y,z,X,y,Z,X,Y,Z,X,Y,z},{x,Y,z,X,Y,z,X,Y,Z,x,Y,Z},{x,y,Z,X,y,Z,X,y,z,x,y,z}};
        for(var face:q)for(int i=0;i<12;i+=3)vertex(v,p,face[i],face[i+1],face[i+2],color);
    }
    static void ring(PoseStack p,MultiBufferSource b,float cx,float y,float cz,float radius,float width,int color,double phase){
        var v=buffer(b);
        for(int i=0;i<64;i++){
            double a=i*Math.PI/32+phase,c=(i+1)*Math.PI/32+phase;
            vertex(v,p,cx+(float)Math.cos(a)*radius,y,cz+(float)Math.sin(a)*radius,color);
            vertex(v,p,cx+(float)Math.cos(a)*(radius-width),y,cz+(float)Math.sin(a)*(radius-width),color);
            vertex(v,p,cx+(float)Math.cos(c)*(radius-width),y,cz+(float)Math.sin(c)*(radius-width),color);
            vertex(v,p,cx+(float)Math.cos(c)*radius,y,cz+(float)Math.sin(c)*radius,color);
        }
    }
}
