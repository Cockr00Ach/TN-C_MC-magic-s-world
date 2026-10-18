package com.tnc.tnc.casino;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;
public record CasinoRoomPacket(ResourceLocation dimension,List<AABB> rooms){
 public void encode(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeVarInt(rooms.size());for(var a:rooms){b.writeDouble(a.minX);b.writeDouble(a.minY);b.writeDouble(a.minZ);b.writeDouble(a.maxX);b.writeDouble(a.maxY);b.writeDouble(a.maxZ);}}
 public static CasinoRoomPacket decode(FriendlyByteBuf b){var id=b.readResourceLocation();int n=b.readVarInt();if(n<0||n>4)throw new IllegalArgumentException("casino regions");var boxes=new ArrayList<AABB>();for(int i=0;i<n;i++)boxes.add(new AABB(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));return new CasinoRoomPacket(id,List.copyOf(boxes));}
 public static void handle(CasinoRoomPacket p,java.util.function.Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->com.tnc.tnc.casino.client.CasinoMusicController.accept(p));c.get().setPacketHandled(true);}
}
