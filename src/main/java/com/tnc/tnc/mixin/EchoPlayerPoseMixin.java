package com.tnc.tnc.mixin;

import com.tnc.tnc.dungeon.mirror.client.EchoPlayer;
import com.tnc.tnc.dungeon.mirror.client.MirrorPoses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class EchoPlayerPoseMixin {
    @Unique private final float[] tnc$echoSaved=new float[36];
    @Unique private boolean tnc$echoChanged;
    @Inject(method={"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V","m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
            at=@At("HEAD"),remap=false,require=1)
    private void tnc$restoreEcho(LivingEntity entity,float walk,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(tnc$echoChanged) {
            MirrorPoses.rotations((PlayerModel<?>)(Object)this,tnc$echoSaved,true);
            tnc$echoChanged=false;
        }
    }
    @Inject(method={"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V","m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
            at=@At("TAIL"),remap=false,require=1)
    private void tnc$poseEcho(LivingEntity entity,float walk,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(entity instanceof EchoPlayer mirror) {
            var model=(PlayerModel<?>)(Object)this;
            MirrorPoses.rotations(model,tnc$echoSaved,false);
            tnc$echoChanged=true;
            MirrorPoses.apply(model,mirror,age);
        }
    }
}
