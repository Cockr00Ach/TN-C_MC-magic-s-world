package com.tnc.tnc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse vanilla arm, equip and swing rendering for the virtual partner blade. */
@Mixin(ItemInHandRenderer.class)
public interface BronzeHandInvoker {
    @Invoker("renderArmWithItem")
    void bronze$renderArmWithItem(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffers,int light);
}
