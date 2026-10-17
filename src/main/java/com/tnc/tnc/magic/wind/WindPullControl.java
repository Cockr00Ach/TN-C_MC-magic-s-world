package com.tnc.tnc.magic.wind;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Resolve after AI resets velocity, without teleporting through terrain or changing boss attributes. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WindPullControl {
    private static final Map<Level,Map<LivingEntity,Vec3>> PENDING=new WeakHashMap<>();
    public static void request(LivingEntity victim,Vec3 force){
        var targets=PENDING.computeIfAbsent(victim.level(),k->new LinkedHashMap<>());
        if(targets.size()<96||targets.containsKey(victim))targets.merge(victim,force,(a,b)->a.lengthSqr()>b.lengthSqr()?a:b);
    }
    public static void cancel(LivingEntity victim){
        var targets=PENDING.get(victim.level());if(targets!=null)targets.remove(victim);
    }
    @SubscribeEvent public static void end(TickEvent.LevelTickEvent event){
        if(event.phase==TickEvent.Phase.END&&!event.level.isClientSide)resolve(event.level);
    }
    public static void resolve(Level level){
        var targets=PENDING.remove(level);if(targets==null)return;
        targets.forEach((victim,force)->{
            if(!victim.isAlive()||victim.level()!=level)return;
            if(victim instanceof Mob mob)mob.getNavigation().stop();
            // A bounded collision-resolved step also works on full knockback resistance and casting bosses.
            victim.move(MoverType.SELF,force.scale(.55));
            victim.setDeltaMovement(force);victim.hurtMarked=true;victim.fallDistance=0;
        });
    }
}
