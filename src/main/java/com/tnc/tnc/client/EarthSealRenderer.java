package com.tnc.tnc.client;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Same solid seal geometry as water, with the earth material and smooth finite fade. */
final class EarthSealRenderer {
    static void render(TNMagicCircleEntity entity,float partial,PoseStack pose,MultiBufferSource buffers){
        float age=entity.tickCount+partial;
        float opacity=Math.max(0,Math.min(1,(entity.life()-age)/18));
        if(opacity<=0)return;
        pose.pushPose();pose.translate(0,.06,0);pose.mulPose(Axis.YP.rotationDegrees(age*.55F));
        float size=(float)entity.radius()*2;
        pose.scale(size,1.2F,size);pose.translate(-.5,0,-.5);
        var mc=Minecraft.getInstance();
        var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","projectile/earth_seal"));
        var consumer=buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        mc.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,pose,new Fade(consumer,opacity));
        pose.popPose();
    }
    private record Fade(com.mojang.blaze3d.vertex.VertexConsumer target,float alpha) implements com.mojang.blaze3d.vertex.VertexConsumer {
        public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer color(int r,int g,int b,int a){target.color(r,g,b,Math.round(a*alpha));return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer uv(float u,float v){target.uv(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u,int v){target.overlayCoords(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u,int v){target.uv2(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
        public void endVertex(){target.endVertex();}
        public void defaultColor(int r,int g,int b,int a){target.defaultColor(r,g,b,Math.round(a*alpha));}
        public void unsetDefaultColor(){target.unsetDefaultColor();}
    }
}
