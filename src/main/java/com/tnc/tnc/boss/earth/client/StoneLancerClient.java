package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.earth.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StoneLancerClient {
    @SubscribeEvent public static void models(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e){
        for(String name:new String[]{"earth_spear_sharp","earth_spike_sharp","earth_orb","earth_clod","earth_shell"})
            e.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","projectile/stone_lancer_"+name));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(StoneLancerRegistration.TYPE,StoneLancerRenderer::new);}
}
