package com.tnc.tnc.life.botanical.client;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.botanical.BotanicalContent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BotanicalClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(BotanicalContent.FIELD_MENU,PortableFieldScreen::new));}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(String s:new String[]{"dance_bell_joint","dance_bell_head","dawn_disk_head","mist_cotton_ring","botanical_sprite_body","botanical_sprite_wing"})e.register(BotanicalContent.id("block/"+s));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(BotanicalContent.PLANT_ENTITY,BotanicalPlantRenderer::new);e.registerEntityRenderer(BotanicalContent.SPRITE,BotanicalSpriteRenderer::new);}
}
