package com.tnc.tnc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.bronze.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class BronzeHeldItemMixin {
    @Inject(method={"renderArmWithItem","m_117184_"},at=@At("HEAD"),remap=false,cancellable=true,require=1)
    private void bronzeHideThrown(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(stack.getItem() instanceof BronzeWeaponItem&&BronzeWeaponItem.inFlight(stack,entity.level()))ci.cancel();
    }
}
