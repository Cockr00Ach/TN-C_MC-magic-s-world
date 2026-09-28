package com.tnc.tnc.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CombatStatePacket(int mode,int progress,String text,int target) {
    public void encode(FriendlyByteBuf b){b.writeVarInt(mode);b.writeVarInt(progress);b.writeUtf(text,128);b.writeInt(target);}
    public static CombatStatePacket decode(FriendlyByteBuf b){return new CombatStatePacket(b.readVarInt(),b.readVarInt(),b.readUtf(128),b.readInt());}
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->ClientOnly.accept(this));context.setPacketHandled(true);
    }
    private static class ClientOnly {static void accept(CombatStatePacket p){com.tnc.tnc.client.CombatHud.accept(p);}}
}
