package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

/** Short, world-space mesh impacts: hit, parry contact, and counter sweep. */
public record WarriorImpactPacket(ResourceLocation dimension, Vec3 at, int kind, int color, long started) {
    public void encode(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);b.writeByte(kind);b.writeInt(color);b.writeLong(started);}
    public static WarriorImpactPacket decode(FriendlyByteBuf b){
        var dim=b.readResourceLocation();var at=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());int kind=b.readUnsignedByte();int color=b.readInt();long time=b.readLong();
        if(!Double.isFinite(at.lengthSqr())||kind>4)throw new io.netty.handler.codec.DecoderException("invalid impact");
        return new WarriorImpactPacket(dim,at,kind,color,time);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->ClientOnly.accept(this));c.setPacketHandled(true);}
    private static class ClientOnly{static void accept(WarriorImpactPacket p){com.tnc.tnc.warrior.client.WarriorImpactRenderer.accept(p);}}
    public static void send(ServerPlayer p,Vec3 at,int kind){
        var packet=new WarriorImpactPacket(p.level().dimension().location(),at,kind,WarriorCombatEffects.style(p).color,p.level().getGameTime());
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),packet);
    }
}
