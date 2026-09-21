package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.SelfNpcEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.tnc.tnc.TNMod;

/**
 * NPC 的客户端接线：把模型层烘焙出来、把渲染器挂到实体上。
 *
 * <p><b>两个事件必须走 MOD 总线</b>（{@code Bus.MOD}）—— 它们是 mod 生命周期事件，
 * 挂到 FORGE 总线上会<b>静默不生效</b>（项目里已经踩过一模一样的坑：
 * {@code RegisterKeyMappingsEvent} 也是这个性质，见 TNMod.ClientModEvents 的注释）。
 *
 * <p>少了 {@code onRegisterLayers} → 实体能生成但<b>一渲染就崩</b>（模型层不存在）；
 * 少了 {@code onRegisterRenderers} → 生成出来是<b>隐形</b>的。
 * 两个都不报错，所以这两步一定要成对出现。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNNpcClientEvents {

    private TNNpcClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SelfNpcModel.LAYER, SelfNpcModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 显式写出 EntityRendererProvider<SelfNpcEntity>：直接传方法引用时
        // javac 对 HumanoidMobRenderer 的两层泛型推断不稳（会报"不兼容的参数类型"）。
        net.minecraft.client.renderer.entity.EntityRendererProvider<SelfNpcEntity> provider =
                SelfNpcRenderer::new;
        event.registerEntityRenderer(com.tnc.tnc.npc.TNNpcs.SELF.get(), provider);
    }
}
