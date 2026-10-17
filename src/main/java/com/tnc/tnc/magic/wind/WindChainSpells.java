package com.tnc.tnc.magic.wind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.lang.ref.WeakReference;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class WindChainSpells {
    private static final Map<LivingEntity,List<WeakReference<Entity>>> ACTIVE=new WeakHashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void release(net.minecraftforge.event.entity.living.MobEffectEvent.Added event){
        if(!event.getEntity().level().isClientSide&&event.getEffectInstance().getEffect() instanceof WindChainRegistration.CastMarker marker)
            cast(event.getEntity(),marker.chain,marker.tier);
    }
    public static boolean enemy(LivingEntity e,LivingEntity owner){return e!=owner&&!(e instanceof Player)&&e.isAlive()&&!e.isSpectator()&&!e.isAlliedTo(owner);}
    public static LivingEntity aim(LivingEntity owner,double range){
        Vec3 eye=owner.getEyePosition(),end=eye.add(owner.getLookAngle().scale(range));
        var wall=owner.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        end=wall.getLocation();LivingEntity best=null;double nearest=eye.distanceToSqr(end);
        for(var e:owner.level().getEntitiesOfClass(LivingEntity.class,new AABB(eye,end).inflate(1.2),v->enemy(v,owner))){
            var hit=e.getBoundingBox().inflate(.5).clip(eye,end);
            if(e.getBoundingBox().inflate(.5).contains(eye))hit=Optional.of(eye);
            if(hit.isPresent()&&eye.distanceToSqr(hit.get())<nearest){best=e;nearest=eye.distanceToSqr(hit.get());}
        }
        return best;
    }
    public static Entity cast(LivingEntity owner,int chain,int tier){
        if(!(owner.level() instanceof ServerLevel level)||!owner.isAlive())return null;
        var mine=ACTIVE.computeIfAbsent(owner,k->new ArrayList<>());mine.removeIf(r->{var e=r.get();return e==null||e.isRemoved();});
        if(mine.size()>=4)return null;
        Entity entity;
        if(chain==0){var ball=WindChainRegistration.BALL.create(level);if(ball==null)return null;ball.configure(owner,tier);entity=ball;}
        else{
            var target=aim(owner,96);
            if(target==null&&tier<4)target=level.getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(32),v->enemy(v,owner)&&owner.hasLineOfSight(v)&&v.position().subtract(owner.position()).normalize().dot(owner.getLookAngle())>.15)
                    .stream().min(Comparator.comparingDouble(v->v.distanceToSqr(owner))).orElse(null);
            if(target==null){if(owner instanceof net.minecraft.server.level.ServerPlayer p)p.displayClientMessage(net.minecraft.network.chat.Component.literal("璇峰厛鐬勫噯鏁屼汉"),true);return null;}
            var volley=WindChainRegistration.VOLLEY.create(level);if(volley==null)return null;
            volley.configure(owner,tier,target);entity=volley;
        }
        if(!level.addFreshEntity(entity))return null;mine.add(new WeakReference<>(entity));return entity;
    }
}
