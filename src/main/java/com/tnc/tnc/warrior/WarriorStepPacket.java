package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.tnc.tnc.network.MagicStoneNetwork;

/**
 * 客户端 → 服务端：战斗步法（默认左 Alt；按下 = 只中断，松手 = 冲一大段，按住 = 小步 + 加速）。
 *
 * <p>按用户口述的规格拆成三拍：
 *
 * <ul>
 *   <li>{@link #CANCEL} —— 刚按下：<b>只中断</b>，不清位移。连招缓冲、正在演的终结技动作
 *       立刻停掉，人手不会被自己的收招锁住；</li>
 *   <li>{@link #TAP} —— 松手（短按）：向前冲<b>一大段</b>；</li>
 *   <li>{@link #HOLD} —— 按住超过半秒：只挪<b>一小段</b>，给一段加速跑。</li>
 * </ul>
 *
 * <p>位置/碰撞/加速全部由服务端算，客户端只说"这是哪一拍"。
 */
public record WarriorStepPacket(int mode) {

    /** 只中断当前动作（按下瞬间发）。 */
    public static final int CANCEL = 0;
    /** 短按：冲一大段。 */
    public static final int TAP = 1;
    /** 长按：小步 + 加速跑。 */
    public static final int HOLD = 2;

    /** 旧签名（布尔）保留：{@code true} = 长按，{@code false} = 短按。 */
    public static void send(boolean sprint) {
        send(sprint ? HOLD : TAP);
    }

    public static void send(int mode) {
        MagicStoneNetwork.CHANNEL.sendToServer(new WarriorStepPacket(mode));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(mode);
    }

    public static WarriorStepPacket decode(FriendlyByteBuf buffer) {
        int mode = buffer.readVarInt();
        // 越界一律当"短按"，宁可冲一下也别整包丢掉
        return new WarriorStepPacket(mode < CANCEL || mode > HOLD ? TAP : mode);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                switch (mode) {
                    case CANCEL -> WarriorComboService.interrupt(player);
                    case HOLD -> WarriorComboService.step(player, true);
                    default -> WarriorComboService.step(player, false);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
