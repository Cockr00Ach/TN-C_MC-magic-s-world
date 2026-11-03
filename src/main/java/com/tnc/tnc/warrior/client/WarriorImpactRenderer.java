package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorImpactPacket;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorImpactRenderer {
    private static final List<WarriorImpactPacket> ACTIVE=new ArrayList<>();
    private static ClientLevel world;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(32768));
    public static void accept(WarriorImpactPacket p){var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(p.dimension()))return;
        if(world!=level){ACTIVE.clear();world=level;}if(ACTIVE.size()>=128)ACTIVE.remove(0);ACTIVE.add(p);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var level=Minecraft.getInstance().level;if(level!=world){ACTIVE.clear();world=level;}if(level==null||ACTIVE.isEmpty())return;
        ACTIVE.removeIf(p->level.getGameTime()-p.started()>12);
        var camera=e.getCamera();var at=camera.getPosition();var matrix=new Matrix4f().translation((float)-at.x,(float)-at.y,(float)-at.z);
        var out=BUFFER.getBuffer(RenderType.lightning());
        var rx=new org.joml.Vector3f(1,0,0).rotate(camera.rotation());
        var uy=new org.joml.Vector3f(0,1,0).rotate(camera.rotation());
        var right=new Vec3(rx.x(),rx.y(),rx.z());var up=new Vec3(uy.x(),uy.y(),uy.z());
        for(var p:ACTIVE){
            float age=(float)Math.max(0,level.getGameTime()-p.started()+e.getPartialTick());
            var r=p.kind()==2?new Vec3(1,0,0):right;var u=p.kind()==2?new Vec3(0,0,1):up;
            for(var q:com.tnc.tnc.warrior.WarriorImpactShape.build(p.kind(),age/com.tnc.tnc.warrior.WarriorImpactShape.duration(p.kind()),p.color())){
                quad(out,matrix,world(p.at(),r,u,q.a()),world(p.at(),r,u,q.b()),world(p.at(),r,u,q.c()),world(p.at(),r,u,q.d()),q.color(),q.alpha());
            }
        }BUFFER.endBatch(RenderType.lightning());
    }
    private static Vec3 world(Vec3 at,Vec3 r,Vec3 u,Vec3 point){return at.add(r.scale(point.x)).add(u.scale(point.y));}
    private static void quad(VertexConsumer o,Matrix4f m,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha){for(var p:new Vec3[]{a,b,c,d})o.vertex(m,(float)p.x,(float)p.y,(float)p.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();}
}
