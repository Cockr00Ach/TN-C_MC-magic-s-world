package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.warrior.WarriorGripGeometry;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Grows the actual item mount; the following trail/ornament draws inherit the same matrix. */
public final class WarriorHammerGrowthRenderer {
    private WarriorHammerGrowthRenderer() {}
    static boolean active(AbstractClientPlayer player){
        var a=WarriorAnimationClient.active(player.getId());
        return a!=null && WarriorHeavyGrowth.applies(a.animation(),WarriorWeapons.family(player.getMainHandItem()),a.hold());
    }
    public static void apply(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose){
        if(!(entity instanceof AbstractClientPlayer player) || left!=(player.getMainArm()==HumanoidArm.LEFT) && !com.tnc.tnc.bronze.BronzeTwinWeapon.active(player)
                || !(context.firstPerson() || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))return;
        var a=WarriorAnimationClient.active(player.getId());
        var mc=Minecraft.getInstance();
        var family=WarriorWeapons.family(stack);
        float charged=WarriorChargeVisual.scale(player.getId(),mc.getFrameTime());
        if(charged<=0 && !WarriorHeavyGrowth.usesEntityGrowth(family))return;
        int points=player==mc.player?TemperingClient.points():WarriorPhantomScales.points(player.getUUID());
        float peak=WarriorHeavyGrowth.peak(points);
        float scale;
        if(charged>0)scale=charged;
        else
        if(a!=null && (a.hold() || a.progress(mc.getFrameTime())<1)){
            float t=WarriorHeavyMotion.progress(a.animation(),family,a.hold(),a.progress(mc.getFrameTime()));
            scale=WarriorHeavyGrowth.scale(a.animation(),family,a.hold(),t,peak);
        }else{
            // Vanilla attacks can start before the server animation arrives, including swings in air.
            float swing=player.getAttackAnim(mc.getFrameTime());
            scale=WarriorHeavyGrowth.scale(com.tnc.tnc.warrior.WarriorAnimation.SLASH_HORIZONTAL,family,false,swing,peak);
        }
        if(scale<=1.00001f)return;
        var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(key==null)return;
        var grip=WarriorModelGrip.find(key).orElse(new Vec3(3/16.0,3/16.0,.5));
        var model=mc.getItemRenderer().getModel(stack,player.level(),player,player.getId()+context.ordinal());
        PoseStack displayed=new PoseStack();
        model.getTransforms().getTransform(context).apply(left,displayed);
        var anchor=WarriorGripGeometry.anchor(displayed.last().pose(),(float)grip.x,(float)grip.y,(float)grip.z);
        var correction=WarriorGripGeometry.compensation(anchor,scale);
        pose.translate(correction.x(),correction.y(),correction.z());
        pose.scale(scale,scale,scale);
    }
}
