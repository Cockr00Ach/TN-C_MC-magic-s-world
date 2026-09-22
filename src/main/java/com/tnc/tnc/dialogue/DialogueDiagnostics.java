package com.tnc.tnc.dialogue;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 剧本自检 —— 开服时把 {@code data/<ns>/dialogues/} 下**每一份**剧本都解析一遍并报数。
 *
 * <h2>为什么要有它</h2>
 * 这个项目最大的坑是"**静默失效**"：剧本文件写错一个字符（少个竖线、{@code @id} 与文件名不符），
 * 玩家右键 NPC 只会得到一句"剧本加载失败"，而**在跑进游戏之前根本不知道**。
 * 这条自检让它在**开服日志**里就暴露出来 —— 与魔法那侧的
 * {@code MagicStoneSelfTest} / {@code mana gate probe} 是同一个思路。
 *
 * <p>它同时解决一个协作问题：**编剧可以自己看日志确认台词被读到了**，
 * 不需要跑来问"我改了怎么没生效"。行号也会打出来，改了几行一目了然。
 */
public final class DialogueDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "dialogues";

    private DialogueDiagnostics() {
    }

    /** 服务器启动后调用（数据包已就绪）。任何一份剧本有问题都会明确报 ERROR。 */
    public static void run(MinecraftServer server) {
        Map<ResourceLocation, Resource> found =
                server.getResourceManager().listResources(DIR, path -> path.getPath().endsWith(".txt"));
        if (found.isEmpty()) {
            LOGGER.error("TN-C dialogue self-check: no script found under data/*/{} —— "
                    + "是不是忘了把 txt 放进 resources/data/tnc/dialogues/ ?", DIR);
            return;
        }

        List<String> okList = new ArrayList<>();
        List<String> badList = new ArrayList<>();

        // 排序让日志稳定（否则每次顺序不同，diff 起来费劲）
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation file : found.keySet()) {
            // data/tnc/dialogues/self_first.txt  ->  tnc:self_first
            String path = file.getPath().substring(DIR.length() + 1);
            ids.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                    path.substring(0, path.length() - 4)));
        }
        ids.sort(java.util.Comparator.comparing(ResourceLocation::toString));

        for (ResourceLocation id : ids) {
            // get() 内部会解析；失败时它自己会打 ERROR（含具体行号），这里只负责汇总
            var script = DialogueLoader.get(server.getResourceManager(), id);
            if (script.isPresent()) {
                okList.add(id.getPath() + "(" + script.get().lines().size() + "行)");
            } else {
                badList.add(id.toString());
            }
        }

        LOGGER.info("TN-C dialogue self-check: {}/{} script(s) ok -> {}",
                okList.size(), ids.size(), String.join(", ", okList));
        if (!badList.isEmpty()) {
            LOGGER.error("TN-C dialogue self-check: {} script(s) FAILED -> {} (详见上面的 parse 错误)",
                    badList.size(), String.join(", ", badList));
        }
    }

    /** 供命令/调试用：直接读一份剧本看看（不经过缓存），用于确认"文件到底在不在"。 */
    public static boolean exists(MinecraftServer server, ResourceLocation id) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), DIR + "/" + id.getPath() + ".txt");
        return server.getResourceManager().getResource(file).isPresent();
    }

    /** 读原始文本（诊断用；正常流程走 DialogueLoader 的缓存）。 */
    public static String rawText(MinecraftServer server, ResourceLocation id) throws Exception {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), DIR + "/" + id.getPath() + ".txt");
        Resource resource = server.getResourceManager().getResource(file).orElseThrow();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
