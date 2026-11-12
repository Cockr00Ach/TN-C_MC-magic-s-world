package com.tnc.tnc.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.bronze.BronzeTwinWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemDisplayContext;

/** Adds only the visual partner blade to the opposite humanoid hand. */
public final class BronzeTwinLayer<T extends LivingEntity,M extends EntityModel<T> & ArmedModel> extends RenderLayer<T,M> {
    public BronzeTwinLayer(RenderLayerParent<T,M> parent){super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T owner,float walk,float amount,float partial,float age,float yaw,float pitch){
        if(!BronzeTwinWeapon.active(owner)||owner.isSpectator())return;
        var arm=owner.getMainArm().getOpposite();boolean left=arm==HumanoidArm.LEFT;
        pose.pushPose();
        getParentModel().translateToHand(arm,pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate((left?-1:1)/16d,.125,-.625);
        var context=left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        BronzeTwinRenderer.withView(BronzeTwinRenderer.View.SECONDARY,owner,()->Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderItem(owner,owner.getMainHandItem(),context,left,pose,buffers,light));
        pose.popPose();
    }
}
