package com.tnc.tnc.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 诊断用 mixin（第 2 层）：把 WhisperingQuests **发给客户端的定义列表**打进日志。
 *
 * <p>第 1 层（{@link WhisperQuestDumpMixin}）的结论是：我们的任务**已被框架解析**
 * （221 条里有 {@code tnc:main/self_talk}）。所以"看不到"发生在它之后：
 * 要么服务端组装同步包时把它滤掉，要么客户端界面不显示。
 *
 * <p>本 mixin 注入 {@code SyncDefinitionsPacket} 的**构造器**（不用 {@code from}：
 * 注入静态工厂要写对泛型返回值 {@code CallbackInfoReturnable<SyncDefinitionsPacket>}，
 * 写成 {@code <Object>} 会**匹配失败且静默跳过** —— 这个坑我踩了两次。
 * 构造器的描述符 {@code (Ljava/util/List;Ljava/util/List;JJLjava/util/List;)V} 直白得多）。
 */
@Mixin(targets = "com.lirxowo.whisperingquests.network.packet.s2c.SyncDefinitionsPacket")
public class WhisperSyncDumpMixin {

    @Inject(method = "<init>(Ljava/util/List;Ljava/util/List;JJLjava/util/List;)V",
            at = @At("TAIL"), require = 0)
    private void tnc$dumpSync(List<?> definitions,
                              List<ResourceLocation> refreshed,
                              long refreshRemainingTicks,
                              long rollSeed,
                              List<?> chapters,
                              CallbackInfo ci) {
        var log = LogUtils.getLogger();
        int nDefs = definitions == null ? -1 : definitions.size();
        int nChaps = chapters == null ? -1 : chapters.size();
        log.info("TN-C sync-dump: 同步包 定义={} 条 / 章节={} 个 / 刷新池={}",
                nDefs, nChaps, refreshed == null ? -1 : refreshed.size());

        int ours = 0, ourChaps = 0;
        if (definitions != null) {
            for (Object d : definitions) {
                String s = String.valueOf(d);
                if (s.contains("tnc:")) { ours++; log.info("TN-C sync-dump:   [我们的定义] {}", s); }
            }
        }
        if (chapters != null) {
            for (Object c : chapters) {
                String s = String.valueOf(c);
                if (s.contains("tnc:")) { ourChaps++; log.info("TN-C sync-dump:   [我们的章节] {}", s); }
            }
        }
        if (ours == 0) {
            log.warn("TN-C sync-dump: **同步包里没有 tnc: 的任务**（共 {} 条）-> 被服务端过滤了", nDefs);
            int i = 0;
            if (definitions != null) for (Object d : definitions) { if (i++ >= 3) break; log.warn("TN-C sync-dump:     样例 {}", d); }
        }
        if (ourChaps == 0) {
            log.warn("TN-C sync-dump: **同步包里没有 tnc: 的章节**（共 {} 个）", nChaps);
            int i = 0;
            if (chapters != null) for (Object c : chapters) { if (i++ >= 5) break; log.warn("TN-C sync-dump:     章节样例 {}", c); }
        }
    }
}
