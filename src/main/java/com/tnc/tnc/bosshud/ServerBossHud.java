package com.tnc.tnc.bosshud;

import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.*;
import java.util.*;

/** Read existing TN-C boss bars without editing their combat classes or identifying them by names. */
@Mod.EventBusSubscriber(modid="tnc")
public final class ServerBossHud {
    private record Access(Field bar,Method phase){}
    private record Entry(ServerBossEvent bar,BossHudTheme theme,boolean owned){}
    private static final Map<Class<?>,Access> ACCESS=new HashMap<>();
    private static final Map<LivingEntity,Entry> ENTRIES=new WeakHashMap<>();
    private ServerBossHud(){}
    public static BossHudTheme theme(LivingEntity entity){var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());return id==null?null:BossHudTheme.forType(id.toString());}
    private static Access access(Class<?> type){return ACCESS.computeIfAbsent(type,c->{
        Field bar=null;Method phase=null;
        for(Class<?> at=c;at!=null&&at.getName().startsWith("com.tnc.tnc.");at=at.getSuperclass())for(var f:at.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&ServerBossEvent.class.isAssignableFrom(f.getType())){f.setAccessible(true);bar=f;break;}
        for(String name:List.of("phase","getPhase"))try{var m=c.getMethod(name);if(m.getReturnType()==int.class){phase=m;break;}}catch(NoSuchMethodException ignored){}
        return new Access(bar,phase);
    });}
    private static Entry ensure(LivingEntity entity){
        var existing=ENTRIES.get(entity);if(existing!=null)return existing;var theme=theme(entity);if(theme==null)return null;
        try{
            var accessor=access(entity.getClass());ServerBossEvent bar=accessor.bar==null?null:(ServerBossEvent)accessor.bar.get(entity);
            boolean own=bar==null;
            if(own&&theme!=BossHudTheme.WIND_GENERAL)return null; // Never duplicate a broken existing boss bar.
            if(own)bar=new ServerBossEvent(entity.getDisplayName(),BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS);
            var entry=new Entry(bar,theme,own);ENTRIES.put(entity,entry);return entry;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot inspect owned TN-C boss bar: "+entity.getType(),failure);}
    }
    public static BossHudNetwork.Snapshot inspect(LivingEntity entity){
        var entry=ensure(entity);if(entry==null)return null;int phase=entry.theme.initialPhase;
        if(!entry.theme.ink())try{var method=access(entity.getClass()).phase;if(method!=null)phase=(int)method.invoke(entity);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        return new BossHudNetwork.Snapshot(entry.bar.getId(),entity.getUUID(),entry.theme.type(),BossHudRules.health(entity.getHealth(),entity.getMaxHealth()),BossHudRules.maximum(entity.getMaxHealth()),BossHudRules.phase(phase,entry.theme.phases),entry.theme.phases);
    }
    public static boolean ownsBar(LivingEntity entity){var entry=ensure(entity);return entry!=null&&entry.owned;}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var entity=event.getEntity();if(entity.level().isClientSide||entity.tickCount%5!=0||theme(entity)==null)return;
        var entry=ensure(entity);if(entry==null)return;
        if(entry.owned){entry.bar.setName(entity.getDisplayName());entry.bar.setProgress(BossHudRules.progress(entity.getHealth(),entity.getMaxHealth()));entry.bar.setVisible(entity.isAlive()&&!entity.isRemoved());}
        if(!entry.bar.isVisible())return;var snapshot=inspect(entity);for(var p:entry.bar.getPlayers())BossHudNetwork.send(p,snapshot);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!(event.getTarget() instanceof LivingEntity entity))return;
        var entry=ensure(entity);if(entry==null)return;if(entry.owned)entry.bar.addPlayer(p);BossHudNetwork.send(p,inspect(entity));
    }
    @SubscribeEvent public static void untracking(PlayerEvent.StopTracking event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!(event.getTarget() instanceof LivingEntity entity))return;
        var entry=ENTRIES.get(entity);if(entry==null)return;if(entry.owned)entry.bar.removePlayer(p);BossHudNetwork.gone(p,entry.bar.getId());
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(!(event.getEntity() instanceof LivingEntity entity)||entity.level().isClientSide)return;
        var entry=ENTRIES.remove(entity);if(entry==null)return;for(var p:entry.bar.getPlayers())BossHudNetwork.gone(p,entry.bar.getId());if(entry.owned)entry.bar.removeAllPlayers();
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){for(var e:ENTRIES.values())if(e.owned)e.bar.removeAllPlayers();ENTRIES.clear();ACCESS.clear();}
}
