package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.wind.WindVolleyEntity;
import com.tnc.tnc.magic.wind.WindChainRules;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Continuous mint/white slipstreams follow the actual weapon trajectory. */
final class WindWeaponTrails {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/beam_white.png");
    private WindWeaponTrails() {}

    static void volley(WindVolleyEntity entity,int slot,double age,PoseStack pose,MultiBufferSource buffers,float alpha) {
        if(age<18||entity.done(slot))return;
        List<Vec3> path=new ArrayList<>();
        double first=entity.tier()==5?WindChainRules.emerge(slot):18;
        for(int i=0;i<18;i++) {
            double sample=age-i*.55;
            if(sample<first)break;
            path.add(entity.weaponAt(slot,sample));
        }
        draw(path,entity.position(),age,Math.min(3.2,.12+WindChainRules.modelScale(entity.tier())*.045),pose,buffers,alpha);
    }

    static void draw(Iterable<Vec3> points,Vec3 origin,double age,double width,
                     PoseStack pose,MultiBufferSource buffers,float alpha) {
        List<Vec3> path=new ArrayList<>();points.forEach(path::add);
        if(path.size()<2)return;
        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        for(int layer=0;layer<3;layer++) {
            Vec3 previousLeft=null,previousRight=null;
            for(int i=0;i<path.size();i++) {
                Vec3 at=path.get(i).subtract(origin);
                Vec3 direction=path.get(Math.min(path.size()-1,i+1)).subtract(path.get(Math.max(0,i-1)));
                Vec3 n=direction.cross(new Vec3(0,1,0)).normalize();
                if(n.lengthSqr()<1e-5)n=new Vec3(1,0,0);
                Vec3 up=direction.normalize().cross(n).normalize();
                float fade=(float)Math.pow(1-i/(double)path.size(),1.35)*alpha;
                double w=width*(layer==0?2.4:layer==1?1:.16)*(1-i/(double)path.size());
                Vec3 across=n.scale(w),offset=layer==2?up.scale(Math.sin(i*.55-age*.2)*width*.6):Vec3.ZERO;
                Vec3 left=at.add(offset).add(across),right=at.add(offset).subtract(across);
                if(previousLeft!=null)quad(out,pose.last(),previousLeft,left,right,previousRight,
                        layer==2?.96F:.48F,layer==2?1F:.94F,layer==2?1F:.82F,fade*(layer==0?.12F:layer==1?.42F:.9F));
                previousLeft=left;previousRight=right;
            }
        }
    }

    private static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha) {
        vertex(out,pose,a,r,g,blue,alpha);vertex(out,pose,b,r,g,blue,alpha);
        vertex(out,pose,c,r,g,blue,alpha);vertex(out,pose,d,r,g,blue,alpha);
        vertex(out,pose,d,r,g,blue,alpha);vertex(out,pose,c,r,g,blue,alpha);
        vertex(out,pose,b,r,g,blue,alpha);vertex(out,pose,a,r,g,blue,alpha);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float r,float g,float b,float alpha) {
        out.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha).uv(.5F,.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0,1,0).endVertex();
    }
}
