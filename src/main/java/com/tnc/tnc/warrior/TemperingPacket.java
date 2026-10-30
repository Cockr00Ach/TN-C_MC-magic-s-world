package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端的只读状态。
 *
 * <p>{@code role} 是当前召出的教官（{@code counter/guard/blade/wind}，没有则空串），
 * 面板据此显示「正在跟谁练」。
 */
public record TemperingPacket(boolean enrolled, int points, int talent, boolean graduate,
                              boolean training, int blocks, int counterTicks, int windTicks,
                              boolean open, boolean ground, int mode, int completions,
                              int bestScore, int bestReaction, String role) {
    public TemperingPacket(boolean enrolled, int points, int talent, boolean graduate, boolean training,
                           int blocks, int counterTicks, int windTicks, boolean open, boolean ground) {
        this(enrolled, points, talent, graduate, training, blocks, counterTicks, windTicks, open, ground, 0, 0, 0, 0, "");
    }

    public TemperingPacket(boolean enrolled, int points, int talent, boolean graduate, boolean training,
                           int blocks, int counterTicks, int windTicks, boolean open) {
        this(enrolled, points, talent, graduate, training, blocks, counterTicks, windTicks, open, false, 0, 0, 0, 0, "");
    }

    public TemperingPacket(boolean enrolled, int points, int talent) {
        this(enrolled, points, talent, false, false, 0, 0, 0, false);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enrolled);
        buffer.writeVarInt(points);
        buffer.writeVarInt(talent);
        buffer.writeBoolean(graduate);
        buffer.writeBoolean(training);
        buffer.writeVarInt(blocks);
        buffer.writeVarInt(counterTicks);
        buffer.writeVarInt(windTicks);
        buffer.writeBoolean(open);
        buffer.writeBoolean(ground);
        buffer.writeVarInt(mode);
        buffer.writeVarInt(completions);
        buffer.writeVarInt(bestScore);
        buffer.writeVarInt(bestReaction);
        buffer.writeUtf(role == null ? "" : role, 16);
    }

    public static TemperingPacket decode(FriendlyByteBuf buffer) {
        return new TemperingPacket(buffer.readBoolean(), TemperingRules.clamp(buffer.readVarInt()),
            WarriorTalentRules.clamp(buffer.readVarInt()), buffer.readBoolean(), buffer.readBoolean(),
            Math.max(0, Math.min(3, buffer.readVarInt())), Math.max(0, Math.min(80, buffer.readVarInt())),
            Math.max(0, Math.min(WindInfusionRules.COOLDOWN_TICKS, buffer.readVarInt())),
            buffer.readBoolean(), buffer.readBoolean(), PracticeDifficulty.clamp(buffer.readVarInt()),
            Math.max(0, buffer.readVarInt()), Math.max(0, Math.min(100, buffer.readVarInt())),
            Math.max(0, Math.min(80, buffer.readVarInt())), buffer.readUtf(16));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this));
        context.setPacketHandled(true);
    }

    private static class ClientOnly {
        static void accept(TemperingPacket packet) {
            com.tnc.tnc.warrior.client.TemperingClient.accept(packet);
        }
    }
}
