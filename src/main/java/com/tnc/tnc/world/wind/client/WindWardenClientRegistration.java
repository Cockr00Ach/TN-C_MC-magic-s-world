package com.tnc.tnc.world.wind.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.wind.WindWardenRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Binds the GeoEntityRenderer; a missing renderer would crash on the first spawn. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WindWardenClientRegistration {
    private WindWardenClientRegistration() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WindWardenRegistration.WIND_WARDEN, WindWardenRenderer::new);
    }
}
