package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.*;
import java.util.*;

/** Approved free mesh, uploaded once per LOD and shared by all dragon instances. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class InkCelestialDragonRenderer extends EntityRenderer<InkCelestialDragonEntity> {
    private static final ResourceLocation MESH=ResourceLocation.fromNamespaceAndPath("tnc","meshes/ink_celestial_dragon.mesh");
    private static InkDragonMeshData data;
    private static ShaderInstance waveShader;
    private static final Set<String> HEAD_GROUPS=Set.of("head","jaw","mane","beard","whiskers");
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("tnc","ink_dragon"),DefaultVertexFormat.POSITION_COLOR),shader->waveShader=shader);
    }
    private static final Map<Integer,List<Batch>> GPU=new HashMap<>();
    private static long uploadNanos;private static int uploads;
    private record Batch(InkDragonMeshData.Group group,VertexBuffer buffer){}
    public InkCelestialDragonRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public boolean shouldRender(InkCelestialDragonEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        // InkDragonDistantView owns exactly one world pass for both tracked and remote dragons.
        return false;
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->Minecraft.getInstance().execute(InkCelestialDragonRenderer::clear));
    }
    private static void clear(){for(var lod:GPU.values())for(var batch:lod)batch.buffer.close();GPU.clear();data=null;uploads=0;uploadNanos=0;}
    static void warmUp(){for(int lod=0;lod<3;lod++)mesh(lod);}
    private static List<Batch> mesh(int lod){
        return GPU.computeIfAbsent(lod,key->{
            long before=System.nanoTime();List<Batch> batches=new ArrayList<>();
            try{
                if(data==null)try(var input=Minecraft.getInstance().getResourceManager().open(MESH)){data=InkDragonMeshData.read(input);}
                for(var group:data.groups()){
                    var builder=new BufferBuilder(Math.max(256,group.levels().get(lod).length));
                    builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
                    ByteBuffer bytes=ByteBuffer.wrap(group.levels().get(lod)).order(ByteOrder.BIG_ENDIAN);
                    while(bytes.hasRemaining()){
                        float x=bytes.getFloat(),y=bytes.getFloat(),z=bytes.getFloat();
                        builder.vertex(x,y,z).color(bytes.get()&255,bytes.get()&255,bytes.get()&255,bytes.get()&255).endVertex();
                    }
                    var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(builder.end());VertexBuffer.unbind();
                    batches.add(new Batch(group,buffer));uploads++;
                }
            }catch(Exception failure){for(var b:batches)b.buffer.close();throw new IllegalStateException("Cannot load approved ink dragon mesh",failure);}
            uploadNanos+=System.nanoTime()-before;return List.copyOf(batches);
        });
    }
    public static Map<String,Object> diagnostics(){return Map.of("fullDetailTriangles",data==null?0:data.triangles(0),
            "middleDetailTriangles",data==null?0:data.triangles(1),"farDetailTriangles",data==null?0:data.triangles(2),
            "gpuBatchUploads",uploads,"loadedLods",GPU.size(),"initialUploadMilliseconds",uploadNanos/1e6,
            "sharedStaticVertexBuffers",true,"continuousWaveShader",waveShader!=null,"independentJaw",data!=null&&data.groups().stream().anyMatch(g->g.name().equals("jaw")),"bodyScale",InkDragonCombatRules.BODY_SCALE);}
    @Override public void render(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        renderDragon(entity,yaw,partial,pose,buffers,light,1,false);
    }
    public void renderWorld(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers){
        renderDragon(entity,yaw,partial,pose,buffers,LightTexture.FULL_BRIGHT,1,false);
    }
    /** Same approved mesh and shared GPU buffers, no duplicate geometry or phantom entities. */
    void phantom(InkCelestialDragonEntity entity,PoseStack pose,MultiBufferSource buffers,float alpha){
        renderDragon(entity,90,0,pose,buffers,LightTexture.FULL_BRIGHT,Mth.clamp(alpha,0,1),true);
    }
    private void renderDragon(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,float opacity,boolean phantom){
        float age=entity.tickCount+partial;
        int lod=Boolean.getBoolean("tnc.inkDragonAudit")?0:InkDragonMeshData.lod(entity.distanceTo(Minecraft.getInstance().getCameraEntity()));
        var batches=mesh(lod);boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),
                depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ShaderInstance previous=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
        int[] blendFactors=phantom?new int[]{GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA)}:null;
        pose.pushPose();
        try{
            pose.scale(InkDragonCombatRules.BODY_SCALE,InkDragonCombatRules.BODY_SCALE,InkDragonCombatRules.BODY_SCALE);
            boolean grounded=entity.isGroundedForObservation();
            pose.translate(0,grounded?1-InkDragonFlightRules.GROUND_MODEL_DROP/InkDragonCombatRules.BODY_SCALE:1.0+Math.sin(age*.025)*.16,0);pose.mulPose(Axis.YP.rotationDegrees(90-yaw));
            float breath=1+(float)Math.sin(age*.04)*.002F;pose.scale(breath,breath,breath);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(!phantom);RenderSystem.disableCull();
            if(phantom){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
            ShaderInstance shader=waveShader==null?GameRenderer.getPositionColorShader():waveShader;
            if(shader.getUniform("WaveTime")!=null)shader.getUniform("WaveTime").set(age);
            if(shader.getUniform("FlightAmount")!=null)shader.getUniform("FlightAmount").set(grounded?0F:1F);
            RenderSystem.setShaderColor(1,1,1,opacity);RenderSystem.setShader(()->shader);
            for(var batch:batches){
                var group=batch.group;pose.pushPose();
                if(HEAD_GROUPS.contains(group.name())){
                    pose.translate(-4,5,0);pose.mulPose(Axis.ZP.rotationDegrees(entity.breathPitch()));pose.translate(4,-5,0);
                }
                if(group.name().equals("jaw")||group.name().equals("beard")){
                    pose.translate(-5.7,4.15,0);pose.mulPose(Axis.ZP.rotationDegrees(-7+55*entity.jawOpen(partial)));pose.translate(5.7,-4.15,0);
                }
                pose.translate(group.x(),group.y(),group.z());
                float sway=(float)Math.sin(age*.035+group.name().hashCode()%9);
                switch(group.name()){
                    case "head"->pose.mulPose(Axis.YP.rotationDegrees(Mth.clamp(Mth.wrapDegrees(entity.yHeadRot-entity.yBodyRot)*.1F,-4,4)));
                    case "mane","beard","whiskers"->pose.mulPose(Axis.YP.rotationDegrees(sway*1.4F));
                    case "tail"->pose.mulPose(Axis.YP.rotationDegrees(sway*1.8F));
                    default->{if(group.name().contains("leg"))pose.mulPose(Axis.ZP.rotationDegrees(sway*.5F));}
                }
                pose.translate(-group.x(),-group.y(),-group.z());batch.buffer.bind();
                var modelView=new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
                batch.buffer.drawWithShader(modelView,RenderSystem.getProjectionMatrix(),shader);
                pose.popPose();
            }
        }finally{
            VertexBuffer.unbind();pose.popPose();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(blendFactors!=null)RenderSystem.blendFuncSeparate(blendFactors[0],blendFactors[1],blendFactors[2],blendFactors[3]);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(depthWrite);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);if(previous!=null)RenderSystem.setShader(()->previous);
        }
        if(phantom)return;
        if(entity.inkMove()==1&&entity.moveAge()>=34&&entity.moveAge()<106){
            float scale=InkDragonCombatRules.BODY_SCALE;pose.pushPose();
            try{pose.scale(scale,scale,scale);
                InkDragonBreathVisuals.aimed(age,entity.mouthPosition().subtract(entity.position()).scale(1/scale),entity.effectVector("point").subtract(entity.position()).scale(1/scale),
                    entity.effectVector("normal"),entity.breathHit(),pose,buffers);
            }finally{pose.popPose();}
        }
        if(entity.inkMove()==2&&entity.moveAge()>=40){
            InkDragonDiveVisuals.render(entity.moveAge()+partial,entity.effectVector("high").subtract(entity.position()),
                    entity.effectVector("point").subtract(entity.position()),entity,pose,buffers);
        }
        // A few long ink ribbons; no continuously spawning particle cloud.
        for(int side:new int[]{-1,1}){
            List<Vec3> ribbon=new ArrayList<>();
            for(int i=0;i<28;i++){
                Vec3 local=new Vec3(-6.3+i*.09,3.6-i*.055,side*(.2+i*.035)+Math.sin(age*.045-i*.25)*i*.004)
                        .scale(InkDragonCombatRules.BODY_SCALE).yRot((float)Math.toRadians(90-yaw));ribbon.add(entity.position().add(local).add(0,InkDragonCombatRules.BODY_SCALE-(entity.isGroundedForObservation()?InkDragonFlightRules.GROUND_MODEL_DROP:0),0));
            }
            InkCraneRitualVisuals.brushTrail(ribbon,entity.position(),age,.065,pose,buffers);
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(InkCelestialDragonEntity entity){return MESH;}
}
