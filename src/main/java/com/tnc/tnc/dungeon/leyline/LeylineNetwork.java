package com.tnc.tnc.dungeon.leyline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class LeylineNetwork {
 static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(com.tnc.tnc.dungeon.DungeonContent.id("leyline_rooms"),()->"1","1"::equals,"1"::equals);
 record State(CompoundTag data){}
 @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){e.enqueueWork(()->CHANNEL.messageBuilder(State.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->b.writeNbt(m.data)).decoder(b->new State(b.readNbt())).consumerMainThread((m,c)->{if(m.data!=null)Client.accept(m.data);c.get().setPacketHandled(true);}).add());}
 static final class Client {static void accept(CompoundTag data){com.tnc.tnc.dungeon.client.LeylineClient.accept(data);}}
 public static void sync(ServerPlayer p,CompoundTag data){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(data));}
}

