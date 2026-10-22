package com.tnc.tnc.world.wind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import java.util.Optional;

/** One targeted S2C message; the camera never moves the server-side player. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CanglanCinematicNetwork {
 private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc","canglan_cinematic"),()->"1","1"::equals,"1"::equals);
 public record Shot(ResourceLocation dimension,int boss,int ticks,boolean execution){}
 @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
  CHANNEL.registerMessage(0,Shot.class,(p,b)->{b.writeResourceLocation(p.dimension());b.writeVarInt(p.boss());b.writeVarInt(p.ticks());b.writeBoolean(p.execution());},
   b->new Shot(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readBoolean()),(p,c)->{
    var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.world.wind.client.CanglanCinematicCamera.start(p)));ctx.setPacketHandled(true);
   },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 }
 public static void send(ServerPlayer player,CanglanWarlordEntity boss,int ticks,boolean execution){
  if(!(player instanceof net.minecraftforge.common.util.FakePlayer))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Shot(player.level().dimension().location(),boss.getId(),ticks,execution));
 }
}
