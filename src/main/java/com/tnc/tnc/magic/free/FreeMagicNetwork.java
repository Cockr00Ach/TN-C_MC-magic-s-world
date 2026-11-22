package com.tnc.tnc.magic.free;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FreeMagicNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","free_magic"),()->"5","5"::equals,"5"::equals);
    public static final int BEGIN=0,PARTIAL=1,FINAL=2,CANCEL=3,STOP=4;
    public static final int CHANT=0,SHOT=1,HIT=2,CLEAR=3,ERROR=4,CHARGE=5,CAST_END=6,IMPACT=7,EQUIPPED_CAST=8;
    public record Input(int action,long session,String text) {
        void encode(FriendlyByteBuf b){b.writeByte(action);b.writeLong(session);b.writeUtf(text,128);}
        static Input decode(FriendlyByteBuf b){return new Input(b.readUnsignedByte(),b.readLong(),b.readUtf(128));}
    }
    public record Event(ResourceLocation dimension,int kind,UUID owner,long token,String text,int shape,int mode,int count,
                        Vec3 at,Vec3 velocity,long started,int duration,int anchor,List<Vec3> targets,
                        int modifiers,int chantPower) {
        public Event {
            if (targets.size() > 8) throw new IllegalArgumentException("Too many water formation targets");
            targets = List.copyOf(targets);
        }

        public Event(ResourceLocation dimension,int kind,UUID owner,long token,String text,int shape,int mode,int count,
                     Vec3 at,Vec3 velocity,long started,int duration,int anchor) {
            this(dimension, kind, owner, token, text, shape, mode, count, at, velocity, started, duration, anchor, List.of());
        }

        public Event(ResourceLocation dimension,int kind,UUID owner,long token,String text,int shape,int mode,int count,
                     Vec3 at,Vec3 velocity,long started,int duration,int anchor,List<Vec3> targets) {
            this(dimension, kind, owner, token, text, shape, mode, count, at, velocity, started, duration, anchor, targets, 0, 0);
        }

        void encode(FriendlyByteBuf b){
            b.writeResourceLocation(dimension);b.writeByte(kind);b.writeUUID(owner);b.writeLong(token);b.writeUtf(text,128);
            b.writeByte(shape);b.writeByte(mode);b.writeByte(count);b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);
            b.writeDouble(velocity.x);b.writeDouble(velocity.y);b.writeDouble(velocity.z);
            b.writeLong(started);b.writeVarInt(duration);b.writeVarInt(anchor);
            b.writeByte(targets.size());
            for (Vec3 target : targets) {
                b.writeDouble(target.x);b.writeDouble(target.y);b.writeDouble(target.z);
            }
            b.writeByte(modifiers);b.writeByte(chantPower);
        }
        static Event decode(FriendlyByteBuf b){return new Event(b.readResourceLocation(),b.readUnsignedByte(),b.readUUID(),b.readLong(),
                b.readUtf(128),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong(),b.readVarInt(),b.readVarInt(),readTargets(b),
                b.readUnsignedByte(),b.readUnsignedByte());}

        private static List<Vec3> readTargets(FriendlyByteBuf buffer) {
            int count = buffer.readUnsignedByte();
            if (count > 8) throw new IllegalArgumentException("Too many water formation targets");
            var targets = new ArrayList<Vec3>(count);
            for (int i = 0; i < count; i++) targets.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
            return targets;
        }
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        CHANNEL.messageBuilder(Input.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Input::encode).decoder(Input::decode)
                .consumerMainThread((p,c)->{var sender=c.get().getSender();if(sender!=null)FreeMagicServer.input(sender,p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Event.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Event::encode).decoder(Event::decode)
                .consumerMainThread((p,c)->{ClientOnly.accept(p);c.get().setPacketHandled(true);}).add();
    }
    static void send(ServerPlayer player, Event event) {
        if (!player.hasDisconnected()) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), event);
    }

    static void showNearby(ServerLevel level, Set<UUID> viewers, Event event) {
        showNearby(level, viewers, event, event.at());
    }

    static void showNearby(ServerLevel level, Set<UUID> viewers, Event event, Vec3 center) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) > 64 * 64) continue;
            viewers.add(player.getUUID());
            send(player, event);
        }
    }

    static void finishForViewers(ServerLevel level, Set<UUID> viewers, Event event) {
        for (ServerPlayer player : level.players()) {
            if (viewers.contains(player.getUUID())) send(player, event);
        }
    }
    private static final class ClientOnly {
        static void accept(Event e){com.tnc.tnc.magic.free.client.FreeMagicClient.accept(e);}
    }
    private FreeMagicNetwork(){}
}
