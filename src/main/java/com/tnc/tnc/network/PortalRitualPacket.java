package com.tnc.tnc.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** One server-authoritative visual update per active altar, not per player. */
public record PortalRitualPacket(BlockPos landing, int elapsed) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(landing);
        buf.writeInt(elapsed);
    }

    public static PortalRitualPacket decode(FriendlyByteBuf buf) {
        return new PortalRitualPacket(buf.readBlockPos(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientOnly.accept(this));
        supplier.get().setPacketHandled(true);
    }

    private static final class ClientOnly {
        static void accept(PortalRitualPacket packet) {
            com.tnc.tnc.client.PortalRitualRenderer.accept(packet.landing(), packet.elapsed());
        }
    }
}
