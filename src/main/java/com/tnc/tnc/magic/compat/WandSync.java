package com.tnc.tnc.magic.compat;

import com.tnc.tnc.TNMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 每秒把玩家的法杖对齐一次魔法石（"知识在魔法石，法杖只是显示器"）。
 *
 * <h2>为什么要一条常驻的兜底</h2>
 * 法杖里那份 {@code spell_container} 是<b>复制</b>，真正的权威是魔法石（玩家身上的
 * {@code MagicStoneData}）。原来的同步只在"登录 / 学习 / 遗忘 / 铸杖 / 命令"时跑，
 * 于是从箱子掏出来的第二根杖、别人给的杖、遗忘之后 —— 都要等下一次事件才对得上，
 * 玩家看到的就是"法术绑在那一根杖上"✗（作者 2026-11-09 报的正是这个）。
 *
 * <p>这条每秒扫一遍，把"换杖 / 忘法术"两种情况都在一秒内抹平。
 * {@link SpellEngineBridge#refreshWands} 内部只在内容真的不一样时才写 NBT，
 * 也不会补发法杖，所以常驻是安全的 ✓
 *
 * <p><b>为什么挂在 {@code ServerTickEvent}</b>：作者实机日志里见过
 * {@code PlayerTickEvent} 整局一次都不进（见 {@code MagicStone.onPlayerTick} 的注释），
 * 而 {@code ServerTickEvent} 是活的 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WandSync {

    /** 20 tick = 1 秒。再密没意义：玩家不会在一秒里换两次杖还指望立刻生效。 */
    private static final int INTERVAL_TICKS = 20;

    private static long lastRun = Long.MIN_VALUE;

    private WandSync() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        // 只在服务端 tick 计数器上做节流；换世界时 getTickCount 单调递增，不会误判 ✓
        if (lastRun != Long.MIN_VALUE && now - lastRun >= 0 && now - lastRun < INTERVAL_TICKS) {
            return;
        }
        lastRun = now;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isDeadOrDying()) {
                continue;
            }
            SpellEngineBridge.refreshWands(player);
        }
    }
}
