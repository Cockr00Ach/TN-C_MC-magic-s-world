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
import java.util.HashMap;

/** Sparse colossal sea tornadoes, visible in the established F8 magic sight only. */
@Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class SkyhavenSeaVision {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(2097152));
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> world=new java.lang.ref.WeakReference<>(null);
    private static List<SeaGyreLayout.Gyre> visible=List.of();
    private static final HashMap<SeaGyreLayout.Gyre,Long> appeared=new HashMap<>();
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().equals(SkyhavenContent.KEY)
                ||!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()){
            visible=List.of();appeared.clear();world.clear();return;
        }
        if(world.get()!=mc.level){visible=List.of();appeared.clear();world=new java.lang.ref.WeakReference<>(mc.level);}
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
        for(var g:visible)if(!eligible.contains(g)&&Math.hypot(g.x()-camera.x,g.z()-camera.z)<210
                &&!mc.level.hasChunkAt(BlockPos.containing(g.x(),48,g.z())))eligible.add(g);
        var storms=SeaTornadoGeometry.select(eligible,visible);
        appeared.keySet().retainAll(storms);
        for(var g:storms)appeared.putIfAbsent(g,mc.level.getGameTime());
        visible=storms;
        double seconds=(mc.level.getGameTime()+event.getPartialTick())/20.;
        float previousFogStart=com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
        float previousFogEnd=com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        try{
        // This magical volume has its own bounded distance fade; ordinary ocean fog
        // must not turn its black lower wall into a pale transparent blue tube.
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(260);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(450);
        // Far storms first; normal terrain depth still occludes the whole 3D volume.
        for(int i=storms.size()-1;i>=0;i--){
            int index=i;
            if(storms.size()==2&&Math.hypot(storms.get(0).x()-camera.x,storms.get(0).z()-camera.z)
                    >Math.hypot(storms.get(1).x()-camera.x,storms.get(1).z()-camera.z))index=1-i;
            var g=storms.get(index);double distance=Math.hypot(g.x()-camera.x,g.z()-camera.z);
            var matrix=new Matrix4f().translation((float)(g.x()-camera.x),(float)(49.05-camera.y),(float)(g.z()-camera.z));
            var out=BUFFER.getBuffer(SeaFlowRenderType.FLOW);
            var detail=distance<185?SeaTornadoGeometry.Detail.NEAR:SeaTornadoGeometry.Detail.FAR;
            double emergence=Math.min(1,(mc.level.getGameTime()+event.getPartialTick()-appeared.get(g))/40.);
            emergence=emergence*emergence*(3-2*emergence);
            SeaTornadoGeometry.build(g,seconds,detail,SeaTornadoGeometry.distanceFade(distance)*emergence,(x,y,z,u,v,rgb,alpha,nx,ny,nz)->{
                double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                out.vertex(matrix,(float)x,(float)y,(float)z).color(rgb>>16&255,rgb>>8&255,rgb&255,alpha)
                        .uv(u,v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal((float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
            });
        }
        BUFFER.endBatch(SeaFlowRenderType.FLOW);
        }finally{
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(previousFogStart);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(previousFogEnd);
        }
    }
    private SkyhavenSeaVision(){}
}
