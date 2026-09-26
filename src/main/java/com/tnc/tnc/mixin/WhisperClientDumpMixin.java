package com.tnc.tnc.mixin;

import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 诊断用 mixin（第 3 层，客户端）：看客户端**最终收到的定义**里有没有我们的任务。
 *
 * <p>前两层结论：
 * <ol>
 *   <li>{@code QuestDataManager.apply} —— 服务端**解析**到了 221 条，含 {@code tnc:main/self_talk} ✓</li>
 *   <li>{@code SyncDefinitionsPacket.from} 的字节码里**没有任何 filter**，整份列表直接下发
 *       ⇒ 包里有我们的任务是必然的。</li>
 * </ol>
 * 所以问题只剩「客户端拿到之后怎么组织界面列表」。这个 mixin 在客户端
 * {@code ClientQuestData.applyDefinitions} 结束时把 DEFINITIONS 的键打出来，
 * 确认客户端侧确实收到了 {@code tnc:main/self_talk}。
 */
@Mixin(targets = "com.lirxowo.whisperingquests.client.ClientQuestData")
public class WhisperClientDumpMixin {

    @Inject(method = "applyDefinitions", at = @At("TAIL"), require = 0)
    private static void tnc$dumpClientDefs(Object packet, CallbackInfo ci) {
        var log = LogUtils.getLogger();
        try {
            var f = Class.forName("com.lirxowo.whisperingquests.client.ClientQuestData")
                    .getDeclaredField("DEFINITIONS");
            f.setAccessible(true);
            Object mapObj = f.get(null);
            if (!(mapObj instanceof java.util.Map<?, ?> map)) {
                log.warn("TN-C client-dump: DEFINITIONS 不是 Map");
                return;
            }
            log.info("TN-C client-dump: 客户端收到 {} 条任务定义", map.size());
            int ours = 0;
            for (Object k : map.keySet()) {
                if (String.valueOf(k).startsWith("tnc:")) {
                    ours++;
                    log.info("TN-C client-dump:   [我们的] {} = {}", k, map.get(k));
                }
            }
            if (ours == 0) log.warn("TN-C client-dump: **客户端没有收到 tnc: 的任务**");
            else log.info("TN-C client-dump: 属于我们的共 {} 条 ✓", ours);
        } catch (Throwable t) {
            log.warn("TN-C client-dump: 读取失败: {}", t.toString());
        }
    }
}
