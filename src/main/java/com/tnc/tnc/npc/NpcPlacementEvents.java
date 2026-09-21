package com.tnc.tnc.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * 固定 NPC 的补位触发器。
 *
 * <p>两个时机：
 * <ol>
 *   <li>{@link ServerStartedEvent} —— 服务器/存档加载完，先补一遍；</li>
 *   <li>每 {@value #CHECK_INTERVAL_TICKS} tick（默认 5 秒）扫一遍 —— 万一运行中被弄没了
 *       （创造模式误杀、区块异常等），几秒内自己回来。</li>
 * </ol>
 *
 * <p>为什么用 ServerTickEvent 而不是 PlayerTickEvent：NPC 的"该在"是世界属性，
 * 跟某个玩家在不在线无关。而且只有主世界一个维度，开销可以忽略。
 */
public final class NpcPlacementEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 100;

    private int timer;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // 换存档/重新生成时锚点会变，先让缓存失效
        SkyIslandAnchors.clearCache();
        ServerLevel overworld = NpcPlacementSavedData.overworldOf(event.getServer());
        if (overworld == null) {
            return;
        }
        NpcPlacementSavedData data = NpcPlacementSavedData.get(overworld);
        int spawned = data.ensureAll(overworld);
        LOGGER.info("TN-C npc: placements = [{}]{}", data.describe(),
                spawned > 0 ? ", restored " + spawned : "");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++timer < CHECK_INTERVAL_TICKS) {
            return;
        }
        timer = 0;
        ServerLevel overworld = NpcPlacementSavedData.overworldOf(event.getServer());
        if (overworld != null) {
            NpcPlacementSavedData.get(overworld).ensureAll(overworld);
        }
    }
}
