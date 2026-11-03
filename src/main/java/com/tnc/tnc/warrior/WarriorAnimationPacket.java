package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端 → 客户端：一次玩家全身动作。
 *
 * <p>为什么单独一个包、而不是塞进连招快照：动作要发给<b>周围所有能看到这个玩家的人</b>
 * （第三人称观察者也要看到对方的挥砍），而快照只发给本人（HUD）。两件事的生命周期不同，
 * 强行合并会让旁观者收到的包多背一份自己用不上的缓冲数据。
 *
 * <p>包只带"谁、哪个动作、多久、停在哪个阶段"，具体姿势在客户端算 —— 动作是表现层的事，
 * 服务端不需要知道手臂抬多少度。
 *
 * <p>{@link #mode()} 三态，给"按着不放就保持这个姿势"用：
 * {@link #START} 正常演一遍；{@link #HOLD} 演到最后一帧<b>停住</b>（振刀架势）；
 * {@link #RELEASE} 收势（客户端用 3 tick 软落地，不会硬跳回站立）。
 */
public record WarriorAnimationPacket(int entityId, byte animation, int durationTicks, byte mode) {

    /** 特殊动作 id：立刻停止这个实体正在演的动作（Shift 中断用）。 */
    public static final int CANCEL = 0xFF;

    public static final int START = 0;
    public static final int HOLD = 1;
    public static final int RELEASE = 2;

    public void encode(FriendlyByteBuf out) {
        out.writeVarInt(entityId);
        out.writeByte(animation);
        out.writeVarInt(durationTicks);
        out.writeByte(mode);
    }

    public static WarriorAnimationPacket decode(FriendlyByteBuf in) {
        int entityId = in.readVarInt();
        byte animation = in.readByte();
        int duration = in.readVarInt();
        if ((animation & 0xFF) == CANCEL) {
            return new WarriorAnimationPacket(entityId, animation, Math.max(1, duration), (byte) START);
        }
        if (WarriorAnimation.byId(animation & 0xFF) == null) {
            throw new io.netty.handler.codec.DecoderException("unknown warrior animation " + animation);
        }
        if (duration <= 0 || duration > 200) {
            throw new io.netty.handler.codec.DecoderException("warrior animation duration out of range " + duration);
        }
        int mode = in.readUnsignedByte();
        if (mode < START || mode > RELEASE) {
            throw new io.netty.handler.codec.DecoderException("warrior animation mode out of range " + mode);
        }
        return new WarriorAnimationPacket(entityId, animation, duration, (byte) mode);
    }

    /** 把动作发给"追这个玩家的人 + 自己"（自己也要第三人称/第一人称看到）。 */
    public static void broadcast(ServerPlayer player, WarriorAnimation animation, int durationTicks) {
        broadcast(player, animation, durationTicks, START);
    }

    public static void broadcast(ServerPlayer player, WarriorAnimation animation, int durationTicks, int mode) {
        if (player == null || animation == null || durationTicks <= 0) {
            return;
        }
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new WarriorAnimationPacket(player.getId(), (byte) animation.id(), durationTicks, (byte) mode));
    }

    /** 演完但停在最后一帧（长按架势）。 */
    public static void hold(ServerPlayer player, WarriorAnimation animation, int durationTicks) {
        broadcast(player, animation, durationTicks, HOLD);
    }

    /** 收势：客户端会在 3 tick 内软落地回站立。 */
    public static void release(ServerPlayer player, WarriorAnimation animation, int durationTicks) {
        broadcast(player, animation, durationTicks, RELEASE);
    }

    /** 立刻停掉这个玩家正在演的动作（Shift 中断用）。 */
    public static void cancel(ServerPlayer player) {
        if (player == null) {
            return;
        }
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new WarriorAnimationPacket(player.getId(), (byte) CANCEL, 1, (byte) START));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this));
        context.setPacketHandled(true);
    }

    private static final class ClientOnly {
        private static void accept(WarriorAnimationPacket packet) {
            if ((packet.animation() & 0xFF) == CANCEL) {
                com.tnc.tnc.warrior.client.WarriorAnimationClient.cancel(packet.entityId());
                return;
            }
            WarriorAnimation animation = WarriorAnimation.byId(packet.animation() & 0xFF);
            if (packet.mode() == RELEASE) {
                com.tnc.tnc.warrior.client.WarriorAnimationClient.release(packet.entityId());
                return;
            }
            com.tnc.tnc.warrior.client.WarriorAnimationClient.accept(packet.entityId(), animation,
                    packet.durationTicks(), packet.mode() == HOLD);
        }
    }
}
