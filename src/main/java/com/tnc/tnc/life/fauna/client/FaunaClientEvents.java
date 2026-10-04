package com.tnc.tnc.life.fauna.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.fauna.FaunaRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FaunaClientEvents {
    private FaunaClientEvents() {}

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BellwoolSheepModel.LAYER, BellwoolSheepModel::createBodyLayer);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FaunaRegistry.BELLWOOL_SHEEP, BellwoolSheepRenderer::new);
    }
}
