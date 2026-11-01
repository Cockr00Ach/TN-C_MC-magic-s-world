package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Draw cosmetic models outside vanilla entity/chunk tracking, sharing the existing GPU mesh. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkDragonDistantView {
    private static final Map<UUID,InkCelestialDragonEntity> VIEWS=new HashMap<>();
    private record Motion(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,float fromYaw,float toYaw,long tick){}
    private static final Map<UUID,Motion> MOTION=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long received;
    private static int drawn,liveDrawn;
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource BUFFERS=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(65536));
    public static int cachedViews(){return VIEWS.size();}
    public static int drawnViews(){return drawn;}
    public static int drawnLive(){return liveDrawn;}
    public static net.minecraft.world.phys.Vec3 remotePosition(UUID id){var e=VIEWS.get(id);return e==null?null:e.position();}
    public static boolean hasRemote(UUID id){return VIEWS.containsKey(id);}
    @SubscribeEvent public static void debug(CustomizeGuiOverlayEvent.DebugText event){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.options.renderDebug)return;
        int tracked=0;for(var e:mc.level.entitiesForRendering())if(e instanceof InkCelestialDragonEntity)tracked++;
        event.getLeft().add("Ink dragon: tracked="+tracked+", drawn="+liveDrawn+", distant="+drawn+", cached="+VIEWS.size());
    }
    public static void accept(InkDragonVisibilityNetwork.SnapshotPacket packet){
        var level=Minecraft.getInstance().level;
        if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        if(world!=level){VIEWS.clear();MOTION.clear();world=level;}
        var keep=new HashSet<UUID>();
        for(var s:packet.dragons()){
            keep.add(s.id());boolean fresh=!VIEWS.containsKey(s.id());var e=VIEWS.computeIfAbsent(s.id(),id->InkBossRegistration.INK_CELESTIAL_DRAGON.create(level));
            MOTION.put(s.id(),new Motion(fresh?s.position():e.position(),s.position(),fresh?s.yaw():e.getYRot(),s.yaw(),level.getGameTime()));
            if(fresh)e.moveTo(s.position().x,s.position().y,s.position().z,s.yaw(),0);
            e.applyVisualSnapshot(s.state());
            e.yRotO=s.yaw();e.yBodyRot=s.yaw();e.yHeadRotO=e.yHeadRot;
        }
        VIEWS.keySet().retainAll(keep);MOTION.keySet().retainAll(keep);received=System.nanoTime();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){VIEWS.clear();MOTION.clear();world=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||mc.level==null)return;
        drawn=0;liveDrawn=0;
        if(world!=mc.level||System.nanoTime()-received>5_000_000_000L){VIEWS.clear();MOTION.clear();world=null;}
        var actual=new HashMap<UUID,InkCelestialDragonEntity>();
        for(var e:mc.level.entitiesForRendering())if(e instanceof InkCelestialDragonEntity dragon)actual.put(e.getUUID(),dragon);
        var camera=event.getCamera().getPosition();var buffers=BUFFERS;
        // The normal entity pass owns tracked dragons. Never replace global projection under Oculus.
        liveDrawn=actual.size();
        for(var entry:VIEWS.entrySet()){
            if(actual.containsKey(entry.getKey()))continue;
            var e=entry.getValue();
            var motion=MOTION.get(entry.getKey());float t=net.minecraft.util.Mth.clamp((mc.level.getGameTime()+event.getPartialTick()-motion.tick())/5F,0,1);
            var at=motion.from().lerp(motion.to(),t);float yaw=net.minecraft.util.Mth.rotLerp(t,motion.fromYaw(),motion.toYaw());e.setPos(at);e.setYRot(yaw);
            if(InkDragonVisibility.distanceSquared(new InkDragonVisibility.Snapshot(entry.getKey(),at,yaw,e.visualSnapshot()),camera)>InkDragonVisibility.RANGE*InkDragonVisibility.RANGE)continue;
            e.tickCount=(int)mc.level.getGameTime();draw(e,at,yaw,event);drawn++;
        }
        buffers.endBatch();
    }
    private static void draw(InkCelestialDragonEntity e,net.minecraft.world.phys.Vec3 at,float yaw,RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();
        mc.getEntityRenderDispatcher().render(e,at.x-camera.x,at.y-camera.y,at.z-camera.z,yaw,event.getPartialTick(),event.getPoseStack(),BUFFERS,LightTexture.FULL_BRIGHT);
    }
}
