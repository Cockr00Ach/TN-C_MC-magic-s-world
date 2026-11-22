package com.tnc.tnc.magic.free;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

final class LightMagicEngine {
    static String requirement(ServerPlayer p,Intent i,LivingEntity t){return i.ability()==Ability.GUARD_LINK&&t==p?"守护链接需要准星指向一位友方。":"";}
    static boolean tick(ElementalEffects.Effect e){int age=e.age();double scale=e.intent.scale();
        switch(e.ability()){
            case LIGHT_BALL,LIGHT_BLADE->{return ElementalEffects.projectile(e);}
            case LASER,PRISM->{Vec3 end=ElementalTargets.trace(e.owner,e.at,e.at.add(e.axis.scale(24))).getLocation();if(age%3==1)e.arc(e.at,end);
                if(age%(e.intent.has(FAST)?10:20)==1){var hits=ElementalTargets.segment(e.owner,e.at,end,.3*scale,Set.of());for(var t:hits.stream().limit(3).toList()){e.damage(t);if(e.intent.has(SHOCK))e.push(t,e.axis,.5,.25);}
                    if(e.ability()==Ability.PRISM&&!hits.isEmpty()){var first=hits.get(0);for(var t:ElementalTargets.area(e.owner,first.position(),7).stream().filter(t->t!=first).limit(2).toList()){e.arc(first.getBoundingBox().getCenter(),t.getBoundingBox().getCenter());e.damage(t,e.intent.damage()*.45f);if(e.intent.has(SHOCK))e.push(t,t.position().subtract(first.position()),.35,.2);}}}}
            case FOCUS_LIGHT->{if(age==3){Vec3 from=e.owner.getEyePosition(),to=ElementalTargets.trace(e.owner,from,from.add(e.owner.getLookAngle().scale(30))).getLocation();e.arc(from,to);for(var t:ElementalTargets.segment(e.owner,from,to,.35*scale,Set.of()).stream().limit(5).toList())e.damage(t);}return age>12;}
            case PURIFY->{if(age==1&&e.target!=null)ElementalStates.cleanse(e.target,e.intent.has(EMPOWER)?4:1);return age>12;}
            case HEAL->{if(age==1&&e.target!=null)e.target.heal(e.intent.damage());return age>12;}
            case LIGHT_BOON,LIGHT_SHIELD->{if(age==1&&e.target!=null)ElementalStates.mark(e,e.target,e.ability(),e.duration,e.ability()==Ability.LIGHT_SHIELD?e.intent.damage():e.intent.power());return age>12;}
            case GUARD_LINK->{if(age==1&&e.target!=null){ElementalStates.unlink(e.owner);var a=ElementalStates.mark(e,e.owner,Ability.GUARD_LINK,e.duration,e.intent.damage());var b=ElementalStates.mark(e,e.target,Ability.GUARD_LINK,e.duration,e.intent.damage());if(a!=null&&b!=null){a.linked=e.target.getUUID();b.linked=e.owner.getUUID();}else ElementalStates.unlink(e.owner);}return age>12;}
            case HEAL_FIELD->{if(age%30==1)for(var t:ElementalTargets.friends(e.owner,e.at,4*scale))t.heal(e.intent.damage());}
            case REVEAL,DAYLIGHT->{if(e.ability()==Ability.DAYLIGHT){e.at=e.owner.position().add(0,3,0);if(age%10==1)TemporaryRock.illuminate(e.owner,net.minecraft.core.BlockPos.containing(e.at));}if(age%20==1)for(var t:e.enemies(12*scale)){t.addEffect(new MobEffectInstance(MobEffects.GLOWING,40,0,false,false));ElementalStates.reveal(t);}}
            case FLASH->{if(age==6)for(var t:e.enemies(4*scale)){ElementalStates.mark(e,t,Ability.BLIND,ElementalStates.controlDuration(t,40),1);e.damage(t);}return age>16;}
            case SUN_MARK->{if(age==1&&e.target!=null)ElementalStates.mark(e,e.target,Ability.SUN_MARK,e.duration,e.intent.damage());return age>12;}
            case EXORCISE->{if(age==5)for(var t:e.enemies(5*scale)){e.damage(t,e.intent.damage()*(t.getMobType()==MobType.UNDEAD||t.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","spirits")))?2.5f:1));e.push(t,t.position().subtract(e.owner.position()),.7,.15);}return age>16;}
            case LIGHT_CURTAIN->WindMagicEngine.deflect(e,true);
            case LIGHT_PATH->{for(var t:ElementalTargets.friends(e.owner,e.at,8*scale))if(e.hits.add(t.getUUID())){ElementalStates.mark(e,t,Ability.LIGHT_PATH,60,1);ElementalStates.mark(e,t,Ability.LIGHT_SHIELD,60,e.intent.damage());}}
            case SANCTUARY->{if(age%20==1)for(var t:ElementalTargets.friends(e.owner,e.at,5*scale)){ElementalStates.cleanse(t,2);t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,25,0,false,true));}}
            case REFLECT_LIGHT->{var stored=ElementalStates.get(e.owner,e.owner,Ability.REFLECT_LIGHT);if(age==1){ElementalStates.mark(e,e.owner,Ability.REFLECT_LIGHT,e.duration+5,0);}if(age>=e.duration-5&&stored!=null){Vec3 from=e.owner.getEyePosition(),to=ElementalTargets.trace(e.owner,from,from.add(e.owner.getLookAngle().scale(24))).getLocation();e.arc(from,to);for(var t:ElementalTargets.segment(e.owner,from,to,.5,Set.of()))e.damage(t,(float)stored.value);ElementalStates.consume(e.owner,e.owner,Ability.REFLECT_LIGHT);return true;}}
            default->throw new IllegalStateException("Unimplemented light action "+e.ability());
        }return false;
    }
    private LightMagicEngine(){}
}
