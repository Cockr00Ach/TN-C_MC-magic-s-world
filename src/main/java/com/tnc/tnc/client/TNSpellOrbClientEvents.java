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
        event.registerEntityRenderer(TNOrbEntities.LIGHTNING_STRIKE.get(), TNLightningStrikeRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.WATER_SPELL.get(), TNWaterSpellRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.WATER_BOLT.get(), TNWaterBoltRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.WATER_FIELD.get(), TNWaterFieldRenderer::new);
        // ★ 光天使（光系第二条链 t3/t4/t5 ✓）：半透明 + 自发光，见 TNAngelRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.ANGEL.get(), com.tnc.tnc.light.client.TNAngelRenderer::new);
        // ★ 实体光柱（光系第三条链「光线」✓）：一整根棱柱 + 彩虹顶点色，见 TNLightBeamRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.LIGHT_BEAM.get(),
                com.tnc.tnc.light.client.TNLightBeamRenderer::new);
        // ★ 战斗天使（光法第 4 条链「召唤天使」✓）：GeckoLib 模型 + 按档位放大，见 TNFightingAngelRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.FIGHTING_ANGEL.get(),
                com.tnc.tnc.light.client.TNFightingAngelRenderer::new);
        // ★ 光明龙（光法第 5 条链「光龙」✓）：作者的东方龙 + 半透明自发光，见 TNDragonRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.LIGHT_DRAGON.get(),
                com.tnc.tnc.light.client.TNDragonRenderer::new);
        // ★ 暗龙（暗系「暗龙」链 ✓，作者："复制一下光龙，生成一个暗龙" ✓）：
        //   同一个实体类 + 同一套 geo/动画 ⇒ 只换渲染器（另一张贴图 + 灵魂火粒子 ✓）
        event.registerEntityRenderer(TNOrbEntities.DARK_DRAGON.get(),
                com.tnc.tnc.dark.client.TNDarkDragonRenderer::new);
        // ★ 暗系第三条链「召唤」的五个召唤物（作者 2026-10-04："暗魔法的召唤流没实装吗" ✓）
        //   五档模型差别很大 ⇒ 五个类型共用**一个**渲染器（模型类按档位挑文件 ✓）
        event.registerEntityRenderer(TNOrbEntities.DARK_IMP.get(),
                com.tnc.tnc.dark.client.TNDarkSummonRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.DARK_GUARD.get(),
                com.tnc.tnc.dark.client.TNDarkSummonRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.DARK_LORD.get(),
                com.tnc.tnc.dark.client.TNDarkSummonRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.DARK_KING.get(),
                com.tnc.tnc.dark.client.TNDarkSummonRenderer::new);
        event.registerEntityRenderer(TNOrbEntities.EVIL_GOD.get(),
                com.tnc.tnc.dark.client.TNDarkSummonRenderer::new);
    }
}
