package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

/** Element-specific movement and silhouettes layered over authored meshes. No opaque HUD panels. */
final class ElementalArt {
    static boolean anchors(Ability a){return a.target==Target.FRIEND||a.target==Target.ENEMY||a.target==Target.CHANNEL_ENEMY;}
    static String model(Ability a){return switch(a){
        case WIND_BALL,WIND_PRESSURE->"wind/wind-core";case WIND_BLADE->"wind/wind-blade";case RETURN_WIND->"wind/return-arc";
        case ROCK_BALL->"earth/rock-ball";case ROCK_BLADE->"earth/rock-blade";case EARTH_SPIKE,FISSURE->"earth/ground-spike";case FALLING_ROCK->"earth/falling-rock";case ROCK_GRASP->"earth/stone-grasp";
        case THUNDER_BALL->"lightning/electric-core";case THUNDER_BLADE->"lightning/electric-blade";case STORE_CHARGE->"lightning/charge-node";
        case LIGHT_BALL->"light/radiant-ball";case LIGHT_BLADE->"light/radiant-blade";case PRISM->"light/prism";
        case DARK_BALL->"dark/shadow-orb";case DARK_BLADE->"dark/shadow-blade";case SHADOW_SPIKE->"dark/shadow-spike";case SUMMON_WRAITH->"dark/wraith";case SPIRIT_SWARM->"dark/lesser-wraith";case DARK_RITUAL->"dark/ritual-focus";
        case MISFORTUNE->"dark/misfortune";case HEAL_LOCK->"dark/heal-lock";case SILENCE->"dark/silence";case PAIN_LINK->"dark/pain-link";case DELAYED_CURSE->"dark/delayed-mark";case ARMOR_EROSION->"dark/armor-erosion";case EXPOSURE->"dark/exposure";case WEARINESS->"dark/weariness";
        default->null;
    };}
    static void draw(PoseStack pose,MultiBufferSource buffers,ElementalNetwork.Visual p,Intent intent,double age,double now,double since){
        var a=intent.ability();float[] color=color(a.element());double scale=intent.scale();double fade=Math.min(1,Math.max(0,(p.duration()-age)/12.0));
        if(p.phase()==ElementalNetwork.ARC){arc(pose,Vec3.ZERO,p.velocity(),color,now,a.element()==Element.LIGHTNING,Math.max(0,1-since/10));return;}
        if(p.phase()==ElementalNetwork.STATUS){status(pose,buffers,p,a,now,color);return;}
        if(p.phase()==ElementalNetwork.IMPACT){for(int n=0;n<3;n++)horizontal(pose,(.5+since*.2+n*.25)*scale,now,Math.max(0,1-since/16),color);return;}
        if(p.phase()==ElementalNetwork.FORM){double q=Math.min(1,age/Math.max(1,p.duration()));pose.pushPose();
            if(a.target==Target.PROJECTILE||a.target==Target.CHANNEL)orient(pose,p.velocity());else pose.mulPose(Axis.XP.rotationDegrees(-90));
            seal(pose,(.5+q)*scale,now,q,color,intent.has(EMPOWER));pose.popPose();
            if(a.target==Target.PROJECTILE&&model(a)!=null){pose.pushPose();orient(pose,p.velocity());pose.scale((float)scale*1.4f,(float)scale*1.4f,(float)scale*1.4f);ElementalMeshes.draw(pose,buffers,model(a),q,now);pose.popPose();}
            for(int n=0;n<12;n++){double angle=n*2.399+now*.06,r=(1-q)*2+.3;Vec3 to=new Vec3(Math.cos(angle)*r,Math.sin(angle)*r,(n%3-1)*r);line(pose,to,to.scale(.65),.025,color,q);}
            return;
        }
        if(a.target==Target.PROJECTILE){pose.pushPose();orient(pose,p.velocity());pose.scale((float)scale*1.4f,(float)scale*1.4f,(float)scale*1.4f);if(model(a)!=null)ElementalMeshes.draw(pose,buffers,model(a),1,now);
            for(int n=0;n<6;n++){double angle=n*Math.PI/3;line(pose,new Vec3(Math.cos(angle)*.45,Math.sin(angle)*.45,-.25),new Vec3(Math.cos(angle+.3)*.25,Math.sin(angle+.3)*.25,intent.has(FAST)?-6:-2),.025,color,.8);}
            if(intent.has(EMPOWER))for(int n=0;n<8;n++){double angle=n*Math.PI/4+now*.1;line(pose,new Vec3(Math.cos(angle)*.7,Math.sin(angle)*.7,-.3),new Vec3(Math.cos(angle+.8)*.4,Math.sin(angle+.8)*.4,-4),.045,new float[]{1,.8f,.2f},.95);}
            pose.popPose();
        }else switch(a.element()){
            case WIND->wind(pose,a,intent,age,now,color,fade);
            case EARTH->earth(pose,buffers,a,intent,age,now,color,fade);
            case LIGHTNING->lightning(pose,buffers,a,intent,age,now,color,fade);
            case LIGHT->light(pose,buffers,a,intent,age,now,color,fade);
            case DARK->dark(pose,buffers,a,intent,age,now,color,fade,p.velocity());
            default->{}
        }
        if(intent.has(EMPOWER)){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,1.5*scale,now,fade,new float[]{1,.78f,.16f},true);pose.popPose();}
        if(intent.has(DURABLE)){horizontal(pose,1.1*scale,now,fade*.75,color);horizontal(pose,1.35*scale,-now,fade*.65,color);}
        if(intent.has(SHOCK))horizontal(pose,((now*.1)%1)*3.5*scale,now,1-(now*.1)%1,color);
    }
    private static void wind(PoseStack pose,Ability a,Intent i,double age,double now,float[] c,double alpha){double r=i.scale();
        switch(a){
            case TAKEOFF,AIR_JUMP,UPDRAFT,LAUNCH,WIND_LIFT,TORNADO->{double height=a==Ability.TORNADO?6:4;for(int n=0;n<6;n++)helix(pose,r*(a==Ability.TORNADO?2:1.2),height,now*.15+n,alpha,c);}
            case GATHER,VACUUM->{for(int n=0;n<8;n++){double q=(now*.025+n/8.0)%1,angle=n*Math.PI/4+q*2;Vec3 outer=new Vec3(Math.cos(angle)*5*r*(1-q),.2+q,Math.sin(angle)*5*r*(1-q));line(pose,outer,outer.scale(.75),.04,c,alpha);}horizontal(pose,4*r,now,alpha*.6,c);}
            case WIND_WALL->{for(int n=0;n<9;n++)line(pose,new Vec3(-3*r,n*.4,0),new Vec3(3*r,n*.4,.3*Math.sin(now*.2+n)),.045,c,alpha);}
            case WIND_CORRIDOR,TAILWIND,HEADWIND->{for(int n=0;n<10;n++){double z=(now*.12+n)%10;line(pose,new Vec3(-r,.1,z),new Vec3(r,.15,z+1),.035,c,alpha);}}
            case DOWNDRAFT->{for(int n=0;n<8;n++){double angle=n*Math.PI/4;line(pose,new Vec3(Math.cos(angle),3,Math.sin(angle)),new Vec3(Math.cos(angle)*.3,-1,Math.sin(angle)*.3),.04,c,alpha);}}
            default->{for(int n=0;n<4;n++)horizontal(pose,(.5+(now*.1+n*.3)%2)*r,now,alpha,c);}
        }
    }
    private static void earth(PoseStack pose,MultiBufferSource b,Ability a,Intent i,double age,double now,float[] c,double alpha){
        String model=model(a);if(model!=null){pose.pushPose();if(a==Ability.ROCK_GRASP)pose.translate(0,-.6,0);pose.scale((float)i.scale(),(float)i.scale(),(float)i.scale());ElementalMeshes.draw(pose,b,model,Math.min(1,age/12),now);pose.popPose();}
        switch(a){
            case ROCK_CRUSH->{for(int side:new int[]{-1,1}){pose.pushPose();pose.translate(side*Math.max(.4,2-age*.085),-.3,0);pose.scale(.55f,1.3f,.8f);ElementalMeshes.draw(pose,b,"earth/rock-ball",1,now);pose.popPose();}}
            case ROCK_ARRAY,QUICKSAND,QUAKE,SHATTER_ROCK->{for(int n=0;n<16;n++){double angle=n*2.399,r=(.8+n*.2)*i.scale();Vec3 start=new Vec3(Math.cos(angle)*r,.05,Math.sin(angle)*r);line(pose,start,start.add(Math.sin(angle+.8)*.7,.02,Math.cos(angle+.8)*.7),.035,c,alpha);}horizontal(pose,4*i.scale(),now,alpha,c);}
            case GRAVEL_FLOW->{for(int n=0;n<12;n++){pose.pushPose();pose.translate(Math.sin(n*2.4)*2,.3,(now*.11+n*.6)%8);pose.scale(.25f,.25f,.25f);pose.mulPose(Axis.XP.rotation((float)now*.15f));ElementalMeshes.draw(pose,b,"earth/rock-ball",1,now);pose.popPose();}}
            case STONE_ARMOR,ROOTED->{for(int n=0;n<6;n++){pose.pushPose();pose.translate(Math.cos(n*Math.PI/3)*.7,.6,Math.sin(n*Math.PI/3)*.7);pose.scale(.3f,.5f,.2f);ElementalMeshes.draw(pose,b,"earth/rock-ball",1,now);pose.popPose();}}
            default->horizontal(pose,1.5*i.scale(),now,alpha,c);
        }
    }
    private static void lightning(PoseStack pose,MultiBufferSource b,Ability a,Intent i,double age,double now,float[] c,double alpha){
        if(a==Ability.THUNDER_STORM||a==Ability.NINE_THUNDERS){pose.pushPose();pose.translate(0,8,0);for(int n=0;n<4;n++)horizontal(pose,2+n*.5,now,alpha,c);pose.popPose();}
        if(a==Ability.STORE_CHARGE){for(int n=0;n<3;n++){pose.pushPose();pose.translate(Math.cos(now*.04+n*2.1),1,Math.sin(now*.04+n*2.1));ElementalMeshes.draw(pose,b,"lightning/charge-node",1,now);pose.popPose();}}
        for(int n=0;n<6;n++){double angle=n*Math.PI/3;Vec3 from=new Vec3(Math.cos(angle)*2*i.scale(),.1,Math.sin(angle)*2*i.scale());arc(pose,from,from.scale(.35).add(0,.8,0),c,now+n,true,alpha);}
        horizontal(pose,(a==Ability.ELECTRIC_PULSE?Math.min(10,age*.3):2)*i.scale(),now,alpha,c);
    }
    private static void light(PoseStack pose,MultiBufferSource b,Ability a,Intent i,double age,double now,float[] c,double alpha){
        if(a==Ability.PRISM){pose.pushPose();pose.translate(0,0,1.2);ElementalMeshes.draw(pose,b,"light/prism",1,now);pose.popPose();}
        if(a==Ability.DAYLIGHT){pose.pushPose();pose.scale(.7f,.7f,.7f);ElementalMeshes.draw(pose,b,"light/radiant-ball",1,now);pose.popPose();}
        if(a==Ability.LIGHT_CURTAIN){for(int n=0;n<9;n++)line(pose,new Vec3(-3,n*.4,0),new Vec3(3,n*.4,0),.018,c,alpha*.55);}
        else {pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,(a==Ability.SANCTUARY||a==Ability.HEAL_FIELD?4:1.2)*i.scale(),now,alpha,c,false);pose.popPose();}
        for(int n=0;n<8;n++){double angle=n*Math.PI/4;Vec3 p=new Vec3(Math.cos(angle),((now*.025+n/8.0)%1)*2,Math.sin(angle));line(pose,p,p.add(0,.25,0),.03,c,alpha*.8);}
    }
    private static void dark(PoseStack pose,MultiBufferSource b,Ability a,Intent i,double age,double now,float[] c,double alpha,Vec3 direction){
        String name=model(a);if(name!=null){pose.pushPose();if(a==Ability.SUMMON_WRAITH||a==Ability.SPIRIT_SWARM){pose.translate(0,Math.sin(now*.07)*.12,0);pose.mulPose(Axis.YP.rotation((float)Math.atan2(direction.x,direction.z)));}
            if(a==Ability.SHADOW_SPIKE)pose.translate(0,-.7,0);pose.scale((float)i.scale(),(float)i.scale(),(float)i.scale());ElementalMeshes.draw(pose,b,name,Math.min(1,age/16),now);pose.popPose();}
        if(a==Ability.DARK_RITUAL){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));seal(pose,4*i.scale(),now,alpha,c,false);pose.popPose();for(int n=0;n<6;n++)helix(pose,3.5,.7,now*.03+n,alpha*.5,c);}
        else if(name==null){horizontal(pose,1.3*i.scale(),now,alpha,c);for(int n=0;n<5;n++)helix(pose,.6,1.5,now*.04+n,alpha*.7,c);}
    }
    private static void status(PoseStack pose,MultiBufferSource b,ElementalNetwork.Visual p,Ability a,double now,float[] color){
        String name=model(a);pose.pushPose();pose.translate(0,1.1,0);pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        if(name!=null&&a.element()==Element.DARK){pose.scale(.4f,.4f,.4f);ElementalMeshes.draw(pose,b,name,1,now);pose.scale(2.5f,2.5f,2.5f);}else seal(pose,.3,now,.8,color,false);
        var mc=Minecraft.getInstance();String owner=mc.level==null?"":mc.level.players().stream().filter(x->x.getUUID().equals(p.owner())).map(x->x.getName().getString()).findFirst().orElse("");
        String text=a.label+" "+Math.max(0,(p.duration()+19)/20)+"s"+(owner.isEmpty()?"":" · "+owner);
        pose.translate(0,.42,0);pose.scale(-.012f,-.012f,.012f);mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFE6DBFF,false,pose.last().pose(),b,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,15728880);pose.popPose();
    }
    private static float[] color(Element e){return switch(e){case WIND->new float[]{.55f,.95f,.87f};case EARTH->new float[]{.91f,.64f,.28f};case LIGHTNING->new float[]{.72f,.58f,1};case LIGHT->new float[]{1,.92f,.62f};case DARK->new float[]{.65f,.3f,.94f};default->new float[]{1,.4f,.1f};};}
    private static void seal(PoseStack pose,double radius,double now,double alpha,float[] c,boolean gold){ring(pose,radius,now*.02,alpha,c);ring(pose,radius*.8,-now*.02,alpha*.65,c);for(int n=0;n<8;n++){double a=n*Math.PI/4+now*.02;line(pose,new Vec3(Math.cos(a)*radius,Math.sin(a)*radius,0),new Vec3(Math.cos(a+.3)*radius*.6,Math.sin(a+.3)*radius*.6,0),.022,c,alpha);}if(gold)ring(pose,radius*1.18,-now*.02,alpha,new float[]{1,.8f,.2f});}
    private static void helix(PoseStack pose,double radius,double height,double angle,double alpha,float[] c){for(int n=0;n<32;n++){double q=n/32.0,q2=(n+1)/32.0,a=angle+q*Math.PI*4,a2=angle+q2*Math.PI*4;line(pose,new Vec3(Math.cos(a)*radius*(.3+q*.7),q*height,Math.sin(a)*radius*(.3+q*.7)),new Vec3(Math.cos(a2)*radius*(.3+q2*.7),q2*height,Math.sin(a2)*radius*(.3+q2*.7)),.035,c,alpha);}}
    private static void arc(PoseStack pose,Vec3 from,Vec3 to,float[] color,double now,boolean jagged,double alpha){Vec3 d=to.subtract(from),side=d.cross(new Vec3(0,1,0)).normalize();Vec3 prior=from;for(int n=1;n<=12;n++){double q=n/12.0,offset=jagged&&n<12?Math.sin(n*13.7+Math.floor(now/3)*1.8)*.35:0;Vec3 next=from.add(d.scale(q)).add(side.scale(offset));line(pose,prior,next,jagged?.045:.075,color,alpha);line(pose,prior,next,.018,new float[]{1,1,1},alpha*.9);prior=next;}}
    private static void horizontal(PoseStack pose,double radius,double now,double alpha,float[] c){pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-90));ring(pose,radius,now*.025,alpha,c);pose.popPose();}
    private static void ring(PoseStack pose,double r,double angle,double alpha,float[] c){for(int n=0;n<48;n++){double a=n*Math.PI/24+angle,b=(n+1)*Math.PI/24+angle;line(pose,new Vec3(Math.cos(a)*r,Math.sin(a)*r,0),new Vec3(Math.cos(b)*r,Math.sin(b)*r,0),.025,c,alpha);}}
    private static void line(PoseStack pose,Vec3 a,Vec3 b,double w,float[] c,double alpha){ElementalVisuals.line(pose,a,b,w,c[0],c[1],c[2],alpha);}
    private static void orient(PoseStack pose,Vec3 d){if(d.lengthSqr()>.001)pose.mulPose(new Quaternionf().rotationTo(0,0,1,(float)d.normalize().x,(float)d.normalize().y,(float)d.normalize().z));}
    private ElementalArt(){}
}
