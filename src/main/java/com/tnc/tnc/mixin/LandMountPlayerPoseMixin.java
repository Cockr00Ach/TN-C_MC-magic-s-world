package com.tnc.tnc.mixin;

import com.tnc.tnc.mount.LandMount;
import com.tnc.tnc.mount.MountRiderPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 900)
public abstract class LandMountPlayerPoseMixin {
    @Unique
    private boolean tnc$adjustedMountedLegs;

    @Inject(method = {
            "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
    }, at = @At("HEAD"), remap = false, require = 1)
    private void tnc$restoreLegPivots(LivingEntity rider, float swing, float amount, float age,
                                      float yaw, float pitch, CallbackInfo callback) {
        if (tnc$adjustedMountedLegs) {
            PlayerModel<?> model = (PlayerModel<?>)(Object)this;
            tnc$restoreLegPivot(model.leftLeg);
            tnc$restoreLegPivot(model.rightLeg);
            tnc$adjustedMountedLegs = false;
        }
    }

    @Inject(method = {
            "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
    }, at = @At("TAIL"), remap = false, require = 1)
    private void tnc$mountStraddle(LivingEntity rider, float swing, float amount, float age,
                                   float yaw, float pitch, CallbackInfo callback) {
        if (!(rider.getVehicle() instanceof LandMount mount)) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>)(Object)this;
        MountRiderPose pose = MountRiderPose.forSpecies(mount.species());
        tnc$poseLeg(model.leftLeg, pose);
        tnc$poseLeg(model.rightLeg, pose);
        // PlayerModel already copied the pants before the tail hook.
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        tnc$adjustedMountedLegs = true;
    }

    @Unique
    private static void tnc$poseLeg(ModelPart leg, MountRiderPose pose) {
        float side = Math.copySign(1.0f, leg.getInitialPose().x);
        leg.setPos(side * MountRiderPose.HIP_WIDTH,
                leg.getInitialPose().y - pose.hipTuck(), leg.getInitialPose().z);
        leg.xRot = MountRiderPose.LEG_PITCH * Mth.DEG_TO_RAD;
        leg.yRot = -side * MountRiderPose.LEG_YAW * Mth.DEG_TO_RAD;
        leg.zRot = -side * MountRiderPose.LEG_SPREAD * Mth.DEG_TO_RAD;
    }

    @Unique
    private static void tnc$restoreLegPivot(ModelPart leg) {
        var initial = leg.getInitialPose();
        leg.setPos(initial.x, initial.y, initial.z);
    }
}
