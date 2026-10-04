package com.tnc.tnc.life.pasture;

import java.util.*;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.wonders.*;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class PastureEffectEvents {
    private record Local(ServerLevel level,BlockPos position,long expires,UUID identity){}
    private static final Map<UUID,Local> LIGHTS=new HashMap<>();
    public static boolean localLight(ServerPlayer p,BlockPos at){
        var level=p.serverLevel();if(!level.hasChunkAt(at))return false;
        Local old=LIGHTS.get(p.getUUID());
        if(old!=null&&!old.position.equals(at)&&old.level.hasChunkAt(old.position)&&old.level.getBlockEntity(old.position) instanceof LightNodeEntity node&&old.identity.equals(node.bloom()))old.level.setBlock(old.position,Blocks.AIR.defaultBlockState(),3);
        if(!level.getBlockState(at).isAir()&&!(level.getBlockEntity(at) instanceof LightNodeEntity node&&p.getUUID().equals(node.bloom())))return false;
        if(level.getBlockState(at).isAir())level.setBlock(at,WonderContent.LIGHT_NODE.defaultBlockState(),3);
        if(level.getBlockEntity(at) instanceof LightNodeEntity node){node.bind(p.getUUID(),level.getGameTime()+25);LIGHTS.put(p.getUUID(),new Local(level,at,level.getGameTime()+25,p.getUUID()));return true;}
        return false;
    }
    @SubscribeEvent public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){
        if(e.getEntity() instanceof ServerPlayer p){var tag=p.getPersistentData();long now=p.level().getGameTime();tag.putLong("TncLastMealDamage",now);tag.remove("TncQuietUntil");
            if(tag.getLong("TncGuardMealUntil")>now&&e.getAmount()>0){e.setAmount(Math.max(0,e.getAmount()-2));tag.remove("TncGuardMealUntil");}
        }
    }
    @SubscribeEvent public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent e){e.getEntity().getPersistentData().remove("TncQuietUntil");}
    @SubscribeEvent public static void vibration(net.minecraftforge.event.VanillaGameEvent e){if(e.getCause() instanceof ServerPlayer p&&e.getVanillaEvent()==net.minecraft.world.level.gameevent.GameEvent.STEP&&p.getPersistentData().getLong("TncQuietUntil")>p.level().getGameTime())e.setCanceled(true);}
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;applyMeals(p,p.level().getGameTime());}
    public static void applyMeals(ServerPlayer p,long now){var tag=p.getPersistentData();if(tag.getLong("TncWarmUntil")>now)p.setTicksFrozen(0);
        if(tag.getLong("TncTravelMealUntil")>=now&&tag.getInt("TncTravelMana")>0&&now>=tag.getLong("TncTravelMealNext")){var magic=MagicStone.getOrNull(p);if(magic!=null&&magic.getMana()<magic.getMaxMana()){magic.addMana(1);MagicStoneNetwork.syncTo(p);}tag.putInt("TncTravelMana",tag.getInt("TncTravelMana")-1);tag.putLong("TncTravelMealNext",now+100);}
        if(tag.getLong("TncRestMealUntil")>=now&&tag.getInt("TncRestHeals")>0&&now>=tag.getLong("TncRestMealNext")){if(tag.getLong("TncLastMealDamage")>tag.getLong("TncRestMealStarted"))tag.putInt("TncRestHeals",0);else{p.heal(1);tag.putInt("TncRestHeals",tag.getInt("TncRestHeals")-1);tag.putLong("TncRestMealNext",now+100);}}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var iterator=LIGHTS.values().iterator();while(iterator.hasNext()){var light=iterator.next();if(light.level.getGameTime()>=light.expires){if(light.level.hasChunkAt(light.position)&&light.level.getBlockEntity(light.position) instanceof LightNodeEntity node&&light.identity.equals(node.bloom()))light.level.setBlock(light.position,Blocks.AIR.defaultBlockState(),3);iterator.remove();}}var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null&&server.getTickCount()%20==0)for(var l:server.getAllLevels())PastureMoistureData.get(l).tick(l);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){LIGHTS.clear();ManaBottleItem.clearSessions();}
}
