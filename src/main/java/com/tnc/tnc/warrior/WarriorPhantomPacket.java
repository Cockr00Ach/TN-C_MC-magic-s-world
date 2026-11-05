package com.tnc.tnc.warrior;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Other players must see this player's reach, never the observer's own progression. */
public record WarriorPhantomPacket(UUID player, float scale) {
    public void encode(FriendlyByteBuf out) { out.writeUUID(player); out.writeFloat(scale); }
    public static WarriorPhantomPacket decode(FriendlyByteBuf in) {
        UUID player = in.readUUID(); float scale = in.readFloat();
        if (!Float.isFinite(scale) || scale < 1 || scale > 1 + WarriorPhantom.MAX_GROWTH + .001)
            throw new io.netty.handler.codec.DecoderException("invalid warrior phantom scale");
        return new WarriorPhantomPacket(player, scale);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this)); context.setPacketHandled(true);
    }
    private static final class ClientOnly {
        static void accept(WarriorPhantomPacket packet) {
            com.tnc.tnc.warrior.client.WarriorPhantomScales.accept(packet.player(), packet.scale());
        }
    }
}
