package com.tnc.tnc.life.routes.mech;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.LightTexture;
/** Full-body armor uses the normal articulated armor pose, with separate lit crystal parts. */
public final class SkywardArmorModel extends HumanoidModel<LivingEntity>{
    public int color=0x59e5e8;
    public SkywardArmorModel(ModelPart root){super(root);}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer out,int light,int overlay,float r,float g,float b,float alpha){
        ModelPart[] parts={head,body,rightArm,leftArm};for(var p:parts)p.getChild("glow").visible=false;
        super.renderToBuffer(pose,out,light,overlay,r,g,b,alpha);
        var lightOut=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.eyes(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","textures/models/armor/mana_mech_glow.png")));
        for(var p:parts){var glow=p.getChild("glow");glow.visible=true;pose.pushPose();p.translateAndRotate(pose);glow.render(pose,lightOut,LightTexture.FULL_BRIGHT,overlay,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);pose.popPose();}
    }
}
