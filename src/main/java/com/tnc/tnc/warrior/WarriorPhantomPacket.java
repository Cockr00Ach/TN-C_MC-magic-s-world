package com.tnc.tnc.warrior;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Other players must see this player's reach, never the observer's own progression. */
public record WarriorPhantomPacket(UUID player, float scale,int points) {
    public WarriorPhantomPacket(UUID player,float scale){this(player,scale,0);}
    public void encode(FriendlyByteBuf out) { out.writeUUID(player); out.writeFloat(scale); out.writeVarInt(points); }
    public static WarriorPhantomPacket decode(FriendlyByteBuf in) {
        UUID player = in.readUUID(); float scale = in.readFloat();
        // Accept the previous wire range; client rendering clamps legacy peaks to the new cap.
        if (!Float.isFinite(scale) || scale < 1 || scale > 10.001f)
            throw new io.netty.handler.codec.DecoderException("invalid warrior phantom scale");
        int points=in.readVarInt();
        if(points<0 || points>TemperingRules.MAX_POINTS)
            throw new io.netty.handler.codec.DecoderException("invalid warrior tempering points");
        return new WarriorPhantomPacket(player, scale,points);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this)); context.setPacketHandled(true);
    }
    private static final class ClientOnly {
        static void accept(WarriorPhantomPacket packet) {
            com.tnc.tnc.warrior.client.WarriorPhantomScales.accept(packet.player(), packet.scale(),packet.points());
        }
    }
}
