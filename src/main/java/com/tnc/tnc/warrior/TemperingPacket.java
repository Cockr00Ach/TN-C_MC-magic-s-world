package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端的只读状态。
 *
 * <p>{@code role} 是当前召出的教官（{@code counter/guard/blade/wind}，没有则空串），
 * 面板据此显示「正在跟谁练」。
 *
 * <p>{@code courses} / {@code course} 是 2026-11-03 加的教官课程进度：前者是四位教官的出师情况
 * （{@code guard:1/3|blade:0/4|wind:0/3|counter:0/1}），后者是正在上的那一节
 * （{@code 第二课 · 振刀 1/2}，没在上课就是空串）。终端面板与图鉴提示都读这两条，不再各自猜。
 */
public record TemperingPacket(boolean enrolled, int points, int talent, boolean graduate,
                              boolean training, int blocks, int counterTicks, int windTicks,
                              boolean open, boolean ground, int mode, int completions,
                              int bestScore, int bestReaction, String role,
                              String courses, String course) {
    public TemperingPacket(boolean enrolled, int points, int talent, boolean graduate, boolean training,
                           int blocks, int counterTicks, int windTicks, boolean open, boolean ground) {
        this(enrolled, points, talent, graduate, training, blocks, counterTicks, windTicks, open, ground,
            0, 0, 0, 0, "", "", "");
    }

    public TemperingPacket(boolean enrolled, int points, int talent, boolean graduate, boolean training,
                           int blocks, int counterTicks, int windTicks, boolean open) {
        this(enrolled, points, talent, graduate, training, blocks, counterTicks, windTicks, open, false,
            0, 0, 0, 0, "", "", "");
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
        buffer.writeUtf(courses == null ? "" : courses, 96);
        buffer.writeUtf(course == null ? "" : course, 48);
    }

    public static TemperingPacket decode(FriendlyByteBuf buffer) {
        return new TemperingPacket(buffer.readBoolean(), TemperingRules.clamp(buffer.readVarInt()),
            WarriorTalentRules.clamp(buffer.readVarInt()), buffer.readBoolean(), buffer.readBoolean(),
            Math.max(0, Math.min(3, buffer.readVarInt())), Math.max(0, Math.min(80, buffer.readVarInt())),
            Math.max(0, Math.min(WindInfusionRules.COOLDOWN_TICKS, buffer.readVarInt())),
            buffer.readBoolean(), buffer.readBoolean(), PracticeDifficulty.clamp(buffer.readVarInt()),
            Math.max(0, buffer.readVarInt()), Math.max(0, Math.min(100, buffer.readVarInt())),
            Math.max(0, Math.min(80, buffer.readVarInt())), buffer.readUtf(16),
            buffer.readUtf(96), buffer.readUtf(48));
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
