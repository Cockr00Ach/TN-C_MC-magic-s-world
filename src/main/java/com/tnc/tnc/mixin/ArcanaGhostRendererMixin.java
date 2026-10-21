package com.tnc.tnc.mixin;

import com.tnc.tnc.arcana.client.ArcanaGhosts;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class ArcanaGhostRendererMixin {
    @SuppressWarnings({"rawtypes","unchecked"})
    @Inject(method="getRenderType",at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$ghostMaterial(LivingEntity entity,boolean body,boolean translucent,boolean glow,CallbackInfoReturnable<net.minecraft.client.renderer.RenderType> ci){
        if(ArcanaGhosts.active(entity))ci.setReturnValue(net.minecraft.client.renderer.RenderType.entityTranslucent(((LivingEntityRenderer)(Object)this).getTextureLocation(entity)));
    }
    @ModifyArgs(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),require=1)
    private void tnc$ghostMesh(Args args,LivingEntity entity,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        if(!ArcanaGhosts.active(entity))return;args.set(7,Math.max(.08f,1-ArcanaGhosts.fade()*.92f));args.set(1,ArcanaGhosts.wrap(args.get(1)));
    }
}
