package com.tnc.tnc.tavern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Small S2C payload; the client never guesses an island's world coordinates. */
public record TavernRoomPacket(ResourceLocation dimension,List<AABB> rooms) {
    public void encode(FriendlyByteBuf buf){buf.writeResourceLocation(dimension);buf.writeVarInt(rooms.size());for(var b:rooms){buf.writeDouble(b.minX);buf.writeDouble(b.minY);buf.writeDouble(b.minZ);buf.writeDouble(b.maxX);buf.writeDouble(b.maxY);buf.writeDouble(b.maxZ);}}
    public static TavernRoomPacket decode(FriendlyByteBuf buf){var dimension=buf.readResourceLocation();int size=buf.readVarInt();if(size<0||size>8)throw new IllegalArgumentException("Invalid tavern room count");var rooms=new ArrayList<AABB>();for(int i=0;i<size;i++)rooms.add(new AABB(buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble()));return new TavernRoomPacket(dimension,List.copyOf(rooms));}
    public void handle(Supplier<NetworkEvent.Context> context){context.get().enqueueWork(()->ClientOnly.accept(this));context.get().setPacketHandled(true);}
    private static final class ClientOnly{static void accept(TavernRoomPacket packet){com.tnc.tnc.tavern.client.TavernMusicController.accept(packet);}}
}
