package com.tnc.tnc.travel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：附身状态，给自己看 HUD。 */
public record TravelStatePacket(int element, int tier, int ticksLeft, int totalTicks,
                                int cooldownTicks, int skillCooldownTicks) {

    public static TravelStatePacket inactive(int cooldownTicks) {
        return new TravelStatePacket(-1, 0, 0, 0, Math.max(0, cooldownTicks), 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(Math.max(0, Math.min(255, element + 1)));
        buffer.writeByte(Math.max(0, Math.min(255, tier)));
        buffer.writeVarInt(Math.max(0, ticksLeft));
        buffer.writeVarInt(Math.max(0, totalTicks));
        buffer.writeVarInt(Math.max(0, cooldownTicks));
        buffer.writeVarInt(Math.max(0, skillCooldownTicks));
    }

    public static TravelStatePacket decode(FriendlyByteBuf buffer) {
        return new TravelStatePacket(buffer.readUnsignedByte() - 1,
                buffer.readUnsignedByte(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        TravelStatePacket packet = this;
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TravelClientState.accept(packet)));
        context.setPacketHandled(true);
    }
}
