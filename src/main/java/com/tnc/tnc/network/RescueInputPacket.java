package com.tnc.tnc.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Only a held intent; no client-selected health, progress, team or target. */
public record RescueInputPacket(boolean held) {
    public void encode(FriendlyByteBuf b){b.writeBoolean(held);}
    public static RescueInputPacket decode(FriendlyByteBuf b){return new RescueInputPacket(b.readBoolean());}
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->{
            var p=context.getSender();if(p!=null)com.tnc.tnc.combat.DownedCombat.hold(p,held);
        });context.setPacketHandled(true);
    }
}
