package com.tnc.tnc.warrior;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
public record WarriorSwordFx(int player,Vec3 from,Vec3 to,int kind){
 public void encode(FriendlyByteBuf b){b.writeInt(player);b.writeDouble(from.x);b.writeDouble(from.y);b.writeDouble(from.z);b.writeDouble(to.x);b.writeDouble(to.y);b.writeDouble(to.z);b.writeByte(kind);}
 public static WarriorSwordFx decode(FriendlyByteBuf b){var p=new WarriorSwordFx(b.readInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readUnsignedByte());if(!Double.isFinite(p.from.lengthSqr()+p.to.lengthSqr())||p.from.distanceTo(p.to)>12||p.kind>1)throw new io.netty.handler.codec.DecoderException("Invalid sword path");return p;}
 public void handle(java.util.function.Supplier<NetworkEvent.Context> s){s.get().enqueueWork(()->com.tnc.tnc.warrior.client.WarriorSwordFxRenderer.accept(this));s.get().setPacketHandled(true);}
 public static void send(net.minecraft.server.level.ServerPlayer p,Vec3 a,Vec3 b,int kind){com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new WarriorSwordFx(p.getId(),a,b,kind));}
}
