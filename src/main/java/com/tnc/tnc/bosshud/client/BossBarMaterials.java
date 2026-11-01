package com.tnc.tnc.bosshud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tnc.tnc.bosshud.BossHudTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shaded material field and scalable relief frame. All artwork is static; only health clipping moves. */
final class BossBarMaterials {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("tnc","textures/gui/boss_hud/materials_v1.png");
    private BossBarMaterials(){}

    static void draw(GuiGraphics g,BossHudTheme theme,int phase,int x,int y,int w,int h,float health,float ghost,double time){
        int index=theme.ordinal()-(theme.ordinal()>BossHudTheme.TORTOISE.ordinal()?1:0);
        int filled=Math.max(0,Math.min(w,Math.round(w*health))),trail=Math.max(filled,Math.min(w,Math.round(w*ghost)));
        float red=theme==BossHudTheme.CANGLAN&&phase==2?.78F:1;
        float green=theme==BossHudTheme.CANGLAN&&phase==2?.96F:1;
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try {
            // The empty slot retains its own faint material, rather than a featureless black rectangle.
            tint(red*.10F,green*.11F,.12F,1);
            field(g,index,x,y,w,h);
            if(trail>filled){
                g.enableScissor(x,y,x+trail,y+h);
                try {tint(red*.60F,green*.59F,.59F,.62F);field(g,index,x,y,w,h);}
                finally {g.disableScissor();}
            }
            if(filled>0){
                g.enableScissor(x,y,x+filled,y+h);
                try {
                    tint(red,green,1,1);field(g,index,x,y,w,h);
                    // Broad, slow specular movement is intentionally subtle. Ink receives a dim flowing wash.
                    int glint=x+(int)((time*15)%(w+44))-22;
                    float sheen=theme.ink()?.11F:.22F;
                    tint(1,1,1,sheen);
                    g.blit(ATLAS,glint,y,18,h,0F,1920F,128,32,1024,2048);
                    tint(red,green,1,.6F);
                    g.blit(ATLAS,x+filled-3,y,4,h,128F,1920F,32,32,1024,2048);
                } finally {g.disableScissor();}
            }
            tint(red,green,1,1);
            // Preserve cap proportions as the center stretches; compact rows scale the relief vertically as well.
            int left=Math.round(5*h/7F),right=Math.round(10*h/7F),cap=Math.round(128*h/38F);
            int total=w+left+right,center=total-cap*2,row=index*128;
            g.pose().pushPose();
            try {
                g.pose().translate(x-left,y+h/2F,0);g.pose().scale(1,h/38F,1);
                g.blit(ATLAS,0,-64,cap,128,0F,row,128,128,1024,2048);
                g.blit(ATLAS,cap,-64,center,128,128F,row,768,128,1024,2048);
                g.blit(ATLAS,cap+center,-64,cap,128,896F,row,128,128,1024,2048);
            } finally {g.pose().popPose();}
        } finally {RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static void field(GuiGraphics g,int index,int x,int y,int width,int height){
        g.blit(ATLAS,x,y,width,height,0F,1536+index*32F,1024,32,1024,2048);
    }
    private static void tint(float r,float g,float b,float alpha){RenderSystem.setShaderColor(r,g,b,alpha);}
}
