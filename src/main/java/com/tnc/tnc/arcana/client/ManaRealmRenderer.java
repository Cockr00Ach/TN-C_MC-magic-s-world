package com.tnc.tnc.arcana.client;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** World-only desaturation, followed by a feathered translucent oasis, before GUI composition. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ManaRealmRenderer {
    private static ShaderInstance shader;private static RenderTarget copy;private static VertexBuffer sphere;
    public static long desolationFrames,oasisQuads;
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void load(net.minecraftforge.client.event.RegisterShadersEvent e)throws java.io.IOException{e.registerShader(new ShaderInstance(e.getResourceProvider(),net.minecraft.resources.ResourceLocation.parse("tnc:mana_desolation"),DefaultVertexFormat.POSITION_TEX),s->shader=s);}
    }
    @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderLevelStageEvent e){if(e.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL||!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled())return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(ArcanaClient.deadMana&&shader!=null)desaturate(mc);
        if(ArcanaClient.oases.length==0)return;
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var camera=e.getCamera().getPosition();double time=(mc.level.getGameTime()+e.getPartialTick())*.012;
            for(long raw:ArcanaClient.oases){var center=Vec3.atCenterOf(BlockPos.of(raw)).subtract(camera);if(center.lengthSqr()>160*160)continue;
                for(int i=0;i<96;i++)for(int j=0;j<48;j++){point(b,center,i/96.,j/48.,time);point(b,center,(i+1)/96.,j/48.,time);point(b,center,(i+1)/96.,(j+1)/48.,time);point(b,center,i/96.,(j+1)/48.,time);}oasisQuads+=96*48;
            }
            if(sphere==null)sphere=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);sphere.bind();sphere.upload(b.end());var view=new org.joml.Matrix4f().rotateX((float)Math.toRadians(e.getCamera().getXRot())).rotateY((float)Math.toRadians(e.getCamera().getYRot()+180));sphere.drawWithShader(view,e.getProjectionMatrix(),GameRenderer.getPositionColorShader());VertexBuffer.unbind();
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    public static boolean naturalAt(Vec3 at){var mc=Minecraft.getInstance();if(mc.level==null||!com.tnc.tnc.arcana.ArcanaRealms.barren(mc.level))return true;for(long raw:ArcanaClient.oases)if(Vec3.atCenterOf(BlockPos.of(raw)).distanceToSqr(at)<=48*48)return true;return false;}
    private static void point(BufferBuilder b,Vec3 center,double u,double v,double time){double a=u*Math.PI*2,p=v*Math.PI,fold=.5+.5*Math.sin(a*3+Math.sin(p*4+time)*.8-time);double radius=48+.12*Math.sin(a*7+p*8+time);double shimmer=Math.pow(.5+.5*Math.sin(a*21+p*17+time*.7),6);int rgb=java.awt.Color.HSBtoRGB((float)(.36+.25*fold),.45f,1);int alpha=(int)(255*(.008+.033*shimmer)*Math.sin(p));b.vertex(center.x+Math.cos(a)*Math.sin(p)*radius,center.y+Math.cos(p)*radius,center.z+Math.sin(a)*Math.sin(p)*radius).color(rgb>>16&255,rgb>>8&255,rgb&255,alpha).endVertex();}
    private static void desaturate(Minecraft mc){var main=mc.getMainRenderTarget();if(copy==null)copy=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);else if(copy.width!=main.width||copy.height!=main.height)copy.resize(main.width,main.height,Minecraft.ON_OSX);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,copy.frameBufferId);org.lwjgl.opengl.GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,copy.width,copy.height,org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT,org.lwjgl.opengl.GL30.GL_NEAREST);main.bindWrite(false);
        var previous=RenderSystem.getShader();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();try{RenderSystem.setShader(()->shader);shader.setSampler("Scene",copy.getColorTextureId());var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());desolationFrames++;}finally{RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShader(()->previous);}
    }
    private ManaRealmRenderer(){}
}
