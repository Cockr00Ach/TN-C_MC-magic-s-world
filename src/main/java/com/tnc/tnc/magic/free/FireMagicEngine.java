package com.tnc.tnc.magic.free;

import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

/** Fire has burning, spreading, channel jets and delayed ignition rather than weapon reskins. */
@Mod.EventBusSubscriber(modid="tnc")
public final class FireMagicEngine {
    private static final List<Effect> EFFECTS=new ArrayList<>(),SPAWN=new ArrayList<>();
    private record Key(UUID owner,UUID target){}
    private static final Map<Key,Burn> BURNS=new HashMap<>();
    private static final Map<Key,Long> HISTORY=new HashMap<>();
    private static boolean reflecting;
    private static final class Effect {
        final long id=ElementalRuntime.nextId();final ServerPlayer owner;final ServerLevel level;final ItemStack wand;
        final Intent intent;final long started;final Set<UUID> viewers=new HashSet<>();final Set<Integer> struck=new HashSet<>();
        final Map<Integer,Long> contacts=new HashMap<>();final LivingEntity target;
        final Vec3 anchorOffset;Vec3 at,velocity,axis;int duration,variant;
        Effect(ServerPlayer owner,Intent intent,Vec3 at,Vec3 direction,LivingEntity target){
            this.owner=owner;level=owner.serverLevel();wand=owner.getMainHandItem();this.intent=intent;this.at=at;anchorOffset=at.subtract(owner.position());
            axis=direction.normalize();velocity=axis.scale(.48*intent.speed());this.target=target;started=level.getGameTime();duration=intent.duration();
        }
        long age(){return level.getGameTime()-started;}
        void show(int phase){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),id,phase,intent.ability().ordinal(),intent.flags(),intent.chantPower(),at,velocity,started,duration,variant>0?-1-variant:target==null?-1:target.getId()));}
        boolean channel(){return variant==0&&intent.ability().channel();}
    }
    private static final class Burn {
        final long id=ElementalRuntime.nextId();final ServerPlayer owner;final LivingEntity target;final ServerLevel level;final Set<UUID> viewers=new HashSet<>();
        Intent intent;long expires,next;
        Burn(ServerPlayer owner,LivingEntity target,Intent intent,int duration){this.owner=owner;this.target=target;this.intent=intent;level=owner.serverLevel();expires=level.getGameTime()+duration;next=level.getGameTime()+20;}
        void show(int phase){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),id,phase,intent.ability().ordinal(),intent.flags(),intent.chantPower(),target.getBoundingBox().getCenter(),Vec3.ZERO,level.getGameTime(),Math.max(1,(int)(expires-level.getGameTime())),target.getId()));}
    }
    static int count(ServerPlayer owner){return (int)EFFECTS.stream().filter(e->e.owner==owner&&e.variant==0).count();}
    static boolean burning(ServerPlayer owner,LivingEntity target){return target!=null&&BURNS.containsKey(new Key(owner.getUUID(),target.getUUID()));}
    static void launch(ElementalRuntime.Charge c,int index){
        Intent intent=c.intent;var placement=c.placements.get(index);Vec3 at=placement.at();
        Vec3 aim=c.owner.getEyePosition().add(c.owner.getLookAngle().scale(40)).subtract(at).normalize();
        Effect e=new Effect(c.owner,intent,at,aim,placement.target());
        EFFECTS.add(e);e.show(ElementalNetwork.ACTIVE);
        c.level.playSound(null,c.owner.blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.PLAYERS,.55f,intent.has(EMPOWER)?.6f:1.05f);
    }
    static void stopChannels(ServerPlayer owner){EFFECTS.removeIf(e->{if(e.owner==owner&&e.channel()){e.show(ElementalNetwork.END);return true;}return false;});}
    static void remove(UUID owner){EFFECTS.removeIf(e->{if(e.owner.getUUID().equals(owner)){e.show(ElementalNetwork.END);return true;}return false;});BURNS.entrySet().removeIf(entry->{if(entry.getKey().owner.equals(owner)){entry.getValue().show(ElementalNetwork.END);return true;}return false;});HISTORY.keySet().removeIf(k->k.owner.equals(owner));}
    static void clear(){EFFECTS.clear();SPAWN.clear();BURNS.clear();HISTORY.clear();}
    static void tick(){
        EFFECTS.removeIf(e->{if(advance(e)){e.show(ElementalNetwork.END);return true;}return false;});
        EFFECTS.addAll(SPAWN);SPAWN.clear();
        for(var b:new ArrayList<>(BURNS.values())){
            var key=new Key(b.owner.getUUID(),b.target.getUUID());if(BURNS.get(key)!=b)continue;long now=b.level.getGameTime();
            if(b.owner.level()!=b.level||b.target.level()!=b.level||!valid(b.owner,b.target)||now>=b.expires||b.target.isInWaterRainOrBubble()&&b.intent.ability()!=Ability.VIOLET_FIRE){b.show(ElementalNetwork.END);BURNS.remove(key,b);continue;}
            if(now>=b.next){b.next=now+20;float wet=b.target.isInWaterRainOrBubble()?.45f:1;hurt(b.owner,b.target,b.intent,Math.max(1,b.intent.power()*2)*wet);b.show(ElementalNetwork.BURN);}
        }
        HISTORY.entrySet().removeIf(e->{var p=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(e.getKey().owner);return p==null||p.level().getGameTime()>e.getValue();});
    }
    private static boolean valid(ServerPlayer owner,LivingEntity target){return FreeMagicServer.canRemain(owner)&&ElementalTargets.enemy(owner,target);}
    private static boolean advance(Effect e){
        long age=e.age();Intent i=e.intent;
        if(!FreeMagicServer.canRemain(e.owner)||e.owner.level()!=e.level||age>=e.duration)return true;
        if(age==0)return false;
        if(e.channel()){if(!FreeMagicServer.canAct(e.owner))return true;
            if(e.owner.getMainHandItem()!=e.wand)return true;
            e.at=ElementalTargets.trace(e.owner,e.owner.getEyePosition(),e.owner.position().add(e.anchorOffset)).getLocation();e.axis=e.owner.getLookAngle();e.velocity=e.axis;
            if(age%20==0&&!ElementalRuntime.pay(e.owner,Math.max(1,i.cost()/3)*(i.has(FAST)?2:1))){ElementalRuntime.message(e.owner,"魔力不足，火焰喷射已停止。");return true;}
        }
        boolean done;
        if(e.variant==1)done=drop(e);
        else if(e.variant==2){if(age%12==1)pulse(e,1.5*i.scale(),i.damage()*.35f,true);done=false;}
        else done=switch(i.ability()){
            case FIREBALL,FIREBLADE,FIRE_SERPENT,HEAT_WAVE -> projectile(e);
            case IGNITE,VIOLET_FIRE -> {if(age==1&&valid(e.owner,e.target))ignite(e.owner,e.target,i,i.ability().duration);yield age>2;}
            case FIRE_COVER -> {if(age%(i.has(FAST)?10:20)==1)pulse(e,3*i.scale(),i.damage(),true);yield false;}
            case FIRE_WALL -> {if(age%15==1)wall(e);yield false;}
            case FIRE_RING -> {ring(e);yield false;}
            case FLASH_FIRE -> {if(age==6)burst(e,e.at,2.4*i.scale(),i.damage());yield age>12;}
            case DETONATE_BURN -> {if(age==1)detonate(e);yield age>12;}
            case FIRE_RAIN -> {if(age%(i.has(FAST)?5:10)==1)rain(e);yield false;}
            case FLAME_COLUMN -> {if(age%10==1)column(e);yield false;}
            case LAVA_VENT -> {if(age%(i.has(FAST)?5:10)==1)vent(e);yield false;}
            case SPREAD_FIRE -> {if(age==1)spread(e);yield age>20;}
            case FLAME_CLOAK -> {e.at=e.owner.position().add(0,1,0);yield false;}
            case FIRE_BREATH -> {if(age%(i.has(FAST)?5:10)==1)breath(e);yield false;}
            case MAGMA_SPRAY -> {if(age%(i.has(FAST)?3:6)==1)spawnDrop(e,e.at.add(e.axis.scale(.6)),e.axis.scale(.65*i.speed()).add(0,.1,0));yield false;}
            case EMBER_MARK -> {if(age>=e.duration-6){Vec3 at=e.target!=null&&e.target.isAlive()?e.target.getBoundingBox().getCenter():e.at;burst(e,at,2.2*i.scale(),i.damage());yield true;}yield false;}
            case KINDLE -> {if(age%20==1)kindle(e);yield false;}
            default -> throw new IllegalStateException("Non-fire action reached fire engine");
        };
        if(!done&&age%3==0)e.show(ElementalNetwork.ACTIVE);return done;
    }
    private static boolean projectile(Effect e){
        Intent i=e.intent;
        if(i.ability()==Ability.FIRE_SERPENT){
            var enemy=ElementalTargets.area(e.owner,e.at,10).stream().filter(t->t.getBoundingBox().getCenter().subtract(e.at).normalize().dot(e.velocity.normalize())>-.1).findFirst().orElse(null);
            if(enemy!=null)e.velocity=WaterAttackEngine.turn(e.velocity,enemy.getBoundingBox().getCenter().subtract(e.at),9).scale(.42*i.speed());
            var floor=ElementalTargets.trace(e.owner,e.at.add(0,2,0),e.at.add(0,-5,0));
            if(floor.getType()!=HitResult.Type.MISS)e.velocity=new Vec3(e.velocity.x,Math.max(-.2,Math.min(.2,floor.getLocation().y+.65-e.at.y)),e.velocity.z);
        }
        if(i.ability()==Ability.HEAT_WAVE)e.velocity=e.axis.scale(.24*i.speed());
        Vec3 end=e.at.add(e.velocity);var block=ElementalTargets.trace(e.owner,e.at,end);end=block.getLocation();
        double radius=(i.ability()==Ability.FIREBLADE?1.1:i.ability()==Ability.HEAT_WAVE?2.2:.45)*i.scale();
        var hits=ElementalTargets.segment(e.owner,e.at,end,radius,e.struck);
        for(var target:hits){
            e.struck.add(target.getId());
            if(i.ability()==Ability.FIREBALL){burst(e,target.getBoundingBox().getCenter(),1.4*i.scale(),i.damage());return true;}
            hurt(e.owner,target,i,i.damage());ignite(e.owner,target,i,60);push(e,target,i.ability()==Ability.HEAT_WAVE?1.2:.35);
            if(i.ability()==Ability.FIRE_SERPENT||e.struck.size()>=6)return true;
        }
        e.at=end;if(block.getType()!=HitResult.Type.MISS){if(i.ability()==Ability.FIREBALL)burst(e,end,1.4*i.scale(),i.damage());return true;}return false;
    }
    private static void pulse(Effect e,double radius,float damage,boolean burn){for(var target:ElementalTargets.area(e.owner,e.at.add(0,.6,0),radius)){hurt(e.owner,target,e.intent,damage);if(burn)ignite(e.owner,target,e.intent,60);if(e.variant==2)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,0));if(e.intent.has(SHOCK))push(e,target,.6);}}
    private static void wall(Effect e){
        var right=WaterCastGeometry.right(e.axis);double scale=e.intent.scale();
        for(var target:ElementalTargets.area(e.owner,e.at.add(0,1,0),5*scale)){
            Vec3 off=target.getBoundingBox().getCenter().subtract(e.at);
            if(Math.abs(off.dot(right))<4*scale&&Math.abs(off.dot(new Vec3(e.axis.x,0,e.axis.z).normalize()))<.65*scale&&off.y>=0&&off.y<3*scale){hurt(e.owner,target,e.intent,e.intent.damage());ignite(e.owner,target,e.intent,70);if(e.intent.has(SHOCK))push(e,target,.6);}
        }
    }
    private static void ring(Effect e){double radius=Math.min(7*e.intent.scale(),e.age()*.25*e.intent.speed());for(var t:ElementalTargets.area(e.owner,e.at.add(0,.5,0),radius+.6))if(!e.struck.contains(t.getId())&&t.distanceToSqr(e.at)>=Math.max(0,radius-1.2)*Math.max(0,radius-1.2)){e.struck.add(t.getId());hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,60);push(e,t,.8);}}
    private static void burst(Effect e,Vec3 at,double radius,float damage){Vec3 original=e.at;e.at=at;pulse(e,radius,damage,true);for(var t:ElementalTargets.area(e.owner,at,radius))push(e,t,.4);e.show(ElementalNetwork.IMPACT);e.at=original;e.level.playSound(null,at.x,at.y,at.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.55f,1.1f);}
    private static void detonate(Effect e){
        if(e.target==null)return;Vec3 at=e.target.getBoundingBox().getCenter();
        var original=ElementalTargets.area(e.owner,at,6).stream().filter(t->burning(e.owner,t)).limit(5).toList();
        if(!burning(e.owner,e.target))return;
        Vec3 previous=e.at;e.at=at;
        for(var t:ElementalTargets.area(e.owner,at,3*e.intent.scale())){hurt(e.owner,t,e.intent,e.intent.damage()*2);push(e,t,.4);}
        for(var t:original){var burn=BURNS.remove(new Key(e.owner.getUUID(),t.getUUID()));if(burn!=null){burn.show(ElementalNetwork.END);if(t!=e.target)hurt(e.owner,t,e.intent,e.intent.damage());}}
        var primary=BURNS.remove(new Key(e.owner.getUUID(),e.target.getUUID()));if(primary!=null)primary.show(ElementalNetwork.END);
        e.show(ElementalNetwork.IMPACT);e.at=previous;
    }
    private static void spread(Effect e){if(e.target==null)return;ignite(e.owner,e.target,e.intent,80);for(var t:ElementalTargets.area(e.owner,e.target.position().add(0,.7,0),5*e.intent.scale()))ignite(e.owner,t,e.intent,100);}
    private static void breath(Effect e){
        double range=9,cos=Math.cos(Math.toRadians(e.intent.has(GIANT)?42:22));
        for(var t:ElementalTargets.area(e.owner,e.at,range))if(t.getBoundingBox().getCenter().subtract(e.at).normalize().dot(e.axis)>cos){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,60);if(e.intent.has(SHOCK))push(e,t,.7);}
    }
    private static void column(Effect e){for(var t:ElementalTargets.area(e.owner,e.at.add(0,2,0),5*e.intent.scale())){var off=t.position().subtract(e.at);if(off.x*off.x+off.z*off.z<1.8*1.8*e.intent.scale()*e.intent.scale()&&off.y>=-.2&&off.y<6*e.intent.scale()){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,80);if(e.intent.has(SHOCK))push(e,t,.7);}}}
    private static void rain(Effect e){double a=e.age()*2.39996,r=(1+(e.age()%7)*.5)*e.intent.scale();Vec3 at=e.at.add(Math.cos(a)*r,8,Math.sin(a)*r);if(ElementalTargets.visible(e.owner,e.at.add(0,.2,0),at))spawnDrop(e,at,new Vec3(0,-.65*e.intent.speed(),0));}
    private static void vent(Effect e){double a=e.age()*1.8;spawnDrop(e,e.at.add(0,.3,0),new Vec3(Math.cos(a)*.35,.55,Math.sin(a)*.35).scale(e.intent.speed()));}
    private static void spawnDrop(Effect parent,Vec3 at,Vec3 velocity){if(EFFECTS.stream().filter(e->e.owner==parent.owner&&e.variant>0).count()+SPAWN.size()>=24)return;var drop=new Effect(parent.owner,parent.intent,at,velocity,null);drop.velocity=velocity;drop.variant=1;drop.duration=60;SPAWN.add(drop);drop.show(ElementalNetwork.ACTIVE);}
    private static boolean drop(Effect e){
        e.velocity=e.velocity.add(0,-.035,0);Vec3 end=e.at.add(e.velocity);var block=ElementalTargets.trace(e.owner,e.at,end);var hits=ElementalTargets.segment(e.owner,e.at,block.getLocation(),.3*e.intent.scale(),Set.of());e.at=block.getLocation();
        if(!hits.isEmpty()||block.getType()!=HitResult.Type.MISS){
            for(var t:hits){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,80);}
            var pool=new Effect(e.owner,e.intent,e.at,Vec3.ZERO,null);pool.variant=2;pool.duration=e.intent.has(DURABLE)?120:45;SPAWN.add(pool);pool.show(ElementalNetwork.ACTIVE);return true;
        }return false;
    }
    private static void kindle(Effect e){for(var t:ElementalTargets.area(e.owner,e.at.add(0,.5,0),4*e.intent.scale())){var key=new Key(e.owner.getUUID(),t.getUUID());if(HISTORY.containsKey(key)&&!BURNS.containsKey(key))ignite(e.owner,t,e.intent,50);}}
    private static void ignite(ServerPlayer owner,LivingEntity target,Intent intent,int duration){
        if(!valid(owner,target)||target.fireImmune())return;
        var key=new Key(owner.getUUID(),target.getUUID());long now=owner.level().getGameTime();HISTORY.put(key,now+400);int scaledDuration=duration*(intent.has(DURABLE)?3:1);
        var burn=BURNS.get(key);if(burn==null){burn=new Burn(owner,target,intent,scaledDuration);BURNS.put(key,burn);}else{burn.expires=Math.max(burn.expires,now+scaledDuration);if(intent.power()>burn.intent.power()||intent.ability()==Ability.VIOLET_FIRE)burn.intent=intent;}burn.show(ElementalNetwork.BURN);
    }
    private static void hurt(ServerPlayer owner,LivingEntity target,Intent intent,float damage){if(valid(owner,target)&&!target.fireImmune())target.hurt(owner.level().damageSources().indirectMagic(owner,owner),damage);}
    private static void push(Effect e,LivingEntity target,double strength){
        double resist=target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);Vec3 offset=target.position().subtract(e.at);
        target.knockback(strength*(e.intent.has(SHOCK)?4.2:1),-offset.x,-offset.z);
        if(e.intent.has(SHOCK))target.setDeltaMovement(target.getDeltaMovement().add(0,1.05*(1-resist),0));target.hurtMarked=true;
    }
    @SubscribeEvent public static void attacked(LivingAttackEvent event){
        if(reflecting||!(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))||!(event.getEntity() instanceof ServerPlayer owner)||!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)||!ElementalTargets.enemy(owner,attacker)||owner.distanceToSqr(attacker)>25)return;
        for(var e:EFFECTS)if(e.owner==owner&&e.intent.ability()==Ability.FLAME_CLOAK&&e.age()<e.duration){
            long now=e.level.getGameTime();if(e.contacts.getOrDefault(attacker.getId(),0L)>now)continue;e.contacts.put(attacker.getId(),now+20);
            reflecting=true;try{hurt(owner,attacker,e.intent,e.intent.damage());ignite(owner,attacker,e.intent,80);e.show(ElementalNetwork.IMPACT);}finally{reflecting=false;}break;
        }
    }
    private FireMagicEngine(){}
}
