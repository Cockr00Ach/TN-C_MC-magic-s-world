package com.tnc.tnc.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class DarkGiantSiphonControl {
    private DarkGiantSiphonControl(){}
    public static Vec3 velocity(Vec3 boss,double width,double height,Vec3 victim){
        Vec3 flat=new Vec3(boss.x-victim.x,0,boss.z-victim.z);
        double speed=Math.min(.14,Math.max(0,flat.length()-width*.5-3)*.05);
        Vec3 pull=flat.normalize().scale(speed);
        double targetY=boss.y+Math.min(8,height*.12);
        return new Vec3(pull.x,Math.max(-.06,Math.min(.11,(targetY-victim.y)*.08)),pull.z);
    }
    public static void apply(LivingEntity boss,LivingEntity victim){
        if(com.tnc.tnc.equipment.PurityAmulet.equipped(victim))return;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,4,10,false,false,false));
        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION,4,0,false,false,false));
        victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,30,0,false,false,false));
        victim.setSprinting(false);victim.fallDistance=0;
        victim.setDeltaMovement(velocity(boss.position(),boss.getBbWidth(),boss.getBbHeight(),victim.position()));
        victim.hurtMarked=true;
    }
}
