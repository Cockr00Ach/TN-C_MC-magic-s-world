package com.tnc.tnc.world.ink;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Remember only ground-observation dragons seen in loaded chunks this session. */
@Mod.EventBusSubscriber(modid="tnc")
public final class InkDragonVisibilityServer {
    private static final Map<ServerLevel,LinkedHashMap<UUID,InkDragonVisibility.Snapshot>> CACHE=new IdentityHashMap<>();
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%5!=0)return;
        var cache=CACHE.computeIfAbsent(level,k->new LinkedHashMap<>());
        for(var e:level.getAllEntities())if(e instanceof InkCelestialDragonEntity dragon){
            cache.remove(e.getUUID());
            if(dragon.isAlive())cache.put(e.getUUID(),new InkDragonVisibility.Snapshot(e.getUUID(),e.position(),e.getYRot(),dragon.visualSnapshot()));
        }
        while(cache.size()>InkDragonVisibility.MAX_CACHED)cache.remove(cache.keySet().iterator().next());
        InkDragonKeepAlive.update(level,cache.values());
        for(var player:level.players()){
            var snapshots=cache.values().stream().filter(s->InkDragonVisibility.distanceSquared(s,player.position())<InkDragonVisibility.RANGE*InkDragonVisibility.RANGE)
                    .sorted(Comparator.comparingDouble(s->InkDragonVisibility.distanceSquared(s,player.position())))
                    .limit(InkDragonVisibility.MAX_SENT).toList();
            InkDragonVisibilityNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),
                    new InkDragonVisibilityNetwork.SnapshotPacket(level.dimension().location(),snapshots));
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof InkCelestialDragonEntity dragon))return;
        var cache=CACHE.get(level);if(cache==null)return;
        // Chunk unload is exactly when the cosmetic view is needed. Death/discard must not leave a ghost.
        // isAlive() is also false after a normal unload, not only after death.
        if(dragon.getHealth()<=0||!InkDragonVisibility.retainAfterRemoval(dragon.getRemovalReason()))cache.remove(dragon.getUUID());
        else {var state=dragon.visualSnapshot();state.putInt("move",0);state.putInt("age",0);cache.put(dragon.getUUID(),new InkDragonVisibility.Snapshot(dragon.getUUID(),dragon.position(),dragon.getYRot(),state));}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){CACHE.remove(level);InkDragonKeepAlive.release(level);}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){CACHE.clear();InkDragonKeepAlive.clear();}
}
