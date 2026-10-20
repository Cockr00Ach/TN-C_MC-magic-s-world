package com.tnc.tnc.dungeon.client;
import com.tnc.tnc.dungeon.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DungeonClient {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){for(var f:MobCatalog.Family.values())e.registerLayerDefinition(DungeonMobRenderer.layer(f),()->DungeonMobModel.layer(f));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){for(var s:MobCatalog.ALL)e.registerEntityRenderer(DungeonContent.MOBS.get(s.id()),c->new DungeonMobRenderer(c,s.family()));e.registerEntityRenderer(DungeonContent.BOLT,c->new EntityRenderer<DungeonBolt>(c){@Override public ResourceLocation getTextureLocation(DungeonBolt b){return DungeonContent.id("textures/item/tidal_shell_crystal.png");}});}
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->DungeonContent.GATES.values().forEach(b->ItemBlockRenderTypes.setRenderLayer(b,RenderType.translucent())));}
}
