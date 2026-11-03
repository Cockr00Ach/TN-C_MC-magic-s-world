package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：连招镜像（HUD 用）。
 *
 * <p>客户端不自己算连招进度 —— 服务端才是判定的那一方。这里每输入一次就回一份
 * 权威快照：正在接的序列、各招剩余冷却、刚放出来的是哪一招（用来闪一下图标）。
 * 数据量极小（≤ 8 个输入 + 3 个冷却），不值得做增量。
 */
public record WarriorComboSyncPacket(byte[] buffer, int[] cooldowns, int flashed) {

    public void encode(FriendlyByteBuf out) {
        out.writeByte(buffer.length);
        for (byte move : buffer) {
            out.writeByte(move);
        }
        out.writeVarInt(cooldowns.length);
        for (int cooldown : cooldowns) {
            out.writeVarInt(Math.max(0, cooldown));
        }
        out.writeByte(flashed);
    }

    public static WarriorComboSyncPacket decode(FriendlyByteBuf in) {
        // 越界直接判坏包：夹紧会让后面的字段错位，读到别的数据比丢包更糟
        int length = in.readUnsignedByte();
        if (length > ComboTracker.MAX_BUFFER) {
            throw new io.netty.handler.codec.DecoderException("combo buffer too long: " + length);
        }
        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readByte();
        }
        int skills = in.readVarInt();
        // 上界必须按"连招条数"算，不能按面板槽位（LOADOUT_SLOTS = 18）：
        // 服务端每条连招发一个冷却，条数一旦超过 18，这里就会把正常包判成坏包，
        // 客户端直接 DecoderException 掉线（2026-11-02："点一下 Q 就连接已丢失"）。
        if (skills < 0 || skills > WarriorCombos.all().size()) {
            throw new io.netty.handler.codec.DecoderException("combo cooldown count out of range: " + skills);
        }
        int[] cooldowns = new int[skills];
        for (int i = 0; i < skills; i++) {
            cooldowns[i] = Math.max(0, in.readVarInt());
        }
        return new WarriorComboSyncPacket(buffer, cooldowns, in.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this));
        context.setPacketHandled(true);
    }

    private static final class ClientOnly {
        private static void accept(WarriorComboSyncPacket packet) {
            com.tnc.tnc.warrior.client.WarriorComboClient.accept(packet);
        }
    }
}
