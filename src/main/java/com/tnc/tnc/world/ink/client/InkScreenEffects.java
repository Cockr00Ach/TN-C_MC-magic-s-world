package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Own post-chain after world composition, never replaces GameRenderer's or Oculus' active effect. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkScreenEffects {
    private static final ResourceLocation EFFECT=ResourceLocation.fromNamespaceAndPath("tnc","shaders/post/ink_grayscale.json");
    private static PostChain chain;private static int width,height;private static boolean failed,wasCursed;
    public static boolean cursed(){return InkDragonDread.active(Minecraft.getInstance().player);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var p=Minecraft.getInstance().player;
        if(p==null){wasCursed=false;return;}boolean now=cursed();if(now!=wasCursed){InkSpellTimeBridge.retime(p,now?4:.25);wasCursed=now;}}
    public static void finishFrame(float partial){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        // Ink blocks, terrain and sky already have monochrome materials. Entering
        // the dimension must not filter the player, inventory or other GUI mods.
        // Only the explicit, time-limited dragon debuff owns a final-frame effect.
        if(cursed())process(partial);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(!cursed()||mc.options.hideGui)return;
        var effect=mc.player.getEffect(InkDragonDread.EFFECT);String label="墨魇 · 失色  行动25%  "+(int)Math.ceil(effect.getDuration()/20.0)+"s";
        int x=Math.max(8,mc.getWindow().getGuiScaledWidth()-mc.font.width(label)-12);e.getGuiGraphics().drawString(mc.font,label,x,12,0xEEEEEE,true);}
    private static void process(float partial){
        var mc=Minecraft.getInstance();if(failed)return;
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        int depthFunc=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        int src=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        try{
            if(chain==null)chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),EFFECT);
            int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
            if(w!=width||h!=height){chain.resize(w,h);width=w;height=h;}
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();chain.process(partial);
        }catch(Exception e){failed=true;org.slf4j.LoggerFactory.getLogger(InkScreenEffects.class).error("Ink final-frame grayscale failed",e);
        }finally{
            mc.getMainRenderTarget().bindWrite(false);
            com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(src,dst,srcAlpha,dstAlpha);
            if(blend)com.mojang.blaze3d.systems.RenderSystem.enableBlend();else com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.depthFunc(depthFunc);com.mojang.blaze3d.systems.RenderSystem.depthMask(depthWrite);
            if(depth)com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();else com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        }
    }
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)r->Minecraft.getInstance().execute(()->{if(chain!=null)chain.close();chain=null;width=height=0;failed=false;}));}
    }
}
