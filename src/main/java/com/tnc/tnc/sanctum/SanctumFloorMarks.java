package com.tnc.tnc.sanctum;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SanctumFloorMarks {
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","sanctum_floor"),()->"1","1"::equals,"1"::equals);
    public record Mark(UUID owner,String key,Vec3 center,float outer,float inner,int rgb,long start,int duration,float speed) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(owner); buf.writeUtf(key,64);
            buf.writeDouble(center.x); buf.writeDouble(center.y); buf.writeDouble(center.z);
            buf.writeFloat(outer);buf.writeFloat(inner);buf.writeInt(rgb);
            buf.writeLong(start);buf.writeVarInt(duration);buf.writeFloat(speed);
        }
        static Mark read(FriendlyByteBuf buf) {
            return new Mark(buf.readUUID(),buf.readUtf(64),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),
                    buf.readFloat(),buf.readFloat(),buf.readInt(),buf.readLong(),buf.readVarInt(),buf.readFloat());
        }
    }
    @SubscribeEvent public static void register(FMLCommonSetupEvent event) {
        CHANNEL.registerMessage(0,Mark.class,Mark::write,Mark::read,(mark,context)->{
            var ctx=context.get();
            ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.sanctum.client.SanctumFloorRenderer.accept(mark)));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void mark(List<ServerPlayer> players,UUID entity,String key,Vec3 where,double outer,double inner,
                            int rgb,int duration,double speed) {
        if(players.isEmpty())return;
        var packet=new Mark(entity,key,where,(float)outer,(float)inner,rgb,players.get(0).level().getGameTime(),duration,(float)speed);
        for(var p:players)if(p.connection!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->p),packet);
    }
    public static void clear(List<ServerPlayer> players,UUID owner){
        mark(players,owner,"",Vec3.ZERO,0,0,0,0,0);
    }
}
