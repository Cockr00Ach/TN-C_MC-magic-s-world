package com.tnc.tnc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.warrior.client.WarriorPhantomRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shares the weapon's actual mount for either hand, including poses provided by other render layers. */
@Mixin(ItemInHandRenderer.class)
public abstract class WarriorHeldWeaponMixin {
    @Inject(method="renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At("TAIL"))
    private void tnc$boundWeapon(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        com.tnc.tnc.warrior.client.WarriorTrail.capture(entity,stack,context,left,pose);
        WarriorPhantomRenderer.renderBound(entity, stack, context, left, pose, buffers, light);
        com.tnc.tnc.warrior.client.WarriorWeaponOrnamentRenderer.render(entity,stack,context,left,pose,buffers,light);
    }
}
