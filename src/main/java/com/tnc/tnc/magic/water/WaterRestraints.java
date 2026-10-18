package com.tnc.tnc.magic.water;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short leases store identifiers and positions, never strong world/entity references. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WaterRestraints {
    private record Lock(UUID source,Vec3 position,long until) {}
    private static final Map<ServerLevel,Map<UUID,Lock>> LOCKS=new WeakHashMap<>();
    private WaterRestraints() {}
    static void hold(TNWaterFieldEntity source,LivingEntity target) {
        if(!WaterSpellRules.canRestrain(target.getBbHeight()))return;
        var level=(ServerLevel)source.level();
        var locks=LOCKS.computeIfAbsent(level,k->new HashMap<>());
        var previous=locks.get(target.getUUID());
        Vec3 at=previous!=null&&previous.source.equals(source.getUUID())?previous.position:target.position();
        locks.put(target.getUUID(),new Lock(source.getUUID(),at,level.getGameTime()+2));
        freeze(target,at);
    }
    private static void freeze(LivingEntity target,Vec3 at) {
        boolean displaced=target.position().distanceToSqr(at)>1e-8;
        boolean impulse=target.getDeltaMovement().horizontalDistanceSqr()>1e-6||target.getDeltaMovement().y>.02;
        if(displaced)target.teleportTo(at.x,at.y,at.z);
        target.setDeltaMovement(Vec3.ZERO);target.setJumping(false);target.fallDistance=0;
        if(displaced||impulse)target.hurtMarked=true;
        if(target instanceof Mob mob)mob.getNavigation().stop();
    }
    static void enforce(ServerLevel level) {
        var locks=LOCKS.get(level);if(locks==null)return;
        var iterator=locks.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var lock=entry.getValue();
            var source=level.getEntity(lock.source);var entity=level.getEntity(entry.getKey());
            if(lock.until<level.getGameTime()||source==null||source.isRemoved()
                    ||!(entity instanceof LivingEntity target)||!target.isAlive()
                    ||!WaterSpellRules.canRestrain(target.getBbHeight())) {iterator.remove();continue;}
            freeze(target,lock.position);
        }
        if(locks.isEmpty())LOCKS.remove(level);
    }
    static void release(ServerLevel level,UUID source) {
        var locks=LOCKS.get(level);if(locks==null)return;
        locks.values().removeIf(lock->lock.source.equals(source));
        if(locks.isEmpty())LOCKS.remove(level);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase==TickEvent.Phase.END&&e.level instanceof ServerLevel level)enforce(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel level)LOCKS.remove(level);
    }
}
