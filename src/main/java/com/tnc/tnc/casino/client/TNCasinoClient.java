package com.tnc.tnc.casino.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.casino.machine.TNCasinoRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 赌场机器的客户端渲染器注册 ✓。
 *
 * <h2>⚠️ 漏了这一步的症状是"机器隐形、且不报错"✗</h2>
 * 方块本身是 {@code RenderShape.INVISIBLE}（几何全由我们自绘 ✓），
 * 所以<b>渲染器没注册 = 什么都看不见</b>，而日志里<b>一条错误都没有</b> ✗ ——
 * 本工程在 NPC 那边踩过同一个坑（{@code npc/client/TNNpcClientEvents} 的头注释写得很清楚 ✓）。
 *
 * <p>必须挂 <b>MOD 总线</b>（{@code RegisterRenderers} 是 mod 生命周期事件 ✗）——
 * 挂到 Forge 总线上会静默不生效 ✓（同 {@code BotanicalClient} / {@code PastureClientEvents} 的写法 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNCasinoClient {

    private TNCasinoClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TNCasinoRegistry.WHEEL_ENTITY, TNCasinoGameRenderer::new);
        event.registerBlockEntityRenderer(TNCasinoRegistry.CARD_ENTITY, TNCasinoGameRenderer::new);
        // ⚠️ 两个都必须在注册表非空时才能注册 ✗ —— RegisterRenderers 的触发时机晚于
        //    RegisterEvent（方块/方块实体已经注册完 ✓），所以这里正常都有值 ✓；
        //    万一为空（注册器类没被加载 ✗）就打一条日志：
        //    "机器隐形且不报错"这个症状太难查了，宁可留一行线索 ✓。
        if (TNCasinoRegistry.SLOT_ENTITY != null) {
            event.registerBlockEntityRenderer(TNCasinoRegistry.SLOT_ENTITY, TNSlotMachineRenderer::new);
        } else {
            org.slf4j.LoggerFactory.getLogger("tnc")
                    .error("TN-C casino: slot machine block entity type is null - renderer NOT registered, "
                            + "the machine will be invisible. Check that TNCasinoRegistry was loaded.");
        }
        if (TNCasinoRegistry.ROULETTE_ENTITY != null) {
            event.registerBlockEntityRenderer(TNCasinoRegistry.ROULETTE_ENTITY, TNRouletteRenderer::new);
        } else {
            org.slf4j.LoggerFactory.getLogger("tnc")
                    .error("TN-C casino: roulette table block entity type is null - renderer NOT registered, "
                            + "the table will be invisible. Check that TNCasinoRegistry was loaded.");
        }
        // 4x2 赌博桌：它以前走 models/block/gamble_table.json ✗，那份模型 extents 0..64
        // 超过加载器上限 32 ⇒ 这张桌子**从来没显示过** ✓。现在同样是自绘 ✓，
        // 所以这一条也不能漏 ✗，否则它继续隐形 ✓。
        if (TNCasinoRegistry.GAMBLE_ENTITY != null) {
            event.registerBlockEntityRenderer(TNCasinoRegistry.GAMBLE_ENTITY, TNGambleTableRenderer::new);
        } else {
            org.slf4j.LoggerFactory.getLogger("tnc")
                    .error("TN-C casino: gamble table block entity type is null - renderer NOT registered, "
                            + "the table will be invisible. Check that TNCasinoRegistry was loaded.");
        }
    }
}
