package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import com.mojang.blaze3d.vertex.*;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored attack silhouettes, soft blade plumes and brief pressure fronts. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorUltimateEffects {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediateWithBuffers(java.util.Map.of(WarriorTrailRenderType.PLUME,new BufferBuilder(131072),WarriorTrailRenderType.TYPE,new BufferBuilder(65536),WarriorUltimateRadianceRenderType.TYPE,new BufferBuilder(65536)),new BufferBuilder(16384));
    private record Frame(VertexConsumer plume,VertexConsumer solid,VertexConsumer light,Vec3 camera,int segments,float visibility) {}

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=e.getCamera().getPosition();int rendered=0;
        var plume=WarriorTrailRenderType.plume(BUFFER,.30);var solid=WarriorTrailRenderType.buffer(BUFFER);
        var light=WarriorUltimateRadianceRenderType.buffer(BUFFER);
        for(var p:mc.level.players().stream().sorted(java.util.Comparator.comparingDouble(player->player==mc.player?-1:player.distanceToSqr(camera))).toList()){
            var animation=WarriorAnimationClient.active(p.getId());
            double distance=p.distanceToSqr(camera);
            if(animation==null||animation.animation()!=WarriorAnimation.ULTIMATE||distance>48*48||rendered>=8)continue;
            rendered++;
            var id=ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());if(id==null)continue;
            var family=WarriorWeapons.family(p.getMainHandItem());
            String element=p.getMainHandItem().hasTag()?p.getMainHandItem().getTag().getString(WindInfusion.ELEMENT_KEY):"";
            String domain=WarriorDomainLink.activeElementId(p);if(domain!=null)element=domain;
            var style=WarriorTrailAppearance.of(id.getPath(),family,element);
            Frame frame=new Frame(plume,solid,light,camera,distance<24*24?40:24,(float)Math.min(1,(48-Math.sqrt(distance))/12));
            double age=animation.progress(e.getPartialTick())*animation.durationTicks();
            Vec3 feet=p.getPosition(e.getPartialTick()),forward=Vec3.directionFromRotation(0,p.yBodyRot),side=new Vec3(forward.z,0,-forward.x);
            String polearm=WarriorPolearmUltimates.kind(p.getMainHandItem());
            if(family!=WarriorWeaponFamily.BOW)charge(frame,feet,forward,side,age,style);

            // Polearm identity takes priority: the scythe belongs to the greatsword tag too.
            if(!polearm.isEmpty()){
                polearm(frame,p,polearm,feet,forward,side,age,style);
            }else if(family==WarriorWeaponFamily.GREATSWORD){
                greatsword(frame,feet,forward,side,age,style);
            }else if(family==WarriorWeaponFamily.BOW&&WarriorSpecialUltimates.elemental(p)){
                bow(frame,feet,forward,side,age,style);
            }else if(family!=WarriorWeaponFamily.LONGSWORD){
                strokes(frame,feet,forward,side,p.yBodyRot,age,WarriorUltimates.forId(id.toString()),style,family);
            }else if(age>=78&&age<=90){
                // The server owns the nine piercing paths; the sheath gathers their remaining light.
                double t=(age-78)/12;
                float power=(float)Math.sin(t*Math.PI);
                ring(frame,feet.add(0,.08,0),3.4*(1-t)+.15,.10,style.core(),power*.35f);
                lightRing(frame,feet.add(0,.13,0),new Vec3(1,0,0),new Vec3(0,0,1),3.6*(1-t)+.20,.24,style,power*.5f);
                Vec3 hilt=feet.add(side.scale(.45)).add(0,1.0,0);
                int rays=frame.segments>=40?9:5;
                for(int i=0;i<rays;i++){
                    double a0=i*Math.PI*2/rays;Vec3 source=feet.add(Math.cos(a0)*(1+3*(1-t)),.15,Math.sin(a0)*(1+3*(1-t)));
                    lightRay(frame,source,hilt,side,.065,style,power*.28f);
                }
                glint(frame,hilt,side,UP,1.2,style,(float)pulse(age,84,2,5)*.72f);
            }
        }
        BUFFER.endBatch(WarriorTrailRenderType.PLUME);BUFFER.endBatch(WarriorTrailRenderType.TYPE);BUFFER.endBatch(WarriorUltimateRadianceRenderType.TYPE);
    }

    private static void charge(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        if(age<5||age>25)return;
        double rise=clamp((age-5)/16),fade=clamp((25-age)/5),power=rise*fade;
        ring(f,feet.add(0,.06,0),3.8-2.4*rise,.05+rise*.06,style.shell(),(float)(power*.30));
        ring(f,feet.add(0,.10,0),1.1+rise*.4,.025,style.core(),(float)(power*.55));
        lightRing(f,feet.add(0,.14,0),new Vec3(1,0,0),new Vec3(0,0,1),1.1+rise*.4,.28,style,(float)(power*.32));
        glint(f,feet.add(forward.scale(.52)).add(0,1.25,0),side,UP,.55+rise*.45,style,(float)(power*pulse(age,21,4,4)*.5));
        // Three ascending air strands make the still preparation read as pressure, not idle time.
        for(int n=0;n<3;n++){
            final double phase=n*Math.PI*2/3+age*.10;
            ribbon(f,t->{double r=1.7*(1-t*.75),angle=phase+t*2.7;return feet.add(Math.cos(angle)*r,t*2.5,Math.sin(angle)*r);},side,.055,style,(float)(power*.30));
        }
        if(f.segments>=40)for(int i=-1;i<=1;i++){
            Vec3 root=feet.add(side.scale(i*.6)).subtract(forward.scale(.35)).add(0,.6,0);
            blade(f,root,root.add(UP.scale(2.6)).add(side.scale(i*.32)),side,.085,style,(float)(power*.12));
        }
    }

    private static void greatsword(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        Vec3 origin=feet.add(0,1.1,0);
        if(age>=22&&age<=56){
            double t=clamp((age-22)/32),angle=1.5-t*1.65;
            // The full-sized blade hangs overhead before the bright edge accelerates into the ground.
            double radius=4+18*clamp((age-22)/16);
            arc(f,origin,forward,UP,angle,.34+Math.pow(t,3)*.80,radius,style,(float)(.25+.58*t),true);
            Vec3 tip=origin.add(forward.scale(Math.cos(angle)*radius)).add(UP.scale(Math.sin(angle)*radius));
            blade(f,origin.lerp(tip,.18),tip,side,.45,style,(float)(.13+.20*t));
            glint(f,tip,side,UP,.65+1.5*t,style,(float)(.38+.32*t));
            lightRay(f,origin.lerp(tip,.48),tip,side,.12+radius*.015,style,(float)(.24+.24*t));
            if(age>=53&&age<=65)radiantImpact(f,feet.add(forward.scale(2)),age-54,12,style,.9f,3);
            if(age>=50){double flash=pulse(age,54,3,8);fan(f,feet.add(forward.scale(2)),forward,side,9,style,(float)(flash*.85));}
        }
        if(age>=57&&age<=69)radiantImpact(f,feet.add(forward.scale(2)),age-54,12,style,.9f,3);
        if(age>=56&&age<=87){
            double turn=(age-56)/30*Math.PI*6;
            arc(f,origin,forward,side,turn,2.7,10.5,style,(float)(.85*clamp((88-age)/5)),true);
            for(int hit:new int[]{64,74,84})impact(f,feet,age-hit,10,style,.65f);
        }
    }

    private static void polearm(Frame f,Player p,String kind,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        Vec3 origin=feet.add(0,1.15,0);
        switch(kind){
            case "scythe" -> {
                if(age>=25&&age<=45){
                    double turn=(age-25)/19*Math.PI*6;
                    arc(f,origin,forward,side,turn,2.65,9.2,style,(float)clamp((46-age)/3),true);
                    for(int hit:new int[]{26,34,42})impact(f,feet,age-hit,7.8,style,.38f);
                }
                boolean giant=WarriorExecutionVisual.giant(p.getId());
                if(giant&&age>=48&&age<=73){
                    double power=clamp((age-48)/20);
                    Vec3 tip=origin.add(UP.scale(3+14*power)).add(forward.scale(2));
                    blade(f,origin,tip,side,.25+power*.35,style,(float)(.22+power*.25));
                    glint(f,tip,side,UP,.7+power*1.3,style,(float)(.2+power*.42));
                    if(age>=65)arc(f,origin,forward,UP,1.4-(age-65)*.36,1.2,20,style,.95f,true);
                }else if(!giant&&age>=47&&age<=74){
                    // Chest thrust followed by a rising hook. The close-up camera follows the actual victim.
                    double lift=clamp((age-49)/20);
                    Vec3 root=origin.add(forward.scale(.4)),tip=origin.add(forward.scale(2.6)).add(0,lift*2.2,0);
                    ribbon(f,t->root.lerp(tip,t),side,.10,style,(float)(.65*clamp((76-age)/5)));
                    glint(f,tip,side,UP,.65+lift*.35,style,(float)(pulse(age,48,1,6)*.9+pulse(age,72,3,3)*.55));
                    lightRing(f,tip,side,UP,.48+.3*lift,.12,style,(float)(Math.sin(lift*Math.PI)*.28));
                    for(int strand=0;strand<2;strand++){
                        final double phase=strand*Math.PI+age*.16;
                        ribbon(f,t->tip.add(Math.cos(phase+t*4)*.5,(t-.6)*2,Math.sin(phase+t*4)*.5),side,.06,style,.35f);
                    }
                }
                impact(f,feet.add(forward.scale(2)),age-(giant?72:74),giant?12:5,style,.9f);
            }
            case "spear" -> {
                if(age>=24&&age<=67){
                    double phase=((age-24)%3)/3;
                    Vec3 tip=origin.add(forward.scale(3.5+phase*2));
                    ribbon(f,t->origin.lerp(tip,t),side,.085,style,(float)(.55*(1-phase)));
                    glint(f,tip,side,UP,.6,style,(float)(.55*(1-phase)));
                    lightRing(f,origin.add(forward.scale(.9)),side,UP,.25+phase*1.1,.14,style,(float)(.35*(1-phase)));
                    // Parallel compressed air lines preserve the horizontal spear silhouette.
                    for(int sign:new int[]{-1,1})ribbon(f,t->origin.add(side.scale(sign*.45)).lerp(tip,t),UP,.04,style,(float)(.3*(1-phase)));
                }
                if(age>=72&&age<=83){
                    arc(f,origin,forward,side,(age-72)/10*Math.PI*2,2.9,12,style,(float)clamp((84-age)/3),true);
                    impact(f,feet,age-76,11,style,.6f);
                }
            }
            case "halberd" -> {
                if(age>=24&&age<=45){
                    double t=clamp((age-24)/18);Vec3 top=origin.add(0,3+t*12,0);
                    blade(f,origin,top,side,.25+t*.25,style,.35f);
                    glint(f,top,side,forward,.7+t*.9,style,(float)(.22+t*.22));
                    lightRing(f,feet.add(0,.1,0),side,forward,1.6+t*1.2,.22,style,(float)(.28*(1-t*.5)));
                    for(int n=0;n<3;n++){
                        final double phase=n*Math.PI*2/3+age*.12;
                        ribbon(f,u->feet.add(Math.cos(phase+u*2)*1.5,-u*4,Math.sin(phase+u*2)*1.5),side,.07,style,.25f);
                    }
                }
                if(age>=46&&age<=61){
                    // A long comet wake points upward while the weapon plunges down.
                    glint(f,feet.add(0,.15,0),side,forward,1.4,style,(float)(.62*(1-clamp((age-58)/4))));
                    for(int n=0;n<4;n++){
                        double angle=n*Math.PI/2;Vec3 radial=forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
                        ribbon(f,t->origin.add(radial.scale((1-t)*2.2)).add(0,t*11,0),radial,.20,style,(float)(.6*(1-clamp((age-57)/5))));
                    }
                }
                if(age>=64&&age<=79)arc(f,origin,forward,side,(age-64)/15*Math.PI*4,2.8,10.2,style,(float)clamp((80-age)/3),true);
                if(age>=81&&age<=90){
                    arc(f,origin,forward,UP,1.5-(age-82)/7*Math.PI,1.3,20,style,(float)pulse(age,86,5,5),true);
                    impact(f,feet.add(forward.scale(3)),age-86,12,style,.9f);
                }
            }
            default -> {}
        }
    }

    private static void bow(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        if(age>88)return;
        double gather=clamp(age/24),release=age<26?1:clamp((88-age)/10);
        Vec3 center=feet.add(0,1.35,0).add(forward.scale(.65));
        ring(f,feet.add(0,.08,0),2.7-gather*.7,.09,style.shell(),(float)(release*.45));
        ring(f,feet.add(0,.12,0),1.5,.035,style.core(),(float)(release*.60));
        double gatherCycle=(age%9)/9;
        lightRing(f,feet.add(0,.16,0),side,forward,2.4-gatherCycle*1.0,.22,style,(float)((1-gatherCycle)*release*.38));
        if(age<26)glint(f,center,side,UP,.55+gather*.55,style,(float)(gather*.42));
        for(int n=0;n<4;n++){
            final double phase=n*Math.PI*.5-age*.08;
            ribbon(f,t->{double radius=2.1*(1-t)+.2;return feet.add(Math.cos(phase+t*3)*radius,t*1.5,Math.sin(phase+t*3)*radius);},side,.075,style,(float)(gather*release*.35));
        }
        int remaining=age<26?10:Math.max(0,10-(int)((age-26)/6));
        for(int n=0;n<remaining;n++){
            double angle=n*Math.PI*2/10+age*.025;
            Vec3 root=feet.add(side.scale(Math.cos(angle)*2.1)).add(0,1.5+Math.sin(angle)*.85,0).subtract(forward.scale(.5));
            blade(f,root,root.add(forward.scale(1.5)),side,.075,style,(float)(gather*release*.45));
        }
        if(age>=26&&age<=85){
            double dt=(age-26)%3;
            double radius=.2+dt*.55;
            verticalRing(f,center,side,UP,radius,.06,style.core(),(float)((1-dt/3)*.65));
            lightRing(f,center,side,UP,radius,.19,style,(float)((1-dt/3)*.42));
            glint(f,center.add(forward.scale(.4)),side,UP,.65+dt*.15,style,(float)((1-dt/3)*.6));
            ribbon(f,t->center.add(forward.scale(t*4)),side,.15,style,(float)((1-dt/3)*.65));
        }
    }

    private static void strokes(Frame f,Vec3 feet,Vec3 forward,Vec3 side,float yaw,double age,WarriorUltimates.Art art,WarriorTrailAppearance.Style style,WarriorWeaponFamily family){
        int index=0;
        for(var stroke:art.strokes()){
            double dt=age-stroke.tick();boolean last=++index==art.strokes().size();
            if(dt< -3||dt>15)continue;
            float alpha=(float)(clamp((dt+3)/3)*Math.pow(clamp(1-dt/15),1.6));
            Vec3 origin=feet.add(0,1.1,0),direction=WarriorUltimates.direction(yaw,stroke),across=new Vec3(direction.z,0,-direction.x);
            DoubleFunction<Vec3> path=t->point(stroke,origin,direction,across,t);
            Vec3 normal=stroke.shape().equals("H")||stroke.shape().equals("V")?across:UP;
            ribbon(f,path,normal,last?.24:.16,style,alpha);
            Vec3 cuttingTip=path.apply(clamp((dt+3)/7));
            glint(f,cuttingTip,across,UP,last?1.6:.8,style,alpha*(last?.65f:.40f));
            for(int n=1;n<=4;n++){
                Vec3 tip=path.apply(n/5.0),root=origin.lerp(tip,.35);
                blade(f,root,tip,normal,.075,style,alpha*(last?.40f:.23f));
            }
            if(family==WarriorWeaponFamily.HAMMER){
                impact(f,feet.add(direction.scale(1.2)),dt,last?9:6,style,last?.85f:.5f);
                if(dt>=0&&dt<=7)fan(f,feet.add(direction.scale(1.2)),direction,across,last?7:4,style,alpha*.65f);
            }else if(last)impact(f,feet,dt,Math.min(10,stroke.radius()),style,.45f);
        }
    }

    /** A swept sector: soft outer feathers, coloured body, thin leading edge and sparse weapon echoes. */
    private static void arc(Frame f,Vec3 origin,Vec3 axisA,Vec3 axisB,double head,double span,double radius,WarriorTrailAppearance.Style style,float strength,boolean echoes){
        if(strength<=.001f)return;
        Vec3 normal=axisA.cross(axisB).normalize();
        lightArc(f,origin,axisA,axisB,head,span,radius,Math.max(.12,radius*.025),style,strength*.65f);
        Vec3 leading=origin.add(axisA.scale(Math.cos(head)*radius)).add(axisB.scale(Math.sin(head)*radius));
        glint(f,leading,normal,axisA.scale(-Math.sin(head)).add(axisB.scale(Math.cos(head))),Math.max(.45,radius*.075),style,strength*.62f);
        for(int i=0;i<f.segments;i++){
            double t=i/(double)f.segments,u=(i+1)/(double)f.segments;
            double a=head-t*span,b=head-u*span;
            Vec3 da=axisA.scale(Math.cos(a)).add(axisB.scale(Math.sin(a))),db=axisA.scale(Math.cos(b)).add(axisB.scale(Math.sin(b)));
            float aa=strength*(float)Math.pow(1-t,1.35),ab=strength*(float)Math.pow(1-u,1.35);
            // Deliberately keep the center open: the actor stays legible inside the blade arc.
            quad(f,f.plume,origin.add(da.scale(radius*1.035)),origin.add(db.scale(radius*1.035)),origin.add(db.scale(radius*.74)),origin.add(da.scale(radius*.74)),style.shell(),aa*.18f,ab*.18f,0,0);
            quad(f,f.plume,origin.add(da.scale(radius)),origin.add(db.scale(radius)),origin.add(db.scale(radius*.84)),origin.add(da.scale(radius*.84)),style.shell(),aa*.52f,ab*.52f,0,0);
            quad(f,f.solid,origin.add(da.scale(radius)),origin.add(db.scale(radius)),origin.add(db.scale(radius*.989)),origin.add(da.scale(radius*.989)),style.core(),aa*.86f,ab*.86f,ab*.13f,aa*.13f);
            // A second thin filament separates the sharp edge from its vapor skirt.
            quad(f,f.plume,origin.add(da.scale(radius*1.045)).add(normal.scale(.04)),origin.add(db.scale(radius*1.045)).add(normal.scale(.04)),origin.add(db.scale(radius*1.035)),origin.add(da.scale(radius*1.035)),style.core(),aa*.24f,ab*.24f,0,0);
        }
        if(echoes&&f.segments>=40)for(int n=1;n<=3;n++){
            double angle=head-span*n/4.0;Vec3 d=axisA.scale(Math.cos(angle)).add(axisB.scale(Math.sin(angle)));
            blade(f,origin.add(d.scale(radius*.48)),origin.add(d.scale(radius*1.025)),normal,Math.max(.06,radius*.012),style,strength*.29f);
        }
    }

    private static void ribbon(Frame f,DoubleFunction<Vec3> path,Vec3 normal,double width,WarriorTrailAppearance.Style style,float alpha){
        if(alpha<=.001f)return;
        int segments=Math.max(12,f.segments/2);Vec3 n=normal.normalize();
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,u=(i+1)/(double)segments;Vec3 a=path.apply(t),b=path.apply(u);
            float aa=alpha*(float)Math.sin(Math.PI*t),ab=alpha*(float)Math.sin(Math.PI*u);
            quad(f,f.light,a.add(n.scale(width*.27)),a.subtract(n.scale(width*.27)),b.subtract(n.scale(width*.27)),b.add(n.scale(width*.27)),style.core(),aa*.16f,aa*.16f,ab*.16f,ab*.16f);
            for(int layer=2;layer>=0;layer--){
                double w=width*(layer==2?2.8:layer==1?1:.18);
                float gain=layer==2?.12f:layer==1?.5f:.92f;
                VertexConsumer consumer=layer==0?f.solid:f.plume;
                quad(f,consumer,a.add(n.scale(w)),a.subtract(n.scale(w)),b.subtract(n.scale(w)),b.add(n.scale(w)),layer==0?style.core():style.shell(),aa*gain,aa*gain,ab*gain,ab*gain);
            }
        }
    }

    private static void blade(Frame f,Vec3 root,Vec3 tip,Vec3 widthAxis,double width,WarriorTrailAppearance.Style style,float alpha){
        if(alpha<=.001f)return;
        Vec3 w=widthAxis.normalize().scale(width),mid=root.lerp(tip,.70),heel=root.lerp(tip,.12);
        quad(f,f.plume,root,mid.add(w.scale(1.8)),tip,mid.subtract(w.scale(1.8)),style.shell(),0,alpha*.22f,0,alpha*.22f);
        quad(f,f.solid,root,mid.add(w),tip,mid.subtract(w),style.core(),alpha*.10f,alpha*.5f,alpha*.25f,alpha*.25f);
        quad(f,f.light,root,mid.add(w.scale(.75)),tip,mid.subtract(w.scale(.75)),style.shell(),0,alpha*.19f,alpha*.08f,alpha*.19f);
        quad(f,f.solid,root,mid,tip,mid.subtract(w.scale(.2)),style.base(),alpha*.35f,alpha*.4f,alpha*.15f,alpha*.22f);
        Vec3 along=tip.subtract(root).normalize().scale(width*.22);
        quad(f,f.solid,heel.add(w.scale(1.6)).add(along),heel.subtract(w.scale(1.6)).add(along),heel.subtract(w.scale(1.6)).subtract(along),heel.add(w.scale(1.6)).subtract(along),style.shell(),alpha*.45f,alpha*.45f,alpha*.2f,alpha*.2f);
    }

    private static void impact(Frame f,Vec3 at,double dt,double maximum,WarriorTrailAppearance.Style style,float power){
        if(dt<0||dt>13)return;
        double t=dt/13;float fade=(float)((1-t)*(1-t))*power;
        radiantImpact(f,at,dt,maximum,style,power*.48f,2);
        ring(f,at.add(0,.07,0),.3+maximum*Math.sqrt(t),.06+maximum*.018,style.shell(),fade*.45f);
        ring(f,at.add(0,.11,0),.2+maximum*.74*Math.sqrt(t),.035,style.core(),fade*.7f);
        if(dt<6){double flash=1-dt/6;fan(f,at,new Vec3(1,0,0),new Vec3(0,0,1),maximum*.55,style,(float)(flash*.45));}
    }

    /** Short staggered coronas and outward spokes: bright at contact, quickly opening around the actor. */
    private static void radiantImpact(Frame f,Vec3 at,double dt,double maximum,WarriorTrailAppearance.Style style,float power,int delay){
        if(dt<0||dt>16||power<=.001f)return;
        double t=clamp(dt/12);float fade=(float)Math.pow(1-t,1.8)*power;
        lightRing(f,at.add(0,.16,0),new Vec3(1,0,0),new Vec3(0,0,1),.6+maximum*Math.sqrt(t),.20+maximum*.018,style,fade*.75f);
        double second=clamp((dt-delay)/11);
        if(dt>=delay)lightRing(f,at.add(0,.23,0),new Vec3(1,0,0),new Vec3(0,0,1),.5+maximum*.80*Math.sqrt(second),.11+maximum*.012,style,(float)((1-second)*(1-second)*power*.50));
        int spokes=f.segments>=40?12:8;
        if(dt<9)for(int i=0;i<spokes;i++){
            double angle=(i+.25)*Math.PI*2/spokes,travel=clamp(dt/9);
            Vec3 dir=new Vec3(Math.cos(angle),0,Math.sin(angle)),normal=new Vec3(-dir.z,0,dir.x);
            Vec3 root=at.add(dir.scale(.9+maximum*.25*travel)).add(0,.18,0);
            Vec3 tip=at.add(dir.scale(1.3+maximum*(.5+.5*travel)*(i%2==0?1:.73))).add(0,.18,0);
            lightRay(f,root,tip,normal,.055+maximum*.004,style,(float)(power*.42*(1-travel)));
        }
    }

    /** Four cutting glints stay small in world space; no screen overlay or persistent white flash. */
    private static void glint(Frame f,Vec3 at,Vec3 axisA,Vec3 axisB,double size,WarriorTrailAppearance.Style style,float alpha){
        if(alpha<=.015f||size<=0)return;
        Vec3 a=axisA.normalize(),b=axisB.normalize();
        for(int layer=1;layer>=0;layer--){
            double length=size*(layer==1?1.18:1),width=size*(layer==1?.13:.025);
            float gain=layer==1?.16f:.65f;int color=layer==1?style.shell():style.core();
            quad(f,f.light,at.add(a.scale(length)),at.add(b.scale(width)),at.subtract(a.scale(length)),at.subtract(b.scale(width)),color,0,alpha*gain,0,alpha*gain);
            quad(f,f.light,at.add(b.scale(length*.65)),at.add(a.scale(width)),at.subtract(b.scale(length*.65)),at.subtract(a.scale(width)),color,0,alpha*gain*.8f,0,alpha*gain*.8f);
        }
    }

    private static void lightRay(Frame f,Vec3 from,Vec3 to,Vec3 normal,double width,WarriorTrailAppearance.Style style,float alpha){
        if(alpha<=.005f)return;Vec3 n=normal.normalize();Vec3 middle=from.lerp(to,.55);
        // Two tapered diamonds give a broad low glow and a precise light core without a hard rectangle.
        quad(f,f.light,from,middle.add(n.scale(width*3)),to,middle.subtract(n.scale(width*3)),style.shell(),0,alpha*.13f,0,alpha*.13f);
        quad(f,f.light,from,middle.add(n.scale(width*.27)),to,middle.subtract(n.scale(width*.27)),style.core(),0,alpha*.70f,0,alpha*.70f);
    }

    private static void lightRing(Frame f,Vec3 at,Vec3 axisA,Vec3 axisB,double radius,double width,WarriorTrailAppearance.Style style,float alpha){
        lightArc(f,at,axisA,axisB,Math.PI*2,Math.PI*2,radius,width,style,alpha);
    }
    private static void lightArc(Frame f,Vec3 at,Vec3 axisA,Vec3 axisB,double head,double span,double radius,double width,WarriorTrailAppearance.Style style,float alpha){
        if(alpha<=.005f||radius<=.01)return;
        int segments=Math.max(12,f.segments/2);boolean closed=span>=Math.PI*2-.001;
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,u=(i+1)/(double)segments,x=head-t*span,y=head-u*span;
            Vec3 a=axisA.scale(Math.cos(x)).add(axisB.scale(Math.sin(x))),b=axisA.scale(Math.cos(y)).add(axisB.scale(Math.sin(y)));
            float aa=alpha*(float)(closed?1:Math.pow(1-t,1.4)),ab=alpha*(float)(closed?1:Math.pow(1-u,1.4));
            Vec3 ac=at.add(a.scale(radius)),bc=at.add(b.scale(radius));
            quad(f,f.light,at.add(a.scale(radius+width*2.4)),at.add(b.scale(radius+width*2.4)),bc,ac,style.shell(),0,0,ab*.20f,aa*.20f);
            quad(f,f.light,ac,bc,at.add(b.scale(Math.max(0,radius-width*2.4))),at.add(a.scale(Math.max(0,radius-width*2.4))),style.shell(),aa*.20f,ab*.20f,0,0);
            quad(f,f.light,at.add(a.scale(radius+width*.12)),at.add(b.scale(radius+width*.12)),at.add(b.scale(Math.max(0,radius-width*.12))),at.add(a.scale(Math.max(0,radius-width*.12))),style.core(),aa*.38f,ab*.38f,ab*.38f,aa*.38f);
        }
    }

    private static void fan(Frame f,Vec3 at,Vec3 forward,Vec3 side,double length,WarriorTrailAppearance.Style style,float alpha){
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;Vec3 d=forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            Vec3 tip=at.add(d.scale(length*(i%2==0?1:.68))).add(0,.15,0),normal=d.cross(UP).normalize();
            ribbon(f,t->at.lerp(tip,t).add(0,Math.sin(t*Math.PI)*.12,0),normal,.055,style,alpha);
        }
    }

    private static void ring(Frame f,Vec3 origin,double radius,double width,int color,float alpha){verticalRing(f,origin,new Vec3(1,0,0),new Vec3(0,0,1),radius,width,color,alpha);}
    private static void verticalRing(Frame f,Vec3 origin,Vec3 a,Vec3 b,double radius,double width,int color,float alpha){
        if(alpha<=.001f||radius<=0)return;
        int segments=f.segments;
        for(int i=0;i<segments;i++){
            double x=i*Math.PI*2/segments,y=(i+1)*Math.PI*2/segments;
            Vec3 dx=a.scale(Math.cos(x)).add(b.scale(Math.sin(x))),dy=a.scale(Math.cos(y)).add(b.scale(Math.sin(y)));
            quad(f,f.plume,origin.add(dx.scale(radius+width)),origin.add(dy.scale(radius+width)),origin.add(dy.scale(Math.max(0,radius-width))),origin.add(dx.scale(Math.max(0,radius-width))),color,alpha*.12f,alpha*.12f,alpha*.12f,alpha*.12f);
            quad(f,f.solid,origin.add(dx.scale(radius+width*.12)),origin.add(dy.scale(radius+width*.12)),origin.add(dy.scale(Math.max(0,radius-width*.12))),origin.add(dx.scale(Math.max(0,radius-width*.12))),color,alpha,alpha,alpha,alpha);
        }
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
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static double pulse(double age,double hit,double lead,double tail){return age<hit?clamp(1-(hit-age)/lead):clamp(1-(age-hit)/tail);}
    private static void quad(Frame f,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float aa,float ab,float ac,float ad){
        vertex(out,a,f.camera,color,aa*f.visibility);vertex(out,b,f.camera,color,ab*f.visibility);vertex(out,c,f.camera,color,ac*f.visibility);vertex(out,d,f.camera,color,ad*f.visibility);
    }
    private static void vertex(VertexConsumer out,Vec3 p,Vec3 camera,int color,float alpha){out.vertex(p.x-camera.x,p.y-camera.y,p.z-camera.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();}
}
