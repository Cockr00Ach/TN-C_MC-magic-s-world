package com.tnc.tnc.alchemy.client;
import com.tnc.tnc.alchemy.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class AlchemyClient{
    @SubscribeEvent public static void models(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e){e.register(net.minecraft.resources.ResourceLocation.parse("tnc:block/spellflame_cauldron_lid"));}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{net.minecraft.client.gui.screens.MenuScreens.register(AlchemyContent.MENU,AlchemyScreen::new);net.minecraft.client.gui.screens.MenuScreens.register(AlchemyContent.PREP_MENU,com.tnc.tnc.journey.client.PreparationScreen::new);});}
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(AlchemyContent.ENTITY,AlchemyRenderer::new);e.registerBlockEntityRenderer(AlchemyContent.CACHE_ENTITY,HiddenCacheRenderer::new);}
}
