package com.tnc.tnc.dungeon.mirror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.dungeon.mirror.EchoDouble;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class EchoDoubleRenderer extends EntityRenderer<EchoDouble> {
    public EchoDoubleRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius=.43f; }

    @Override public void render(EchoDouble actor,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var level=(ClientLevel)actor.level();
        var profileData=actor.appearance().getCompound("Profile");
        if(!profileData.hasUUID("Id")) return;
        if(actor.renderSubject==null) actor.renderSubject=new EchoPlayer(level,NbtUtils.readGameProfile(profileData),actor);
        var body=(EchoPlayer)actor.renderSubject;
        body.sync();
        float yScale=actor.appearance().getFloat("Height"),xz=actor.appearance().getFloat("Width");
        pose.pushPose();
        if(yScale>0 && xz>0) pose.scale(xz,yScale,xz);
        float age=actor.actionAge()+partial;
        boolean sinking=actor.isDeadOrDying() || "dissolve".equals(actor.actionName());
        float fraction=sinking ? 1-Mth.clamp(age/(actor.melee()?12f:32f),0,1)
                : "rise".equals(actor.actionName()) ? Mth.clamp((age+1)/18f,.05f,1f) : 1;
        if(fraction>.015f) {
            var visible=buffers;
            if(fraction<1) {
                visible=new SubmergedModel(buffers,pose,sinking ? 1.98f*fraction : -.02f,sinking);
                if(!sinking) pose.translate(0,-1.84*(1-fraction),0);
            }
            var skinRenderer=entityRenderDispatcher.getRenderer(body);
            skinRenderer.render(body,yaw,partial,pose,visible,light);
        }
        pose.popPose();
        MirrorEffects.footRipple(actor,partial,pose,buffers);
        if(!sinking) super.render(actor,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(EchoDouble actor) {
        var subject=(EchoPlayer)actor.renderSubject;
        return subject==null ? MirrorEffects.WHITE : subject.getSkinTextureLocation();
    }
}
