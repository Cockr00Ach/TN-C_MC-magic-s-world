package com.tnc.tnc.warrior;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
public record WarriorExecutionFx(int owner,int target,int kind,int duration){
 public void encode(FriendlyByteBuf b){b.writeInt(owner);b.writeInt(target);b.writeByte(kind);b.writeInt(duration);}
 public static WarriorExecutionFx decode(FriendlyByteBuf b){var p=new WarriorExecutionFx(b.readInt(),b.readInt(),b.readUnsignedByte(),b.readInt());if(p.kind>3||p.duration<1||p.duration>600)throw new io.netty.handler.codec.DecoderException("Invalid execution effect");return p;}
 public void handle(java.util.function.Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->com.tnc.tnc.warrior.client.WarriorExecutionVisual.accept(this));c.get().setPacketHandled(true);}
 public static void send(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.entity.LivingEntity target,int kind,int duration){com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new WarriorExecutionFx(p.getId(),target.getId(),kind,duration));}
}
