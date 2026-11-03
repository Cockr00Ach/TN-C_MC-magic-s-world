package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.tnc.tnc.network.MagicStoneNetwork;

/**
 * 客户端 → 服务端：一次斩击输入（Q / E / 左键），带"按下的阶段"。
 *
 * <p>包里只有「按了哪一下、是哪个阶段」，没有任何位置、目标、伤害或冷却 —— 那些全部由
 * 服务端用玩家当前手持、连招缓冲与冷却自己算。客户端说"我按住 Q"不等于"我打中了谁"。
 *
 * <p>四个阶段（{@link #mode()}）：
 *
 * <ul>
 *   <li>{@link #TAP}：按下那一下（轻点）。</li>
 *   <li>{@link #HOLD}：按住超过 {@link ComboTracker#HOLD_TICKS}。服务端把刚记下的那一下
 *       轻点<b>替换</b>成长按，所以不会多算一段。</li>
 *   <li>{@link #RELEASE}：松手。只对"按着不放的架势"有意义（振刀：松手才收架）。</li>
 *   <li>{@link #KEEPALIVE}：还按着。每 10 tick 一次，用来把架势窗口续上；
 *       服务端没有在架招时直接忽略。</li>
 * </ul>
 */
public record WarriorComboInputPacket(int move, int mode) {

    public static final int TAP = 0;
    public static final int HOLD = 1;
    public static final int RELEASE = 2;
    public static final int KEEPALIVE = 3;
    public static final int CHARGE_START = 4;

    public static void send(WarriorMove move) {
        send(move, TAP);
    }

    /** 旧调用：{@code held} 为真就是长按。 */
    public static void send(WarriorMove move, boolean held) {
        send(move, held ? HOLD : TAP);
    }

    public static void send(WarriorMove move, int mode) {
        if (move == null) {
            return;
        }
        MagicStoneNetwork.CHANNEL.sendToServer(
                new WarriorComboInputPacket(move.ordinal(), clamp(mode)));
    }

    private static int clamp(int mode) {
        return mode < TAP || mode > CHARGE_START ? TAP : mode;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(move & 0xFF);
        buffer.writeByte(clamp(mode));
    }

    public static WarriorComboInputPacket decode(FriendlyByteBuf buffer) {
        return new WarriorComboInputPacket(buffer.readUnsignedByte(), clamp(buffer.readUnsignedByte()));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> WarriorComboService.input(player, WarriorMove.byOrdinal(move), mode));
        }
        context.setPacketHandled(true);
    }
}
