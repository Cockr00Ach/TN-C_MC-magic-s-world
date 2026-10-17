package com.tnc.tnc.client;

import com.tnc.tnc.magic.wind.WindArsenalRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindArsenalClientRegistration {
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(String n:WindConstructModels.NAMES)e.register(WindConstructModels.id(n));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WindArsenalRegistration.WEAPON,WindWeaponRenderer::new);}
    @SubscribeEvent public static void chains(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(com.tnc.tnc.magic.wind.WindChainRegistration.BALL,WindChainRenderers.Ball::new);
        e.registerEntityRenderer(com.tnc.tnc.magic.wind.WindChainRegistration.VOLLEY,WindChainRenderers.Volley::new);
    }
}
