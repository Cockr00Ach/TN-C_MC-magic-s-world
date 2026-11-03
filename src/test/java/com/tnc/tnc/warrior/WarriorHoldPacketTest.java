package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * 「长按 / 松手」两个包与双手武器的口径。
 *
 * <p>回归现场一：按住 E 不放，客户端把 GLFW 的自动重复当成"连续轻点"，缓冲里堆满 E，
 * 长按永远判不出来（用户 2026-11-03 反馈）。修法是客户端只认 {@code isDown()} 的上升沿
 * —— 这条测试守的是"阶段字段必须原样过网"，别让阶段的位数被改成错的值。
 *
 * <p>回归现场二：振刀的架势要"长按多久保持多久"，所以动作包多了
 * {@link WarriorAnimationPacket#HOLD} / {@link WarriorAnimationPacket#RELEASE} 两态。
 * 三态混在一起走一个字节，任何一位写错都会变成"按一下 Q 就掉线"那种级别的故障，
 * 所以三态各测一遍，乱码包也测一遍。
 */
class WarriorHoldPacketTest {

    @Test
    void inputStagesSurviveTheWire() {
        for (int mode : new int[]{WarriorComboInputPacket.TAP, WarriorComboInputPacket.HOLD,
                WarriorComboInputPacket.RELEASE, WarriorComboInputPacket.KEEPALIVE,WarriorComboInputPacket.CHARGE_START}) {
            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            new WarriorComboInputPacket(WarriorMove.RIGHT.ordinal(), mode).encode(out);
            WarriorComboInputPacket back = WarriorComboInputPacket.decode(out);
            assertEquals(WarriorMove.RIGHT.ordinal(), back.move(), "方向要原样回来");
            assertEquals(mode, back.mode(), "阶段要原样回来");
        }
    }

    @Test
    void garbageInputStageBecomesATap() {
        // 客户端写坏一个字节不该把服务端弄崩：夹到"轻点"，最坏就是少一次长按
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeByte(WarriorMove.LEFT.ordinal());
        out.writeByte(99);
        assertEquals(WarriorComboInputPacket.TAP, WarriorComboInputPacket.decode(out).mode());
    }

    @Test
    void animationStagesSurviveTheWire() {
        for (int mode : new int[]{WarriorAnimationPacket.START, WarriorAnimationPacket.HOLD,
                WarriorAnimationPacket.RELEASE}) {
            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            new WarriorAnimationPacket(42, (byte) WarriorAnimation.SWORD_STANCE.id(), 30, (byte) mode).encode(out);
            WarriorAnimationPacket back = WarriorAnimationPacket.decode(out);
            assertEquals(42, back.entityId());
            assertEquals(WarriorAnimation.SWORD_STANCE, WarriorAnimation.byId(back.animation() & 0xFF));
            assertEquals(30, back.durationTicks());
            assertEquals(mode, back.mode(), "架势三态要原样回来");
        }
    }

    @Test
    void nonsenseAnimationStageIsRejected() {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeVarInt(1);
        out.writeByte(WarriorAnimation.SWORD_STANCE.id());
        out.writeVarInt(20);
        out.writeByte(9);
        assertThrows(DecoderException.class, () -> WarriorAnimationPacket.decode(out));
    }

    @Test
    void twoHandedFamiliesKeepBothHandsOnTheWeapon() {
        assertTrue(WarriorWeaponFamily.SPEAR.twoHanded(), "长枪是双手武器");
        assertTrue(WarriorWeaponFamily.GREATSWORD.twoHanded(), "重剑是双手武器");
        assertTrue(WarriorWeaponFamily.HAMMER.twoHanded(), "战锤是双手武器");
        assertFalse(WarriorWeaponFamily.LONGSWORD.twoHanded(), "长剑是单手剑");
        assertFalse(WarriorWeaponFamily.BOW.twoHanded(), "弓不算");
    }
}
