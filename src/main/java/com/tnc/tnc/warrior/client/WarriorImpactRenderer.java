package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorImpactRenderer {
    private record Impact(WarriorImpactPacket packet,long received) {}
    private record Recoil(long received,double strength,double duration,int seed) {}
    private static final List<Impact> ACTIVE=new ArrayList<>();
    private static final List<Recoil> RECOIL=new ArrayList<>();
    private static ClientLevel world;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(32768));
    private static void syncWorld(){
        var level=Minecraft.getInstance().level;
        if(world!=level){ACTIVE.clear();RECOIL.clear();world=level;}
    }
    public static void accept(WarriorImpactPacket p){
        syncWorld();var mc=Minecraft.getInstance();
        if(world==null||!world.dimension().location().equals(p.dimension()))return;
        long now=world.getGameTime();
        if(ACTIVE.size()>=64)ACTIVE.remove(0);
        ACTIVE.add(new Impact(p,now));
        // Observers see contact, but only the attacker receives camera recoil.
        if(mc.player!=null&&mc.player.getId()==p.attacker()&&p.targetSize()>0&&(p.kind()==0||p.kind()==3)){
            var recoil=new Recoil(now,WarriorHitFeedback.strength(p.targetSize(),p.kind()==3),WarriorHitFeedback.duration(p.targetSize()),p.seed());
            // A sweep hitting many enemies in one tick is one impact, using the largest target.
            for(int i=0;i<RECOIL.size();i++)if(RECOIL.get(i).received()==now){
                if(recoil.strength()>RECOIL.get(i).strength())RECOIL.set(i,recoil);
                return;
            }
            if(RECOIL.size()>=6)RECOIL.remove(0);RECOIL.add(recoil);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;syncWorld();
        if(world==null)return;
        ACTIVE.removeIf(p->world.getGameTime()-p.received()>WarriorImpactShape.duration(p.packet().kind()));
        RECOIL.removeIf(p->world.getGameTime()-p.received()>p.duration());
        var player=Minecraft.getInstance().player;if(player==null||!player.isAlive())RECOIL.clear();
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        syncWorld();var mc=Minecraft.getInstance();
        if(world==null||mc.isPaused()||mc.player==null||!mc.player.isAlive()||mc.getCameraEntity()!=mc.player)return;
        double pitch=0,yaw=0,roll=0,energy=0;
        for(var r:RECOIL){
            double age=world.getGameTime()-r.received()+e.getPartialTick();
            double v=r.strength()*WarriorHitFeedback.envelope(age,r.duration());
            double sign=(r.seed()&1)==0?1:-1;
            pitch-=Math.cos(age*2.2)*v;
            yaw+=Math.sin(age*2.8)*v*.38*sign;
            roll+=Math.sin(age*2.0)*v*.22*sign;
            energy+=v*v;
        }
        double limit=energy>2.2*2.2?2.2/Math.sqrt(energy):1;
        // Respect the player's existing Minecraft damage-tilt accessibility slider.
        double scale=limit*mc.options.damageTiltStrength().get();
        e.setPitch(e.getPitch()+(float)(Math.max(-2.2,Math.min(2.2,pitch))*scale));
        e.setYaw(e.getYaw()+(float)(Math.max(-.85,Math.min(.85,yaw))*scale));
        e.setRoll(e.getRoll()+(float)(Math.max(-.5,Math.min(.5,roll))*scale));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        syncWorld();if(world==null||ACTIVE.isEmpty())return;
        var camera=e.getCamera();var at=camera.getPosition();
        var matrix=new Matrix4f().translation((float)-at.x,(float)-at.y,(float)-at.z);
        var out=BUFFER.getBuffer(WarriorImpactRenderType.TYPE);
        var rx=new org.joml.Vector3f(1,0,0).rotate(camera.rotation());
        var uy=new org.joml.Vector3f(0,1,0).rotate(camera.rotation());
        var right=new Vec3(rx.x(),rx.y(),rx.z());var up=new Vec3(uy.x(),uy.y(),uy.z());
        for(var impact:ACTIVE){
            var p=impact.packet();if(p.at().distanceToSqr(at)>48*48)continue;
            float age=(float)Math.max(0,world.getGameTime()-impact.received()+e.getPartialTick());
            var n=p.normal().normalize();boolean contact=p.kind()==0||p.kind()==3;
            var r=p.kind()==2?new Vec3(1,0,0):right;var u=p.kind()==2?new Vec3(0,0,1):up;
            if(contact){r=(Math.abs(n.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).cross(n).normalize();u=n.cross(r).normalize();}
            for(var q:WarriorImpactShape.build(p.kind(),age/WarriorImpactShape.duration(p.kind()),p.color(),p.seed(),p.metal())){
                for(var point:new Vec3[]{q.a(),q.b(),q.c(),q.d()}){
                    var v=p.at().add(r.scale(point.x)).add(u.scale(point.y)).add(n.scale(point.z));
                    out.vertex(matrix,(float)v.x,(float)v.y,(float)v.z).color((q.color()>>16&255)/255f,(q.color()>>8&255)/255f,(q.color()&255)/255f,q.alpha()).endVertex();
                }
            }
        }
        BUFFER.endBatch(WarriorImpactRenderType.TYPE);
    }
}
