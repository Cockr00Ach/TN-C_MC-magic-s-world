package com.tnc.tnc.boss.earth.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.boss.earth.StoneLancerEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class StoneLancerRenderer extends GeoEntityRenderer<StoneLancerEntity> {
    public StoneLancerRenderer(EntityRendererProvider.Context c){super(c,new StoneLancerModel());shadowRadius=4F;withScale(5F);}
    @Override public void render(StoneLancerEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(entity.burrowPhase()==2)return;
        pose.pushPose();
        try{pose.translate(0,entity.burrowOffset(partial),0);super.render(entity,yaw,partial,pose,buffers,light);}
        finally{pose.popPose();}
    }
    @Override public void renderRecursively(PoseStack pose,StoneLancerEntity entity,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer out,boolean rerender,float partial,int light,int overlay,float red,float green,float blue,float alpha){
        if(bone.getName().startsWith("glow_"))light=LightTexture.FULL_BRIGHT;
        super.renderRecursively(pose,entity,bone,type,buffers,out,rerender,partial,light,overlay,red,green,blue,alpha);
    }
    @Override protected float getDeathMaxRotation(StoneLancerEntity e){return 0;}
}
