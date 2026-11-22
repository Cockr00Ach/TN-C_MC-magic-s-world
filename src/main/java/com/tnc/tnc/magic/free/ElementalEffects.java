package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.Element;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

/** Server-owned effects. An ability delegates to its element's behavior, never a recolored water attack. */
final class ElementalEffects {
    static final List<Effect> ACTIVE=new ArrayList<>(),QUEUED=new ArrayList<>();
    static final class Effect {
        final long id=ElementalRuntime.nextId();final ServerPlayer owner;final ServerLevel level;final ItemStack wand;
        final Intent intent;final long started;final Set<UUID> viewers=new HashSet<>(),hits=new HashSet<>();
        LivingEntity target;final Vec3 anchorOffset;Vec3 at,axis,velocity,origin;int duration,step;double stored;float boost=1;boolean secondary;
        Effect(ServerPlayer owner,Intent intent,Vec3 at,Vec3 direction,LivingEntity target){
            this.owner=owner;level=owner.serverLevel();wand=owner.getMainHandItem();this.intent=intent;this.at=at;origin=at;
            axis=direction.normalize();velocity=axis.scale((intent.ability().element()==Element.EARTH?.32:.5)*intent.speed());
            this.target=target;anchorOffset=at.subtract(owner.position());duration=intent.duration();started=level.getGameTime();
        }
        Ability ability(){return intent.ability();} int age(){return (int)(level.getGameTime()-started);}
        void show(int phase){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),id,phase,ability().ordinal(),intent.flags(),intent.chantPower(),at,velocity,started,duration,target==null?-1:target.getId()));}
        void arc(Vec3 from,Vec3 to){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),ElementalRuntime.nextId(),ElementalNetwork.ARC,ability().ordinal(),intent.flags(),intent.chantPower(),from,to.subtract(from),level.getGameTime(),10,-1));}
        List<LivingEntity> enemies(double radius){return ElementalTargets.area(owner,at,radius);}
        void damage(LivingEntity target,float amount){if(ElementalTargets.enemy(owner,target))target.hurt(level.damageSources().indirectMagic(owner,owner),amount*boost);}
        void damage(LivingEntity target){damage(target,intent.damage());}
        void push(LivingEntity target,Vec3 direction,double force,double lift){ElementalStates.force(owner,target,direction,force*(intent.has(SHOCK)?3:1),lift*(intent.has(SHOCK)?2:1));}
    }
    static int count(ServerPlayer p){return (int)ACTIVE.stream().filter(e->e.owner==p).count();}
    static String requirement(ServerPlayer p,Intent intent,LivingEntity target,Vec3 at){return switch(intent.ability().element()){
        case WIND->WindMagicEngine.requirement(p,intent);case EARTH->EarthMagicEngine.requirement(p,intent,at);
        case LIGHTNING->LightningMagicEngine.requirement(p,intent,target,at);case DARK->DarkMagicEngine.requirement(p,intent,target);
        case LIGHT->LightMagicEngine.requirement(p,intent,target);default->"";};}
    static void launch(ElementalRuntime.Charge c,int index){
        var p=c.placements.get(index);var direction=c.owner.getEyePosition().add(c.owner.getLookAngle().scale(40)).subtract(p.at());
        var e=new Effect(c.owner,c.intent,p.at(),direction,p.target());e.boost=c.boost;ACTIVE.add(e);e.show(ElementalNetwork.ACTIVE);
        e.level.playSound(null,e.owner.blockPosition(),e.ability().element()==Element.EARTH?SoundEvents.STONE_BREAK:SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.8f,e.intent.has(EMPOWER)?.6f:1.1f);
    }
    static void tick(){
        for(var it=ACTIVE.iterator();it.hasNext();){var e=it.next();
            boolean end=!FreeMagicServer.canRemain(e.owner)||e.owner.level()!=e.level||e.age()>=e.duration;
            if(!end&&e.age()==1&&e.target!=null)end=!(e.ability().target==Target.FRIEND?ElementalTargets.friend(e.owner,e.target):ElementalTargets.enemy(e.owner,e.target));
            if(!end&&e.ability().channel()){
                end=!FreeMagicServer.canAct(e.owner)||e.owner.getMainHandItem()!=e.wand;
                if(!end&&e.age()%20==0)end=!ElementalRuntime.pay(e.owner,Math.max(1,e.intent.cost()/3)*(e.intent.has(FAST)?2:1));
                if(e.ability().target==Target.CHANNEL_SELF)e.at=e.owner.position();
                else if(e.ability().target==Target.CHANNEL)e.at=ElementalTargets.trace(e.owner,e.owner.getEyePosition(),e.owner.position().add(e.anchorOffset)).getLocation();
                e.axis=e.owner.getLookAngle();e.velocity=e.axis;
            }
            if(!end)end=switch(e.ability().element()){
                case WIND->WindMagicEngine.tick(e);case EARTH->EarthMagicEngine.tick(e);case LIGHTNING->LightningMagicEngine.tick(e);case LIGHT->LightMagicEngine.tick(e);case DARK->DarkMagicEngine.tick(e);default->true;};
            if(end){e.show(ElementalNetwork.END);it.remove();}else if(e.age()%3==0)e.show(ElementalNetwork.ACTIVE);
        }
        ACTIVE.addAll(QUEUED);QUEUED.clear();ElementalStates.tick();TemporaryRock.tickAll();WindMagicEngine.refreshGrounded();
    }
    static boolean projectile(Effect e){
        Vec3 end=e.at.add(e.velocity);var block=ElementalTargets.trace(e.owner,e.at,end);
        var struck=ElementalTargets.segment(e.owner,e.at,block.getLocation(),.45*e.intent.scale(),Set.of());
        boolean blade=e.ability().name().endsWith("BLADE");
        for(var t:struck)if(e.hits.add(t.getUUID())){
            e.damage(t);e.push(t,e.velocity,.5,.15);
            if(e.ability().element()==Element.LIGHTNING)ElementalStates.mark(e,t,Ability.CONDUCTIVE,120,1);
            if(e.ability().element()==Element.DARK)ElementalStates.mark(e,t,Ability.EROSION,100,1);
            if(!blade){e.at=t.getBoundingBox().getCenter();for(var other:e.enemies(1.5*e.intent.scale()))if(other!=t)e.damage(other,e.intent.damage()*.45f);e.show(ElementalNetwork.IMPACT);return true;}
            if(e.hits.size()>=5)return true;
        }
        e.at=block.getLocation();if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){e.show(ElementalNetwork.IMPACT);return true;}return false;
    }
    static void stopChannels(ServerPlayer owner){ACTIVE.removeIf(e->{if(e.owner==owner&&e.ability().channel()){e.show(ElementalNetwork.END);return true;}return false;});}
    static void remove(UUID owner){ACTIVE.removeIf(e->{if(e.owner.getUUID().equals(owner)){e.show(ElementalNetwork.END);return true;}return false;});ElementalStates.removeOwner(owner);WindMagicEngine.remove(owner);}
    static void clear(){for(var e:ACTIVE)e.show(ElementalNetwork.END);ACTIVE.clear();QUEUED.clear();ElementalStates.clear();WindMagicEngine.clear();}
    private ElementalEffects(){}
}
