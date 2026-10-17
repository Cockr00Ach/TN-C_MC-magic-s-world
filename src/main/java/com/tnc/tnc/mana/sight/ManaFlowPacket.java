package com.tnc.tnc.mana.sight;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
public record ManaFlowPacket(ResourceLocation dimension,List<ManaFlow> flows){
 public void encode(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeVarInt(flows.size());for(var f:flows){b.writeDouble(f.from().x);b.writeDouble(f.from().y);b.writeDouble(f.from().z);b.writeDouble(f.to().x);b.writeDouble(f.to().y);b.writeDouble(f.to().z);b.writeVarInt(f.amount());b.writeInt(f.color());b.writeByte(f.kind());}}
 public static ManaFlowPacket decode(FriendlyByteBuf b){var dimension=b.readResourceLocation();int count=b.readVarInt();if(count<0||count>128)throw new IllegalArgumentException("mana flow packet limit");var flows=new ArrayList<ManaFlow>();for(int i=0;i<count;i++){var f=new ManaFlow(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt(),b.readInt(),b.readUnsignedByte());if(f.valid())flows.add(f);}return new ManaFlowPacket(dimension,List.copyOf(flows));}
 public static void handle(ManaFlowPacket p,java.util.function.Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->com.tnc.tnc.mana.sight.client.ManaSightClient.accept(p.dimension(),p.flows()));c.get().setPacketHandled(true);}
}
