package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 教官只在客户端注册渲染器；模型/动画/贴图资源在 assets/tnc 下。 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WarriorClientEvents {
    private WarriorClientEvents() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WarriorEntities.INSTRUCTOR.get(), InstructorRenderer::new);
    }
}
