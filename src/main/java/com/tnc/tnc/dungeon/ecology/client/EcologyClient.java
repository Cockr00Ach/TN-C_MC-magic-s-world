package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.*;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EcologyClient {
    private static int flashTicks;
    private static ClientLevel owner;
    public static boolean visibleSiphon(ManaSiphonJelly jelly){return ManaSightClient.enabled()||jelly.exposedTicks()>0||jelly.captive();}
    public static boolean canPickSiphon(ManaSiphonJelly jelly){return ManaSightClient.enabled()||jelly.exposedTicks()>0||jelly.captive();}
    public static void flash(int ticks){flashTicks=Math.max(flashTicks,Math.min(100,ticks));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=owner){owner=mc.level;flashTicks=0;}if(mc.player==null){flashTicks=0;return;}if(!mc.isPaused()&&flashTicks>0)flashTicks--;}
    @SubscribeEvent public static void gui(RenderGuiEvent.Post event){if(flashTicks<=0)return;var mc=Minecraft.getInstance();var gui=event.getGuiGraphics();float strength=flashTicks>80?1:flashTicks/80f;int alpha=(int)(245*Math.min(1,strength));gui.fill(0,0,gui.guiWidth(),gui.guiHeight(),(alpha<<24)|0xfffbe8);if(flashTicks>25)gui.drawCenteredString(mc.font,"魔核共鸣 · 强光致盲",gui.guiWidth()/2,gui.guiHeight()/2+36,0xff51465d);}
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(EcologyRenderer.layer("crab"),CrystalCrabModel::layer);event.registerLayerDefinition(EcologyRenderer.layer("siphon"),SiphonJellyModel::layer);event.registerLayerDefinition(EcologyRenderer.layer("flash"),GlareGargoyleModel::layer);event.registerLayerDefinition(EcologyRenderer.layer("worm"),SourceGrubModel::layer);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){for(String kind:EcologyContent.KINDS)event.registerEntityRenderer(EcologyContent.TYPES.get(kind),context->new EcologyRenderer(context,kind));event.registerEntityRenderer(EcologyContent.FLASK,ThrownItemRenderer::new);}
    }
}
