package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Small view-space adaptation of the same attack timeline; never rotates the camera. */
public final class WarriorFirstPersonPose {
    private WarriorFirstPersonPose() {}
    public static void apply(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose){
        if(!context.firstPerson() || entity!=Minecraft.getInstance().player || !WarriorWeapons.bladed(stack)
                || left!=(entity.getMainArm()==HumanoidArm.LEFT)&&!com.tnc.tnc.bronze.BronzeTwinWeapon.active(entity))return;
        var active=WarriorAnimationClient.active(entity.getId());
        if(active==null)return;
        float charge=WarriorChargeVisual.scale(entity.getId(),Minecraft.getInstance().getFrameTime());
        if(charge>0){
            float t=active.progress(Minecraft.getInstance().getFrameTime());
            var family=WarriorWeapons.family(stack);
            var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            var profile=com.tnc.tnc.warrior.WeaponChargeRules.profile(key==null?"":key.getPath(),family);
            boolean sweep=profile.arc()>=180;
            float strike=active.hold()?0:(float)Math.sin(Math.PI*Math.min(1,t*1.7f));
            float hand=left?-1:1;
            pose.translate(-.10*hand,.20-strike*.48,-.28);
            pose.mulPose(Axis.XP.rotationDegrees((sweep?-22:-68)+strike*(sweep?22:125)));
            pose.mulPose(Axis.YP.rotationDegrees((sweep?(-48+110*strike):0)*hand));
            pose.mulPose(Axis.ZP.rotationDegrees((sweep?-28+65*strike:8)*hand));
            return;
        }
        if(active.hold())return;
        float partial=Minecraft.getInstance().getFrameTime();
        float t=active.progress(partial),weight=active.weight(partial);
        if(t>=1 || weight<=0)return;
        var family=WarriorWeapons.family(stack);
        float raw=t;
        t=WarriorHeavyMotion.progress(active.animation(),family,false,t);
        boolean heavy=WarriorHeavyMotion.applies(active.animation(),family,false);
        if(heavy){
            float load=WarriorHeavyMotion.brace(raw)*weight;
            pose.translate(0,-load*.55,-Math.max(0,load)*.30);
        }
        var clip=WarriorMartialClips.clip(active.animation(),stack);
        if(clip!=null && WarriorMartialViewMotion.supports(clip.name())) {
            var motion=WarriorMartialViewMotion.sample(clip,t,left);
            pose.translate(motion.x()*weight,motion.y()*weight,motion.z()*weight);
            pose.mulPose(Axis.XP.rotationDegrees(motion.pitch()*weight));
            pose.mulPose(Axis.YP.rotationDegrees(motion.yaw()*weight));
            pose.mulPose(Axis.ZP.rotationDegrees(motion.roll()*weight));
            return;
        }
        float x,y,z;
        if(clip!=null){
            var angles=clip.rotation("armRight",t*clip.lengthSeconds());
            x=angles[0];y=angles[1];z=angles[2];
        }else{
            var angles=family==com.tnc.tnc.warrior.WarriorWeaponFamily.HAMMER
                    ?WarriorHammerGrowth.fallbackPose(active.animation(),t,false)
                    :WarriorPoseMath.of(active.animation(),t,false);
            x=(float)Math.toDegrees(angles.rightArmX());
            y=(float)Math.toDegrees(angles.rightArmY());
            z=(float)Math.toDegrees(angles.rightArmZ());
        }
        float sign=left?-1:1;
        // Bounded angles keep long weapons in view, even for whole-body spinning finishers.
        pose.mulPose(Axis.XP.rotationDegrees(compress(x,heavy?56:42)*weight));
        pose.mulPose(Axis.YP.rotationDegrees(compress(y,35)*weight*sign));
        pose.mulPose(Axis.ZP.rotationDegrees(compress(z,heavy?65:55)*weight*sign));
    }
    static float compress(float degrees,float limit){
        return (float)(limit*Math.tanh(degrees/100.0));
    }
}
