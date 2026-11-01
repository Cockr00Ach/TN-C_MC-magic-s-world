package com.tnc.tnc.skyacademy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import java.util.Optional;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AcademyNetwork {
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.parse("tnc:sky_academy"),()->"2","2"::equals,"2"::equals);
    public record View(CompoundTag data){}
    public record Action(long token,String action){}
    public record ArrivalReady(long token){}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->{
        CHANNEL.registerMessage(0,View.class,(p,b)->b.writeNbt(p.data),b->new View(b.readNbt()),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.skyacademy.client.AcademyClient.receive(p.data)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Action.class,(p,b)->{b.writeLong(p.token);b.writeUtf(p.action,24);},b->new Action(b.readLong(),b.readUtf(24)),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->{if(ctx.getSender()!=null)AcademyLessons.action(ctx.getSender(),p.token,p.action);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,ArrivalReady.class,(p,b)->b.writeLong(p.token),b->new ArrivalReady(b.readLong()),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->{if(ctx.getSender()!=null)com.tnc.tnc.world.SkyhavenArrival.ready(ctx.getSender(),p.token);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    });}
    public static void send(ServerPlayer p,CompoundTag t){if(!(p instanceof net.minecraftforge.common.util.FakePlayer))CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(t));}
    public static void action(long token,String action){CHANNEL.sendToServer(new Action(token,action));}
    public static void arrivalReady(long token){CHANNEL.sendToServer(new ArrivalReady(token));}
}
