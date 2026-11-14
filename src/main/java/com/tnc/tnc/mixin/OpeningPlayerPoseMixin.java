package com.tnc.tnc.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A relaxed raised wrist for the cup; ordinary player animation resumes after the film. */
@Mixin(PlayerModel.class)
public abstract class OpeningPlayerPoseMixin {
    @Inject(method={"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V","m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},at=@At("TAIL"),remap=false,require=1)
    private void openingWrist(LivingEntity player,float swing,float amount,float age,float yaw,float pitch,CallbackInfo callback){
        if(player!=Minecraft.getInstance().player||!com.tnc.tnc.tavern.client.TavernOpeningScene.active())return;
        var model=(PlayerModel<?>)(Object)this;
        model.rightArm.xRot=-1.65f+(float)Math.sin(age*.035)*.045f;
        model.rightArm.yRot=-.18f;model.rightArm.zRot=.10f;
        model.rightSleeve.copyFrom(model.rightArm);
    }
}
