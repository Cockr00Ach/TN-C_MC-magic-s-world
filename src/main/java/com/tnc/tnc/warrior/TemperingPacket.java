package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server-to-client display state only. */
public record TemperingPacket(boolean enrolled, int points, int talent, boolean graduate,
                              boolean training, int blocks, int counterTicks, int windTicks, boolean open) {
    public TemperingPacket(boolean enrolled,int points,int talent) { this(enrolled,points,talent,false,false,0,0,0,false); }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enrolled); buffer.writeVarInt(points); buffer.writeVarInt(talent);
        buffer.writeBoolean(graduate);buffer.writeBoolean(training);buffer.writeVarInt(blocks);
        buffer.writeVarInt(counterTicks);buffer.writeVarInt(windTicks);buffer.writeBoolean(open);
    }
    public static TemperingPacket decode(FriendlyByteBuf buffer) {
        return new TemperingPacket(buffer.readBoolean(), TemperingRules.clamp(buffer.readVarInt()), WarriorTalentRules.clamp(buffer.readVarInt()),
            buffer.readBoolean(),buffer.readBoolean(),Math.max(0,Math.min(3,buffer.readVarInt())),
            Math.max(0,Math.min(80,buffer.readVarInt())),Math.max(0,Math.min(WindInfusionRules.COOLDOWN_TICKS,buffer.readVarInt())),buffer.readBoolean());
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
