package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class WarriorSwordUltimate {
    private static final class Cast {
        final Set<Integer> hits=new HashSet<>();
        Double firstYaw;
        WarriorSwordDash.Plan last;
        float lookYaw,lookPitch;
    }
    private static final Map<UUID,Cast> CASTS=new HashMap<>();
    public static WarriorSwordDash.Plan lastPlan(ServerPlayer p){var c=CASTS.get(p.getUUID());return c==null?null:c.last;}
    public static void clear(ServerPlayer p){
        var cast=CASTS.remove(p.getUUID());
        if(cast!=null&&cast.last!=null&&p.isAlive()){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;safeLanding(p);}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CASTS.clear();}
    private static boolean free(ServerPlayer p,Vec3 from,Vec3 at){
        var box=p.getBoundingBox().move(at.subtract(from));
        return at.y>=p.level().getMinBuildHeight()+1&&box.maxY<p.level().getMaxBuildHeight()
                &&p.level().getWorldBorder().isWithinBounds(box)&&!p.level().getBlockCollisions(p,box).iterator().hasNext();
    }
    private static void safeLanding(ServerPlayer p){
        if(p.isNoGravity()||p.getAbilities().flying||p.onGround())return;
        Vec3 from=p.position();var hit=p.level().clip(new net.minecraft.world.level.ClipContext(from.add(0,.05,0),from.add(0,-32,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
            Vec3 land=hit.getLocation().add(0,.025,0);
            if(WarriorSwordDash.clear(from,land,at->free(p,from,at))){p.teleportTo(land.x,land.y,land.z);p.fallDistance=0;return;}
        }
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,320,0));
    }
    public static void tick(ServerPlayer p,int age){
        var cast=CASTS.computeIfAbsent(p.getUUID(),id->new Cast());
        if(cast.last!=null){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.setYRot(cast.lookYaw);p.setXRot(cast.lookPitch);p.yBodyRot=cast.lookYaw;}
        if(age==14||age==22)WarriorUltimateSound.anticipate(p,(age-14)/8f);
        if(age==74)WarriorUltimateSound.sheath(p);
        int index=-1;for(int i=0;i<9;i++)if(age==WarriorSwordDash.tickFor(i)){index=i;break;}
        if(index>=0)dash(p,cast,index);
        if(age==84){
            for(int id:cast.hits)if(p.level().getEntity(id) instanceof LivingEntity target&&WarriorComboService.validTarget(p,target)&&p.distanceTo(target)<48&&p.hasLineOfSight(target)){
                WarriorSwordFx.send(p,target.position(),target.position(),1);WarriorUltimateSound.execution(p,target.position());
                if(target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*2.6f)){target.push(0,.65,0);target.hurtMarked=true;WarriorNamedUltimates.swordFinale(p,target);}
            }
            // Keep the air hold until the finite cast ends, so sheathing cannot drop the player.
            cast.hits.clear();
        }
    }
    private static void dash(ServerPlayer p,Cast cast,int index){
        var targets=WarriorComboService.targets(p,24).stream().filter(t->index!=0||WarriorComboService.inArc(p,t,110)).sorted(Comparator.comparingDouble(p::distanceToSqr)).toList();
        Vec3 from=p.position();WarriorSwordDash.Plan plan=null;LivingEntity focus=null;int attempts=0;int[] checks={6000};
        for(var target:targets){
            if(++attempts>8||checks[0]<=0)break;
            Vec3 anchor=target.position().add(0,Math.min(12,Math.max(0,target.getBbHeight()*.55-p.getBbHeight()*.55)),0);
            double yaw=cast.firstYaw==null?Math.toDegrees(Math.atan2(-(anchor.x-from.x),anchor.z-from.z)):cast.firstYaw;
            plan=WarriorSwordDash.plan(from,anchor,yaw,index,at->checks[0]-->0&&free(p,from,at));
            if(plan!=null){if(cast.firstYaw==null)cast.firstYaw=yaw;focus=target;break;}
        }
        if(plan==null){if(targets.isEmpty())emptyDash(p,index,cast);return;}
        cast.last=plan;
        // The orbit is a light wake, not another strike or another humanoid phantom.
        for(int i=1;i<plan.approach().size();i++)if(plan.approach().get(i).distanceToSqr(plan.approach().get(i-1))>.0001)
            WarriorSwordFx.send(p,plan.approach().get(i-1).add(0,.9,0),plan.approach().get(i).add(0,.9,0),3);
        Vec3 start=plan.strikeFrom().add(0,p.getBbHeight()*.5,0),end=plan.strikeTo().add(0,p.getBbHeight()*.5,0);
        for(var target:targets){var box=target.getBoundingBox().inflate(.85);
            if(!box.contains(start)&&!box.contains(end)&&box.clip(start,end).isEmpty())continue;
            if(target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*1.15f))cast.hits.add(target.getId());
        }
        Vec3 to=plan.strikeTo(),look=focus.getBoundingBox().getCenter().subtract(to.add(0,p.getEyeHeight(),0));
        cast.lookYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));cast.lookPitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
        // Send the target-facing rotation with the teleport, rather than snapping back next tick.
        p.setYRot(cast.lookYaw);p.setXRot(cast.lookPitch);p.yBodyRot=cast.lookYaw;
        p.teleportTo(to.x,to.y,to.z);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        WarriorSwordFx.send(p,plan.strikeFrom(),to,0);WarriorUltimateSound.rush(p,false);
    }
    private static void emptyDash(ServerPlayer p,int index,Cast cast){
        Vec3 from=p.position(),direction=Vec3.directionFromRotation(0,p.getYRot()+index*40);Vec3 to=from;
        for(double d=.2;d<=8;d+=.2){var at=from.add(direction.scale(d));if(!free(p,from,at))break;
            var floor=p.level().clip(new net.minecraft.world.level.ClipContext(at.add(0,.1,0),at.add(0,-1.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS)break;to=at;
        }
        p.teleportTo(to.x,to.y,to.z);WarriorSwordFx.send(p,from,to,0);WarriorUltimateSound.rush(p,false);
    }
}
