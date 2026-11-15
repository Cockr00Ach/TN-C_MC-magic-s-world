package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;

/** Server-confirmed paths and impacts. Short impact beats, long readable blade scars. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorSwordFxRenderer {
    private record Visible(WarriorSwordFx packet,long at,int core,int shell,Vec3[] scar){}
    private static final java.util.List<Visible> FX=new java.util.ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static PlayerModel<AbstractClientPlayer> phantom;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediateWithBuffers(java.util.Map.of(WarriorTrailRenderType.PLUME,new BufferBuilder(131072),WarriorTrailRenderType.TYPE,new BufferBuilder(65536),WarriorUltimateRadianceRenderType.TYPE,new BufferBuilder(65536)),new BufferBuilder(16384));
    private static int life(WarriorSwordFx p){return p.kind()==0?80:p.kind()==5?48:p.kind()==3&&p.from().distanceToSqr(p.to())>.01?22:24;}
    public static void accept(WarriorSwordFx packet){
        var level=Minecraft.getInstance().level;
        if(world!=level){FX.clear();world=level;}if(level==null)return;
        int core=0xb5dcff,shell=0x568fe9;
        if(level.getEntity(packet.player()) instanceof net.minecraft.world.entity.LivingEntity p){
            var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
            String element=p.getMainHandItem().hasTag()?p.getMainHandItem().getTag().getString(WindInfusion.ELEMENT_KEY):"";
            String domain=p instanceof net.minecraft.world.entity.player.Player player?WarriorDomainLink.activeElementId(player):null;if(domain!=null)element=domain;
            var style=WarriorTrailAppearance.of(key==null?"":key.getPath(),WarriorWeapons.family(p.getMainHandItem()),element);core=style.core();shell=style.shell();
        }
        FX.removeIf(v->level.getGameTime()-v.at()>life(v.packet()));
        var next=new Visible(packet,level.getGameTime(),core,shell,packet.kind()==0?groundScar(packet):null);
        if(FX.size()>=512){int evict=0;for(int i=1;i<FX.size();i++)if(priority(FX.get(i))<priority(FX.get(evict)))evict=i;if(priority(next)<priority(FX.get(evict)))return;FX.remove(evict);}FX.add(next);
        if(packet.kind()==1||packet.kind()==2)WarriorAnimationClient.impact(packet.player());
    }
    private static int priority(Visible v){
        var local=Minecraft.getInstance().player;int self=local!=null&&v.packet.player()==local.getId()?2:0;
        int kind=v.packet.kind();return self+(kind==3&&v.packet.from().distanceToSqr(v.packet.to())>.01?0:kind==4?1:kind==0?3:5);
    }
    private static double impactStrength(){
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null||mc.isPaused())return 0;
        double sum=0;
        for(var v:FX){int kind=v.packet.kind();if(kind!=1&&kind!=2)continue;
            double age=world.getGameTime()-v.at+mc.getFrameTime();if(age<0||age>12)continue;
            double near=Math.max(0,1-mc.player.position().distanceTo(v.packet.from())/22);
            sum+=near*Math.exp(-age/3.5)*(kind==2?1:.7);
        }return Math.min(1,sum);
    }
    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles e){
        double strength=impactStrength();if(strength<=0)return;double time=world.getGameTime()+Minecraft.getInstance().getFrameTime();
        e.setPitch(e.getPitch()+(float)(Math.sin(time*2.9)*strength*1.45));e.setYaw(e.getYaw()+(float)(Math.sin(time*2.1)*strength*.45));e.setRoll(e.getRoll()+(float)(Math.sin(time*3.7)*strength*.55));
    }
    @SubscribeEvent public static void lens(ViewportEvent.ComputeFov e){double hit=impactStrength();if(hit>0)e.setFOV(e.getFOV()+hit*3);}
    private static boolean hidden(net.minecraft.world.entity.player.Player p,float partial){
        if(!WarriorPolearmUltimates.kind(p.getMainHandItem()).isEmpty())return false;
        var a=WarriorAnimationClient.active(p.getId());return a!=null&&a.animation()==WarriorAnimation.ULTIMATE&&WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.LONGSWORD&&a.progress(partial)*96>=26&&a.progress(partial)*96<70&&FX.stream().anyMatch(v->v.packet.player()==p.getId()&&v.packet.kind()==0&&world!=null&&world.getGameTime()-v.at<16&&v.packet.from().distanceToSqr(v.packet.to())>.04);
    }
    @SubscribeEvent public static void hand(RenderHandEvent e){var p=Minecraft.getInstance().player;if(p!=null&&hidden(p,e.getPartialTick()))e.setCanceled(true);}
    @SubscribeEvent public static void ghost(RenderPlayerEvent.Pre e){if(hidden(e.getEntity(),e.getPartialTick()))e.setCanceled(true);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();
        if(mc.level!=world){FX.clear();world=mc.level;}if(world==null)return;
        long now=world.getGameTime();FX.removeIf(v->now-v.at()>life(v.packet()));
        Vec3 camera=e.getCamera().getPosition();var light=WarriorUltimateRadianceRenderType.buffer(BUFFER);var out=WarriorTrailRenderType.plume(BUFFER,.30);int budget=0,ghosts=0;
        for(int n=FX.size()-1;n>=0;n--){var v=FX.get(n);var p=v.packet;double age=now-v.at+e.getPartialTick();
            if(p.from().distanceToSqr(camera)>64*64||budget++>=256)continue;
            switch(p.kind()){
                case 0 -> {path(out,camera,v,age,v.core,v.shell);if(age<14&&ghosts++<18)phantom(e.getPoseStack(),camera,p,age,v.core);}
                case 1 -> lightning(out,camera,p.from(),age,v.core,v.shell);
                case 2 -> impact(out,camera,p.from(),age,v.core,v.shell);
                case 3 -> {if(p.from().distanceToSqr(p.to())>.01)arrow(out,camera,p,age,v.core,v.shell);else sweep(out,camera,p.from(),age,v.core,v.shell);}
                case 4 -> gather(out,camera,p.from(),age,v.core,v.shell);
                case 5 -> blades(out,camera,p.from(),age,v.core,v.shell);
                default -> {}
            }
            radiance(light,camera,p,age,v.core,v.shell);
        }
        BUFFER.endBatch(WarriorTrailRenderType.PLUME);BUFFER.endBatch(WarriorTrailRenderType.TYPE);BUFFER.endBatch(WarriorUltimateRadianceRenderType.TYPE);
    }
    private static Vec3[] groundScar(WarriorSwordFx packet){
        Vec3[] nodes=new Vec3[33];for(int i=0;i<nodes.length;i++)nodes[i]=ground(packet.from().lerp(packet.to(),i/32.0));return nodes;
    }
    private static void radiance(VertexConsumer out,Vec3 cam,WarriorSwordFx p,double age,int core,int shell){
        int white=blend(core,0xffffff,.36f);Vec3 at=p.from();
        if(p.kind()==0){
            if(age>=16)return;float fade=(float)Math.pow(1-age/16,1.6);Vec3 d=p.to().subtract(at).normalize(),side=new Vec3(-d.z,0,d.x).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);Vec3 up=d.cross(side).normalize();
            Vec3 a=at.add(0,.95,0),b=p.to().add(0,.95,0);
            ribbon(out,cam,a,b,side,.28,shell,fade*.12f);ribbon(out,cam,a,b,up,.20,shell,fade*.10f);
            ribbon(out,cam,a,b,side,.025,white,fade*.82f);ribbon(out,cam,a,b,up,.02,core,fade*.55f);
            if(age<7){halo(out,cam,b,1.0+age*.10,core,fade*.24f);ring(out,cam,a,side,up,.25+age*.12,.065,core,fade*.5f,24);star(out,cam,b,.45+age*.11,white,fade*.65f);}
        }else if(p.kind()==1){
            if(age>=22)return;float fade=(float)Math.pow(1-age/22,1.7);
            for(int i=0;i<2;i++){Vec3 side=i==0?new Vec3(1,0,0):new Vec3(0,0,1);ribbon(out,cam,at,at.add(0,12,0),side,.055,white,fade*.95f);ribbon(out,cam,at,at.add(0,12,0),side,1.35,shell,fade*.10f);}
            ring(out,cam,at.add(0,.08,0),new Vec3(1,0,0),new Vec3(0,0,1),.8+age*.25,.08,core,fade*.75f,48);
            if(age<9){halo(out,cam,at.add(0,1.2,0),2.0+age*.12,core,(float)(1-age/9)*.35f);star(out,cam,at.add(0,1.2,0),1+age*.3,white,(float)(1-age/9)*.75f);}
        }else if(p.kind()==2){
            if(age>=20)return;float fade=(float)(1-age/20);double r=.4+age*.65;
            ring(out,cam,at.add(0,.16,0),new Vec3(1,0,0),new Vec3(0,0,1),r,.09,core,fade*.65f,48);
            if(age>3)ring(out,cam,at.add(0,.24,0),new Vec3(1,0,0),new Vec3(0,0,1),(age-3)*.6,.055,shell,fade*.45f,48);
            if(age<6){halo(out,cam,at.add(0,.5,0),2.4+age*.15,shell,(float)(1-age/6)*.28f);star(out,cam,at.add(0,.5,0),1+age*.4,white,(float)(1-age/6)*.8f);}
        }else if(p.kind()==3&&p.from().distanceToSqr(p.to())>.01){
            if(age>=14)return;Vec3 d=p.to().subtract(at).normalize(),side=d.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);Vec3 up=d.cross(side).normalize();float fade=(float)(1-age/14);
            ribbon(out,cam,at,p.to(),side,.025,white,fade*.65f);ribbon(out,cam,at,p.to(),up,.02,core,fade*.5f);ribbon(out,cam,at,p.to(),up,.22,shell,fade*.09f);
        }else if(p.kind()==3&&age<12){
            ring(out,cam,at.add(0,1.15,0),new Vec3(1,0,0),new Vec3(0,0,1),7+age*.22,.045,white,(float)Math.pow(1-age/12,2)*.7f,48);
        }else if(p.kind()==4&&age<18){
            double r=(18-age)*.18;float fade=(float)Math.sin(age/18*Math.PI);for(int i=0;i<6;i++){double angle=i*Math.PI/3+age*.2;star(out,cam,at.add(Math.cos(angle)*r,.2+age*.04,Math.sin(angle)*r),.10,core,fade*.45f);}
        }else if(p.kind()==5&&age<40){
            double h=7*Math.min(1,age/4),fade=Math.min(1,(40-age)/10);
            for(int i=0;i<10;i++){double angle=i*Math.PI/5;Vec3 tip=at.add(Math.cos(angle)*6,h+1.4,Math.sin(angle)*6);star(out,cam,tip,.13,core,(float)fade*.6f);}
        }
    }
    private static void halo(VertexConsumer out,Vec3 cam,Vec3 at,double radius,int color,float alpha){
        Vec3 normal=cam.subtract(at).normalize(),side=new Vec3(-normal.z,0,normal.x).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);Vec3 up=normal.cross(side).normalize();
        for(int i=0;i<16;i++){double a=i*Math.PI/8,b=(i+1)*Math.PI/8;Vec3 x=at.add(side.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius)),y=at.add(side.scale(Math.cos(b)*radius)).add(up.scale(Math.sin(b)*radius));
            lightVertex(out,cam,at,color,alpha);lightVertex(out,cam,x,color,0);lightVertex(out,cam,y,color,0);lightVertex(out,cam,at,color,alpha);
        }
    }
    private static void lightVertex(VertexConsumer out,Vec3 cam,Vec3 p,int color,float alpha){out.vertex(p.x-cam.x,p.y-cam.y,p.z-cam.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();}
    private static int blend(int a,int b,float t){int r=(int)((a>>16&255)*(1-t)+(b>>16&255)*t),g=(int)((a>>8&255)*(1-t)+(b>>8&255)*t),c=(int)((a&255)*(1-t)+(b&255)*t);return r<<16|g<<8|c;}
    private static void path(VertexConsumer out,Vec3 cam,Visible visible,double age,int core,int shell){
        var p=visible.packet;
        Vec3 d=p.to().subtract(p.from()).normalize(),side=new Vec3(-d.z,0,d.x).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
        float fade=(float)Math.pow(Math.max(0,1-age/80),1.4);
        for(int i=0;i<32;i++){
            Vec3 a=visible.scar[i],b=visible.scar[i+1];
            if(a==null||b==null)continue;
            ribbon(out,cam,a,b,side,.23,shell,fade*.24f);ribbon(out,cam,a.add(0,.012,0),b.add(0,.012,0),side,.035,core,fade*.9f);
        }
        if(age<9){float a=(float)(1-age/9);for(int i=0;i<3;i++){double h=.55+i*.45;ribbon(out,cam,p.from().add(0,h,0),p.to().add(0,h,0),side,.09,shell,a*.28f);}}
        if(age<6)ring(out,cam,p.to().add(0,.8,0),Vec3.ZERO.add(1,0,0),new Vec3(0,1,0),.3+age*.14,.06,core,(float)(1-age/6)*.5f,32);
    }
    private static void phantom(PoseStack pose,Vec3 cam,WarriorSwordFx p,double age,int color){
        pose=new PoseStack();var mc=Minecraft.getInstance();if(phantom==null)phantom=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        Vec3 d=p.to().subtract(p.from()).normalize(),at=p.from().lerp(p.to(),.58);float alpha=(float)Math.pow(1-age/14,1.5)*.38f;
        phantom.setAllVisible(false);phantom.head.visible=phantom.body.visible=phantom.rightArm.visible=phantom.leftArm.visible=phantom.rightLeg.visible=phantom.leftLeg.visible=true;
        phantom.body.xRot=.26f;phantom.head.xRot=-.16f;phantom.head.yRot=-.12f;
        phantom.rightArm.xRot=-1.65f;phantom.rightArm.yRot=-.12f;phantom.rightArm.zRot=.04f;
        phantom.leftArm.xRot=-1.15f;phantom.leftArm.yRot=.55f;phantom.leftArm.zRot=-.18f;
        phantom.rightLeg.xRot=-.75f;phantom.leftLeg.xRot=.65f;
        pose.pushPose();pose.translate(at.x-cam.x,at.y-cam.y,at.z-cam.z);pose.mulPose(Axis.YP.rotationDegrees(180-(float)Math.toDegrees(Math.atan2(-d.x,d.z))));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        phantom.renderToBuffer(pose,BUFFER.getBuffer(WarriorTrailRenderType.TYPE),15728880,OverlayTexture.NO_OVERLAY,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);phantom.renderToBuffer(pose,BUFFER.getBuffer(WarriorUltimateRadianceRenderType.TYPE),15728880,OverlayTexture.NO_OVERLAY,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha*.24f);pose.popPose();
        Vec3 side=new Vec3(-d.z,0,d.x),root=at.add(d.scale(.8)).add(0,1.25,0),tip=root.add(d.scale(2.1));
        var out=WarriorTrailRenderType.plume(BUFFER,.30);ribbon(out,cam,root,tip,side,.055,color,alpha*2);
    }
    private static void lightning(VertexConsumer out,Vec3 cam,Vec3 at,double age,int core,int shell){
        if(age>=20)return;float fade=(float)Math.pow(Math.max(0,1-age/20),1.5);double top=12;
        for(int i=2;i>=0;i--){double w=i==0?.17:i==1?.48:.95;int tint=i==0?blend(core,0xffffff,.35f):shell;float a=fade*(i==0?.95f:i==1?.38f:.12f);
            ribbon(out,cam,at,at.add(0,top,0),new Vec3(1,0,0),w,tint,a);ribbon(out,cam,at,at.add(0,top,0),new Vec3(0,0,1),w,tint,a);
        }
        ring(out,cam,at.add(0,.06,0),new Vec3(1,0,0),new Vec3(0,0,1),.4+age*.22,.15,shell,fade*.65f,48);
        if(age<8)star(out,cam,at.add(0,1,0),.6+age*.23,core,fade*.7f);
    }
    private static void impact(VertexConsumer out,Vec3 cam,Vec3 at,double age,int core,int shell){
        if(age>=24)return;float fade=(float)Math.pow(1-age/24,1.5);double radius=.3+Math.min(18,age)*.62;
        for(int layer=0;layer<3;layer++)ring(out,cam,at.add(0,.08+layer*.10,0),new Vec3(1,0,0),new Vec3(0,0,1),Math.max(.1,radius-layer*.45),layer==0?.35:.13,layer==0?shell:core,fade*(layer==0?.48f:.27f),64);
        if(age<6)star(out,cam,at.add(0,.25,0),.8+age*.45,core,(float)(1-age/6)*.65f);
        for(int i=0;i<12;i++){double angle=i*Math.PI/6;Vec3 d=new Vec3(Math.cos(angle),0,Math.sin(angle));Vec3 a=at.add(d.scale(radius*.7)).add(0,.15+Math.sin(Math.min(1,age/18)*Math.PI)*1.5,0);ribbon(out,cam,a,a.add(d.scale(1.4)).add(0,-.3,0),new Vec3(-d.z,0,d.x),.05,shell,fade*.55f);}
    }
    private static void arrow(VertexConsumer out,Vec3 cam,WarriorSwordFx p,double age,int core,int shell){
        if(age>=22)return;Vec3 d=p.to().subtract(p.from()).normalize(),side=d.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
        Vec3 up=d.cross(side).normalize();float fade=(float)Math.pow(1-age/22,1.3);ribbon(out,cam,p.from(),p.to(),up,.10,shell,fade*.25f);ribbon(out,cam,p.from(),p.to(),up,.022,core,fade*.7f);ribbon(out,cam,p.from(),p.to(),side,.14,shell,fade*.4f);ribbon(out,cam,p.from(),p.to(),side,.032,core,fade*.85f);
        if(age<3)ring(out,cam,p.to(),side,d.cross(side).normalize(),.10+age*.10,.025,core,(float)(1-age/3)*.45f,16);
    }
    private static void sweep(VertexConsumer out,Vec3 cam,Vec3 at,double age,int core,int shell){
        if(age>=15)return;float fade=(float)Math.pow(1-age/15,2);double r=7+age*.20;
        ring(out,cam,at.add(0,1.1,0),new Vec3(1,0,0),new Vec3(0,0,1),r,.48,shell,fade*.3f,64);
        ring(out,cam,at.add(0,1.12,0),new Vec3(1,0,0),new Vec3(0,0,1),r+.12,.055,core,fade*.8f,64);
    }
    private static void gather(VertexConsumer out,Vec3 cam,Vec3 at,double age,int core,int shell){
        if(age>=18)return;double r=(18-age)*.18;float fade=(float)Math.sin(age/18*Math.PI);
        ring(out,cam,at.add(0,.08,0),new Vec3(1,0,0),new Vec3(0,0,1),r,.10,shell,fade*.55f,40);
        for(int i=0;i<6;i++){double t=i*Math.PI/3+age*.14;Vec3 a=at.add(Math.cos(t)*r,.12+age*.045,Math.sin(t)*r),b=at.add(Math.cos(t-.35)*(r+.45),.02,Math.sin(t-.35)*(r+.45));ribbon(out,cam,b,a,new Vec3(0,1,0),.05,core,fade*.75f);}
    }
    private static void blades(VertexConsumer out,Vec3 cam,Vec3 origin,double age,int core,int shell){
        if(age>=48)return;double rise=Math.min(1,age/4),fade=Math.min(1,(48-age)/10),h=7*rise;
        for(int i=0;i<10;i++){double angle=i*Math.PI/5;Vec3 at=origin.add(Math.cos(angle)*6,0,Math.sin(angle)*6);Vec3 side=new Vec3(Math.cos(angle),0,Math.sin(angle)),cross=new Vec3(-side.z,0,side.x);
            for(Vec3 axis:new Vec3[]{side,cross}){
                ribbon(out,cam,at,at.add(0,h,0),axis,.13,0x10091c,(float)fade*.98f);
                quad(out,cam,at.add(0,h+1.4,0),at.add(axis.scale(.55)).add(0,h-.3,0),at.add(0,h-1.2,0),at.subtract(axis.scale(.55)).add(0,h-.3,0),0x10091c,(float)fade*.98f);
                ribbon(out,cam,at.add(axis.scale(.55)).add(0,h-.3,0),at.add(0,h+1.4,0),cross,.035,shell,(float)fade*.65f);
            }
            if(age<12)ring(out,cam,at.add(0,.07,0),new Vec3(1,0,0),new Vec3(0,0,1),.2+age*.06,.06,core,(float)(1-age/12)*.55f,20);
        }
    }
    private static void star(VertexConsumer out,Vec3 cam,Vec3 at,double radius,int color,float alpha){
        for(int i=0;i<8;i++){double angle=i*Math.PI/4;Vec3 d=new Vec3(Math.cos(angle),Math.sin(angle)*.5,Math.sin(angle));Vec3 side=d.cross(new Vec3(0,1,0)).normalize();ribbon(out,cam,at.add(d.scale(.2)),at.add(d.scale(radius)),side,.04,color,alpha);}
    }
    private static void ring(VertexConsumer out,Vec3 cam,Vec3 at,Vec3 x,Vec3 z,double radius,double width,int color,float alpha,int steps){
        if(radius<=0||alpha<=0)return;for(int i=0;i<steps;i++){double a=i*Math.PI*2/steps,b=(i+1)*Math.PI*2/steps;Vec3 u=x.scale(Math.cos(a)).add(z.scale(Math.sin(a))),v=x.scale(Math.cos(b)).add(z.scale(Math.sin(b)));quad(out,cam,at.add(u.scale(radius)),at.add(v.scale(radius)),at.add(v.scale(Math.max(0,radius-width))),at.add(u.scale(Math.max(0,radius-width))),color,alpha);}
    }
    private static Vec3 ground(Vec3 p){var hit=world.clip(new net.minecraft.world.level.ClipContext(p.add(0,.5,0),p.add(0,-32,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null));return hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?null:hit.getLocation().add(0,.035,0);}
    private static void ribbon(VertexConsumer out,Vec3 cam,Vec3 a,Vec3 b,Vec3 side,double width,int color,float alpha){quad(out,cam,a.add(side.scale(width)),a.subtract(side.scale(width)),b.subtract(side.scale(width)),b.add(side.scale(width)),color,alpha);}
    private static void quad(VertexConsumer out,Vec3 cam,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha){for(Vec3 p:new Vec3[]{a,b,c,d})out.vertex(p.x-cam.x,p.y-cam.y,p.z-cam.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();}
}
