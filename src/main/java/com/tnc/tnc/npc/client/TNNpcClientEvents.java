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
        event.registerLayerDefinition(TnHumanoidNpcModel.LAYER, TnHumanoidNpcModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 泛型显式写出：直接传方法引用时 javac 对 HumanoidMobRenderer 的两层泛型推断不稳
        // （会报"不兼容的参数类型"）。这里两个 NPC 共用同一个渲染器，区别只在皮肤。
        net.minecraft.client.renderer.entity.EntityRendererProvider<TnDialogueNpc> provider =
                TnNpcRenderer::new;
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.SELF.get(), provider);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.CAVA.get(), provider);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.HUAI.get(), provider);
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.ZHUANGQUERANG.get(), provider);
    }
}
