package com.tnc.tnc.world.ink.client;


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

import java.nio.*;
import java.util.*;

/** Shared decoded free mesh, submitted through the shader-compatible entity pipeline. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class InkCelestialDragonRenderer extends EntityRenderer<InkCelestialDragonEntity> {
    private static final ResourceLocation MESH=ResourceLocation.fromNamespaceAndPath("tnc","meshes/ink_celestial_dragon.mesh");
    private static InkDragonMeshData data;
    private static long cameraPasses,shadowPasses;
    private static final Map<Integer,List<InkDragonBufferedMesh.Batch>> MESHES=new HashMap<>();
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private static final Set<String> HEAD_GROUPS=Set.of("head","jaw","mane","beard","whiskers");
    public InkCelestialDragonRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public boolean shouldRender(InkCelestialDragonEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){
        // Cull using the full animated dragon, never its small vanilla entity box.
        // In particular, do not submit an off-screen dragon to every shadow pass.
        return f.isVisible(e.getBoundingBoxForCulling());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->Minecraft.getInstance().execute(()->{MESHES.clear();data=null;}));
    }
    static void warmUp(){mesh(2);}
    private static List<InkDragonBufferedMesh.Batch> mesh(int lod){
        return MESHES.computeIfAbsent(lod,key->{
            try{
                if(data==null)try(var input=Minecraft.getInstance().getResourceManager().open(MESH)){data=InkDragonMeshData.read(input);}
                return data.groups().stream().map(g->InkDragonBufferedMesh.decode(g,lod)).toList();
            }catch(Exception e){throw new IllegalStateException("Cannot read ink dragon mesh",e);}
        });
    }
    public static Map<String,Object> diagnostics(){return Map.of("fullDetailTriangles",data==null?0:data.triangles(0),
        "middleDetailTriangles",data==null?0:data.triangles(1),"farDetailTriangles",data==null?0:data.triangles(2),
        "gpuBatchUploads",0,"loadedLods",MESHES.size(),"sharedStaticVertexBuffers",false,
        "continuousWaveShader",false,"standardEntityPipeline",true,"bodyScale",InkDragonCombatRules.BODY_SCALE);}
    public static Map<String,Object> structureDiagnostics(){
        var batches=mesh(0);int corners=0,unique=0,paired=0;
        for(var b:batches){corners+=b.mesh().corners().length;unique+=b.mesh().vertices();paired+=b.mesh().pairedQuads();}
        return Map.of("originalVisibleTriangles",data.triangles(0),"submittedCorners",corners,"animatedUniqueVertices",unique,
                "pairedQuads",paired,"cameraPasses",cameraPasses,"oculusShadowPasses",shadowPasses,"shadowTriangles",data.triangles(2));
    }
    @Override public void render(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        renderDragon(entity,yaw,partial,pose,buffers,light,1,false);
    }
    public void renderWorld(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers){
        renderDragon(entity,yaw,partial,pose,buffers,LightTexture.FULL_BRIGHT,1,false);
    }
    /** Reuse the silhouette LOD for large translucent duplicates. */
    void phantom(InkCelestialDragonEntity entity,PoseStack pose,MultiBufferSource buffers,float alpha){
        renderDragon(entity,90,0,pose,buffers,LightTexture.FULL_BRIGHT,Mth.clamp(alpha,0,1),true);
    }
    private void renderDragon(InkCelestialDragonEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,float opacity,boolean phantom){
        float age=entity.tickCount+partial;
        boolean shadow=InkShaderPass.shadow();
        if(shadow&&phantom)return;
        if(!phantom){if(shadow)shadowPasses++;else cameraPasses++;}
        if(entity.inkMove()==4&&InkDragonEruptionRules.hidden(entity.moveAge()))return;
        boolean audit=Boolean.getBoolean("tnc.inkDragonAudit");
        // Entity origin can be hundreds of blocks from its tail or head. Select facial
        // detail by the actual head, not by this unrelated origin distance.
        var head=entity.position().add(InkDragonFlightRules.HEAD.scale(InkDragonCombatRules.BODY_SCALE)
                .yRot((float)Math.toRadians(90-yaw))).add(0,InkDragonCombatRules.BODY_SCALE,0);
        double headDistance=head.distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var bounds=entity.isGroundedForObservation()?InkDragonFlightRules.restingBounds(entity.position(),yaw):InkDragonFlightRules.bounds(entity.position(),yaw);
        double bodyDistance=camera.distanceTo(new Vec3(Mth.clamp(camera.x,bounds.minX,bounds.maxX),
                Mth.clamp(camera.y,bounds.minY,bounds.maxY),Mth.clamp(camera.z,bounds.minZ,bounds.maxZ)));
        var batches=mesh(2);
        var out=buffers.getBuffer(phantom?RenderType.entityTranslucent(WHITE):RenderType.entityCutoutNoCull(WHITE));
        var wave=InkDragonBufferedMesh.wave(age,entity.isGroundedForObservation());
        pose.pushPose();
        try{
            pose.scale(InkDragonCombatRules.BODY_SCALE,InkDragonCombatRules.BODY_SCALE,InkDragonCombatRules.BODY_SCALE);
            boolean grounded=entity.isGroundedForObservation();
            pose.translate(0,grounded?1-InkDragonFlightRules.GROUND_MODEL_DROP/InkDragonCombatRules.BODY_SCALE:1.0+Math.sin(age*.025)*.16,0);pose.mulPose(Axis.YP.rotationDegrees(90-yaw));
            if(entity.inkMove()==4){pose.translate(-4,5,0);pose.mulPose(Axis.ZP.rotationDegrees(InkDragonEruptionRules.pitch(entity.moveAge()+partial)));pose.translate(4,-5,0);}
            if(entity.inkMove()==5){pose.translate(-4,5,0);pose.mulPose(Axis.ZP.rotationDegrees(InkDragonDrainRules.pitch(entity.moveAge()+partial)));pose.translate(4,-5,0);}
            float breath=1+(float)Math.sin(age*.04)*.002F;pose.scale(breath,breath,breath);
            for(int index=0;index<batches.size();index++){
                var base=batches.get(index);
                double detailDistance=HEAD_GROUPS.contains(base.group().name())?headDistance:Math.min(headDistance,bodyDistance);
                int lod=shadow?2:audit?0:InkDragonRenderBudget.lod(base.group().name(),detailDistance,phantom);
                var batch=lod==2?base:mesh(lod).get(index);
                var group=batch.group();pose.pushPose();
                if(HEAD_GROUPS.contains(group.name())){
                    pose.translate(-4,5,0);
                    if(entity.inkMove()==0)pose.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(Mth.wrapDegrees(entity.yHeadRot-entity.getYRot()),-85,85)));
                    if(entity.inkMove()!=5)pose.mulPose(Axis.ZP.rotationDegrees(entity.breathPitch()));pose.translate(4,-5,0);
                }
                if(group.name().equals("jaw")||group.name().equals("beard")){
                    pose.translate(-5.7,4.15,0);pose.mulPose(Axis.ZP.rotationDegrees(-7+55*entity.jawOpen(partial)));pose.translate(5.7,-4.15,0);
                }
                pose.translate(group.x(),group.y(),group.z());
                float sway=(float)Math.sin(age*.035+group.name().hashCode()%9);
                switch(group.name()){
                    case "head"->{}
                    case "mane","beard","whiskers"->pose.mulPose(Axis.YP.rotationDegrees(sway*1.4F));
                    case "tail"->pose.mulPose(Axis.YP.rotationDegrees(sway*1.8F));
                    default->{if(group.name().contains("leg"))pose.mulPose(Axis.ZP.rotationDegrees(sway*.5F));}
                }
                pose.translate(-group.x(),-group.y(),-group.z());
                InkDragonBufferedMesh.draw(batch,wave,pose.last(),out,light,opacity);
                pose.popPose();
            }
        }finally{pose.popPose();}
        // The light pass gets only the independent solid silhouette, not spell
        // ribbons, translucent impact clouds or another giant ghost dragon.
        if(phantom||shadow)return;
        if(entity.inkMove()==3||entity.inkMove()==4)InkDragonStormVisuals.render(entity,partial,pose,buffers);
        if(entity.inkMove()==5)InkDragonDrainVisuals.render(entity,partial,pose,buffers);
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
    @Override public ResourceLocation getTextureLocation(InkCelestialDragonEntity entity){return WHITE;}
}
