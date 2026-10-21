package com.tnc.tnc.arcana.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tnc.tnc.mana.sight.client.ManaSightClient;

/** Open, softly feathered auroral folds across the upper sky. No enclosing polygon fence. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class AuroraRenderer {
    public static long renderedQuads;
    private static VertexBuffer skyBuffer;
    public static String projectionDiagnostic="";
    @SubscribeEvent public static void sky(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||!ArcanaClient.aurora)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        double time=(mc.level.getGameTime()+e.getPartialTick())*.0018;boolean sight=ManaSightClient.enabled();var pose=e.getPoseStack();pose.pushPose();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();RenderSystem.setShaderFogStart(10000);RenderSystem.setShaderFogEnd(12000);
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.enableDepthTest();RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        try{var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            int columns=256,rows=24;
            for(int layer=0;layer<3;layer++)for(int i=0;i<columns;i++)for(int j=0;j<rows;j++){
                double u=i/(double)columns,un=(i+1)/(double)columns,v=j/(double)rows,vn=(j+1)/(double)rows;
                sample(b,pose,u,v,time,layer,sight);sample(b,pose,un,v,time,layer,sight);sample(b,pose,un,vn,time,layer,sight);sample(b,pose,u,vn,time,layer,sight);
            }
            if(skyBuffer==null)skyBuffer=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            skyBuffer.bind();skyBuffer.upload(b.end());
            // Camera-centred sky coordinates need one explicit view transform. The shader pack
            // also changes RenderSystem's global model-view; pretransforming vertices then using
            // BufferUploader.drawWithShader applies an unrelated world transform a second time.
            var view=new org.joml.Matrix4f().rotateX((float)Math.toRadians(e.getCamera().getXRot())).rotateY((float)Math.toRadians(e.getCamera().getYRot()+180));
            skyBuffer.drawWithShader(view,e.getProjectionMatrix(),GameRenderer.getPositionColorShader());VertexBuffer.unbind();
            var clip=new org.joml.Vector4f(0,125,-170,1).mul(view).mul(e.getProjectionMatrix());
            if(projectionDiagnostic.isEmpty())projectionDiagnostic="camera="+e.getCamera().getYRot()+","+e.getCamera().getXRot()+";ndc="+(clip.x/clip.w)+","+(clip.y/clip.w)+","+(clip.z/clip.w);
            renderedQuads+=3L*columns*rows;
        }finally{RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();pose.popPose();}
    }
    private static void sample(BufferBuilder b,PoseStack pose,double u,double v,double time,int layer,boolean sight){
        double along=(u-.5)*560,heading=.12+layer*.92,fold=Math.sin(u*8.2+time+layer)*44+Math.sin(u*18.4-time*.4+layer)*13;
        double transverse=-170+layer*85+fold+Math.sin(v*3+u*9+time)*v*18;
        double x=Math.cos(heading)*along-Math.sin(heading)*transverse,z=Math.sin(heading)*along+Math.cos(heading)*transverse;
        double y=150+layer*14+Math.sin(u*8.2+time+layer)*24-v*(50+Math.sin(u*9-time)*17);
        // Every neighboring cell samples the same density: no seams, shelves or hard top/bottom edges.
        double fringe=Math.pow(Math.sin(Math.PI*v),1.7),end=Math.pow(Math.sin(Math.PI*u),1.8);
        double ray=.42+.58*Math.pow(.5+.5*Math.sin(u*180+Math.sin(u*31+time)*1.5-time*.8),3);
        double veil=.45+.55*Math.pow(.5+.5*Math.sin(u*11-time*.35+layer),2);
        double alpha=(sight?.52:.025)*fringe*end*ray*veil;
        float hue=(float)(.35+.14*(.5+.5*Math.sin(u*4+time*.1+layer))+.16*Math.pow(v,2));
        int color=java.awt.Color.HSBtoRGB(hue,(float)(.52-.12*v),1);
        b.vertex((float)x,(float)y,(float)z).color(color>>16&255,color>>8&255,color&255,(int)(255*alpha)).endVertex();
    }
    private AuroraRenderer(){}
}