package com.tnc.tnc.magic.free;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

final class DarkMagicEngine {
    static List<ElementalEffects.Effect> spirits(ServerPlayer p){return ElementalEffects.ACTIVE.stream().filter(e->e.owner==p&&e.secondary&&(e.ability()==Ability.SUMMON_WRAITH||e.ability()==Ability.SPIRIT_SWARM)).toList();}
    static String requirement(ServerPlayer p,Intent intent,LivingEntity target){return switch(intent.ability()){
        case SUMMON_WRAITH,SPIRIT_SWARM->spirits(p).size()+(intent.ability()==Ability.SPIRIT_SWARM?3:1)>6?"最多维持六只恶灵，先役灵或献祭已有恶灵。":"";
        case COMMAND_SPIRIT,SACRIFICE->spirits(p).isEmpty()?"你没有可指挥或献祭的恶灵。":"";
        case BLOOD_SACRIFICE->p.getHealth()<=6?"血祭需要生命高于三颗心，不会致死。":"";
        case DEVOUR_CURSE->ElementalStates.get(p,target,Ability.EROSION)==null?"目标没有属于你的侵蚀层数。":"";
        case SHADOW_SWAP->LightningMagicEngine.returnRequirement(p,Ability.SHADOW_SWAP);
        default->"";
    };}
    static boolean tick(ElementalEffects.Effect e){int age=e.age();
        switch(e.ability()){
            case DARK_BALL,DARK_BLADE->{return ElementalEffects.projectile(e);}
            case SUMMON_WRAITH,SPIRIT_SWARM->{if(e.secondary)return spirit(e);if(age==1){int count=e.ability()==Ability.SPIRIT_SWARM?3:1;for(int i=0;i<count;i++){var spirit=new ElementalEffects.Effect(e.owner,e.intent,e.at.add(Math.cos(i*2.1),.5,Math.sin(i*2.1)),e.axis,e.target);spirit.secondary=true;spirit.boost=e.boost;ElementalEffects.QUEUED.add(spirit);spirit.show(ElementalNetwork.ACTIVE);}}return age>1;}
            case COMMAND_SPIRIT->{if(age==1)for(var spirit:spirits(e.owner))spirit.target=e.target;return age>4;}
            case SHADOW_BIND,BLIND,WEAKNESS_CURSE,SLOW_CURSE,EROSION,FEAR,MISFORTUNE,HEAL_LOCK,SILENCE,DELAYED_CURSE,ARMOR_EROSION,EXPOSURE,WEARINESS->{if(age==1&&e.target!=null){var m=ElementalStates.mark(e,e.target,e.ability(),e.duration,e.ability()==Ability.EROSION||e.ability()==Ability.MISFORTUNE||e.ability()==Ability.DELAYED_CURSE?e.intent.damage():e.intent.power());if(m!=null&&e.ability()==Ability.EROSION&&ritual(e.owner,e.target.position()))m.stacks=Math.min(3,m.stacks+1);}return age>6;}
            case PAIN_LINK->{if(age==1&&e.target!=null){ElementalStates.mark(e,e.target,Ability.PAIN_LINK,e.duration,1);for(var t:e.enemies(7).stream().filter(t->t!=e.target).limit(2).toList()){ElementalStates.mark(e,t,Ability.PAIN_LINK,e.duration,1);e.arc(e.target.getBoundingBox().getCenter(),t.getBoundingBox().getCenter());}}return age>12;}
            case DEVOUR_CURSE->{if(age==1&&e.target!=null){var mark=ElementalStates.get(e.owner,e.target,Ability.EROSION);if(mark!=null){int stacks=mark.stacks;ElementalStates.consume(e.owner,e.target,Ability.EROSION);e.damage(e.target,e.intent.damage()*stacks);e.show(ElementalNetwork.IMPACT);}}return age>16;}
            case SHADOW_HIDE->{if(age==1){ElementalStates.mark(e,e.owner,Ability.SHADOW_HIDE,e.duration,e.intent.power());WindMagicEngine.dash(e.owner,5,new Vec3(e.axis.x,0,e.axis.z));}return age>10;}
            case SHADOW_SPIKE->{if(age==18&&LightningMagicEngine.valid(e)){e.damage(e.target);e.push(e.target,e.axis,.2,.5);e.show(ElementalNetwork.IMPACT);}return age>26;}
            case SHADOW_ASSAULT->{if(age==1&&LightningMagicEngine.valid(e)){Vec3 to=e.target.position().subtract(e.owner.position());WindMagicEngine.dash(e.owner,Math.max(0,Math.min(10,to.length()-1.6)),to);if(e.owner.distanceToSqr(e.target)<3*3&&e.owner.hasLineOfSight(e.target))e.damage(e.target);}return age>12;}
            case SHADOW_SWAP->{if(age==1)LightningMagicEngine.returnPoint(e,Ability.SHADOW_SWAP);return age>8;}
            case DARK_RITUAL->{if(age%20==1)for(var spirit:spirits(e.owner))if(spirit.at.distanceToSqr(e.at)<36){spirit.duration=Math.min(spirit.intent.duration()+100,spirit.duration+10);e.arc(e.at,spirit.at);}}
            case SACRIFICE->{if(age==1){var spirits=spirits(e.owner);if(!spirits.isEmpty()){var victim=spirits.get(0);victim.duration=0;victim.show(ElementalNetwork.END);ElementalStates.mark(e,e.owner,Ability.SACRIFICE,e.duration,1.65*e.intent.power());}}return age>10;}
            case BLOOD_SACRIFICE->{if(age==1&&e.owner.getHealth()>6){e.owner.setHealth(e.owner.getHealth()-4);ElementalStates.mark(e,e.owner,Ability.SACRIFICE,e.duration,1.5*e.intent.power());}return age>10;}
            case SOUL_DRAIN->{if(!LightningMagicEngine.valid(e))return true;e.at=e.owner.getEyePosition();if(age%3==1)e.arc(e.at,e.target.getBoundingBox().getCenter());if(age%(e.intent.has(FAST)?10:20)==1){float before=e.target.getHealth();e.damage(e.target);e.owner.heal(Math.min(2,Math.max(0,before-e.target.getHealth())*.3f));}}
            default->throw new IllegalStateException("Unimplemented dark action "+e.ability());
        }return false;
    }
    private static boolean ritual(ServerPlayer p,Vec3 at){return ritualPower(p,at)>0;}
    private static double ritualPower(ServerPlayer p,Vec3 at){return ElementalEffects.ACTIVE.stream().filter(e->e.owner==p&&e.ability()==Ability.DARK_RITUAL&&at.distanceToSqr(e.at)<36*e.intent.scale()*e.intent.scale()).mapToDouble(e->e.intent.power()).max().orElse(0);}
    private static boolean spirit(ElementalEffects.Effect e){
        if(e.target==null||!ElementalTargets.enemy(e.owner,e.target)||e.target.distanceToSqr(e.owner)>28*28)e.target=ElementalTargets.area(e.owner,e.at,12).stream().findFirst().orElse(null);
        Vec3 desired=e.target==null?e.owner.position().add(Math.cos(e.age()*.04+e.id)*2,1.7,Math.sin(e.age()*.04+e.id)*2):e.target.getBoundingBox().getCenter();
        Vec3 delta=desired.subtract(e.at);e.velocity=delta.normalize().scale(Math.min(delta.length(),.16*e.intent.speed()));
        e.at=ElementalTargets.trace(e.owner,e.at,e.at.add(e.velocity)).getLocation();
        if(e.at.distanceToSqr(e.owner.position())>32*32)return true;
        if(e.target!=null&&delta.length()<1.7&&e.age()%(e.intent.has(FAST)?10:20)==1&&ElementalTargets.visible(e.owner,e.at,e.target.getBoundingBox().getCenter())){e.damage(e.target,e.intent.damage()*(float)(1+.25*ritualPower(e.owner,e.at)));if(ritual(e.owner,e.at))ElementalStates.mark(e,e.target,Ability.EROSION,80,1);}
        // Hostile physical missiles disperse a wraith; ownership and summon cap remain server-authoritative.
        for(var projectile:e.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,new AABB(e.at,e.at).inflate(.7)))if(projectile.getOwner() instanceof LivingEntity shooter&&ElementalTargets.enemy(e.owner,shooter)){projectile.discard();e.show(ElementalNetwork.IMPACT);return true;}
        return false;
    }
    private DarkMagicEngine(){}
}
