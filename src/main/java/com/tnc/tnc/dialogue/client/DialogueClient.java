package com.tnc.tnc.dialogue.client;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.dialogue.DialogueScript;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * 客户端侧接收服务端派来的剧本并开界面。
 *
 * <p>单独一个类，是为了<b>把 MC 客户端类关在这里</b> —— 服务端的包处理里只引用
 * {@code DialogueClient} 这个名字，不直接引用 {@code Screen}/{@code Minecraft}，
 * 这样专用服务器加载这个包时也不会因为找不到客户端类而崩
 * （项目里 {@code MagicStoneClientSync} 用的是同一套隔离办法）。
 */
public final class DialogueClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DialogueClient() {
    }

    /** 由 OpenDialogue 包在主线程调用。 */
    public static void accept(DialogueScript script) {
        LOGGER.info("TN-C dialogue: opening {} ({} line(s))", script.id(), script.lines().size());
        Minecraft.getInstance().setScreen(new DialogueScreen(script));
    }
}
