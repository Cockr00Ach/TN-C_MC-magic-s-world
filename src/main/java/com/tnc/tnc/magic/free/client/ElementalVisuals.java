package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ElementalVisuals {
    private record Seen(ElementalNetwork.Visual packet,long received){}
    private static final Map<Long,Seen> VIEWS=new LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(262144));
    private static final RenderType GLOW=WaterEffectPass.create(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/eclipse/white.png"));
    private static final RenderType FLAME=WaterEffectPass.create(ResourceLocation.fromNamespaceAndPath("tnc","textures/effects/free_fire/flame.png"));
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long rendered;
    static Map<String,Object> auditSnapshot(){return Map.of("rendered",rendered,"forms",VIEWS.values().stream().filter(v->v.packet.phase()==ElementalNetwork.FORM).count(),"active",VIEWS.values().stream().filter(v->v.packet.phase()!=ElementalNetwork.FORM).count());}
    public static void accept(ElementalNetwork.Visual packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension())||packet.ability()<0||packet.ability()>=Ability.values().length)return;
        if(packet.phase()==ElementalNetwork.END){VIEWS.remove(packet.id());return;}
        // Impact is transient and does not replace the parent area's regular position.
        long key=packet.phase()==ElementalNetwork.IMPACT?-packet.id():packet.id();
        VIEWS.put(key,new Seen(packet,mc.level.getGameTime()));while(VIEWS.size()>192)VIEWS.remove(VIEWS.keySet().iterator().next());
    }
    public static boolean maintaining(UUID owner){return VIEWS.values().stream().anyMatch(v->v.packet.owner().equals(owner)&&v.packet.phase()==ElementalNetwork.ACTIVE&&Ability.values()[v.packet.ability()].channel());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=world){VIEWS.clear();world=mc.level;}if(world==null)return;long now=world.getGameTime();VIEWS.values().removeIf(v->now-v.received>((v.packet.phase()==ElementalNetwork.IMPACT||v.packet.phase()==ElementalNetwork.ARC)?16:Math.max(30,v.packet.duration())));}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->Minecraft.getInstance().execute(ElementalMeshes::clear));}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        Vec3 camera=event.getCamera().getPosition();double now=mc.level.getGameTime()+event.getPartialTick();var pose=new PoseStack();
        rendered++;
        for(var seen:VIEWS.values()){
            var p=seen.packet;var ability=Ability.values()[p.ability()];var intent=new Intent(ability,p.flags(),0,p.power());
            Vec3 at=p.at();double age=Math.max(0,now-p.started());var anchor=p.target()<0?null:mc.level.getEntity(p.target());
            if(anchor!=null&&p.phase()!=ElementalNetwork.FORM&&(ability.element()==com.tnc.tnc.magic.Element.FIRE||p.phase()==ElementalNetwork.STATUS||ElementalArt.anchors(ability)))at=anchor.getPosition(event.getPartialTick()).add(0,anchor.getBbHeight()*.5,0);
            else if(p.phase()==ElementalNetwork.ACTIVE&&(ability.target==Target.PROJECTILE||p.target()==-2))at=at.add(p.velocity().scale(Math.min(3,now-seen.received)));
            if(at.distanceToSqr(camera)>100*100)continue;
            pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            if(ability.element()!=com.tnc.tnc.magic.Element.FIRE){ElementalArt.draw(pose,BUFFER,p,intent,age,now,now-seen.received);}
            else if(p.phase()==ElementalNetwork.IMPACT){shock(pose,Math.min(1,(now-seen.received)/16.0)*4*intent.scale(),1-(now-seen.received)/16.0,1,.6f,.15f);}
            else if(p.phase()==ElementalNetwork.BURN){
                if(ability==Ability.VIOLET_FIRE){pose.translate(0,-.65,0);ElementalMeshes.draw(pose,BUFFER,"violet-fire",1,now);}
                else for(int n=0;n<5;n++)tongue(pose,Math.cos(n*1.256)*.35,-.6,Math.sin(n*1.256)*.35,.35,.9,now+n, .75);
            }else if(p.phase()==ElementalNetwork.FORM){
                double progress=Math.min(1,age/Math.max(1,p.duration()));
                if(ability.target==Target.PROJECTILE||ability.channel())orient(pose,p.velocity());else pose.mulPose(Axis.XP.rotationDegrees(-90));
                double radius=(.45+progress)*intent.scale();seal(pose,radius,now,progress,intent.has(EMPOWER));
                if(ability.target==Target.PROJECTILE&&progress>.15){pose.scale((float)intent.scale()*1.4f,(float)intent.scale()*1.4f,(float)intent.scale()*1.4f);ElementalMeshes.draw(pose,BUFFER,model(ability),Math.min(1,(progress-.15)/.75),now);}
            }else drawActive(pose,seen,intent,age,now);
            pose.popPose();
        }
        BUFFER.endBatch();
    }
    private static String model(Ability ability){return switch(ability){case FIREBLADE->"fireblade";case FIRE_SERPENT->"fire-snake";case MAGMA_SPRAY,LAVA_VENT->"magma-drop";case VIOLET_FIRE->"violet-fire";default->"fireball";};}
    private static void drawActive(PoseStack pose,Seen seen,Intent intent,double age,double now){
        var p=seen.packet;var ability=intent.ability();double scale=intent.scale(),fade=Math.min(1,Math.max(0,(p.duration()-age)/12.0));
        if(p.target()==-2){orient(pose,p.velocity());pose.scale((float)scale,(float)scale,(float)scale);ElementalMeshes.draw(pose,BUFFER,ability==Ability.FIRE_RAIN?"fireball":"magma-drop",1,now);return;}
        if(p.target()==-3){field(pose,1.5*scale,.5,now,fade,10);return;}
        switch(ability){
            case FIREBALL,FIREBLADE,FIRE_SERPENT -> {
                pose.pushPose();orient(pose,p.velocity());pose.scale((float)scale*1.4f,(float)scale*1.4f,(float)scale*1.4f);ElementalMeshes.draw(pose,BUFFER,model(ability),1,now);
                for(int n=0;n<4;n++)line(pose,new Vec3(Math.cos(n*1.57)*.3,Math.sin(n*1.57)*.3,-.6),new Vec3(Math.cos(n*1.57+.3)*.2,Math.sin(n*1.57+.3)*.2,intent.has(FAST)?-6:-2),.035,1,.45f,.05f,.7);
                if(intent.has(EMPOWER))for(int n=0;n<8;n++)line(pose,new Vec3(Math.cos(n*.785+now*.12)*.6,Math.sin(n*.785+now*.12)*.6,-.5),new Vec3(Math.cos(n*.785+now*.12+.7)*.4,Math.sin(n*.785+now*.12+.7)*.4,-3.5),.05,1,.85f,.3f,.9);
                pose.popPose();
            }
            case FIRE_BREATH,MAGMA_SPRAY -> {
                pose.pushPose();
                orient(pose,p.velocity());seal(pose,.65*scale,now,1,intent.has(EMPOWER));
                for(int n=0;n<22;n++){double q=(now*.045+n/22.0)%1,z=q*9,a=n*2.399;pose.pushPose();pose.translate(Math.cos(a)*q*1.9*scale,Math.sin(a)*q*1.9*scale,z);pose.mulPose(Axis.XP.rotationDegrees(90));tongue(pose,0,0,0,(.15+q*.6)*scale,1.2,now+n,fade*(1-q)*.8);pose.popPose();}
                pose.popPose();
            }
            case FIRE_COVER,KINDLE -> {field(pose,(ability==Ability.KINDLE?4:3)*scale,ability==Ability.KINDLE?.5:.9,now,fade,20);if(ability==Ability.KINDLE){pose.pushPose();pose.translate(0,.2,0);ElementalMeshes.draw(pose,BUFFER,"fireball",1,now);pose.popPose();}}
            case FIRE_WALL -> {double yaw=Math.atan2(p.velocity().x,p.velocity().z);pose.mulPose(Axis.YP.rotation((float)yaw));for(int n=-9;n<=9;n++)tongue(pose,n*.42*scale,0,0,.65*scale,3*scale,now+n,fade);}
            case FIRE_RING -> {double radius=Math.min(7*scale,age*.25*intent.speed());field(pose,radius,1,now,fade,30);}
            case FLASH_FIRE,DETONATE_BURN -> {double q=Math.min(1,age/12);shock(pose,q*4*scale,1-q,1,.4f,.02f);field(pose,q*3*scale,2*(1-q),now,1-q,16);}
            case FIRE_RAIN -> {pose.pushPose();pose.translate(0,8,0);field(pose,3.5*scale,.7,now,fade,20);pose.popPose();}
            case FLAME_COLUMN -> {for(int n=0;n<14;n++){double a=n*2.399; tongue(pose,Math.cos(a)*scale,0,Math.sin(a)*scale,.9*scale,6*scale,now+n,fade*.85);}}
            case LAVA_VENT -> field(pose,1.8*scale,1.2,now,fade,16);
            case FLAME_CLOAK -> {for(int n=0;n<8;n++){double a=n*.785+now*.025;tongue(pose,Math.cos(a)*.65,-.8,Math.sin(a)*.65,.4,1.6,now+n,fade*.65);}}
            case HEAT_WAVE -> {pose.pushPose();orient(pose,p.velocity());seal(pose,2.2*scale,now,fade,intent.has(EMPOWER));for(int n=0;n<8;n++)line(pose,new Vec3(-2*scale,n*.3-1,-.3),new Vec3(2*scale,n*.3-1,.3),.025,1,.7f,.3f,.4*fade);pose.popPose();}
            case EMBER_MARK -> {pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,1.2*(1-age/p.duration()),now*2,fade,true);pose.popPose();}
            default -> {pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,.9,now,fade,false);pose.popPose();}
        }
        if(intent.has(EMPOWER)){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,1.2*scale,now,fade*.8,true);pose.popPose();}
        if(intent.has(SHOCK))shock(pose,((now*.08)%1)*2.4*scale,1-(now*.08)%1,1,.8f,.4f);
        if(intent.has(DURABLE)){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));ring(pose,1.1*scale,now*.02,.6*fade,1,.3f,.08f);ring(pose,1.3*scale,-now*.02,.4*fade,1,.6f,.12f);pose.popPose();}
    }
    private static void orient(PoseStack pose,Vec3 direction){if(direction.lengthSqr()>.001)pose.mulPose(new Quaternionf().rotationTo(0,0,1,(float)direction.normalize().x,(float)direction.normalize().y,(float)direction.normalize().z));}
    private static void field(PoseStack pose,double radius,double height,double now,double alpha,int count){
        for(int n=0;n<count;n++){double a=n*2.39996,r=radius*(.65+.35*Math.sin(n*1.7)*Math.sin(n*1.7));tongue(pose,Math.cos(a)*r,0,Math.sin(a)*r,.55,height*(.8+.2*Math.sin(now*.15+n)),now+n,alpha*.8);}
        pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));ring(pose,radius,0,alpha*.65,1,.35f,.05f);pose.popPose();
    }
    private static void tongue(PoseStack pose,double x,double y,double z,double width,double height,double now,double alpha){
        var out=BUFFER.getBuffer(FLAME);
        for(int cross=0;cross<2;cross++){double a=cross*Math.PI/2;Vec3 right=new Vec3(Math.cos(a),0,Math.sin(a));
            for(int n=0;n<4;n++){double q=n/4.0,next=(n+1)/4.0;Vec3 bottom=new Vec3(x+Math.sin(now*.13+q*4)*width*q,y+q*height,z),top=new Vec3(x+Math.sin(now*.13+next*4)*width*next,y+next*height,z);double w=width*(1-q),v=width*(1-next);
                quad(pose,out,bottom.subtract(right.scale(w)),bottom.add(right.scale(w)),top.add(right.scale(v)),top.subtract(right.scale(v)),1,1,1,(float)alpha,(float)q,(float)next);
            }
        }
    }
    private static void seal(PoseStack pose,double radius,double now,double alpha,boolean gold){
        ring(pose,radius,now*.03,alpha,1,gold?.85f:.4f,gold?.3f:.05f);ring(pose,radius*.83,-now*.025,alpha*.8,1,.65f,.15f);
        if(gold)ring(pose,radius*1.18,-now*.018,alpha*.9,1,.9f,.4f);
        for(int n=0;n<8;n++){double a=n*Math.PI/4+now*.03;Vec3 p=new Vec3(Math.cos(a)*radius,Math.sin(a)*radius,0),q=new Vec3(Math.cos(a+.28)*radius*.63,Math.sin(a+.28)*radius*.63,0);line(pose,p,q,.025,1,.65f,.15f,alpha*.7);}
    }
    private static void ring(PoseStack pose,double r,double angle,double alpha,float red,float green,float blue){for(int n=0;n<48;n++){double a=n*Math.PI/24+angle,b=(n+1)*Math.PI/24+angle;line(pose,new Vec3(Math.cos(a)*r,Math.sin(a)*r,0),new Vec3(Math.cos(b)*r,Math.sin(b)*r,0),.025,red,green,blue,alpha);}}
    private static void shock(PoseStack pose,double radius,double alpha,float r,float g,float b){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));ring(pose,radius,0,Math.max(0,alpha),r,g,b);pose.popPose();}
    static void line(PoseStack pose,Vec3 from,Vec3 to,double width,float r,float g,float b,double alpha){Vec3 right=to.subtract(from).cross(new Vec3(0,0,1)).normalize().scale(width);if(right.lengthSqr()<1e-8)right=new Vec3(width,0,0);quad(pose,BUFFER.getBuffer(GLOW),from.subtract(right),from.add(right),to.add(right),to.subtract(right),r,g,b,(float)alpha,0,1);}
    private static void quad(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha,float bottom,float top){Vec3[] points={a,b,c,d};for(int n=0;n<4;n++){Vec3 v=points[n];out.vertex(pose.last().pose(),(float)v.x,(float)v.y,(float)v.z).color(r,g,blue,Math.max(0,Math.min(1,alpha))).uv(n==0||n==3?0:1,n<2?bottom:top).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,0,1).endVertex();}}
    private ElementalVisuals(){}
}
