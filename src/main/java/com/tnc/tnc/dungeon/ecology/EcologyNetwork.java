package com.tnc.tnc.dungeon.ecology;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only the observer who actually met the server gaze test receives the flash. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class EcologyNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(EcologyContent.id("dungeon_ecology"),()->"1","1"::equals,"1"::equals);
    private record Flash(int ticks){}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->CHANNEL.messageBuilder(Flash.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->b.writeVarInt(m.ticks)).decoder(b->new Flash(Math.max(0,Math.min(100,b.readVarInt())))).consumerMainThread((m,c)->{com.tnc.tnc.dungeon.ecology.client.EcologyClient.flash(m.ticks);c.get().setPacketHandled(true);}).add());}
    public static void flash(ServerPlayer player,int ticks){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Flash(ticks));}
}
