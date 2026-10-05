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
        // ★ 火球链的自有投射物（火系第 1 条链 ✓）：命中要精确知道法术与伤害才能挂焚身，
        //   所以不走引擎的 PROJECTILE。视觉暂由粒子承担，见 TNFireBoltRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.FIRE_BOLT.get(), TNFireBoltRenderer::new);
        // ★ 熔岩地（火球链 t3 熔岩火球起）：贴地的持续灼烧区域，视觉同样由粒子承担 ✓
        event.registerEntityRenderer(TNOrbEntities.LAVA_FIELD.get(), TNLavaFieldRenderer::new);
        // ★ 两座法阵（t4 熔岳天倾的头顶阵 / t5 炎葬的脚下阵）：同样是粒子表达，
        //   共用通用的空渲染器，见 TNParticleOnlyRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.SKYFALL.get(), TNSigilRenderer::skyfall);
        event.registerEntityRenderer(TNOrbEntities.METEOR_FALL.get(), TNSigilRenderer::meteorFall);
        // ★ 光天使（光系第二条链 t3/t4/t5 ✓）：半透明 + 自发光，见 TNAngelRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.ANGEL.get(), com.tnc.tnc.light.client.TNAngelRenderer::new);
        // ★ 实体光柱（光系第三条链「光线」✓）：一整根棱柱 + 彩虹顶点色，见 TNLightBeamRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.LIGHT_BEAM.get(),
                com.tnc.tnc.light.client.TNLightBeamRenderer::new);
        // ★ 战斗天使（光法第 4 条链「召唤天使」✓）：GeckoLib 模型 + 按档位放大，见 TNFightingAngelRenderer ✓
        event.registerEntityRenderer(TNOrbEntities.FIGHTING_ANGEL.get(),
                com.tnc.tnc.light.client.TNFightingAngelRenderer::new);
        // ★ 龙（光龙 / 暗龙 ✓）：走**普通 Forge 渲染器** ✓，几何+贴图全内嵌 ✓（见 TNDragonRenderer ✓）。
        //   ★ 这一句是**必须**的 ✗ —— 之前用原版 Display 实体、以为"原版会自己画" ✗，
        //   结果原版只给内置的 minecraft:block_display 注册渲染器 ✓，我们自己的实体类型没有 ✗
        //   ⇒ EntityRenderDispatcher 空指针 ⇒ **释放龙直接卡退** ✗（崩报里那句 entityrenderer is null ✓）。
        event.registerEntityRenderer(TNOrbEntities.DRAGON.get(),
                com.tnc.tnc.light.client.TNDragonRenderer::new);
        // ★★ 2026-10-04 卡退修复 ✗：**每一个实体类型都必须有渲染器** ✓
        //   作者："释放龙法术会卡退" ✓ —— 崩报就是这一句：
        //     NullPointerException: Cannot invoke "EntityRenderer.shouldRender(...)" because "entityrenderer" is null
        //     at EntityRenderDispatcher.render(EntityRenderDispatcher.java:127)
        //   根因：装机那一版（16:44 构建）里，龙已经换成新实体类型 {@code tnc:dragon}，
        //   而**客户端这一行还没进构建** ✗ ⇒ 龙一生成，客户端查不到渲染器 ⇒ 整个游戏崩 ✗。
        //   （同一个坑第三次了：2026-09-30 yan_dark、光龙、暗龙 ✗ ⇒ 见
        //    {@code OrbEntityRendererCoverageTest}：现在**每一个**实体类型都必须有渲染器，漏了就构建红 ✓）
        //
        //   下面这两条是**旧版 GeckoLib 龙**的类型（{@code TNDragonEntity} ✓，现在的法术已经不用它们 ✗）——
        //   照样补上渲染器 ✓：万一老存档里还留着、或者以后哪条路又生成它们，也不会再卡退 ✗。
        //   ★ 用 {@code TNDarkDragonRenderer}（它就是 {@code GeoEntityRenderer<TNDragonEntity>} ✓）：
        //     老的"光龙渲染器"已经跟着 Display 那条路改成渲染 Display 实体了 ✗，泛型对不上 ✗。
        event.registerEntityRenderer(TNOrbEntities.LIGHT_DRAGON.get(),
                com.tnc.tnc.dark.client.TNDarkDragonRenderer::new);
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
        // ★ 暗系第四条链「黑雾」（作者 2026-10-09："你全做吧"）：雾是**引擎 SpellCloud 的子类**
        //   （tnc:fog ⇒ DarkFogCloudEntity），所以直接复用**引擎自己的**
        //   SpellCloudRenderer ✓ —— 它会读 cloud.client_data.model 把穹顶画出来
        //   （走 CustomModels.render，和投射物同一条通道 ✓）。
        //   ⚠️ 这一行**不能省** ✗：实体类型没有渲染器 ⇒ 一生成就
        //   `entityrenderer is null` 卡退 ✗（这条坑在本项目已经崩过三次，
        //   现在由 OrbEntityRendererCoverageTest 在构建期把关 ✓）。
        //
        //   ★ 上游（协作者）把它挪进了一个 ModList 守卫 ⇒ **保留那层守卫** ✓：
        //   SpellCloudRenderer 来自 SpellEngine，没装引擎时那个类都不该被解析 ✗
        //   （TNFogClientRegistration 整个只在条件内被引用 ✓）。
        if (net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) {
            TNFogClientRegistration.register(event);
        }
    }
}
