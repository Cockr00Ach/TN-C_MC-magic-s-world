package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.world.ink.InkCraneAttackRules;
import com.tnc.tnc.world.ink.InkCraneBossEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Reuses this exact frame's posed bones: no detached entity, interpolation lag or separate animation clock. */
final class InkCraneSkyShroudLayer extends GeoRenderLayer<InkCraneBossEntity> {
    InkCraneSkyShroudLayer(GeoRenderer<InkCraneBossEntity> renderer){super(renderer);}
    @Override public void render(PoseStack pose,InkCraneBossEntity boss,BakedGeoModel model,RenderType type,
            MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay) {
        if(!boss.isAlive() || boss.inkMove()!=InkCraneAttackRules.SKY || !boss.skyShroudVisible())return;
        var ghost=RenderType.entityTranslucent(getTextureResource(boss));
        pose.pushPose();
        var processor=getRenderer().getGeoModel().getAnimationProcessor();
        var left=processor.getBone("wing_l");var right=processor.getBone("wing_r");
        float leftScale=left==null?1:left.getScaleX(),rightScale=right==null?1:right.getScaleX();
        try {
            if(left!=null)left.setScaleX(leftScale*1.5F);if(right!=null)right.setScaleX(rightScale*1.5F);
            // Body pivot = 123 model pixels. Enlarge around the torso, not a separate point in the sky.
            pose.translate(0,123.0/16,0);
            float scale=InkCraneAttackRules.SKY_SHROUD_SCALE;
            pose.scale(scale,scale,scale);
            pose.translate(0,-123.0/16,0);
            getRenderer().reRender(model,pose,buffers,boss,ghost,buffers.getBuffer(ghost),partial,
                    LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,1,1,.32F);
        }finally {if(left!=null)left.setScaleX(leftScale);if(right!=null)right.setScaleX(rightScale);pose.popPose();}
    }
}
