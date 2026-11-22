package com.tnc.tnc.magic.free;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

final class LightningMagicEngine {
    static String requirement(ServerPlayer p,Intent i,LivingEntity target,Vec3 at){
        if(i.ability()==Ability.OVERLOAD&&ElementalStates.get(p,target,Ability.CONDUCTIVE)==null)return "过载需要你的导电印。";
        if(i.ability()==Ability.MAGNETIC_PULL&&ElementalTargets.area(p,at,10).stream().noneMatch(t->ElementalStates.get(p,t,Ability.CONDUCTIVE)!=null))return "节点附近没有带导电印的敌人。";
        if(i.ability()==Ability.THUNDER_RETURN)return returnRequirement(p,Ability.THUNDER_RETURN);
        return "";
    }
    static String returnRequirement(ServerPlayer p,Ability ability){var m=ElementalStates.get(p,p,ability);return m!=null&&(p.position().distanceToSqr(m.anchor)>24*24||!ElementalTargets.space(p,m.anchor))?"返回印记超出距离或被遮挡。":"";}
    static boolean tick(ElementalEffects.Effect e){int age=e.age();
        switch(e.ability()){
            case THUNDER_BALL,THUNDER_BLADE->{return ElementalEffects.projectile(e);}
            case THUNDER_SPEED,THUNDER_ARMOR,STORE_CHARGE->{if(age==1)ElementalStates.mark(e,e.owner,e.ability(),e.duration,e.ability()==Ability.STORE_CHARGE?1.5*e.intent.power():e.ability()==Ability.THUNDER_ARMOR?e.intent.damage():e.intent.power());return age>5;}
            case PARALYZE,CONDUCTIVE,ELECTRIC_BIND->{if(age==1&&e.target!=null){if(e.ability()==Ability.ELECTRIC_BIND)for(var old:e.enemies(12))ElementalStates.consume(e.owner,old,Ability.ELECTRIC_BIND);ElementalStates.mark(e,e.target,e.ability(),e.duration,e.intent.power());if(e.ability()==Ability.ELECTRIC_BIND)for(var t:e.enemies(6))if(t!=e.target){var link=ElementalStates.mark(e,t,Ability.ELECTRIC_BIND,e.duration,e.intent.power());if(link!=null)link.linked=e.target.getUUID();var main=ElementalStates.get(e.owner,e.target,Ability.ELECTRIC_BIND);if(main!=null&&main.linked==null)main.linked=t.getUUID();}}if(e.ability()==Ability.ELECTRIC_BIND){for(var t:e.enemies(8))if(ElementalStates.get(e.owner,t,Ability.ELECTRIC_BIND)!=null){e.arc(e.at,t.getBoundingBox().getCenter());e.push(t,e.at.subtract(t.position()),.1,0);}}else return age>5;}
            case THUNDER_DASH->{if(age==1)WindMagicEngine.dash(e.owner,10*(e.intent.has(EMPOWER)?1.4:1)*(e.intent.has(FAST)?1.3:1),e.owner.getLookAngle());return age>8;}
            case THUNDER_RETURN->{if(age==1)returnPoint(e,Ability.THUNDER_RETURN);return age>6;}
            case THUNDER_STRIKE->{if(age==12){if(valid(e))strike(e,e.target.getBoundingBox().getCenter(),2*e.intent.scale(),e.intent.damage());}return age>20;}
            case NINE_THUNDERS->{int interval=e.intent.has(FAST)?10:18;if(age%interval==1&&e.step<9){if(e.step<4&&valid(e))e.at=e.target.position();else if(e.step==4&&valid(e))e.at=e.target.position();e.step++;strike(e,e.at,2*e.intent.scale(),e.intent.damage());}return e.step>=9&&age%interval>6;}
            case CHAIN_LIGHTNING->{if(age==1&&valid(e))chain(e,e.target,5,e.intent.damage());return age>16;}
            case OVERLOAD->{if(age==1&&valid(e)){e.damage(e.target,e.intent.damage());ElementalStates.consume(e.owner,e.target,Ability.CONDUCTIVE);chain(e,e.target,4,e.intent.damage()*.35f);}return age>16;}
            case THUNDERCLAP->{if(age==6){strike(e,e.owner.position(),4*e.intent.scale(),e.intent.damage());for(var t:e.enemies(4*e.intent.scale()))e.push(t,t.position().subtract(e.at),1,.5);}return age>18;}
            case THUNDER_STORM->{if(age%(e.intent.has(FAST)?12:24)==1){var targets=e.enemies(5*e.intent.scale());if(!targets.isEmpty())strike(e,targets.get(e.step++%targets.size()).position(),1.4,e.intent.damage());}}
            case ELECTRIC_NET->{if(age%20==1)for(var t:e.enemies(5*e.intent.scale())){ElementalStates.mark(e,t,Ability.ELECTRIC_NET,25,e.intent.power());ElementalStates.mark(e,t,Ability.CONDUCTIVE,100,1);e.damage(t);}}
            case MAGNETIC_PULL->{if(age%4==1)for(var t:e.enemies(10*e.intent.scale()))if(ElementalStates.get(e.owner,t,Ability.CONDUCTIVE)!=null){e.arc(e.at,t.getBoundingBox().getCenter());e.push(t,e.at.subtract(t.position()),.25*e.intent.power(),.03);}}
            case ELECTRIC_PULSE->{for(var t:e.enemies(age*.3*e.intent.scale()))if(e.hits.add(t.getUUID())&&ElementalStates.get(e.owner,t,Ability.CONDUCTIVE)!=null){e.damage(t);e.push(t,t.position().subtract(e.at),.6,.25);ElementalStates.mark(e,t,Ability.PARALYZE,16,1);ElementalStates.consume(e.owner,t,Ability.CONDUCTIVE);}}
            case THUNDER_PIERCE->{if(age==5){Vec3 end=ElementalTargets.trace(e.owner,e.owner.getEyePosition(),e.owner.getEyePosition().add(e.owner.getLookAngle().scale(28))).getLocation();e.arc(e.owner.getEyePosition(),end);for(var t:ElementalTargets.segment(e.owner,e.owner.getEyePosition(),end,.45*e.intent.scale(),Set.of())){e.damage(t);e.push(t,e.axis,.7,.1);}}return age>16;}
            default->throw new IllegalStateException("Unimplemented lightning action "+e.ability());
        }return false;
    }
    static boolean valid(ElementalEffects.Effect e){return e.target!=null&&ElementalTargets.enemy(e.owner,e.target)&&e.owner.hasLineOfSight(e.target)&&e.owner.distanceToSqr(e.target)<28*28;}
    static void returnPoint(ElementalEffects.Effect e,Ability ability){var m=ElementalStates.get(e.owner,e.owner,ability);if(m==null){m=ElementalStates.mark(e,e.owner,ability,e.duration,1);m.anchor=e.owner.position();}else{Vec3 to=m.anchor;if(ElementalTargets.space(e.owner,to)&&e.owner.position().distanceToSqr(to)<24*24){e.owner.connection.teleport(to.x,to.y,to.z,e.owner.getYRot(),e.owner.getXRot());e.owner.fallDistance=0;}ElementalStates.consume(e.owner,e.owner,ability);}}
    static void strike(ElementalEffects.Effect e,Vec3 at,double radius,float damage){e.arc(at.add(0,12,0),at);for(var t:ElementalTargets.area(e.owner,at.add(0,.5,0),radius)){e.damage(t,damage);ElementalStates.mark(e,t,Ability.CONDUCTIVE,80,1);if(e.intent.has(SHOCK))e.push(t,t.position().subtract(at),.7,.5);}e.level.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,net.minecraft.sounds.SoundSource.PLAYERS,.35f,1.5f);}
    static void chain(ElementalEffects.Effect e,LivingEntity first,int count,float damage){LivingEntity current=first;Set<UUID> used=new HashSet<>();Vec3 from=e.owner.getEyePosition();
        for(int n=0;n<count&&current!=null;n++){used.add(current.getUUID());e.arc(from,current.getBoundingBox().getCenter());e.damage(current,damage);from=current.getBoundingBox().getCenter();var options=ElementalTargets.area(e.owner,from,7);current=options.stream().filter(t->!used.contains(t.getUUID())).sorted(Comparator.comparingInt(t->ElementalStates.get(e.owner,t,Ability.CONDUCTIVE)!=null?0:t.isInWaterRainOrBubble()?1:2)).findFirst().orElse(null);}
    }
    private LightningMagicEngine(){}
}
