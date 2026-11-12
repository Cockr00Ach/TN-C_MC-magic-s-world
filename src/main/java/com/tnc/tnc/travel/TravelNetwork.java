package com.tnc.tnc.travel;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** 元素附身自己的通道；版本独立，不牵动 {@code tnc:main} 的协议号。 */
public final class TravelNetwork {

    public static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "travel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private TravelNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(TravelInputPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TravelInputPacket::encode)
                .decoder(TravelInputPacket::decode)
                .consumerMainThread(TravelInputPacket::handle)
                .add();
        CHANNEL.messageBuilder(TravelStatePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TravelStatePacket::encode)
                .decoder(TravelStatePacket::decode)
                .consumerMainThread(TravelStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(TravelAppearancePacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TravelAppearancePacket::encode)
                .decoder(TravelAppearancePacket::decode)
                .consumerMainThread(TravelAppearancePacket::handle)
                .add();
    }

    public static void sendState(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                TravelFormService.statePacket(player));
    }

    /** 进入/退出时告诉周围所有客户端：这个 entityId 该显示成什么。 */
    public static void sendAppearance(ServerPlayer player, boolean active) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                TravelFormService.appearancePacket(player, active));
    }

    /** 新追踪者补一份，避免走远再回来的人看不到形态。 */
    public static void sendAppearanceTo(ServerPlayer target, ServerPlayer receiver) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                TravelFormService.appearancePacket(target, TravelFormService.active(target)));
    }
}
