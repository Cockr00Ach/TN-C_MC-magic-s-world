package com.tnc.tnc.mixin;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 诊断用 mixin：把 WhisperingQuests **实际扫到的任务**打进日志。
 *
 * <h2>为什么需要它</h2>
 * 我们往任务书里加了自己的任务，但游戏里看不到，而框架：
 * 解析失败只 {@code System.err.println}（日志里翻不到）、成功时**一行不打** ——
 * "到底扫到了什么"完全不可见。
 *
 * <h2>踩的坑（第一版没生效）</h2>
 * 第一版注入的是 {@code prepare}（父类 {@code SimpleJsonResourceReloadListener} 的方法），
 * 但 {@code QuestDataManager} **只覆写了 {@code apply}**，没有覆写 {@code prepare}
 * ⇒ mixin 匹配不到目标方法；又因为 {@code defaultRequire:0}，**静默跳过、连警告都没有**。
 * 现在改注入 {@code apply}：它收到的 map 正好就是"解析出来的任务定义"（id -> JSON）。
 *
 * <p>诊断完可以删掉本文件 + {@code tnc.mixins.json} 里的那一行。
 */
@Mixin(targets = "com.lirxowo.whisperingquests.data.QuestDataManager")
public class WhisperQuestDumpMixin {

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), require = 0)
    private void tnc$dumpQuests(Map<ResourceLocation, JsonElement> map,
                                net.minecraft.server.packs.resources.ResourceManager rm,
                                net.minecraft.util.profiling.ProfilerFiller profiler,
                                CallbackInfo ci) {
        var log = LogUtils.getLogger();
        if (map == null) { log.warn("TN-C quest-dump: apply() 收到 null"); return; }
        log.info("TN-C quest-dump: ===== WhisperingQuests 扫到 {} 条任务 =====", map.size());
        int ours = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
            String id = e.getKey().toString();
            if (id.startsWith("tnc:")) {
                ours++;
                log.info("TN-C quest-dump:   [我们的] {} = {}", id, e.getValue());
            }
        }
        if (ours == 0) {
            log.warn("TN-C quest-dump: **没有扫到任何 tnc: 开头任务**（共 {} 条）—— 样例前 5 条：", map.size());
            int i = 0;
            for (ResourceLocation k : map.keySet()) {
                if (i++ >= 5) break;
                log.warn("TN-C quest-dump:     样例 {}", k);
            }
        } else {
            log.info("TN-C quest-dump: 其中属于我们的有 {} 条 ✓", ours);
        }
    }
}
