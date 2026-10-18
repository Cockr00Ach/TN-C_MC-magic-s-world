package com.tnc.tnc.casino.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.casino.machine.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CasinoResidentsClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CasinoResidents.TYPE.get(),Renderer::new);
    }
    private static final class Renderer extends net.minecraft.client.renderer.entity.MobRenderer<CasinoGuestEntity,net.minecraft.client.model.VillagerModel<CasinoGuestEntity>> {
        Renderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context c) {
            super(c,new net.minecraft.client.model.VillagerModel<>(c.bakeLayer(net.minecraft.client.model.geom.ModelLayers.VILLAGER)),.4F);
            addLayer(new net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer<>(this,c.getResourceManager(),"villager"));
        }
        @Override public net.minecraft.resources.ResourceLocation getTextureLocation(CasinoGuestEntity e) {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/villager/villager.png");
        }
    }
}
