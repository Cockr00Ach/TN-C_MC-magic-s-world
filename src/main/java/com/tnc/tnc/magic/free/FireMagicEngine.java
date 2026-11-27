package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.fire.FireHeat;
import com.tnc.tnc.magic.fire.FreeFireDetonation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
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
    private static final int DROP = 1, POOL = 2, SNAKE_TRAIL = 3;
    private static boolean reflecting;
    private static final class Effect {
        final long id=ElementalRuntime.nextId();final ServerPlayer owner;final ServerLevel level;final ItemStack wand;
        final Intent intent;final long started;final Set<UUID> viewers=new HashSet<>();final Set<Integer> struck=new HashSet<>();
        final Map<Integer,Long> contacts=new HashMap<>();final LivingEntity target;
        Vec3 at,velocity,axis,lastTrail;int duration,variant;
        Effect(ServerPlayer owner,Intent intent,Vec3 at,Vec3 direction,LivingEntity target){
            this.owner=owner;level=owner.serverLevel();wand=owner.getMainHandItem();this.intent=intent;this.at=at;
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
        void show(int phase){ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),id,phase,intent.ability().ordinal(),intent.flags(),intent.chantPower(),target.getBoundingBox().getCenter(),new Vec3(FireHeat.ratio(target),0,0),level.getGameTime(),Math.max(1,(int)(expires-level.getGameTime())),target.getId()));}
    }
    static int count(ServerPlayer owner){return (int)EFFECTS.stream().filter(e->e.owner==owner&&e.variant==0).count();}
    static boolean burning(ServerPlayer owner,LivingEntity target){return target!=null&&BURNS.containsKey(new Key(owner.getUUID(),target.getUUID()));}
    static void launch(ElementalRuntime.Charge c,int index){
        Intent intent=c.intent;var placement=c.placements.get(index);Vec3 at=placement.at();
        Vec3 aim=c.owner.getEyePosition().add(c.owner.getLookAngle().scale(40)).subtract(at).normalize();
        Effect e=new Effect(c.owner,intent,at,aim,placement.target());
        EFFECTS.add(e);e.show(ElementalNetwork.ACTIVE);
        SoundEvent release = switch (intent.ability()) {
            case MAGMA_SPRAY, LAVA_VENT, FIRE_COVER -> SoundEvents.LAVA_POP;
            case FIRE_SERPENT, HEAT_WAVE -> SoundEvents.FIRECHARGE_USE;
            case IGNITE, KINDLE, EMBER_MARK -> SoundEvents.FLINTANDSTEEL_USE;
            case VIOLET_FIRE -> SoundEvents.SOUL_ESCAPE;
            default -> SoundEvents.BLAZE_SHOOT;
        };
        c.level.playSound(null,c.owner.blockPosition(),release,SoundSource.PLAYERS,.55f,intent.has(EMPOWER)?.6f:1.05f);
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
            e.axis = e.owner.getLookAngle();
            e.velocity = e.axis;
            e.at = ElementalTargets.origin(e.owner, i.scale());
            if(age%20==0&&!ElementalRuntime.pay(e.owner,Math.max(1,i.cost()/3)*(i.has(FAST)?2:1))){ElementalRuntime.message(e.owner,"魔力不足，火焰喷射已停止。");return true;}
        }
        boolean done;
        if(e.variant==DROP)done=drop(e);
        else if(e.variant==POOL){if(age%12==1)pulse(e,1.5*i.scale(),i.damage()*.35f,true);done=false;}
        else if(e.variant==SNAKE_TRAIL){if(age%10==1)scorchTrail(e);done=false;}
        else done=switch(i.ability()){
            case FIREBALL,FIREBLADE,FIRE_SERPENT,HEAT_WAVE -> projectile(e);
            case IGNITE,VIOLET_FIRE -> {if(age==1&&valid(e.owner,e.target))ignite(e.owner,e.target,i,i.ability().duration);yield age>2;}
            case FIRE_COVER -> {spreadField(e);yield false;}
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
            case MAGMA_SPRAY -> {
                if(age%(i.has(FAST)?3:6)==1){
                    // Counter the first part of the ballistic drop at ordinary aiming distance.
                    Vec3 launch = e.axis.scale(.65*i.speed()).add(0,.16/i.speed(),0);
                    spawnDrop(e.owner,i,e.at,launch);
                }
                yield false;
            }
            case EMBER_MARK -> {if(age>=e.duration-6){Vec3 at=e.target!=null&&e.target.isAlive()?e.target.getBoundingBox().getCenter():e.at;burst(e,at,2.2*i.scale(),i.damage());yield true;}yield false;}
            case KINDLE -> {if(age%20==1)kindle(e);yield false;}
            default -> throw new IllegalStateException("Non-fire action reached fire engine");
        };
        if(!done&&age%(e.variant==SNAKE_TRAIL||e.variant==POOL?20:3)==0)e.show(ElementalNetwork.ACTIVE);return done;
    }
    private static boolean projectile(Effect e){
        Intent i=e.intent;
        if(i.ability()==Ability.FIRE_SERPENT)steerSerpent(e);
        if(i.ability()==Ability.HEAT_WAVE)e.velocity=e.axis.scale(.24*i.speed());
        Vec3 end=e.at.add(e.velocity);var block=ElementalTargets.trace(e.owner,e.at,end);end=block.getLocation();
        double radius=(i.ability()==Ability.FIREBLADE?1.1:i.ability()==Ability.HEAT_WAVE?2.2:.45)*i.scale();
        var hits=ElementalTargets.segment(e.owner,e.at,end,radius,e.struck);
        for(var target:hits){
            e.struck.add(target.getId());
            if(i.ability()==Ability.FIREBALL){burst(e,target.getBoundingBox().getCenter(),1.4*i.scale(),i.damage());return true;}
            hurt(e.owner,target,i,i.damage());ignite(e.owner,target,i,60);push(e,target,i.ability()==Ability.HEAT_WAVE?1.2:.35);
            impact(e,target.getBoundingBox().getCenter(),e.velocity);
            if(i.ability()==Ability.FIRE_SERPENT||e.struck.size()>=6)return true;
        }
        e.at=end;if(block.getType()!=HitResult.Type.MISS){if(i.ability()==Ability.FIREBALL)burst(e,end,1.4*i.scale(),i.damage());else impact(e,end,e.velocity);return true;}return false;
    }
    private static void impact(Effect e, Vec3 point, Vec3 direction) {
        var packet = new ElementalNetwork.Visual(e.level.dimension().location(), e.owner.getUUID(),
                ElementalRuntime.nextId(), ElementalNetwork.IMPACT, e.intent.ability().ordinal(),
                e.intent.flags(), e.intent.chantPower(), point, direction, e.level.getGameTime(), 16, -1);
        ElementalNetwork.show(e.owner, e.viewers, packet);
        if (e.intent.ability() == Ability.FIRE_SERPENT) {
            e.level.playSound(null,point.x,point.y,point.z,SoundEvents.FIRE_EXTINGUISH,SoundSource.PLAYERS,.65f,.74f);
        } else if (e.variant == DROP) {
            e.level.playSound(null,point.x,point.y,point.z,SoundEvents.LAVA_POP,SoundSource.PLAYERS,.35f,.68f);
        }
    }

    private static void steerSerpent(Effect e) {
        Vec3 forward = e.axis;
        LivingEntity prey = serpentPrey(e.owner,e.at,forward);
        if (prey != null) {
            e.axis = WaterAttackEngine.turn(forward, prey.getBoundingBox().getCenter().subtract(e.at), 12);
        }
        double speed = .48 * e.intent.speed();
        double sway = Math.sin(e.age() * .48) * .20;
        e.velocity = e.axis.add(WaterCastGeometry.right(e.axis).scale(sway)).normalize().scale(speed);
        Vec3 ground = groundBelow(e.owner,e.at,5);
        if (ground == null) return;
        double height = ground.y + .55;
        // Rear up into a marked airborne target only on the final approach.
        if (prey != null && prey.position().distanceToSqr(e.at) < 16) height = prey.getBoundingBox().getCenter().y;
        e.velocity = new Vec3(e.velocity.x, Math.max(-speed, Math.min(speed, height - e.at.y)), e.velocity.z);
        if (ground.distanceToSqr(e.at) > 2.25 || (e.lastTrail != null && ground.distanceToSqr(e.lastTrail) < 3.24)) return;
        if (spawnChild(e.owner,e.intent,SNAKE_TRAIL,ground,e.velocity,e.intent.has(DURABLE) ? 96 : 32)) e.lastTrail = ground;
    }
    static LivingEntity serpentPrey(ServerPlayer owner, Vec3 at, Vec3 forward) {
        LivingEntity best = null;
        float bestHeat = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : owner.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(at,at).inflate(12), t -> ElementalTargets.enemy(owner,t)
                        && !t.fireImmune() && !t.hasEffect(MobEffects.FIRE_RESISTANCE))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 offset = center.subtract(at);
            double distance = offset.lengthSqr();
            if (distance > 144 || offset.dot(forward) < -.15 * Math.sqrt(distance)) continue;
            float heat = FireHeat.of(candidate);
            if (heat < bestHeat || (heat == bestHeat && distance >= bestDistance)) continue;
            if (!ElementalTargets.visible(owner,at,center)) continue;
            best = candidate;
            bestHeat = heat;
            bestDistance = distance;
        }
        return best;
    }

    static Vec3 groundBelow(ServerPlayer owner, Vec3 start, double depth) {
        ServerLevel level = owner.serverLevel();
        if (level.isOutsideBuildHeight(BlockPos.containing(start))) return null;
        Vec3 end = new Vec3(start.x,Math.max(level.getMinBuildHeight(),start.y-depth),start.z);
        BlockHitResult hit = ElementalTargets.trace(owner,start,end);
        var pos = hit.getBlockPos();
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP
                || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) return null;
        return hit.getLocation().add(0,.08,0);
    }

    private static void scorchTrail(Effect e) {
        double radius = .8 * e.intent.scale();
        double top = e.at.y + .55;
        Vec3 flame = e.at.add(0,.16,0);
        AABB band = new AABB(e.at.x-radius,e.at.y-.10,e.at.z-radius,e.at.x+radius,top,e.at.z+radius);
        for (var target : e.level.getEntitiesOfClass(LivingEntity.class,band,t -> ElementalTargets.enemy(e.owner,t))) {
            if (e.struck.contains(target.getId())) continue;
            AABB box = target.getBoundingBox();
            double x = Math.max(box.minX,Math.min(flame.x,box.maxX));
            double z = Math.max(box.minZ,Math.min(flame.z,box.maxZ));
            double dx = x-flame.x, dz = z-flame.z;
            if (dx*dx + dz*dz > radius*radius) continue;
            double y = Math.max(box.minY + .01,Math.min(flame.y,box.maxY));
            if (!ElementalTargets.visible(e.owner,flame,new Vec3(x,y,z))) continue;
            e.struck.add(target.getId());
            hurt(e.owner, target, e.intent, e.intent.damage() * .20f);
            ignite(e.owner, target, e.intent, 30, 4);
        }
    }
    private static void spreadField(Effect e) {
        long age = e.age();
        long interval = e.intent.has(FAST) ? 10 : 20;
        if (age <= 12) {
            double radius = 3 * e.intent.scale() * (.30 + .70 * age / 12);
            for (var victim : ElementalTargets.area(e.owner,e.at.add(0,.6,0),radius)) {
                if (!e.struck.add(victim.getId())) continue;
                hurt(e.owner,victim,e.intent,e.intent.damage());
                ignite(e.owner,victim,e.intent,60);
                if (e.intent.has(SHOCK)) push(e,victim,.6);
            }
        } else if ((age - 12) % interval == 0) {
            pulse(e,3 * e.intent.scale(),e.intent.damage(),true);
        }
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
        if(e.target == null || !valid(e.owner,e.target) || !burning(e.owner,e.target)) return;
        e.at = e.target.getBoundingBox().getCenter();
        float fuel = FireHeat.of(e.target);
        double radius = (2.35 + 2.15 * fuel / FireHeat.MAX) * e.intent.scale();
        var linked = ElementalTargets.area(e.owner,e.at,6 * e.intent.scale()).stream()
                .filter(t -> t != e.target && burning(e.owner,t)).limit(4).toList();
        float power = e.intent.power();
        float damage = (e.intent.ability().damage * 2 + fuel * .32f) * power;
        if (!FreeFireDetonation.hit(e.owner,e.target,damage)) return;
        FireHeat.consume(e.target);
        removeBurn(e.owner, e.target);
        push(e,e.target,.4 + fuel / 240);
        for (var victim : ElementalTargets.area(e.owner,e.at,radius)) {
            if (victim == e.target) continue;
            double distance = victim.getBoundingBox().getCenter().distanceTo(e.at);
            float falloff = (float)(1 - .5 * distance / radius);
            if (FreeFireDetonation.hit(e.owner,victim,damage * falloff)) {
                push(e,victim,.4 + fuel / 240);
                removeBurn(e.owner,victim);
            }
        }
        impact(e,e.at,new Vec3(radius,0,0));
        for (var victim : linked) {
            if (!victim.isAlive() || !burning(e.owner,victim) || victim.getBoundingBox().getCenter().distanceTo(e.at) <= radius) continue;
            if (FreeFireDetonation.hit(e.owner,victim,e.intent.damage())) {
                removeBurn(e.owner,victim);
                impact(e,victim.getBoundingBox().getCenter(),new Vec3(.85 * e.intent.scale(),0,0));
            }
        }
        e.level.playSound(null,e.at.x,e.at.y,e.at.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,
                .65f + fuel / 210,1.2f - fuel / 230);
    }
    private static void removeBurn(ServerPlayer owner, LivingEntity target) {
        Burn removed = BURNS.remove(new Key(owner.getUUID(),target.getUUID()));
        if (removed != null) removed.show(ElementalNetwork.END);
    }
    private static void spread(Effect e){
        if(e.target==null || !valid(e.owner,e.target))return;
        float transferred = 5 + FireHeat.of(e.target) * .15f;
        ignite(e.owner,e.target,e.intent,80);
        for(var t:ElementalTargets.area(e.owner,e.target.position().add(0,.7,0),5*e.intent.scale())){
            if(t!=e.target)ignite(e.owner,t,e.intent,100,transferred);
        }
    }
    private static void breath(Effect e){
        double range=9,cos=Math.cos(Math.toRadians(e.intent.has(GIANT)?42:22));
        for(var t:ElementalTargets.area(e.owner,e.at,range))if(t.getBoundingBox().getCenter().subtract(e.at).normalize().dot(e.axis)>cos){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,60);impact(e,t.getBoundingBox().getCenter(),e.axis);if(e.intent.has(SHOCK))push(e,t,.7);}
    }
    private static void column(Effect e){for(var t:ElementalTargets.area(e.owner,e.at.add(0,2,0),5*e.intent.scale())){var off=t.position().subtract(e.at);if(off.x*off.x+off.z*off.z<1.8*1.8*e.intent.scale()*e.intent.scale()&&off.y>=-.2&&off.y<6*e.intent.scale()){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,80);if(e.intent.has(SHOCK))push(e,t,.7);}}}
    private static void rain(Effect e){double a=e.age()*2.39996,r=(1+(e.age()%7)*.5)*e.intent.scale();Vec3 at=e.at.add(Math.cos(a)*r,8,Math.sin(a)*r);if(ElementalTargets.visible(e.owner,e.at.add(0,.2,0),at))spawnDrop(e.owner,e.intent,at,new Vec3(0,-.65*e.intent.speed(),0));}
    private static void vent(Effect e){double a=e.age()*1.8;spawnDrop(e.owner,e.intent,e.at.add(0,.3,0),new Vec3(Math.cos(a)*.35,.55,Math.sin(a)*.35).scale(e.intent.speed()));}
    static void spawnDrop(ServerPlayer owner,Intent intent,Vec3 at,Vec3 velocity){spawnChild(owner,intent,DROP,at,velocity,60);}
    private static boolean spawnChild(ServerPlayer owner, Intent intent, int variant, Vec3 at, Vec3 velocity, int duration) {
        long active = EFFECTS.stream().filter(e -> e.owner == owner && e.variant != 0).count();
        long queued = SPAWN.stream().filter(e -> e.owner == owner).count();
        if (active + queued >= 24) return false;
        queueChild(owner,intent,variant,at,velocity,duration);
        return true;
    }
    private static void queueChild(ServerPlayer owner, Intent intent, int variant, Vec3 at, Vec3 velocity, int duration) {
        Effect child = new Effect(owner,intent,at,velocity,null);
        child.variant=variant;
        child.duration=duration;
        child.velocity=velocity;
        SPAWN.add(child);
        child.show(ElementalNetwork.ACTIVE);
    }
    private static boolean drop(Effect e){
        e.velocity=e.velocity.add(0,-.035,0);Vec3 end=e.at.add(e.velocity);var block=ElementalTargets.trace(e.owner,e.at,end);var hits=ElementalTargets.segment(e.owner,e.at,block.getLocation(),.3*e.intent.scale(),Set.of());e.at=block.getLocation();
        if(!hits.isEmpty()||block.getType()!=HitResult.Type.MISS){
            if (!hits.isEmpty()) e.at = hits.get(0).getBoundingBox().getCenter();
            for(var t:hits){hurt(e.owner,t,e.intent,e.intent.damage());ignite(e.owner,t,e.intent,80);if(e.intent.has(SHOCK))push(e,t,.55);}
            impact(e,e.at,e.velocity);
            Vec3 contact = e.at.subtract(e.velocity.normalize().scale(.06));
            Vec3 ground = groundBelow(e.owner,contact.add(0,.03,0),6);
            if (ground != null) {
                // The drop retires now, so its pool already owns a slot even when all 24 land together.
                queueChild(e.owner,e.intent,POOL,ground,Vec3.ZERO,e.intent.has(DURABLE)?120:45);
            }
            return true;
        }return false;
    }
    private static void kindle(Effect e){for(var t:ElementalTargets.area(e.owner,e.at.add(0,.5,0),4*e.intent.scale())){var key=new Key(e.owner.getUUID(),t.getUUID());if(HISTORY.containsKey(key)&&!BURNS.containsKey(key))ignite(e.owner,t,e.intent,50);}}
    private static void ignite(ServerPlayer owner,LivingEntity target,Intent intent,int duration){
        float heat = switch (intent.ability()) {
            case IGNITE -> 26;
            case FIRE_SERPENT -> 18;
            case FIREBALL, FIREBLADE, EMBER_MARK, VIOLET_FIRE -> 12;
            case FIRE_WALL, FIRE_COVER -> 10;
            case FIRE_BREATH -> 8;
            default -> 6;
        };
        ignite(owner,target,intent,duration,heat);
    }
    private static void ignite(ServerPlayer owner,LivingEntity target,Intent intent,int duration,float heat){
        if(!valid(owner,target)||target.fireImmune()||target.hasEffect(MobEffects.FIRE_RESISTANCE))return;
        FireHeat.add(target,owner,heat * (float)Math.sqrt(intent.power()));
        var key=new Key(owner.getUUID(),target.getUUID());long now=owner.level().getGameTime();HISTORY.put(key,now+400);int scaledDuration=duration*(intent.has(DURABLE)?3:1);
        var burn=BURNS.get(key);if(burn==null){burn=new Burn(owner,target,intent,scaledDuration);BURNS.put(key,burn);}else{burn.expires=Math.max(burn.expires,now+scaledDuration);if(intent.power()>burn.intent.power()||intent.ability()==Ability.VIOLET_FIRE)burn.intent=intent;}burn.show(ElementalNetwork.BURN);
    }
    private static void hurt(ServerPlayer owner,LivingEntity target,Intent intent,float damage){
        if(valid(owner,target)&&!target.fireImmune()){
            var fire = owner.level().damageSources().onFire().typeHolder();
            target.hurt(new DamageSource(fire,owner,owner),damage);
        }
    }
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
