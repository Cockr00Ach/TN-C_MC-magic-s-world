package com.tnc.tnc.world.yan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YanPrologueNetwork {
    public enum Stage { STORY, FIGHT, DEFEAT, REWIND, END }
    record Defeat(Vec3 position, float yaw) {}
    record Frame(long token, Stage stage, Defeat defeat) {}
    public record Advance(long token) {}

    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc", "yan_prologue"), () -> "2", "2"::equals, "2"::equals);

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.messageBuilder(Frame.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(YanPrologueNetwork::encodeFrame)
                    .decoder(YanPrologueNetwork::decodeFrame)
                    .consumerMainThread((m, context) -> {
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> YanPrologueScreen.receive(m));
                        context.get().setPacketHandled(true);
                    }).add();
            CHANNEL.messageBuilder(Advance.class, 1, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((m, b) -> b.writeLong(m.token()))
                    .decoder(b -> new Advance(b.readLong()))
                    .consumerMainThread((m, context) -> {
                        ServerPlayer player = context.get().getSender();
                        if (player != null) YanPrologue.advance(player, m.token());
                        context.get().setPacketHandled(true);
                    }).add();
        });
    }

    private static void encodeFrame(Frame frame, FriendlyByteBuf buffer) {
        buffer.writeLong(frame.token());
        buffer.writeEnum(frame.stage());
        if (frame.defeat() != null) {
            var point = frame.defeat().position();
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
            buffer.writeFloat(frame.defeat().yaw());
        }
    }

    private static Frame decodeFrame(FriendlyByteBuf buffer) {
        long token = buffer.readLong();
        var stage = buffer.readEnum(Stage.class);
        var defeat = stage == Stage.DEFEAT
                ? new Defeat(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat())
                : null;
        return new Frame(token, stage, defeat);
    }

    static void send(ServerPlayer player, long token, Stage stage) {
        send(player, new Frame(token, stage, null));
    }

    static void sendDefeat(ServerPlayer player, long token, Vec3 position, float yaw) {
        send(player, new Frame(token, Stage.DEFEAT, new Defeat(position, yaw)));
    }

    private static void send(ServerPlayer player, Frame frame) {
        if (!(player instanceof net.minecraftforge.common.util.FakePlayer)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), frame);
        }
    }

    static void advance(long token) { CHANNEL.sendToServer(new Advance(token)); }

    private YanPrologueNetwork() {}
}
