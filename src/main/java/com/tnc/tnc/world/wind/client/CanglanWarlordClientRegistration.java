package com.tnc.tnc.world.wind.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.wind.CanglanWarlordRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Binds the GeoEntityRenderer; a missing renderer would crash on the first spawn. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CanglanWarlordClientRegistration {
    private CanglanWarlordClientRegistration() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CanglanWarlordRegistration.CANGLAN_WARLORD, CanglanWarlordRenderer::new);
    }
}
