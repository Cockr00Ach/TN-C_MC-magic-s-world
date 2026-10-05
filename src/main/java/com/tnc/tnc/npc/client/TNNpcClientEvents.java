package com.tnc.tnc.npc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.TnDialogueNpc;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * NPC 的客户端接线：把**共用的**人形模型层烘焙出来、把共用渲染器挂到每个 NPC 实体上。
 *
 * <p><b>两个事件必须走 MOD 总线</b>（{@code Bus.MOD}）—— 它们是 mod 生命周期事件，
 * 挂到 FORGE 总线上会<b>静默不生效</b>（项目里踩过一模一样的坑：
 * {@code RegisterKeyMappingsEvent} 也是这个性质，见 TNMod.ClientModEvents 的注释）。
 *
 * <p>少了 {@code onRegisterLayers} → 实体能生成但<b>一渲染就崩</b>（模型层不存在）；
 * 少了 {@code onRegisterRenderers} → 生成出来是<b>隐形</b>的。两个都不报错，必须成对出现。
 *
 * <p><b>加新 NPC 时这里只需加一行 registerEntityRenderer</b> —— 模型与渲染器都是共用的，
 * 区别只在各自的皮肤 PNG（由 {@link TnDialogueNpc#skinName()} 决定）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNNpcClientEvents {

    private TNNpcClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.tnc.tnc.tavern.client.TavernGuestRenderer.LAYER,com.tnc.tnc.tavern.client.TavernGuestRenderer::layer);
        event.registerLayerDefinition(TnHumanoidNpcModel.LAYER, TnHumanoidNpcModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.TAVERN_GUEST.get(),com.tnc.tnc.tavern.client.TavernGuestRenderer::new);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.RESIDENT.get(),net.minecraft.client.renderer.entity.VillagerRenderer::new);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.SERVICE_NPC.get(),net.minecraft.client.renderer.entity.VillagerRenderer::new);
        // 泛型显式写出：直接传方法引用时 javac 对 HumanoidMobRenderer 的两层泛型推断不稳
        // （会报"不兼容的参数类型"）。这里几个 NPC 共用同一个渲染器，区别只在皮肤。
        net.minecraft.client.renderer.entity.EntityRendererProvider<TnDialogueNpc> provider =
                TnNpcRenderer::new;
        // Self：装了 GeckoLib 就用 Bedrock 模型渲染器（**能播 Blockbench 关键帧** ✓，
        // 剧情演出的擦手/放抹布/看酒杯都靠它），否则走共用的人形渲染器 + 64×64 皮肤 ✓。
        // 分流判断与实体注册处（TNNpcs.SELF）用的是同一个 GeoSelfSupport.available() ✓。
        if (com.tnc.tnc.npc.compat.GeoSelfSupport.available()) {
            SelfBedrockRenderers.register(event);
        } else {
            event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.SELF.get(), provider);
        }
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.CAVA.get(), provider);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.HUAI.get(), provider);
        // 庄鹊让：装了 GeckoLib 就用女仆模型渲染器（GeckoLib 的类只在那个分支里被引用 ✓），
        // 否则走上面这个共用的人形渲染器 + 64×64 降级皮肤 ✓
        if (com.tnc.tnc.npc.compat.MaidNpcSupport.available()) {
            MaidNpcRenderers.register(event);
        } else {
            event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.ZHUANGQUERANG.get(), provider);
        }
        // 周坐望：与 self 同一条路（他也有自己的 Bedrock 模型，要 GeckoLib 才能画/播动作）✓，
        // 所以复用同一个分流判断 GeoSelfSupport.available() ✓
        if (com.tnc.tnc.npc.compat.GeoSelfSupport.available()) {
            ZuowangBedrockRenderers.register(event);
        } else {
            event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.ZUOWANG.get(), provider);
        }
        // ★★ 公孙衍（迷失）yan_dark —— **这一行必须在 if/else 外面** ✗✗
        //    （2026-09-30 客户端崩溃的真因，crash-2026-09-30_18.37.25-client.txt）
        //
        //    它原来被插进了上面那个 else 的花括号里 ✗：本整合包**装了 GeckoLib** ⇒
        //    GeoSelfSupport.available() == true ⇒ 只走 if 分支 ⇒ **else 整段永不执行** ⇒
        //    yan_dark 在客户端**根本没有渲染器** ✗。于是实体一进世界，
        //    Oculus/Iris 的影子渲染阶段会调 EntityRenderDispatcher.shouldRender(...)，
        //    而它内部直接 `this.getRenderer(entity).shouldRender(...)`、**不做 null 检查** ✗
        //    ⇒ NullPointerException，整个客户端崩掉（存档里/刚召唤出来就崩）。
        //    原版自己的 render 路径有 null 检查，所以"只有影子一开就崩"是这条 bug 的指纹 ✓。
        //
        //    ★ 教训：实体渲染器 / 模型层这类注册**不要写在条件分支里**，
        //      除非那个条件本身就是"这个实体类型存不存在"（self/maid 那种分流才是合法的 ✓）。
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.YAN_DARK.get(),
                ctx -> new com.tnc.tnc.boss.client.YanDarkRenderer(ctx));
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.APOLLO.get(), ctx -> new com.tnc.tnc.boss.client.TNApolloRenderer(ctx));
        // ★ 暗系巨人领主（作者 2026-10-03 ✓）：4.5 格高的巨人，渲染器只做整体缩放 ✓
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.DARK_GIANT.get(),
                ctx -> new com.tnc.tnc.boss.client.TNDarkGiantRenderer(ctx));
    }
}
