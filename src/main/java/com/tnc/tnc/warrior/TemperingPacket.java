package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server-to-client display state only. */
public record TemperingPacket(boolean enrolled, int points, int talent) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(enrolled); buffer.writeVarInt(points); buffer.writeVarInt(talent); }
    public static TemperingPacket decode(FriendlyByteBuf buffer) {
        return new TemperingPacket(buffer.readBoolean(), TemperingRules.clamp(buffer.readVarInt()), WarriorTalentRules.clamp(buffer.readVarInt()));
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this));
        context.setPacketHandled(true);
    }
    private static class ClientOnly {
        static void accept(TemperingPacket packet) { com.tnc.tnc.warrior.client.TemperingClient.accept(packet); }
    }
}
