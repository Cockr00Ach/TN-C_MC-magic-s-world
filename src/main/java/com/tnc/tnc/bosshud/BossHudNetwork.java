package com.tnc.tnc.bosshud;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BossHudNetwork {
    private static boolean compatible(String version){return version.equals("1")||version.equals(NetworkRegistry.ABSENT)||version.equals(NetworkRegistry.ACCEPTVANILLA);}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc","epic_boss_hud"),()->"1",BossHudNetwork::compatible,BossHudNetwork::compatible);
    public record Snapshot(UUID bar,UUID entity,String type,float health,float maximum,int phase,int phases){}
    public record Gone(UUID bar){}
    public static void write(Snapshot p,FriendlyByteBuf b){b.writeUUID(p.bar);b.writeUUID(p.entity);b.writeUtf(p.type,96);b.writeFloat(p.health);b.writeFloat(p.maximum);b.writeVarInt(p.phase);b.writeVarInt(p.phases);}
    public static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readUUID(),b.readUUID(),b.readUtf(96),b.readFloat(),b.readFloat(),b.readVarInt(),b.readVarInt());}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        CHANNEL.registerMessage(0,Snapshot.class,BossHudNetwork::write,BossHudNetwork::read,(p,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.bosshud.client.EpicBossHud.accept(p)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Gone.class,(p,b)->b.writeUUID(p.bar),b->new Gone(b.readUUID()),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.bosshud.client.EpicBossHud.remove(p.bar)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static boolean present(ServerPlayer p){return !(p instanceof net.minecraftforge.common.util.FakePlayer)&&p.connection!=null&&CHANNEL.isRemotePresent(p.connection.connection);}
    public static void send(ServerPlayer p,Snapshot value){if(present(p))CHANNEL.send(PacketDistributor.PLAYER.with(()->p),value);}
    public static void gone(ServerPlayer p,UUID bar){if(present(p))CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Gone(bar));}
}
