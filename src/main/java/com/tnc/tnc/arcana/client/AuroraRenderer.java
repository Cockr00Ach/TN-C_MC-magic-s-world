package com.tnc.tnc.arcana.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tnc.tnc.mana.sight.client.ManaSightClient;

/** A sky-dome curtain, independent of particle distance and world chunk loading. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class AuroraRenderer {
    @SubscribeEvent public static void sky(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY||!ArcanaClient.aurora)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        double time=(mc.level.getGameTime()+e.getPartialTick())*.0015;boolean sight=ManaSightClient.enabled();
        var pose=e.getPoseStack();pose.pushPose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int curtain=0;curtain<(sight?5:1);curtain++)for(int i=0;i<180;i++)for(int row=0;row<6;row++){
            double a=i*Math.PI*2/180,an=(i+1)*Math.PI*2/180;
            double r=125+curtain*19,wave=Math.sin(a*3+time+curtain)*13+Math.cos(a*7-time*.6)*6;
            double wn=Math.sin(an*3+time+curtain)*13+Math.cos(an*7-time*.6)*6;
            double top=100+curtain*12+wave,topN=100+curtain*12+wn;
            int color=java.awt.Color.HSBtoRGB((float)((a/Math.PI*.23+curtain*.11+time*.014)%1),.52f,1);
            float alpha=(sight?.22f:.018f)*(1-row/6f)*(float)(.48+.52*Math.pow(Math.sin(a*19+time),2));
            float y=(float)(top-row*10),yn=(float)(topN-row*10);
            var m=pose.last().pose();float x=(float)(Math.cos(a)*r),z=(float)(Math.sin(a)*r),xn=(float)(Math.cos(an)*r),zn=(float)(Math.sin(an)*r);
            int red=color>>16&255,green=color>>8&255,blue=color&255;
            b.vertex(m,x,y,z).color(red,green,blue,(int)(alpha*255)).endVertex();b.vertex(m,xn,yn,zn).color(red,green,blue,(int)(alpha*255)).endVertex();
            b.vertex(m,xn,yn-10,zn).color(red,green,blue,(int)(alpha*.7*255)).endVertex();b.vertex(m,x,y-10,z).color(red,green,blue,(int)(alpha*.7*255)).endVertex();
        }
        BufferUploader.drawWithShader(b.end());RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();pose.popPose();
    }
    private AuroraRenderer(){}
}
