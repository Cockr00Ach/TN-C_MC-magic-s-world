package com.tnc.tnc.sanctum.client;

import com.tnc.tnc.sanctum.SanctumAppearance.State;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** The carrier can be out of the vanilla frustum while its sixty-block head fills the display. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SanctumFigures {
    private record Tracked(State state,long received){}
    private static final Map<Integer,Tracked> TRACKED=new HashMap<>();
    private static ClientLevel world;
    private static void session(){
        var current=Minecraft.getInstance().level;
        if(world!=current){TRACKED.clear();SanctumProjectedBlade.clear();SanctumCinematic.stop();world=current;}
    }
    public static void accept(State packet){
        session();if(world==null||!world.dimension().location().equals(packet.dimension()))return;
        TRACKED.put(packet.id(),new Tracked(packet,world.getGameTime()));
        if(!packet.model().equals("angel_seal"))SanctumMesh.request(overview(meshKey(packet.model())));
        if(packet.action().equals("awaken")||packet.action().equals("rupture"))SanctumMesh.request(meshKey(packet.model()));
        if(packet.model().equals("angel"))SanctumMesh.request("bleeding");
        if(packet.model().equals("bleeding"))SanctumMesh.request("demon");
        if(packet.action().equals("awaken")||packet.action().equals("rupture"))SanctumCinematic.start(packet);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;session();
        if(world!=null&&!TRACKED.isEmpty()){
            TRACKED.values().removeIf(tracked->{
                var state=tracked.state();var entity=world.getEntity(state.id());
                if(entity!=null&&entity.getUUID().equals(state.uuid()))entity.noCulling=true;
                return entity==null?world.getGameTime()-tracked.received()>40:!entity.getUUID().equals(state.uuid());
            });
        }
        if(world!=null&&!Minecraft.getInstance().isPaused()){
            for(var entry:TRACKED.values())wingFire(entry.state());
        }
        SanctumCinematic.tick();
    }
    private static final net.minecraft.core.particles.DustParticleOptions DARK_FLAME=
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.28F,.025F,.18F),2.4F);
    private static void wingFire(State state){
        if(!state.model().equals("demon")||!state.action().equals("funeral"))return;
        long age=world.getGameTime()-state.started();
        if(age<0||age>state.duration()*.85)return;
        var boss=world.getEntity(state.id());if(boss==null)return;
        var rng=world.random;float yaw=(float)Math.toRadians(-boss.getYRot());
        for(int side=-1;side<=1;side+=2)for(int k=0;k<5;k++){
            double span=9+rng.nextDouble()*41;
            Vec3 tip=new Vec3(side*span,42+span*.32+rng.nextDouble()*2,-4-rng.nextDouble()*3).yRot(yaw).add(boss.position());
            world.addParticle(DARK_FLAME,true,tip.x,tip.y,tip.z,0,.07,0);
            if((age+k)%3==0)world.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,true,
                    tip.x,tip.y+.4,tip.z,0,.12,0);
        }
    }
    @SubscribeEvent public static void leave(ClientPlayerNetworkEvent.LoggingOut event){
        TRACKED.clear();SanctumProjectedBlade.clear();SanctumCinematic.stop();world=null;SanctumMesh.clear();
    }
    private static final MultiBufferSource.BufferSource SHOT_BUFFERS=MultiBufferSource.immediate(new BufferBuilder(262144));
    private static boolean upperBodyPass(State state,LivingEntity entity){
        if(SanctumCinematic.focuses(state.uuid()))return true;
        int height=switch(state.model()){
            case "gate" -> 48;
            case "gate_left","gate_right" -> 20;
            case "angel","bleeding","bleeding_feral" -> 64;
            case "demon" -> state.action().equals("funeral")?72:32;
            default -> 0;
        };
        if(height==0)return false;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        var feet=entity.position().subtract(camera.getPosition());
        if(feet.lengthSqr()>120*120)return false;
        var direction=camera.getLookVector();
        var forward=new Vec3(direction.x(),direction.y(),direction.z());
        return feet.normalize().dot(forward)<.35&&feet.add(0,height*.76,0).normalize().dot(forward)>.55;
    }
    @SubscribeEvent public static void closeup(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||world==null)return;
        // Chunk/occlusion mods may skip the two-block carrier when the shot looks straight at its high face.
        for(var tracked:TRACKED.values()){
            var s=tracked.state();
            if(!(world.getEntity(s.id()) instanceof LivingEntity entity)||!upperBodyPass(s,entity))continue;
            float partial=event.getPartialTick();
            var at=entity.getPosition(partial).subtract(event.getCamera().getPosition());
            var origin=new Matrix4f(event.getPoseStack().last().pose()).translate((float)at.x,(float)at.y,(float)at.z);
            int light=Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(entity,partial);
            renderState(s,entity,partial,origin,SHOT_BUFFERS,light);
            SHOT_BUFFERS.endBatch();
        }
    }
    @SubscribeEvent public static void drawCarrier(RenderLivingEvent.Pre<?,?> event){
        var e=event.getEntity();var tracked=TRACKED.get(e.getId());
        if(world==null||tracked==null||!e.getUUID().equals(tracked.state().uuid()))return;
        event.setCanceled(true);
        if(upperBodyPass(tracked.state(),e))return;
        renderState(tracked.state(),e,event.getPartialTick(),new Matrix4f(event.getPoseStack().last().pose()),
                event.getMultiBufferSource(),event.getPackedLight());
    }
    private static void renderState(State s,LivingEntity e,float partial,Matrix4f base,MultiBufferSource buffers,int light){
        float yaw=SanctumCinematic.yaw(e.getUUID(),Mth.rotLerp(partial,e.yRotO,e.getYRot()));
        base.rotateY((float)Math.toRadians(-yaw));
        if(s.model().equals("angel_seal")){
            SanctumAngelSeal.draw(buffers,base,e.tickCount+partial);
            return;
        }
        var mc=Minecraft.getInstance();
        if(s.model().equals("wound")&&mc.options.getCameraType().isFirstPerson()&&mc.getCameraEntity()==mc.player
                &&e.getPosition(partial).distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())<10*10){
            SanctumNearClaw.wound(buffers,base);return;
        }
        float elapsed=world.getGameTime()-s.started()+partial;
        String action=elapsed>=0&&elapsed<s.duration()?s.action():"";
        float t=action.isEmpty()?0:elapsed/s.duration();
        if(s.model().startsWith("bleeding") && SanctumGazeView.extending(e,partial))action="gaze_trunk";
        float age=e.tickCount+partial,speed=Math.min(1,e.walkAnimation.speed(partial)*1.8F);
        if(s.model().equals("shade"))age=SanctumSentinelPose.stride(e,partial);
        if(s.model().equals("gate_left")||s.model().equals("gate_right")||s.model().equals("gargoyle"))speed=SanctumSentinelPose.injury(e,partial);
        var lock=action.equals("combo")?SanctumProjectedBlade.target(e.getUUID(),e.getPosition(partial),yaw,s.started()):null;
        var reach=s.model().equals("demon")?SanctumDemonReach.lock(e.getUUID(),action,t,s.started(),e.getPosition(partial),yaw,base):null;
        draw(s.model(),action,t,age,speed,base,buffers,light,lock,reach);
    }
    private static String meshKey(String form){
        return switch(form){
            case "wrist","wrist_right","gate_left","gate_right" -> "gate";case "wound" -> "demon";case "bleeding_feral" -> "bleeding";default -> form;};
    }
    private static String overview(String form){
        return switch(form){case "gate","angel","bleeding","demon" -> form+"_view";default -> form;};
    }
    private static SanctumMesh mesh(String form,boolean detail){
        String key=meshKey(form);
        if(detail){var sharp=SanctumMesh.get(key);if(sharp!=null)return sharp;}
        return SanctumMesh.get(overview(key));
    }
    private static void draw(String form,String action,float t,float age,float speed,Matrix4f base,MultiBufferSource buffers,int light,SanctumProjectedBlade.Strike lock,SanctumDemonReach reach){
        if(form.startsWith("wrist")||form.equals("wound")){
            boolean claw=form.equals("wound"),right=form.equals("wrist_right");
            var body=mesh(form,false);
            if(body!=null)body.draw(claw?new Matrix4f(base).scale(.14F,.28F,.12F).translate(12.1F,-28.3F,-12.7F)
                        :new Matrix4f(base).scale(.47F).translate(right?-9.25F:10.1F,right?-13.67F:-10.85F,right?-3.68F:-1.1F),buffers,
                    new SanctumPose(claw?"demon":"gate","",0,0,0,true),claw?"hand_-1":right?"gate_heavy_hand_R":"gate_heavy_hand_L",light);
            return;
        }
        if(form.equals("gate_left")||form.equals("gate_right"))base.scale(.42F);
        boolean awaken=action.equals("awaken"),rupture=action.equals("rupture");
        if(awaken&&t*AWAKEN<EYES_START)form="angel";
        if(rupture&&t*RUPTURE<BLACK_END){
            if(t*RUPTURE>=SHELL_SPLIT)return;
            SanctumRupture.draw(mesh("bleeding", true), mesh("demon", true),
                    buffers, base, t, age, light);
            return;
        }
        var mesh=mesh(form,awaken||rupture);
        if(mesh==null)return;
        var pose=new SanctumPose(form,action,t,age,speed,false);
        if(form.equals("demon")){
            SanctumDemonMotion.frame(pose,base);
        }
        mesh.draw(base,buffers,pose,null,light,lock,reach);
        if(form.equals("demon")&&action.equals("funeral")){
            float spread=SanctumPose.ease(t,.05F,.30F)*(1-SanctumPose.ease(t,.71F,.90F));
            if(spread>.01F){
                for(int pair=1;pair<=2;pair++)for(int side:new int[]{-1,1}){
                    float scale=(1-pair*.13F)*spread;
                    var extra=new Matrix4f(base).translate(0,43-pair*1.8F,-pair*2F)
                            .rotateZ((float)Math.toRadians(side*pair*21*spread))
                            .scale(scale).translate(0,-43,0);
                    mesh.draw(extra,buffers,pose,"wing_"+side,light);
                }
            }
        }
    }
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void listeners(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->SanctumMesh.clear());
        }
    }
}
