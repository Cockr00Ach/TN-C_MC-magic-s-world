package com.tnc.tnc.magic.free;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

final class WindMagicEngine {
    private static final Set<UUID> AIR_JUMPS=new HashSet<>();
    private static final Map<UUID,ServerPlayer> JUMPERS=new HashMap<>();
    static void refreshGrounded(){JUMPERS.entrySet().removeIf(entry->{var p=entry.getValue();if(EarthMagicEngine.supported(p)||!p.isAlive()||p.hasDisconnected()){AIR_JUMPS.remove(entry.getKey());return true;}return false;});}
    static String requirement(ServerPlayer p,Intent i){if(EarthMagicEngine.supported(p))AIR_JUMPS.remove(p.getUUID());if(i.ability()==Ability.TAKEOFF&&!EarthMagicEngine.supported(p))return "起飞需要脚下有支撑，空中可使用一次跃风。";return i.ability()==Ability.AIR_JUMP&&(EarthMagicEngine.supported(p)||AIR_JUMPS.contains(p.getUUID()))?"跃风需要腾空；每次落地刷新一次。":"";}
    static boolean tick(ElementalEffects.Effect e){
        int age=e.age();double scale=e.intent.scale();
        if(e.owner.onGround())AIR_JUMPS.remove(e.owner.getUUID());
        switch(e.ability()){
            case WIND_BALL,WIND_BLADE-> {return ElementalEffects.projectile(e);}
            case RETURN_WIND->{if(age==25){e.axis=e.owner.getEyePosition().subtract(e.at).normalize();e.velocity=e.axis.scale(.55*e.intent.speed());e.hits.clear();}return ElementalEffects.projectile(e);}
            case TAKEOFF,AIR_JUMP->{if(age==1){if(e.ability()==Ability.AIR_JUMP)AIR_JUMPS.add(e.owner.getUUID());JUMPERS.put(e.owner.getUUID(),e.owner);lift(e.owner,Math.min(1.3,(e.ability()==Ability.TAKEOFF?.95:.65)*Math.sqrt(e.intent.power())));e.owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,e.intent.duration()+40,0,false,true));}return age>10;}
            case WIND_LIFT->{if(age==1&&e.target!=null){lift(e.target,Math.min(1.2,.6*Math.sqrt(e.intent.power())));e.target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,100,0,false,true));}return age>12;}
            case WIND_STEP->{if(age==1)dash(e.owner,8*(e.intent.has(EMPOWER)?1.3:1)*(e.intent.has(FAST)?1.4:1),e.owner.getLookAngle());return age>10;}
            case GLIDE->{e.at=e.owner.position();var v=e.owner.getDeltaMovement();Vec3 look=e.owner.getLookAngle();e.owner.setDeltaMovement(v.x*.7+look.x*.09*Math.sqrt(e.intent.power()),Math.max(-.1,v.y),v.z*.7+look.z*.09*Math.sqrt(e.intent.power()));e.owner.fallDistance=0;e.owner.hurtMarked=true;}
            case GATHER,TORNADO->{if(e.ability()==Ability.TORNADO){Vec3 next=ElementalTargets.trace(e.owner,e.at.add(0,.5,0),e.at.add(e.axis.scale(.08))).getLocation();ElementalTargets.groundNear(e.owner,next).ifPresent(p->e.at=p);}for(var t:e.enemies(5*scale))if(age%4==0){e.push(t,e.at.subtract(t.position()),.16,e.ability()==Ability.TORNADO?.32:0);if(age%20==0)e.damage(t);}}
            case UPDRAFT,LAUNCH->{if(age==8)for(var t:e.enemies(3*scale)){e.damage(t);e.push(t,t.position().subtract(e.owner.position()),.7,e.ability()==Ability.UPDRAFT?1.05:.75);}return age>18;}
            case WIND_PRESSURE->{Vec3 next=e.at.add(e.velocity);var hit=ElementalTargets.trace(e.owner,e.at,next);for(var t:ElementalTargets.segment(e.owner,e.at,hit.getLocation(),2*scale,Set.of()))if(e.hits.add(t.getUUID())){e.damage(t);e.push(t,e.axis,1.2,.3);}e.at=hit.getLocation();return hit.getType()!=HitResult.Type.MISS;}
            case TAILWIND->{if(age%10==1)for(var ally:ElementalTargets.friends(e.owner,e.at,6*scale))ElementalStates.mark(e,ally,Ability.TAILWIND,25,e.intent.power());}
            case HEADWIND->{if(age%5==1)for(var t:e.enemies(5*scale)){ElementalStates.mark(e,t,Ability.HEADWIND,20,e.intent.power());e.push(t,e.axis.scale(-1),.18,0);}}
            case WIND_WALL->{deflect(e,false);}
            case VACUUM->{for(var t:e.enemies(4*scale)){if(age<30&&age%4==0)e.push(t,e.at.subtract(t.position()),.25,0);if(age==32){e.damage(t);e.push(t,t.position().subtract(e.at),1.1,.65);}}if(age==32)e.show(ElementalNetwork.IMPACT);return age>40;}
            case WIND_CORRIDOR->{if(age%(e.intent.has(FAST)?10:20)==1)for(var t:e.enemies(10)){Vec3 off=t.position().subtract(e.at);double along=off.dot(e.axis);if(along>=0&&along<=10&&off.subtract(e.axis.scale(along)).length()<1.2*scale){e.damage(t);e.arc(e.at,e.at.add(e.axis.scale(10)));}}}
            case DOWNDRAFT->{if(e.target==null||!ElementalTargets.enemy(e.owner,e.target))return true;if(e.target.onGround()){e.damage(e.target);e.show(ElementalNetwork.IMPACT);return true;}e.target.setDeltaMovement(e.target.getDeltaMovement().add(0,ElementalStates.boss(e.target)?-.1:-.35,0));e.target.hurtMarked=true;}
            case AIR_BURST->{if(age==6){for(var t:e.enemies(4*scale)){e.damage(t);e.push(t,t.position().subtract(e.at),1.3,.5);}e.show(ElementalNetwork.IMPACT);}return age>16;}
            default->throw new IllegalStateException("Unimplemented wind action "+e.ability());
        }return false;
    }
    static void lift(LivingEntity entity,double speed){entity.setDeltaMovement(entity.getDeltaMovement().add(0,speed,0));entity.hurtMarked=true;entity.fallDistance=0;}
    static boolean dash(ServerPlayer p,double distance,Vec3 direction){
        Vec3 origin=p.position(),last=origin,d=direction.normalize();
        for(double step=.25;step<=distance;step+=.25){Vec3 next=origin.add(d.scale(step));if(!ElementalTargets.space(p,next))break;last=next;}
        if(last.distanceToSqr(origin)<.2)return false;p.connection.teleport(last.x,last.y,last.z,p.getYRot(),p.getXRot());p.fallDistance=0;return true;
    }
    static void deflect(ElementalEffects.Effect e,boolean weaken){
        for(var p:e.level.getEntitiesOfClass(Projectile.class,new AABB(e.at,e.at).inflate(4*e.intent.scale()*Math.sqrt(e.intent.power())))){
            if(!(p instanceof AbstractArrow||p instanceof ThrowableItemProjectile)||!(p.getOwner() instanceof LivingEntity owner)||!ElementalTargets.enemy(e.owner,owner)||e.hits.contains(p.getUUID()))continue;
            if(Math.abs(p.position().subtract(e.at).dot(e.axis))>1.2)continue;e.hits.add(p.getUUID());
            if(weaken&&p instanceof AbstractArrow arrow)arrow.setBaseDamage(arrow.getBaseDamage()*(e.intent.has(EMPOWER)?.2:.4));
            p.setDeltaMovement(p.getDeltaMovement().scale(weaken?.5:-.5).add(0,weaken?0:.3,0));p.hurtMarked=true;e.arc(e.at,p.position());
        }
    }
    static void remove(UUID id){AIR_JUMPS.remove(id);JUMPERS.remove(id);}static void clear(){AIR_JUMPS.clear();JUMPERS.clear();}
    private WindMagicEngine(){}
}
