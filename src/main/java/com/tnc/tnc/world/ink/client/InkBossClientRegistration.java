package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.ink.InkBossRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InkBossClientRegistration {
    private InkBossClientRegistration() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(InkBossRegistration.INK_CELESTIAL_DRAGON,InkCelestialDragonRenderer::new);
        event.registerEntityRenderer(InkBossRegistration.INK_WINGED_TIGER,InkWingedTigerRenderer::new);
        event.registerEntityRenderer(InkBossRegistration.INK_TIGER_EFFECT,InkTigerEffectRenderer::new);
        event.registerEntityRenderer(InkBossRegistration.INK_ABYSS_DRAKE, InkAbyssDrakeRenderer::new);
        event.registerEntityRenderer(InkBossRegistration.INK_CRANE_BOSS, InkCraneBossRenderer::new);
        event.registerEntityRenderer(InkBossRegistration.INK_CRANE_PHANTOM, InkCranePhantomRenderer::new);
    }
}
