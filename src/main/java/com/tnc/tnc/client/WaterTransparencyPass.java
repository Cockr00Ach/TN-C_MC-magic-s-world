package com.tnc.tnc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Water transparency is composited after opaque entities, while still testing world depth. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WaterTransparencyPass {
    private static final Source SOURCE=new Source();
    private static boolean collecting,pending;
    private static Matrix4f modelView;
    private WaterTransparencyPass() {}
    static MultiBufferSource route(MultiBufferSource original) {
        if(!collecting)return original;
        return type->{
            if(!SOURCE.accepts(type))return original.getBuffer(type);
            if(!pending)modelView=new Matrix4f(RenderSystem.getModelViewMatrix());
            pending=true;
            return SOURCE.getBuffer(type);
        };
    }
    static void begin() {
        SOURCE.discardFrame();pending=false;modelView=null;collecting=true;
    }
    static void finish() {
        collecting=false;
        if(!pending)return;
        var matrices=RenderSystem.getModelViewStack();
        int framebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        matrices.pushPose();
        try {
            // Vertex positions already contain the entity pose. Do not apply the late view twice.
            matrices.setIdentity();matrices.mulPoseMatrix(modelView);RenderSystem.applyModelViewMatrix();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            SOURCE.endBatch();
        } finally {
            matrices.popPose();RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
            SOURCE.discardFrame();pending=false;modelView=null;
        }
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)begin();
        else if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)finish();
    }
    private static final class Source extends MultiBufferSource.BufferSource {
        Source(){super(new BufferBuilder(256),layers());}
        private static LinkedHashMap<RenderType,BufferBuilder> layers() {
            var layers=new LinkedHashMap<RenderType,BufferBuilder>();
            // Separate builders keep consumers valid when bodies, models and glow interleave.
            layers.put(WaterRenderTypes.geometry(),new BufferBuilder(262144));
            layers.put(WaterModelRenderTypes.alpha(),new BufferBuilder(131072));
            layers.put(WaterRenderTypes.glow(),new BufferBuilder(131072));
            layers.put(WaterModelRenderTypes.emission(),new BufferBuilder(131072));
            return layers;
        }
        boolean accepts(RenderType type){return fixedBuffers.containsKey(type);}
        void discardFrame(){
            for(var buffer:startedBuffers) {
                if(buffer.building())buffer.end().release();
                buffer.discard();
            }
            startedBuffers.clear();lastState=Optional.empty();
        }
    }
}
