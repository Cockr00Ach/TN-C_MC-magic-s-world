package com.tnc.tnc.sanctum.client;

import com.tnc.tnc.sanctum.FalseGodPart;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FalseGodPartRenderer {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(FalseGodPart.TYPE,NoopRenderer::new);
    }
}
