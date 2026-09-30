package com.tnc.tnc.light;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 光翼的"每 tick 维持"入口 ✓ —— 真正的逻辑在 {@link TNLightChainMechanics#tickWings}（数值也都在那边 ✓）。
 *
 * <h2>★ 2026-10-01 事故：飞行链"既飞不了也看不到翅膀"</h2>
 * 这个类原来把逻辑挂在 {@code TickEvent.PlayerTickEvent} 上 ✗ —— 而<b>该事件在本仓库里是死的</b> ✗
 * （{@code TnSpellMechanics} 里早有实机记录：<i>"PlayerTickEvent 在我们的 mod 上一个世界整局都没进来过"</i> ✗）。
 * 于是：法术放得出来（引擎侧完全正常 ✓）、蓝也扣了、冷却也进了 ✗，
 * 但"挂光翼标记 + 给 mayfly"这两件事一次都没发生 ⇒ 作者看到的就是"没法飞、也没翅膀" ✗。
 *
 * <p>现在改挂 {@code TickEvent.ServerTickEvent}（<b>已证活着的那个</b> ✓，和
 * {@code TnSpellMechanics.onServerTick} 同一个）＋ 同时由 {@code TnSpellMechanics.tickPlayer}
 * 转发一份 ✓ —— 两条路互为保险，{@code tickWings} 本身幂等（状态没变不发包 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNLightWingsEvents {

    private TNLightWingsEvents() {
    }

    /** 服务端每 tick：给所有玩家维持光翼状态 ✓。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TNLightChainMechanics.tickWings(player);
        }
    }
}
