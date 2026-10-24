package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VolcanicTortoiseClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VolcanicTortoiseRegistration.TYPE, VolcanicTortoiseRenderer::new);
        event.registerEntityRenderer(VolcanicTortoiseRegistration.FIREBALL, VolcanicFireballRenderer::new);
    }
}
