package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared hit-frame choreography: five differently oriented cuts with fading spectral blades. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorUltimateEffects {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(65536));
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        var out=WarriorTrailRenderType.buffer(BUFFER);Vec3 camera=e.getCamera().getPosition();int rendered=0;
        for(var p:mc.level.players()){
            var a=WarriorAnimationClient.active(p.getId());
            if(a==null||a.animation()!=WarriorAnimation.ULTIMATE||p.distanceToSqr(camera)>48*48||rendered++>=12)continue;
            if(WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.LONGSWORD)continue;
            var id=ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());var art=WarriorUltimates.forId(id.toString());
            String element=com.tnc.tnc.warrior.WarriorDomainLink.activeElementId(p);
            var style=WarriorTrailAppearance.of(id.getPath(),WarriorWeapons.family(p.getMainHandItem()),element==null?"":element);
            double age=a.progress(e.getPartialTick())*a.durationTicks();
            for(var s:art.strokes()){
                double dt=age-s.tick();if(dt< -3||dt>13)continue;
                Vec3 origin=p.getPosition(e.getPartialTick()).add(0,1.1,0);
                Vec3 forward=WarriorUltimates.direction(p.yBodyRot,s),side=new Vec3(forward.z,0,-forward.x);
                float alpha=(float)(Math.min(1,(dt+3)/3)*Math.pow(Math.max(0,1-dt/13),2));
                for(int layer=2;layer>=0;layer--)for(int i=0;i<48;i++){
                    double t=i/48.0,u=(i+1)/48.0;
                    Vec3 x=point(s,origin,forward,side,t),y=point(s,origin,forward,side,u);
                    Vec3 normal=s.shape().equals("H")?side:new Vec3(0,1,0);
                    double width=layer==0?.018:layer==1?.08:.20;
                    float ax=alpha*(float)Math.sin(t*Math.PI)*(layer==0?.85f:layer==1?.35f:.07f);
                    float ay=alpha*(float)Math.sin(u*Math.PI)*(layer==0?.85f:layer==1?.35f:.07f);
                    int color=layer==0?style.core():style.shell();
                    quad(out,camera,x.add(normal.scale(width)),x.subtract(normal.scale(width)),y.subtract(normal.scale(width)),y.add(normal.scale(width)),color,ax,ax,ay,ay);
                }
                // Four pointed swords sit along the arc and dissolve; no particle sprites.
                for(int i=1;i<=4;i++){
                    Vec3 tip=point(s,origin,forward,side,i/5.0);Vec3 root=origin.lerp(tip,.55),mid=root.lerp(tip,.55);
                    quad(out,camera,root,mid.add(0,.10,0),tip,mid.add(0,-.10,0),style.core(),0,alpha*.18f,alpha*.06f,alpha*.18f);
                }
            }
        }
        BUFFER.endBatch(WarriorTrailRenderType.TYPE);
    }
    private static Vec3 point(WarriorUltimates.Stroke s,Vec3 o,Vec3 f,Vec3 side,double t){
        double r=s.radius();
        return switch(s.shape()){
            case "T","B" -> o.add(f.scale(t*r)).add(side.scale(Math.sin(t*Math.PI)*.13));
            case "H" -> o.add(f.scale(Math.sin(t*Math.PI)*r*.75)).add(0,Math.cos(t*Math.PI)*2,0);
            case "V" -> o.add(f.scale(Math.sin(t*Math.PI)*r*.75)).add(0,-Math.cos(t*Math.PI)*2,0);
            default -> {double angle=(t-.5)*(s.shape().equals("O")?Math.PI*2:Math.PI*1.35);if(s.shape().equals("R"))angle=-angle;yield o.add(f.scale(Math.cos(angle)*r)).add(side.scale(Math.sin(angle)*r)).add(0,Math.sin(t*Math.PI*2)*.25,0);}
        };
    }
    private static void quad(VertexConsumer v,Vec3 cam,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float aa,float ab,float ac,float ad){vertex(v,cam,a,color,aa);vertex(v,cam,b,color,ab);vertex(v,cam,c,color,ac);vertex(v,cam,d,color,ad);}
    private static void vertex(VertexConsumer v,Vec3 c,Vec3 p,int rgb,float alpha){v.vertex(p.x-c.x,p.y-c.y,p.z-c.z).color((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha).endVertex();}
}
