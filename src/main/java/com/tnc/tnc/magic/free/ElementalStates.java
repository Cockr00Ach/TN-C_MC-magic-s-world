package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.Element;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

/** Owned finite states, with a single strongest attribute modifier and bounded event propagation. */
@Mod.EventBusSubscriber(modid="tnc")
public final class ElementalStates {
    public static final MobEffect SILENCE_EFFECT=new MobEffect(MobEffectCategory.HARMFUL,0x604070){};
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ElementalStateRegistry {@SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent e){e.register(net.minecraft.core.registries.Registries.MOB_EFFECT,h->h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","free_magic_silence"),SILENCE_EFFECT));}}
    public static boolean silenced(LivingEntity target){return target.hasEffect(SILENCE_EFFECT);}
    static void reveal(LivingEntity target){for(var m:on(target))if(m.ability==Ability.SHADOW_HIDE){consume(m.owner,target,Ability.SHADOW_HIDE);target.removeEffect(MobEffects.INVISIBILITY);}}
    static void unlink(ServerPlayer owner){for(var m:new ArrayList<>(MARKS.values()))if(m.owner==owner&&m.ability==Ability.GUARD_LINK)consume(owner,m.target,Ability.GUARD_LINK);}
    record Key(UUID owner,UUID target,Ability ability){}
    static final class Mark {
        final long id=ElementalRuntime.nextId();final ServerPlayer owner;final LivingEntity target;final ServerLevel level;final Ability ability;final Set<UUID> viewers=new HashSet<>();
        long end;int stacks=1;double value;Vec3 anchor;UUID linked;
        Mark(ElementalEffects.Effect e,LivingEntity target,Ability ability,int duration,double value){owner=e.owner;level=e.level;this.target=target;this.ability=ability;end=level.getGameTime()+duration;this.value=value;anchor=target.position();}
        void show(int phase){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),id,phase,ability.ordinal(),0,stacks,anchor,Vec3.ZERO,level.getGameTime(),Math.max(1,(int)(end-level.getGameTime())),target.getId()));}
    }
    private static final Map<Key,Mark> MARKS=new LinkedHashMap<>();
    private record Control(long last,int count){}
    private static final Map<UUID,Control> CONTROLS=new HashMap<>();
    private static boolean propagating;
    static Mark get(ServerPlayer owner,LivingEntity target,Ability ability){if(target==null)return null;var mark=MARKS.get(new Key(owner.getUUID(),target.getUUID(),ability));return mark!=null&&mark.end>mark.level.getGameTime()?mark:null;}
    static boolean has(LivingEntity target,Ability ability){return MARKS.values().stream().anyMatch(m->m.target==target&&m.ability==ability&&m.end>m.level.getGameTime());}
    static List<Mark> on(LivingEntity target){return MARKS.values().stream().filter(m->m.target==target&&m.end>m.level.getGameTime()).toList();}
    static Mark mark(ElementalEffects.Effect e,LivingEntity target,Ability ability,int duration,double value){
        if(!target.isAlive()||target.level()!=e.level)return null;
        if(e.intent.has(EMPOWER))duration=(int)(duration*1.5);if(e.boost>1){duration=(int)(duration*Math.sqrt(e.boost));value*=e.boost;}
        if(isControl(ability))duration=controlDuration(target,duration);
        var key=new Key(e.owner.getUUID(),target.getUUID(),ability);var m=MARKS.get(key);
        if(m==null){m=new Mark(e,target,ability,duration,value);MARKS.put(key,m);}
        else {m.end=Math.max(m.end,e.level.getGameTime()+duration);m.value=Math.max(m.value,value);if(ability==Ability.EROSION)m.stacks=Math.min(3,m.stacks+1);}
        if(ability==Ability.SILENCE)target.addEffect(new MobEffectInstance(SILENCE_EFFECT,duration,0,false,true));
        m.show(ElementalNetwork.STATUS);attributes(target);return m;
    }
    static void consume(ServerPlayer owner,LivingEntity target,Ability ability){var m=MARKS.remove(new Key(owner.getUUID(),target.getUUID(),ability));if(m!=null){m.show(ElementalNetwork.END);cleanupCarrier(target,ability);attributes(target);}}
    static boolean boss(LivingEntity t){return !t.canChangeDimensions()||t.getMaxHealth()>=100||t.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge","bosses")));}
    static int controlDuration(LivingEntity target,int ticks){long now=target.level().getGameTime();var old=CONTROLS.get(target.getUUID());int count=old==null||now-old.last>160?0:Math.min(4,old.count+1);CONTROLS.put(target.getUUID(),new Control(now,count));return Math.max(4,(int)(ticks*(boss(target)?.25:target instanceof Player?.55:1)/(1+count*.6)));}
    private static boolean isControl(Ability a){return a==Ability.PARALYZE||a==Ability.SHADOW_BIND||a==Ability.ROCK_GRASP||a==Ability.FEAR||a==Ability.BLIND||a==Ability.SILENCE;}
    static void force(ServerPlayer owner,LivingEntity target,Vec3 direction,double force,double lift){
        if(!ElementalTargets.enemy(owner,target))return;
        double resistance=target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),strength=(1-resistance)*(boss(target)?.18:1);
        Vec3 flat=new Vec3(direction.x,0,direction.z).normalize().scale(Math.min(1.8,force)*strength);
        var previous=target.getDeltaMovement();target.setDeltaMovement(new Vec3(Math.max(-1.8,Math.min(1.8,previous.x+flat.x)),Math.min(1.15,Math.max(previous.y,lift*strength)),Math.max(-1.8,Math.min(1.8,previous.z+flat.z))));target.hurtMarked=true;
    }
    static float attackPower(ServerPlayer owner,Element element){Ability a=element==Element.LIGHTNING?Ability.STORE_CHARGE:element==Element.DARK?Ability.SACRIFICE:null;if(a==null)return 1;var m=get(owner,owner,a);if(m==null)return 1;float power=(float)m.value;consume(owner,owner,a);return power;}
    static void tick(){
        for(var m:new ArrayList<>(MARKS.values())){
            long now=m.level.getGameTime();
            if(now>=m.end||!m.target.isAlive()||!m.owner.isAlive()||m.owner.hasDisconnected()||m.target.level()!=m.level||m.owner.level()!=m.level){
                if(m.ability==Ability.DELAYED_CURSE&&m.owner.level()==m.level)explode(m,m.target.isAlive()?1:.45f);
                MARKS.remove(new Key(m.owner.getUUID(),m.target.getUUID(),m.ability));m.show(ElementalNetwork.END);cleanupCarrier(m.target,m.ability);attributes(m.target);continue;
            }
            if(m.ability!=Ability.THUNDER_RETURN&&m.ability!=Ability.SHADOW_SWAP)m.anchor=m.target.position();if(now%10==0)m.show(ElementalNetwork.STATUS);
            if(m.ability==Ability.EROSION&&now%20==0)m.target.hurt(m.level.damageSources().indirectMagic(m.owner,m.owner),(float)m.value*m.stacks);
            if(m.ability==Ability.PARALYZE||m.ability==Ability.SHADOW_BIND||m.ability==Ability.ROCK_GRASP){var v=m.target.getDeltaMovement();m.target.setDeltaMovement(v.multiply(.15,1,.15));m.target.hurtMarked=true;if(m.target instanceof Mob mob)mob.getNavigation().stop();}
            if((m.ability==Ability.FEAR||m.ability==Ability.BLIND)&&m.target instanceof Mob mob){mob.setTarget(null);if(m.ability==Ability.FEAR&&!boss(m.target)&&now%10==0){Vec3 flee=m.target.position().subtract(m.owner.position()).normalize().scale(7).add(m.target.position());mob.getNavigation().moveTo(flee.x,flee.y,flee.z,1.1);}}
            if(m.ability==Ability.BLIND&&m.target instanceof Player)m.target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,15,0,false,false));
            if(m.ability==Ability.SHADOW_HIDE){if(m.level.getMaxLocalRawBrightness(m.target.blockPosition())>=13){m.end=now;continue;}m.target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,12,0,false,false));}
            if(m.ability==Ability.GUARD_LINK&&m.linked!=null){var partner=m.level.getEntity(m.linked);if(partner==null||!partner.isAlive()||partner.distanceToSqr(m.target)>18*18||!(partner instanceof LivingEntity living)||get(m.owner,living,Ability.GUARD_LINK)==null||!m.target.getUUID().equals(get(m.owner,living,Ability.GUARD_LINK).linked))m.end=now;}
            if(m.ability==Ability.ELECTRIC_BIND&&m.linked!=null){var partner=m.level.getEntity(m.linked);if(!(partner instanceof LivingEntity living)||!partner.isAlive()||partner.distanceToSqr(m.target)>8*8||get(m.owner,living,Ability.ELECTRIC_BIND)==null){m.target.hurt(m.level.damageSources().indirectMagic(m.owner,m.owner),4);m.end=now;m.show(ElementalNetwork.IMPACT);}}

        }
        if(!MARKS.isEmpty()&&MARKS.values().iterator().next().level.getGameTime()%200==0)CONTROLS.entrySet().removeIf(e->MARKS.values().stream().noneMatch(m->m.target.getUUID().equals(e.getKey())));
    }
    private static void explode(Mark m,float scale){for(var t:ElementalTargets.area(m.owner,m.anchor.add(0,.5,0),2.5))t.hurt(m.level.damageSources().indirectMagic(m.owner,m.owner),(float)m.value*scale);m.show(ElementalNetwork.IMPACT);}
    static void cleanse(LivingEntity target){cleanse(target,1);}
    static void cleanse(LivingEntity target,int limit){
        int remaining=limit;
        for(var ability:on(target).stream().filter(m->negative(m.ability)).map(m->m.ability).distinct().toList()){
            if(remaining--<=0)break;
            for(var m:on(target))if(m.ability==ability)consume(m.owner,target,ability);
            cleanupCarrier(target,ability);
        }
        var types=java.util.List.of(MobEffects.POISON,MobEffects.WITHER,MobEffects.BLINDNESS,MobEffects.MOVEMENT_SLOWDOWN,MobEffects.WEAKNESS,SILENCE_EFFECT,com.tnc.tnc.magic.TNEffects.CHAOS_SILENCE.get());
        for(var type:types)if(remaining>0&&target.removeEffect(type))remaining--;
        attributes(target);
    }
    private static void cleanupCarrier(LivingEntity target,Ability ability){
        if(has(target,ability))return;
        if(ability==Ability.SILENCE)target.removeEffect(SILENCE_EFFECT);
        if(ability==Ability.BLIND&&target.hasEffect(MobEffects.BLINDNESS)&&target.getEffect(MobEffects.BLINDNESS).getDuration()<=15)target.removeEffect(MobEffects.BLINDNESS);
        if(ability==Ability.SHADOW_HIDE&&target.hasEffect(MobEffects.INVISIBILITY)&&target.getEffect(MobEffects.INVISIBILITY).getDuration()<=12)target.removeEffect(MobEffects.INVISIBILITY);
    }
    static boolean negative(Ability a){return a.element()==Element.DARK&&a.target!=Target.SELF||a==Ability.PARALYZE||a==Ability.CONDUCTIVE||a==Ability.ROCK_GRASP||a==Ability.ELECTRIC_BIND;}
    private static final UUID SPEED_ID=UUID.fromString("fc94b424-26a5-4c38-85f4-7d768e498511"),ARMOR_ID=UUID.fromString("fc94b424-26a5-4c38-85f4-7d768e498512"),ATTACK_ID=UUID.fromString("fc94b424-26a5-4c38-85f4-7d768e498513"),RATE_ID=UUID.fromString("fc94b424-26a5-4c38-85f4-7d768e498514"),RESIST_ID=UUID.fromString("fc94b424-26a5-4c38-85f4-7d768e498515");
    private static void attribute(LivingEntity target,Attribute type,UUID id,double value,AttributeModifier.Operation operation){var instance=target.getAttribute(type);if(instance==null)return;instance.removeModifier(id);if(value!=0)instance.addTransientModifier(new AttributeModifier(id,"tnc.elemental",value,operation));}
    private static void attributes(LivingEntity t){double speed=0,slow=0,armor=0,erosion=0,attack=0,weak=0,rate=0,resist=0;
        for(var m:on(t))switch(m.ability){
            case THUNDER_SPEED->speed=Math.max(speed,.65*m.value);case TAILWIND,SHADOW_HIDE,LIGHT_PATH->speed=Math.max(speed,.25*m.value);
            case SLOW_CURSE,WEARINESS,ROCK_ARRAY,QUICKSAND,ELECTRIC_NET,HEADWIND,ELECTRIC_BIND->slow=Math.max(slow,Math.min(.75,.3*m.value));
            case ROOTED->{slow=Math.max(slow,.75);resist=1;}case STONE_ARMOR->{armor=Math.max(armor,8*m.value);slow=Math.max(slow,.12);}
            case ARMOR_EROSION->erosion=Math.max(erosion,Math.min(8,4*m.value));case LIGHT_BOON->attack=Math.max(attack,.2*m.value);
            case WEAKNESS_CURSE->weak=Math.max(weak,Math.min(.6,.25*m.value));default->{}
        }
        if(has(t,Ability.WEARINESS)||has(t,Ability.SLOW_CURSE))rate=-.35;
        attribute(t,Attributes.MOVEMENT_SPEED,SPEED_ID,Math.min(1.5,speed)-slow,AttributeModifier.Operation.MULTIPLY_TOTAL);
        attribute(t,Attributes.ARMOR,ARMOR_ID,Math.min(16,armor)-erosion,AttributeModifier.Operation.ADDITION);
        attribute(t,Attributes.ATTACK_DAMAGE,ATTACK_ID,attack-weak,AttributeModifier.Operation.MULTIPLY_TOTAL);
        attribute(t,Attributes.ATTACK_SPEED,RATE_ID,rate,AttributeModifier.Operation.MULTIPLY_TOTAL);
        attribute(t,Attributes.KNOCKBACK_RESISTANCE,RESIST_ID,resist,AttributeModifier.Operation.ADDITION);
    }
    @SubscribeEvent public static void healed(LivingHealEvent event){double power=on(event.getEntity()).stream().filter(m->m.ability==Ability.HEAL_LOCK).mapToDouble(m->m.value).max().orElse(0);if(power>0)event.setAmount(event.getAmount()*(float)Math.max(.1,1-.65*power));}
    @SubscribeEvent public static void hurt(LivingHurtEvent event){
        if(event.getEntity().level().isClientSide||propagating||event.getAmount()<=0)return;
        LivingEntity target=event.getEntity();var attacker=event.getSource().getEntity();
        if(attacker instanceof LivingEntity living){for(var m:on(living))if(m.ability==Ability.SHADOW_HIDE){m.end=m.level.getGameTime();living.removeEffect(MobEffects.INVISIBILITY);}}
        double exposure=on(target).stream().filter(m->m.ability==Ability.EXPOSURE).mapToDouble(m->m.value).max().orElse(0);if(exposure>0)event.setAmount(event.getAmount()*(float)(1+Math.min(.5,.2*exposure)));
        for(var m:on(target)){
            boolean hostile=attacker instanceof LivingEntity living&&ElementalTargets.enemy(m.owner,living);
            switch(m.ability){
                case EXPOSURE->{}
                case MISFORTUNE->{if(attacker==m.owner||attacker instanceof LivingEntity l&&m.owner.isAlliedTo(l)){event.setAmount(event.getAmount()+(float)m.value);m.end=0;}}
                case PAIN_LINK->{if(attacker==m.owner){propagating=true;try{for(var other:ElementalTargets.area(m.owner,target.position(),7))if(other!=target&&get(m.owner,other,Ability.PAIN_LINK)!=null)other.hurt(m.level.damageSources().indirectMagic(m.owner,m.owner),Math.min(8,event.getAmount()*.25f));}finally{propagating=false;}}}
                case SUN_MARK->{if(attacker==m.owner&&++m.stacks>=4){event.setAmount(event.getAmount()+(float)m.value);m.end=0;}}
                case LIGHT_SHIELD,GUARD_LINK->{if(m.ability==Ability.GUARD_LINK&&m.linked!=null){var partner=m.level.getEntity(m.linked);if(!(partner instanceof LivingEntity ally)||partner.distanceToSqr(target)>18*18||get(m.owner,ally,Ability.GUARD_LINK)==null||!target.getUUID().equals(get(m.owner,ally,Ability.GUARD_LINK).linked)){m.end=0;continue;}}if(hostile){double amount=Math.min(event.getAmount(),m.value);m.value-=amount;event.setAmount(event.getAmount()-(float)amount);if(m.linked!=null){var partner=m.level.getEntity(m.linked);if(partner instanceof LivingEntity l){var linked=get(m.owner,l,Ability.GUARD_LINK);if(linked!=null&&m.target.getUUID().equals(linked.linked))linked.value=m.value;}}if(m.value<=0)m.end=0;}}
                case REFLECT_LIGHT->{if(hostile)m.value=Math.min(24,m.value+event.getAmount()*.35);}
                case THUNDER_ARMOR->{if(hostile&&attacker instanceof LivingEntity l&&event.getSource().getDirectEntity()==attacker&&target.distanceToSqr(l)<25&&m.level.getGameTime()>=m.stacks){m.stacks=(int)m.level.getGameTime()+20;propagating=true;try{l.hurt(m.level.damageSources().indirectMagic(m.owner,m.owner),(float)m.value);}finally{propagating=false;}}}
                case ROCK_GRASP->m.end=Math.min(m.end,m.level.getGameTime()+10);default->{}
            }
        }
        if(has(target,Ability.GUARD_LINK))for(var m:on(target))if(m.ability==Ability.GUARD_LINK&&m.linked!=null){var linked=m.level.getEntity(m.linked);if(linked==null||linked.distanceToSqr(target)>18*18)m.end=0;}
    }
    @SubscribeEvent public static void blockNewCast(EntityJoinLevelEvent e){if(e.getLevel().isClientSide)return;LivingEntity owner=null;if(e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner() instanceof LivingEntity living)owner=living;else if(e.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs)owner=fangs.getOwner();if(owner!=null&&silenced(owner))e.setCanceled(true);}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e){if(e.getEntity() instanceof LivingEntity t&&!e.getLevel().isClientSide)attributes(t);}
    static void removeOwner(UUID owner){for(var m:new ArrayList<>(MARKS.values()))if(m.owner.getUUID().equals(owner)||m.target.getUUID().equals(owner)){MARKS.remove(new Key(m.owner.getUUID(),m.target.getUUID(),m.ability));m.show(ElementalNetwork.END);cleanupCarrier(m.target,m.ability);attributes(m.target);}CONTROLS.remove(owner);}
    static void clear(){var targets=MARKS.values().stream().map(m->m.target).distinct().toList();var marks=new ArrayList<>(MARKS.values());MARKS.clear();for(var m:marks)cleanupCarrier(m.target,m.ability);for(var t:targets)attributes(t);CONTROLS.clear();}
    private ElementalStates(){}
}
