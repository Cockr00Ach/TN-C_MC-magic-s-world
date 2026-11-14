package com.tnc.tnc.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cup layer owns the actor's hands during the film; inventory remains untouched. */
@Mixin(value=ItemInHandLayer.class,priority=1100)
public abstract class OpeningHeldItemMixin {
    @Inject(method={"renderArmWithItem","m_117184_"},at=@At("HEAD"),remap=false,cancellable=true,require=1)
    private void openingProp(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo callback){
        if(com.tnc.tnc.tavern.client.TavernOpeningScene.active()&&entity==Minecraft.getInstance().player)callback.cancel();
    }
}
