package com.tnc.tnc.world.wind;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short server-owned spear-tip leases; never persist a captive/gravity flag in player saves. */
@Mod.EventBusSubscriber(modid="tnc")
public final class CanglanExecutionPins {
    private static final class Pin {
        final CanglanWarlordEntity boss;final LivingEntity victim;final boolean gravity;final long expires;
        Vec3 point;
        Pin(CanglanWarlordEntity b,LivingEntity v,Vec3 p){boss=b;victim=v;point=p;gravity=v.isNoGravity();expires=b.level().getGameTime()+100;}
    }
    private static final Map<UUID,Pin> PINS=new HashMap<>();
    private CanglanExecutionPins(){}
    public static boolean held(LivingEntity e){return PINS.containsKey(e.getUUID());}
    private static boolean valid(Pin pin){
        return pin.boss.isAlive()&&!pin.boss.isRemoved()&&pin.victim.isAlive()&&!pin.victim.isRemoved()
                &&pin.victim.level()==pin.boss.level()&&pin.boss.level().getGameTime()<pin.expires
                &&(!(pin.victim instanceof Player p)||!p.isCreative()&&!p.isSpectator());
    }
    private static boolean safe(LivingEntity e,Vec3 point){
        var box=e.getBoundingBox().move(point.subtract(e.position()));
        return Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                &&point.y>e.level().getMinBuildHeight()&&box.maxY<e.level().getMaxBuildHeight()
                &&e.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point))
                &&e.level().getWorldBorder().isWithinBounds(box)&&e.level().noCollision(e,box);
    }
    public static boolean bind(CanglanWarlordEntity boss,LivingEntity victim,Vec3 point){
        if(PINS.containsKey(victim.getUUID())||victim.isPassenger()||!safe(victim,point))return false;
        Pin pin=new Pin(boss,victim,point);if(!valid(pin))return false;
        PINS.put(victim.getUUID(),pin);victim.stopUsingItem();victim.setSprinting(false);force(pin);return true;
    }
    public static boolean move(CanglanWarlordEntity boss,LivingEntity victim,Vec3 point){
        Pin pin=PINS.get(victim.getUUID());
        if(pin==null||pin.boss!=boss)return false;
        if(!valid(pin)||!safe(victim,point)){release(boss,victim);return false;}
        pin.point=point;force(pin);return true;
    }
    private static void force(Pin pin){
        // Do not turn on NoGravity: vanilla can serialize that flag during a crash/save mid-capture.
        var v=pin.victim;v.setOnGround(false);v.setDeltaMovement(Vec3.ZERO);v.fallDistance=0;v.hurtMarked=true;v.setSprinting(false);
        if(v instanceof ServerPlayer p&&!(p instanceof net.minecraftforge.common.util.FakePlayer)){
            if(p.position().distanceToSqr(pin.point)>.000001)p.connection.teleport(pin.point.x,pin.point.y,pin.point.z,p.getYRot(),p.getXRot());
        }else v.setPos(pin.point);
    }
    public static void release(CanglanWarlordEntity boss,LivingEntity victim){
        if(victim==null)return;Pin pin=PINS.get(victim.getUUID());if(pin==null||pin.boss!=boss)return;
        PINS.remove(victim.getUUID());victim.setNoGravity(pin.gravity);victim.fallDistance=0;victim.setDeltaMovement(Vec3.ZERO);victim.hurtMarked=true;
    }
    public static void releaseAll(CanglanWarlordEntity boss){for(var pin:new ArrayList<>(PINS.values()))if(pin.boss==boss)release(boss,pin.victim);}
    private static void enforce(LivingEntity v){Pin pin=PINS.get(v.getUUID());if(pin==null)return;if(!valid(pin)||!safe(v,pin.point))release(pin.boss,v);else force(pin);}
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&!e.player.level().isClientSide)enforce(e.player);}
    @SubscribeEvent public static void livingTick(LivingEvent.LivingTickEvent e){if(!(e.getEntity() instanceof Player)&&!e.getEntity().level().isClientSide)enforce(e.getEntity());}
    @SubscribeEvent public static void interact(PlayerInteractEvent e){if(held(e.getEntity())&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(held(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start e){if(held(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){Pin p=PINS.get(e.getEntity().getUUID());if(p!=null)release(p.boss,p.victim);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof CanglanWarlordEntity b)b.battle().syncExecution(p);}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){logoutRelease(e.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){logoutRelease(e.getEntity());}
    private static void logoutRelease(LivingEntity v){Pin p=PINS.get(v.getUUID());if(p!=null)release(p.boss,v);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){for(var p:new ArrayList<>(PINS.values()))release(p.boss,p.victim);}
}
