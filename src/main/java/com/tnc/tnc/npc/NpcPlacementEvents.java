package com.tnc.tnc.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * 固定 NPC 的补位触发器。
 *
 * <p>三个时机：
 * <ol>
 *   <li>{@link ServerStartedEvent} —— 服务器/存档加载完，先补一遍；</li>
 *   <li>{@link PlayerEvent.PlayerLoggedInEvent} / {@link PlayerEvent.PlayerChangedDimensionEvent}
 *       —— 玩家一进来就补（不用等下一次轮询）；</li>
 *   <li>每 {@value #CHECK_INTERVAL_TICKS} tick（默认 1 秒）扫一遍 ——
 *       ★ 2026-09-22 从 5 秒改成 1 秒：只有玩家**在附近**时实体才放得进去
 *       （见 {@code NpcPlacementSavedData.ensureOne} 里"实体刻"的注释），
 *       所以补位必须跟得上玩家的移动，否则会出现"人到了、NPC 还在路上"的空白。</li>
 * </ol>
 *
 * <p>为什么用 ServerTickEvent 而不是 PlayerTickEvent：NPC 的"该在"是世界属性，
 * 跟某个玩家在不在线无关。而且只有主世界一个维度，开销可以忽略
 * （{@code ensureAll} 里每次检查都还在实体刻范围内才动手，绝大多数时候是几个空判断）。
 */
public final class NpcPlacementEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 20;

    private int timer;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = NpcPlacementSavedData.overworldOf(event.getServer());
        if (overworld == null) {
            return;
        }
        NpcPlacementSavedData data = NpcPlacementSavedData.get(overworld);
        // ★ 新存档第一次加载时把**默认登记**补进去 ——
        //   玩家不需要每开一个档就手敲一次 /tnc npc here self。
        //   另外，默认表升版（NpcPlacementDefaults.VERSION）后，
        //   老存档也会在这里被刷新到新默认值，不需要手敲命令。
        data.seedDefaults();
        int spawned = data.ensureAll(overworld);
        LOGGER.info("TN-C npc: placements = [{}]{}", data.describe(),
                spawned > 0 ? ", restored " + spawned : "");
    }

    /** 玩家进世界/换维度时立刻补一遍 —— 这时他的加载范围刚变，正是实体放得进去的时刻。 */
    @SubscribeEvent
    public void onPlayerArrived(PlayerEvent.PlayerLoggedInEvent event) {
        ensureNear(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ensureNear(event.getEntity());
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

    private static void ensureNear(net.minecraft.world.entity.player.Player player) {
        if (player.level() instanceof ServerLevel level) {
            NpcPlacementSavedData.get(level).ensureAll(level);
        }
    }
}
