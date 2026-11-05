package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * 连招镜像包（服务端 → HUD）的编解码。
 *
 * <p>回归现场：{@code decode} 曾拿 {@code WarriorSkill.LOADOUT_SLOTS}（面板槽位 9×2 = 18）
 * 当冷却数组的上界，而服务端是<b>每条连招发一个冷却</b>。连招涨到 19 条之后，
 * 这份"正常包"就被判成坏包 —— 客户端 {@code DecoderException}，一按 Q/E 直接"连接已丢失"。
 * 这两条测试一个守着"正常包必须过得去"，一个守着"乱写的包仍然要拦"。
 */
class WarriorComboSyncPacketTest {

    @Test
    void everyComboFitsOnTheWire() {
        int count = WarriorCombos.all().size();
        assertTrue(count > WarriorSkill.LOADOUT_SLOTS,
                "连招条数应该已经超过面板槽位数（这正是当年掉线的原因）；若真的缩回去了，这条测试可以改");

        int[] cooldowns = new int[count];
        for (int i = 0; i < cooldowns.length; i++) {
            cooldowns[i] = i * 3;
        }
        byte[] buffer = {0, 1, 2, (byte) (0x80 | 1)};

        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        new WarriorComboSyncPacket(buffer, cooldowns, 7).encode(out);
        WarriorComboSyncPacket back = WarriorComboSyncPacket.decode(out);

        assertArrayEquals(buffer, back.buffer(), "输入缓冲必须原样回来");
        assertArrayEquals(cooldowns, back.cooldowns(), "每一条连招的冷却都要能过网");
        assertEquals(7, back.flashed(), "高亮的那一招也要原样回来");
    }

    @Test
    void absurdCountIsStillRejected() {
        // 夹紧会让后面的字段错位，读到别的数据比丢包更糟：所以仍然是"判坏包"
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeByte(0);
        out.writeVarInt(1000);
        assertThrows(DecoderException.class, () -> WarriorComboSyncPacket.decode(out));
    }

    @Test
    void negativeCountIsRejected() {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeByte(0);
        out.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> WarriorComboSyncPacket.decode(out));
    }
}
