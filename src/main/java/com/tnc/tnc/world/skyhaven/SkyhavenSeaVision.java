package com.tnc.tnc.world.skyhaven;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** A layered bank of black storms and violent mana currents in F8 magic sight. */
@Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class SkyhavenSeaVision {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(2097152));
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> world=new java.lang.ref.WeakReference<>(null);
    private static List<SeaGyreLayout.Gyre> visible=List.of();
    private static final SeaStormVisibility VISIBILITY=new SeaStormVisibility();
    private static final boolean AUDIT=Boolean.getBoolean("tnc.skyhavenTornadoAudit");
    private static int lastStorms,lastDrawn,lastQuads,lastLinks;
    private static long samples;
    private static double totalMillis,maxMillis;
    public static Map<String,Object> auditSnapshot(){
        return Map.of("visible_storms",lastStorms,"drawn_storms",lastDrawn,"quads",lastQuads,"links",lastLinks,
                "sample_frames",samples,"mean_effect_cpu_ms",samples==0?0:totalMillis/samples,"max_effect_cpu_ms",maxMillis);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().equals(SkyhavenContent.KEY)
                ||!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()){
            visible=List.of();VISIBILITY.clear();world.clear();return;
        }
        if(world.get()!=mc.level){visible=List.of();VISIBILITY.clear();world=new java.lang.ref.WeakReference<>(mc.level);}
        long began=AUDIT?System.nanoTime():0;
        var camera=event.getCamera().getPosition();
        var eligible=new ArrayList<SeaGyreLayout.Gyre>();
        for(var g:SeaTornadoLayout.near(camera.x,camera.z)){
            var at=BlockPos.containing(g.x(),48,g.z());
            if(!mc.level.hasChunkAt(at)||!mc.level.getFluidState(at).is(SkyhavenContent.SOURCE))continue;
            if(mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())>51)continue;
            eligible.add(g);
        }
        // A previously confirmed sea column may finish fading after its source chunk
        // unloads. This uses cached knowledge only, never requests a far chunk.
        for(var g:visible)if(!eligible.contains(g)&&Math.hypot(g.x()-camera.x,g.z()-camera.z)<SeaTornadoGeometry.VIEW_DISTANCE
                &&!mc.level.hasChunkAt(BlockPos.containing(g.x(),48,g.z())))eligible.add(g);
        var storms=VISIBILITY.update(eligible,camera.x,camera.z,mc.level.getGameTime()+event.getPartialTick());
        visible=storms;
        var ordered=new ArrayList<>(storms);
        ordered.sort(Comparator.comparingDouble(g->Math.hypot(g.x()-camera.x,g.z()-camera.z)));
        double seconds=(mc.level.getGameTime()+event.getPartialTick())/20.;
        int quads=0,links=0,drawn=0;
        float previousFogStart=com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
        float previousFogEnd=com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        try{
        // This magical volume has its own bounded distance fade; ordinary ocean fog
        // must not turn its black lower wall into a pale transparent blue tube.
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(390);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(520);
        // Flush each storm's body then its flow. A nearer black body can occlude
        // the distant glow; drawing all glows last would turn the sea into a wire cage.
        for(int i=ordered.size()-1;i>=0;i--){
            var g=ordered.get(i);double distance=Math.hypot(g.x()-camera.x,g.z()-camera.z);
            double extent=g.radius()*2.55+6;
            if(!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(g.x()-extent,49,g.z()-extent,g.x()+extent,169,g.z()+extent)))continue;
            drawn++;
            var matrix=new Matrix4f().translation((float)(g.x()-camera.x),(float)(49.05-camera.y),(float)(g.z()-camera.z));
            var detail=SeaTornadoGeometry.detail(i,distance);
            double opacity=opacity(g,distance,mc.level.getGameTime()+event.getPartialTick());
            SeaTornadoGeometry.build(g,seconds,detail,opacity,sink(BUFFER.getBuffer(SeaFlowRenderType.FLOW),matrix));
            BUFFER.endBatch(SeaFlowRenderType.FLOW);
            SeaStormEnergyGeometry.build(g,seconds,detail,opacity,sink(BUFFER.getBuffer(SeaFlowRenderType.ENERGY),matrix));
            BUFFER.endBatch(SeaFlowRenderType.ENERGY);
            quads+=detail.quads()+SeaStormEnergyGeometry.quads(detail);
        }
        var matrix=new Matrix4f().translation((float)-camera.x,(float)(49.05-camera.y),(float)-camera.z);
        var flow=sink(BUFFER.getBuffer(SeaFlowRenderType.ENERGY),matrix);
        for(int i=0;i<ordered.size();i++)for(int j=i+1;j<ordered.size();j++){
            if(!SeaStormEnergyGeometry.linked(ordered,i,j))continue;
            var a=ordered.get(i);var b=ordered.get(j);
            if(!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(Math.min(a.x(),b.x())-30,49,Math.min(a.z(),b.z())-30,
                    Math.max(a.x(),b.x())+30,110,Math.max(a.z(),b.z())+30)))continue;
            double alpha=Math.min(opacity(a,Math.hypot(a.x()-camera.x,a.z()-camera.z),mc.level.getGameTime()+event.getPartialTick()),
                    opacity(b,Math.hypot(b.x()-camera.x,b.z()-camera.z),mc.level.getGameTime()+event.getPartialTick()));
            SeaStormEnergyGeometry.buildLink(a,b,seconds,alpha,flow);
            links++;quads+=SeaStormEnergyGeometry.linkQuads();
        }
        BUFFER.endBatch(SeaFlowRenderType.ENERGY);
        }finally{
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(previousFogStart);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(previousFogEnd);
        }
        if(AUDIT){
            lastStorms=ordered.size();lastDrawn=drawn;lastQuads=quads;lastLinks=links;
            double millis=(System.nanoTime()-began)/1_000_000.;samples++;totalMillis+=millis;maxMillis=Math.max(maxMillis,millis);
        }
    }
    private static double opacity(SeaGyreLayout.Gyre g,double distance,double time){
        return SeaTornadoGeometry.distanceFade(distance)*VISIBILITY.opacity(g,time);
    }
    private static SeaTornadoGeometry.Sink sink(VertexConsumer out,Matrix4f matrix){
        return (x,y,z,u,v,rgb,alpha,nx,ny,nz)->{
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            out.vertex(matrix,(float)x,(float)y,(float)z).color(rgb>>16&255,rgb>>8&255,rgb&255,alpha)
                    .uv(u,v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal((float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
        };
    }
    private SkyhavenSeaVision(){}
}
