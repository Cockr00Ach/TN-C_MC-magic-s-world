package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 法术实体（环绕雷球）的客户端接线：烘焙模型层 + 挂渲染器。
 *
 * <p>和 NPC 那边一样的两个坑（都踩过 ✗）：
 * <ul>
 *   <li>两个事件必须走 <b>MOD 总线</b>，挂错总线会**静默不生效**；</li>
 *   <li>少了 {@code RegisterLayerDefinitions} → 实体一渲染就崩；少了 {@code RegisterRenderers} → 实体**隐形**。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNSpellOrbClientEvents {

    private TNSpellOrbClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TNThunderOrbModel.LAYER, TNThunderOrbModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TNOrbEntities.THUNDER_ORB.get(), TNThunderOrbRenderer::new);
        // 魔法阵与冲击波环共用同一个渲染器（都是"贴地的一张面片"）✓
        event.registerEntityRenderer(TNOrbEntities.MAGIC_CIRCLE.get(), TNMagicCircleRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.SHOCKWAVE.get(), TNMagicCircleRenderer::new);
    }
}
