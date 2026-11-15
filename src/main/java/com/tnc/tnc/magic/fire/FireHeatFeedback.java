package com.tnc.tnc.magic.fire;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import java.util.*;

/** One recent target per caster, updated at 4 Hz. No heat is inferred on the client. */
@Mod.EventBusSubscriber(modid="tnc")
public final class FireHeatFeedback {
    public record Target(ResourceLocation dimension,UUID target,float heat,int form){}
    private record Watch(ResourceLocation dimension,UUID target,long until){}
    private static final Map<UUID,Watch> WATCHES=new HashMap<>();
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","fire_heat_feedback"),()->"1","1"::equals,"1"::equals);
    public static void watch(ServerPlayer player,LivingEntity target){
        if(player==null||target==null)return;
        WATCHES.put(player.getUUID(),new Watch(target.level().dimension().location(),target.getUUID(),player.level().getGameTime()+100));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%5!=0)return;
        for(var it=WATCHES.entrySet().iterator();it.hasNext();){
            var entry=it.next();var player=e.getServer().getPlayerList().getPlayer(entry.getKey());var watch=entry.getValue();
            if(player==null||!player.isAlive()||player.level().getGameTime()>watch.until||!player.level().dimension().location().equals(watch.dimension)){it.remove();continue;}
            var entity=player.serverLevel().getEntity(watch.target);
            if(!(entity instanceof LivingEntity target)||!target.isAlive()||target.distanceToSqr(player)>64*64){it.remove();continue;}
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Target(watch.dimension,watch.target,FireHeat.of(target),TNFireForms.tier(player)));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){WATCHES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){WATCHES.clear();}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){
            CHANNEL.registerMessage(0,Target.class,(p,b)->{b.writeResourceLocation(p.dimension);b.writeUUID(p.target);b.writeFloat(p.heat);b.writeVarInt(p.form);},
                b->{var dim=b.readResourceLocation();var id=b.readUUID();float heat=b.readFloat();int form=b.readVarInt();if(!Float.isFinite(heat)||heat<0||heat>FireHeat.MAX||form<0||form>5)throw new IllegalArgumentException("Invalid heat feedback");return new Target(dim,id,heat,form);},
                (p,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.client.FireHeatHud.accept(p)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }
}
