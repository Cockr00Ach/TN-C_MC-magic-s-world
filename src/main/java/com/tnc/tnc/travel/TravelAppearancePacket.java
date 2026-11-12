package com.tnc.tnc.travel;

import com.tnc.tnc.travel.client.TravelAppearance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 周围客户端：某个玩家当前是否处于元素形态。
 *
 * <p>{@link TravelStatePacket} 只发给本人，用来画 HUD；第三人称要把玩家模型
 * 换成闪电/幽灵，必须让**其他玩家**也知道谁在附身。这个包只描述“外观”，
 * 不参与任何服务端判定。
 */
public record TravelAppearancePacket(int entityId, int element, int tier, boolean active) {

    public static TravelAppearancePacket inactive(int entityId) {
        return new TravelAppearancePacket(entityId, -1, 0, false);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(Math.max(0, entityId));
        buffer.writeByte(active ? Math.max(0, Math.min(255, element)) + 1 : 0);
        buffer.writeByte(Math.max(0, Math.min(255, tier)));
        buffer.writeBoolean(active);
    }

    public static TravelAppearancePacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int raw = buffer.readUnsignedByte();
        int tier = buffer.readUnsignedByte();
        boolean active = buffer.readBoolean();
        return new TravelAppearancePacket(entityId, active ? raw - 1 : -1, tier, active);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        TravelAppearancePacket packet = this;
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TravelAppearance.accept(packet)));
        context.setPacketHandled(true);
    }
}
