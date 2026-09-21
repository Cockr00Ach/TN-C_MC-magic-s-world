package com.tnc.tnc.dialogue;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

/**
 * 对话的网络包 —— <b>共用 {@link com.tnc.tnc.network.MagicStoneNetwork#CHANNEL} 那一条通道</b>。
 *
 * <h2>⚠️ 这里踩过一个会崩游戏的坑（2026-09-21）</h2>
 * 我原本在 {@link DialogueNetwork} 里又写了一次
 * {@code NetworkRegistry.newSimpleChannel(tnc:main, ...)} —— 结果是启动时抛：
 * <pre>
 *   java.lang.IllegalArgumentException: NetworkDirection Channel {tnc:main} already registered
 * </pre>
 * Forge 的 {@code newSimpleChannel} 内部维护一张**全局**的 channel 表，同名再建一次直接抛异常。
 * 而这个异常发生在 <b>mod 构造期</b>，后果不是"少个包"，而是：
 * <pre>
 *   Failed to create mod instance. ModID: tnc
 *   Failed to complete lifecycle event CONSTRUCT
 *   => mod 状态变成 broken
 *   => Cowardly refusing to send event ModelEvent$ModifyBakingResult to a broken mod state
 *   => ModelBakery 大批模型加载失败、别的 mod（paramagic）读自己的 shader 也失败
 *   => 最后 Unhandled game exception，游戏起不来
 * </pre>
 * 也就是说：<b>一个注册错误会把整个包的启动带下水</b>，而且日志里最显眼的报错
 * （paramagic 的 ShaderException）根本不是原因，是后果。
 *
 * <p>正确做法：**一个 mod 只用一条 SimpleChannel**，所有包挂在它上面，用不同的包 id 区分。
 * 包 id 从 100 开始，和魔法石的 0/1 错开。
 */
public final class DialogueNetwork {

    private DialogueNetwork() {
    }

    /** 把对话的两个包注册到**已有的**主通道上（由 MagicStoneNetwork.register 统一调用）。 */
    public static void registerPackets(net.minecraftforge.network.simple.SimpleChannel channel, int firstId) {
        int id = firstId;
        channel.messageBuilder(DialoguePackets.OpenDialogue.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DialoguePackets.OpenDialogue::encode)
                .decoder(DialoguePackets.OpenDialogue::decode)
                .consumerMainThread(DialoguePackets.OpenDialogue::handle)
                .add();

        channel.messageBuilder(DialoguePackets.DialogueDone.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialoguePackets.DialogueDone::encode)
                .decoder(DialoguePackets.DialogueDone::decode)
                .consumerMainThread(DialoguePackets.DialogueDone::handle)
                .add();
    }

    /** 服务端 → 某个玩家：播这条剧本。 */
    public static void openFor(ServerPlayer player, DialogueScript script) {
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new DialoguePackets.OpenDialogue(script));
    }

    /** 客户端 → 服务端：播完了。 */
    public static void notifyDone(ResourceLocation scriptId) {
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(
                new DialoguePackets.DialogueDone(scriptId));
    }

    /** 只用于日志/诊断：确认我们挂在哪条通道上。 */
    public static String channelName() {
        return TNMod.MODID + ":main";
    }
}
