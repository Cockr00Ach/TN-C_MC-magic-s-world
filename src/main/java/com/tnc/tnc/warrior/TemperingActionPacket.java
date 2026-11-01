package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.tnc.tnc.network.MagicStoneNetwork;

/**
 * 客户端 → 服务端的动作请求。
 *
 * <p>召谁、能不能练、练多久，全部由服务端复核（见 {@link PracticeArena#summon}）。
 */
public record TemperingActionPacket(Action action, String role) {
    public enum Action {
        VIEW, START, CANCEL, SUMMON, DISMISS, CYCLE_PRACTICE
    }

    public TemperingActionPacket(Action action) {
        this(action, "");
    }

    public static void send(Action action) {
        MagicStoneNetwork.CHANNEL.sendToServer(new TemperingActionPacket(action, ""));
    }

    public static void send(Action action, String role) {
        MagicStoneNetwork.CHANNEL.sendToServer(new TemperingActionPacket(action, role == null ? "" : role));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeUtf(role == null ? "" : role, 16);
    }

    public static TemperingActionPacket decode(FriendlyByteBuf buffer) {
        return new TemperingActionPacket(buffer.readEnum(Action.class), buffer.readUtf(16));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        var player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> {
                switch (action) {
                    case VIEW -> TemperingEvents.sync(player, true);
                    case START -> {
                        if (!TemperingTraining.start(player)) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "当前无法开始训练：需要战士天赋、在演武场内、生存模式且未倒地。"));
                        }
                        TemperingEvents.sync(player);
                    }
                    case CANCEL -> PracticeArena.dismiss(player);
                    case SUMMON -> PracticeArena.summon(player, role);
                    case DISMISS -> PracticeArena.dismiss(player);
                    case CYCLE_PRACTICE -> {
                        TrainingGround.cyclePractice(player);
                        TemperingEvents.sync(player);
                    }
                }
            });
        }
        ctx.setPacketHandled(true);
    }
}
