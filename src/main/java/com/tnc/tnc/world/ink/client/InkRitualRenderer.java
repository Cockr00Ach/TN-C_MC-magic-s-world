package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.InkRitualEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class InkRitualRenderer extends EntityRenderer<InkRitualEntity> {
    private static final ResourceLocation SEAL=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_vortex_seal.png");
    public InkRitualRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(InkRitualEntity e){return SEAL;}
    @Override public void render(InkRitualEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        double age=e.age()+partial;
        if(e.mode()==InkRitualEntity.ORB){
            if(age<65){double t=Math.min(1,age/65),radius=.8+6.2*t*t*(3-2*t);InkTigerSpellVisuals.gather(pose,buffers,age,Vec3.ZERO,radius,24);}
            else {InkTigerSpellVisuals.burst(pose,buffers,(age-65)*1.2,26);InkPaintingRenderer.aftermath(100+age-65,pose,buffers);}
            return;
        }
        float fade=(float)Math.min(Math.min(1,age/7),Math.max(0,(e.life()-age)/10));
        seal(pose,buffers,age,e.mode()==InkRitualEntity.CRANE?5:10,fade);
    }
    static void seal(PoseStack pose,MultiBufferSource buffers,double age,double radius,float alpha){
        pose.pushPose();try{
            pose.translate(0,.055,0);pose.mulPose(Axis.YP.rotationDegrees((float)(age*3.5)));
            var ink=buffers.getBuffer(RenderType.entityTranslucent(SEAL));
            InkCraneRitualVisuals.quad(ink,pose.last(),new Vec3(-radius,0,-radius),new Vec3(-radius,0,radius),new Vec3(radius,0,radius),new Vec3(radius,0,-radius),1,alpha);
        }finally{pose.popPose();}
    }
}
