package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

/** Short, world-space mesh impacts: hit, parry contact, and counter sweep. */
public record WarriorImpactPacket(ResourceLocation dimension, Vec3 at, int kind, int color, long started, int attacker, float targetSize, Vec3 normal, int seed, boolean metal) {
    public WarriorImpactPacket(ResourceLocation dimension, Vec3 at, int kind, int color, long started) {
        this(dimension,at,kind,color,started,-1,0,new Vec3(0,0,1),0,false);
    }
    public void encode(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);b.writeByte(kind);b.writeInt(color);b.writeLong(started);b.writeInt(attacker);b.writeFloat(targetSize);b.writeDouble(normal.x);b.writeDouble(normal.y);b.writeDouble(normal.z);b.writeInt(seed);b.writeBoolean(metal);}
    public static WarriorImpactPacket decode(FriendlyByteBuf b){
        var dim=b.readResourceLocation();var at=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());int kind=b.readUnsignedByte();int color=b.readInt();long time=b.readLong();
        int attacker=b.readInt();float size=b.readFloat();var normal=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());int seed=b.readInt();boolean metal=b.readBoolean();
        if(!Float.isFinite(size)||size<0||size>128||!Double.isFinite(normal.lengthSqr())||normal.lengthSqr()<.5||normal.lengthSqr()>1.5)throw new io.netty.handler.codec.DecoderException("invalid impact frame");
        if(!Double.isFinite(at.lengthSqr())||kind>4)throw new io.netty.handler.codec.DecoderException("invalid impact");
        return new WarriorImpactPacket(dim,at,kind,color,time,attacker,size,normal,seed,metal);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->ClientOnly.accept(this));c.setPacketHandled(true);}
    private static class ClientOnly{static void accept(WarriorImpactPacket p){com.tnc.tnc.warrior.client.WarriorImpactRenderer.accept(p);}}
    public static void hit(ServerPlayer p,net.minecraft.world.entity.LivingEntity target,boolean heavy,Vec3 source) {
        var box=target.getBoundingBox();var normal=WarriorHitFeedback.normal(box,source);
        var at=WarriorHitFeedback.contact(box,source);
        var packet=new WarriorImpactPacket(p.level().dimension().location(),at,heavy?3:0,
            WarriorCombatEffects.style(p).color,p.level().getGameTime(),p.getId(),
            (float)Math.min(128,Math.max(target.getBbHeight(),target.getBbWidth())),normal,p.getRandom().nextInt(),target.getArmorValue()>0);
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),packet);
    }
    public static void send(ServerPlayer p,Vec3 at,int kind){
        var packet=new WarriorImpactPacket(p.level().dimension().location(),at,kind,WarriorCombatEffects.style(p).color,p.level().getGameTime());
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),packet);
    }
}
