package com.tnc.tnc.life.wonders.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.wonders.WonderContent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WonderClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{var cutout=net.minecraft.client.renderer.RenderType.cutout();net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(WonderContent.VINE_LEAF,cutout);net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(WonderContent.SKY_VINE,cutout);net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(WonderContent.ROPE,cutout);});}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WonderContent.FARLIGHT,ThrownItemRenderer::new);}
}
