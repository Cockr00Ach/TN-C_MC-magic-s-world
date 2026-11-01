package com.tnc.tnc.world.ink;

import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short-lived positional holds, never permanent movement attributes, gravity flags or player NBT. */
@Mod.EventBusSubscriber(modid="tnc")
public final class InkRestraints {
    private record Hold(UUID owner,ServerLevel level,Vec3 point,long expires){}
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private InkRestraints(){}
    public static boolean hold(Mob owner,LivingEntity victim,Vec3 point,int ticks){
        if(!(owner.level() instanceof ServerLevel level)||victim.level()!=level||!owner.isAlive()||!victim.isAlive()
                ||victim instanceof Player p&&(p.isCreative()||p.isSpectator())||victim.isPassenger())return false;
        if(!level.getWorldBorder().isWithinBounds(victim.getBoundingBox().move(point.subtract(victim.position())))
                ||!level.noCollision(victim,victim.getBoundingBox().move(point.subtract(victim.position()))))return false;
        Hold existing=HOLDS.get(victim.getUUID());
        if(existing!=null&&!existing.owner.equals(owner.getUUID())&&existing.expires>level.getGameTime())return false;
        HOLDS.put(victim.getUUID(),new Hold(owner.getUUID(),level,point,level.getGameTime()+Math.max(1,Math.min(ticks,200))));
        if(victim instanceof ServerPlayer player)InkEncounterNetwork.hold(player,ticks);return true;
    }
    public static boolean held(LivingEntity e){return HOLDS.containsKey(e.getUUID());}
    public static void release(Mob owner){HOLDS.entrySet().removeIf(e->{if(!e.getValue().owner.equals(owner.getUUID()))return false;unlock(e.getKey(),e.getValue());return true;});}
    private static void unlock(UUID id,Hold hold){var player=hold.level.getServer().getPlayerList().getPlayer(id);if(player!=null)InkEncounterNetwork.hold(player,0);}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var e=event.getEntity();if(e.level().isClientSide)return;Hold h=HOLDS.get(e.getUUID());if(h==null)return;
        Entity owner=h.level.getEntity(h.owner);
        if(e.level()!=h.level||!e.isAlive()||owner==null||!owner.isAlive()||h.level.getGameTime()>=h.expires
                ||e instanceof Player p&&(p.isCreative()||p.isSpectator())){unlock(e.getUUID(),h);HOLDS.remove(e.getUUID());return;}
        if(!h.level.noCollision(e,e.getBoundingBox().move(h.point.subtract(e.position())))){unlock(e.getUUID(),h);HOLDS.remove(e.getUUID());return;}
        e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;e.hurtMarked=true;
        if(e instanceof ServerPlayer p){if(p.position().distanceToSqr(h.point)>.0004)p.connection.teleport(h.point.x,h.point.y,h.point.z,p.getYRot(),p.getXRot());}
        else e.setPos(h.point);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){HOLDS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){HOLDS.clear();}
}
