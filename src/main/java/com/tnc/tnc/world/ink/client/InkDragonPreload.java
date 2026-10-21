package com.tnc.tnc.world.ink.client;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Prepare shared geometry while entering a world, not when first spotting the boss. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkDragonPreload {
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){Minecraft.getInstance().execute(InkCelestialDragonRenderer::warmUp);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        for(var entity:level.entitiesForRendering())if(entity instanceof com.tnc.tnc.world.ink.InkCelestialDragonEntity)InkDragonCullingBridge.reveal(entity);
    }
}
