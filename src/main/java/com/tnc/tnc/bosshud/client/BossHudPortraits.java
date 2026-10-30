package com.tnc.tnc.bosshud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tnc.tnc.bosshud.BossHudTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Twelve baked portraits share one filtered atlas; no models or entities are rendered in the HUD. */
final class BossHudPortraits {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("tnc","textures/gui/boss_hud/portraits_v1.png");
    private BossHudPortraits(){}
    static void draw(GuiGraphics graphics,BossHudTheme theme){
        if(theme==BossHudTheme.TORTOISE)return;
        int index=theme.ordinal()-(theme.ordinal()>BossHudTheme.TORTOISE.ordinal()?1:0);
        // Flush geometry first: a filled portrait must cover the terminal under it, not be overdrawn by it later.
        graphics.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);
        graphics.blit(ATLAS,-18,-18,36,36,index%4*256F,index/4*256F,256,256,1024,768);
    }
}
