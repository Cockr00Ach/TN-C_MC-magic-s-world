package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 教官只在客户端注册渲染器；模型/动画/贴图资源在 assets/tnc 下。 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WarriorClientEvents {
    private WarriorClientEvents() {
    }
    @SubscribeEvent public static void bowProperties(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){
        event.enqueueWork(()->{
            var bow=TNMod.BOW.get();
            net.minecraft.client.renderer.item.ItemProperties.register(bow,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","pulling"),
                    (stack,level,entity,seed)->entity!=null&&entity.isUsingItem()&&entity.getUseItem()==stack?1:0);
            net.minecraft.client.renderer.item.ItemProperties.register(bow,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","pull"),
                    (stack,level,entity,seed)->entity!=null&&entity.getUseItem()==stack?(stack.getUseDuration()-entity.getUseItemRemainingTicks())/20f:0);
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WarriorEntities.TEST_DUMMY.get(),TestDummyRenderer::new);
        event.registerEntityRenderer(WarriorEntities.INSTRUCTOR.get(), InstructorRenderer::new);
    }

    /**
     * 给每个皮肤变体的玩家渲染器挂上刀光层。
     *
     * <p>用 {@code EntityRenderersEvent.AddLayers} 而不是自己去包 {@code PlayerRenderer}：
     * 这样和别的模组加的层不打架，原版换皮肤也不用管。Default / Slim 两套模型都会走到。
     */
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new WarriorTrailLayer(renderer));
            }
        }    }
}
