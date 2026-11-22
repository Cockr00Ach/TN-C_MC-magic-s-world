package com.tnc.tnc.magic.free;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ElementalNetwork {
    public static final int FORM=0,ACTIVE=1,END=2,IMPACT=3,BURN=4,STATUS=5,ARC=6;
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc","elemental_magic"),()->"2","2"::equals,"2"::equals);
    public record Visual(ResourceLocation dimension,UUID owner,long id,int phase,int ability,int flags,int power,
                         Vec3 at,Vec3 velocity,long started,int duration,int target){
        void encode(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeUUID(owner);b.writeLong(id);b.writeByte(phase);b.writeVarInt(ability);b.writeByte(flags);b.writeByte(power);vec(b,at);vec(b,velocity);b.writeLong(started);b.writeVarInt(duration);b.writeInt(target);}
        static Visual decode(FriendlyByteBuf b){return new Visual(b.readResourceLocation(),b.readUUID(),b.readLong(),b.readUnsignedByte(),b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),vec(b),vec(b),b.readLong(),b.readVarInt(),b.readInt());}
    }
    private static void vec(FriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vec(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){
        CHANNEL.messageBuilder(Visual.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Visual::encode).decoder(Visual::decode)
                .consumerMainThread((p,c)->{ClientOnly.accept(p);c.get().setPacketHandled(true);}).add();
    }
    static void show(ServerPlayer owner,Set<UUID> viewers,Visual packet){
        for(var viewer:owner.getServer().getPlayerList().getPlayers())if(viewer.level().dimension().location().equals(packet.dimension())&&(viewers.contains(viewer.getUUID())||viewer.distanceToSqr(packet.at)<=80*80||viewer==owner)){
            viewers.add(viewer.getUUID());CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet);
        }
    }
    private static final class ClientOnly {static void accept(Visual p){com.tnc.tnc.magic.free.client.ElementalVisuals.accept(p);}}
    private ElementalNetwork(){}
}
