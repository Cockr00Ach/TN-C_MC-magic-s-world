package com.tnc.tnc.arcana;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ArcanaNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.parse("tnc:deep_arcana"),()->"1","1"::equals,"1"::equals);
    public record State(CompoundTag tag){}
    public record Pact(int entity,int offer){}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){e.enqueueWork(()->{
        CHANNEL.messageBuilder(State.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((m,b)->b.writeNbt(m.tag)).decoder(b->new State(b.readNbt())).consumerMainThread((m,c)->{Client.receive(m.tag);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Pact.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeInt(m.entity);b.writeVarInt(m.offer);}).decoder(b->new Pact(b.readInt(),b.readVarInt())).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)ArcanaSpirits.trade(p,m.entity,m.offer);c.get().setPacketHandled(true);}).add();
    });}
    private static final class Client{static void receive(CompoundTag tag){com.tnc.tnc.arcana.client.ArcanaClient.receive(tag);}}
    public static void sync(ServerPlayer p,CompoundTag tag){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(tag.copy()));}
    public static void pact(int id,int offer){CHANNEL.sendToServer(new Pact(id,offer));}
}
