package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Named weapon signatures: carved flying dragon, frost moon, morning stars and red silk. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorNamedUltimateEffects {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final Vec3 X=new Vec3(1,0,0),Z=new Vec3(0,0,1);
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediateWithBuffers(java.util.Map.of(WarriorTrailRenderType.PLUME,new BufferBuilder(131072),WarriorTrailRenderType.TYPE,new BufferBuilder(65536),WarriorGeometryRenderType.TYPE,new BufferBuilder(262144),WarriorUltimateRadianceRenderType.TYPE,new BufferBuilder(131072)),new BufferBuilder(16384));
    private static volatile DragonMesh dragonMesh;
    private record Face(int a,int b,int c,int color,float alpha) {}
    private record DragonMesh(List<Vec3> points,List<Face> faces,int headStart,Vec3 headAnchor,Vec3 headForward) {}
    private record Frame(VertexConsumer plume,VertexConsumer solid,VertexConsumer dragon,VertexConsumer light,Vec3 camera,int segments,float alpha) {}

    private WarriorNamedUltimateEffects() {}

    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void listen(net.minecraftforge.client.event.RegisterClientReloadListenersEvent e){
            // Procedural data only: immutable geometry is recreated lazily on the render thread.
            e.registerReloadListener((ResourceManagerReloadListener)manager->Minecraft.getInstance().execute(()->dragonMesh=null));
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=e.getCamera().getPosition();int named=0,dragons=0;
        VertexConsumer plume=WarriorTrailRenderType.plume(BUFFER,.30),solid=WarriorTrailRenderType.buffer(BUFFER);
        VertexConsumer carved=BUFFER.getBuffer(WarriorGeometryRenderType.TYPE),light=WarriorUltimateRadianceRenderType.buffer(BUFFER);
        for(var player:mc.level.players().stream().sorted(java.util.Comparator.comparingDouble(p->p==mc.player?-1:p.distanceToSqr(camera))).toList()){
            var animation=WarriorAnimationClient.active(player.getId());double distance=player.distanceToSqr(camera);
            if(animation==null||animation.animation()!=WarriorAnimation.ULTIMATE||distance>64*64||named>=8)continue;
            var id=ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());if(id==null||!id.getNamespace().equals("tnc"))continue;
            String weapon=id.getPath();
            if(!weapon.equals("dragon_sword")&&!weapon.equals("frost_katana")&&!weapon.equals("star_rapier")&&!weapon.equals("red_tassel_spear"))continue;
            named++;
            double age=animation.progress(e.getPartialTick())*animation.durationTicks();if(age<0||age>96)continue;
            float visibility=(float)(Math.min(1,(64-Math.sqrt(distance))/16)*Math.min(1,age/6)*Math.min(1,(96-age)/8));
            Frame f=new Frame(plume,solid,carved,light,camera,distance<32*32?36:20,visibility);
            Vec3 feet=player.getPosition(e.getPartialTick()),forward=Vec3.directionFromRotation(0,player.yBodyRot),side=new Vec3(forward.z,0,-forward.x);
            String element=player.getMainHandItem().hasTag()?player.getMainHandItem().getTag().getString(WindInfusion.ELEMENT_KEY):"";
            String domain=WarriorDomainLink.activeElementId(player);if(domain!=null)element=domain;
            var style=WarriorTrailAppearance.of(weapon,WarriorWeapons.family(player.getMainHandItem()),element);
            switch(weapon){
                case "dragon_sword" -> {if(dragons++<2)dragon(f,feet,forward,side,age,style);}
                case "frost_katana" -> frostMoon(f,feet,forward,side,age,style);
                case "star_rapier" -> morningStars(f,feet,forward,side,age,style);
                case "red_tassel_spear" -> redSilk(f,feet,forward,side,age,style);
                default -> {}
            }
        }
        BUFFER.endBatch(WarriorGeometryRenderType.TYPE);
        BUFFER.endBatch(WarriorTrailRenderType.PLUME);BUFFER.endBatch(WarriorTrailRenderType.TYPE);
        BUFFER.endBatch(WarriorUltimateRadianceRenderType.TYPE);
    }

    /** Cache the authored scales, jaw, claws and antlers once, rather than rebuilding boxes each frame. */
    private static DragonMesh mesh(){
        DragonMesh cached=dragonMesh;if(cached!=null)return cached;
        var triangles=WarriorDragonGeometry.build(Vec3.ZERO,Z,X,1,1);
        var points=new ArrayList<Vec3>();var faces=new ArrayList<Face>(triangles.size());
        var indices=new IdentityHashMap<Vec3,Integer>();
        int bodyFaces=triangles.size()-WarriorDragonGeometry.headGeometry(Vec3.ZERO,Z,X,1,1).size();
        int headStart=0;
        for(int i=0;i<triangles.size();i++){
            if(i==bodyFaces)headStart=points.size();
            var t=triangles.get(i);
            faces.add(new Face(index(indices,points,t.a()),index(indices,points,t.b()),index(indices,points,t.c()),t.color(),t.alpha()));
        }
        Vec3 anchor=WarriorDragonGeometry.path(Vec3.ZERO,Z,X,1),headForward=WarriorDragonGeometry.tangent(Vec3.ZERO,Z,X,1,1);
        Vec3 right=new Vec3(-headForward.z,0,headForward.x).normalize(),up=right.cross(headForward).normalize();
        // Head boxes own distinct vertices. Store their coordinates in the original rigid frame once.
        for(int i=headStart;i<points.size();i++){
            Vec3 d=points.get(i).subtract(anchor);
            points.set(i,new Vec3(d.dot(right),d.dot(up),d.dot(headForward)));
        }
        cached=new DragonMesh(List.copyOf(points),List.copyOf(faces),headStart,anchor,headForward);dragonMesh=cached;return cached;
    }
    private static int index(IdentityHashMap<Vec3,Integer> indices,List<Vec3> points,Vec3 p){
        Integer found=indices.get(p);if(found!=null)return found;int value=points.size();points.add(p);indices.put(p,value);return value;
    }

    private static void dragon(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        DragonMesh mesh=mesh();Vec3[] world=new Vec3[mesh.points.size()];
        double dash=clamp((age-22)/8)*(1-clamp((age-66)/8));
        double finale=clamp((age-70)/20),radius=1.8+dash*2.1+finale*3.7;
        double orbit=age*(age<24?.10:age<70?.24:.16),lift=.35+finale*5;
        Vec3 center=feet.subtract(forward.scale(.35));
        Vec3 headAt=orbitPoint(mesh.headAnchor,center,forward,side,age,orbit,radius,lift);
        Vec3 ahead=orbitPoint(mesh.headAnchor.add(mesh.headForward.scale(.01)),center,forward,side,age,orbit,radius,lift);
        Vec3 behind=orbitPoint(mesh.headAnchor.subtract(mesh.headForward.scale(.01)),center,forward,side,age,orbit,radius,lift);
        Vec3 headForward=ahead.subtract(behind).normalize();
        Vec3 headRight=new Vec3(-headForward.z,0,headForward.x).normalize(),headUp=headRight.cross(headForward).normalize();
        for(int i=0;i<world.length;i++){
            Vec3 local=mesh.points.get(i);
            if(i<mesh.headStart){
                world[i]=orbitPoint(local,center,forward,side,age,orbit,radius,lift);
            }else{
                // A wider orbit must never stretch the jaw, teeth or antlers along the flight direction.
                // Only the anchor and orientation follow the coil; all head dimensions share one scale.
                double x=local.x*.85,y=local.y*.85,z=local.z*.85;
                world[i]=new Vec3(headAt.x+headRight.x*x+headUp.x*y+headForward.x*z,
                        headAt.y+headRight.y*x+headUp.y*y+headForward.y*z,
                        headAt.z+headRight.z*x+headUp.z*y+headForward.z*z);
            }
        }
        float alpha=(float)(.64+.15*dash);
        for(Face face:mesh.faces){
            Vec3 a=world[face.a],b=world[face.b],c=world[face.c];float opacity=face.alpha*alpha*f.alpha;
            vertex(f.dragon,a,f.camera,face.color,opacity);vertex(f.dragon,b,f.camera,face.color,opacity);
            vertex(f.dragon,c,f.camera,face.color,opacity);vertex(f.dragon,c,f.camera,face.color,opacity);
        }
        float pulse=(float)(.72+.18*Math.sin(age*.22));
        for(int sign:new int[]{-1,1}){
            Vec3 eye=rigidHeadPoint(headAt,headRight,headUp,headForward,sign*.413,.15,.29);
            glint(f,eye,headForward,headUp,.055,0xFFDA8D,pulse*.8f);
            Vec3 antler=rigidHeadPoint(headAt,headRight,headUp,headForward,sign*.44,.87,-.60);
            glint(f,antler,headRight,headUp,.066,0xFFE7AC,pulse*.38f);
        }
        // A narrow gold dorsal filament and detached outer coil leave the scales and jaw legible.
        radiantRibbon(f,t->{Vec3 local=WarriorDragonGeometry.path(Vec3.ZERO,Z,X,t);
            return orbitPoint(local.add(0,WarriorDragonGeometry.radius(t)*.95,0),center,forward,side,age,orbit,radius,lift);},UP,.025,0xFFD995,.20f);
        radiantRibbon(f,t->{double angle=orbit+(t-1)*4.8,r=radius+.85;
            return center.add(forward.scale(Math.cos(angle)*r)).add(side.scale(Math.sin(angle)*r)).add(0,lift+.15+t*1.15,0);
        },UP,.045,0xFFB95F,(float)(.12+.08*dash));
        // A thin red-gold wake follows the dragon without washing out its scales.
        for(int strand=0;strand<2;strand++){
            final double offset=strand*.16;
            ribbon(f,t->{double angle=orbit+(t-1)*3.9;double r=radius+offset;return center.add(forward.scale(Math.cos(angle)*r)).add(side.scale(Math.sin(angle)*r)).add(0,lift+t*.9+offset+Math.sin(angle*1.3+age*.045)*.35,0);},UP,.055,style.shell(),style.core(),.32f);
        }
        if(age>=78){double t=clamp((age-78)/14);for(int n=0;n<3;n++){
            double angle=orbit+n*Math.PI*2/3;Vec3 origin=feet.add(side.scale(Math.cos(angle)*(2+4*t))).add(forward.scale(Math.sin(angle)*(2+4*t)));
            ribbon(f,u->origin.add(0,u*(4+t*7),0),side,.065,0xB64F5F,0xF4DEAE,(float)((1-t)*.42));
            lightRay(f,origin,origin.add(0,4+t*7,0),.035,0xFFE4A6,(float)((1-t)*.30));
        }}
    }

    private static Vec3 orbitPoint(Vec3 local,Vec3 center,Vec3 forward,Vec3 side,double age,double orbit,double radius,double lift){
        double angle=orbit+(local.z-WarriorDragonGeometry.LENGTH)*.54,r=radius+local.x*.67;
        double y=lift+local.y*.85+local.z*.11+Math.sin(angle*1.3+age*.045)*.35;
        double along=Math.cos(angle)*r,across=Math.sin(angle)*r;
        return new Vec3(center.x+forward.x*along+side.x*across,center.y+y,center.z+forward.z*along+side.z*across);
    }

    /** 霜月太刀: one clear crescent and individually faceted ice, not the generic sword halo. */
    private static void frostMoon(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        double grow=clamp(age/22),finale=clamp((age-78)/12);
        Vec3 moon=feet.subtract(forward.scale(.8)).add(0,1.7+grow*1.2+finale*1.8,0);
        float presence=age<26?(float)(.65*grow):age<72?.22f:(float)(.5*(1-clamp((age-88)/8)));
        crescent(f,moon,side,UP,1.1+grow*.8+finale*.7,age*.012,.20,0x64CBEA,0xE2FBFF,presence);
        int crystals=age<74?8:12;
        for(int i=0;i<crystals;i++){
            double angle=i*Math.PI*2/crystals+age*.018;
            double throwOut=age>=26&&age<=65?Math.pow(((age-26+i*1.7)%9)/9,2):0;
            double radius=2.0+throwOut*4.5+finale*1.4;
            Vec3 root=feet.add(side.scale(Math.cos(angle)*radius)).add(forward.scale(Math.sin(angle)*radius));
            root=root.add(0,age<74?.8+Math.sin(angle*2+age*.04)*.3:.05,0);
            double height=age<74?.42+grow*.4:1.1+finale*(i%2==0?2.2:1.1);
            Vec3 axis=UP.add(side.scale(Math.cos(angle)*.18));
            crystal(f,root,axis,height,height*.15,0x64CBEA,0xEDFFFF,age<74?.38f:.62f);
            double sparkle=Math.pow(Math.max(0,Math.sin(age*.19+i*2.11)),8);
            if(sparkle>.12&&(f.segments>=30||i%2==0))glint(f,root.add(axis.normalize().scale(height)),side,UP,.055+sparkle*.075,0xD5FAFF,(float)(sparkle*.65));
        }
        if(age>=26&&age<=66){
            double phase=((age-26)%5)/5;
            Vec3 cut=feet.add(0,1.15,0).add(forward.scale(2+phase*5));
            crescent(f,cut,side,UP,.55+phase*.75,Math.PI*.15+age*.07,.09,style.shell(),style.core(),(float)((1-phase)*.55));
        }
        if(age>=82&&age<=91){
            float fade=(float)(1-clamp((age-84)/8));
            for(int n=0;n<3;n++)crescent(f,feet.add(0,1.3+n*.65,0),side,forward,2.5+n*.9+(age-82)*.08,n*.9,.07,0x86DDF2,0xF2FFFF,fade*.28f);
        }
    }

    /** 晨星细剑: a seven-point constellation primes narrow meteor thrusts and the closing starburst. */
    private static void morningStars(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        Vec3 center=feet.add(0,2.4,0).subtract(forward.scale(.8));double scale=.7+.8*clamp(age/22);
        double[] sx={-1.25,-.75,-.35,.25,.75,1.15,.45},sy={-.3,.45,1.0,.65,.95,.1,-.65};
        Vec3[] stars=new Vec3[sx.length];
        for(int i=0;i<stars.length;i++){
            stars[i]=center.add(side.scale(sx[i]*scale)).add(0,sy[i]*scale,0);
            float twinkle=(float)(.45+.25*Math.sin(age*.14+i));
            star(f,stars[i],side,UP,.11+(i==2?.07:0),0xF0C876,0xFFF4CE,twinkle);
            if(i>0){Vec3 a=stars[i-1],b=stars[i];ribbon(f,t->a.lerp(b,t),UP,.018,0xBA8E4C,0xFFE8AB,age<26?.32f:.16f);lightRay(f,a,b,.012,0xFFEAB6,age<26?.16f:.07f);}
        }
        if(age>=24&&age<=69)for(int meteor=0;meteor<4;meteor++){
            double phase=((age-24+meteor*2.5)%10)/10;
            Vec3 start=feet.add(0,1.2+meteor*.17,0).add(side.scale((meteor-1.5)*.38)).subtract(forward.scale(1.3));
            Vec3 head=start.add(forward.scale(1+phase*10));Vec3 tail=head.subtract(forward.scale(2+phase*2));
            float fade=(float)(Math.sin(phase*Math.PI)*.75);
            ribbon(f,t->tail.lerp(head,t),side,.07,style.shell(),style.core(),fade);
            star(f,head,side,UP,.12+phase*.06,0xF4D485,0xFFFFE0,fade);
            lightRay(f,tail,head,.035,0xFFF2C5,fade*.35f);
        }
        if(age>=77&&age<=93){
            double t=clamp((age-77)/16);float fade=(float)(Math.sin(t*Math.PI)*.75);
            Vec3 burst=feet.add(0,1.3+t*.8,0).add(forward.scale(2));
            glint(f,burst,side,UP,.35+Math.sin(t*Math.PI)*.55,0xFFF6D2,fade*.65f);
            for(int i=0;i<5;i++){
                double angle=i*Math.PI*2/5-Math.PI*.5;Vec3 radial=side.scale(Math.cos(angle)).add(UP.scale(Math.sin(angle)));
                Vec3 head=burst.add(radial.scale(1+t*4));
                ribbon(f,u->burst.lerp(head,u),radial.cross(forward),.055,0xD8A550,0xFFF3BB,fade*.6f);
                star(f,head,side,UP,.17,0xF0C876,0xFFF9DA,fade);
                lightRay(f,burst,head,.028,0xFFE6A8,fade*.32f);
            }
        }
    }

    /** 红缨枪: broad red silk stays tied to the horizontal spear and unwinds in the returning cut. */
    private static void redSilk(Frame f,Vec3 feet,Vec3 forward,Vec3 side,double age,WarriorTrailAppearance.Style style){
        Vec3 grip=feet.add(0,1.25,0);float power=age<24?(float)clamp(age/22):age<84?1:(float)clamp((96-age)/12);
        double speed=age>=26&&age<=65?1:0,returnTurn=clamp((age-70)/12)*Math.PI*2;
        for(int n=0;n<3;n++){
            final double phase=n*Math.PI*2/3+age*.15;
            DoubleFunction<Vec3> silk=t->{
                double angle=phase+t*(age<24?Math.PI*2:Math.PI*1.4)+returnTurn;
                double radius=(.6+t*(1.0+speed))*(1+clamp((age-70)/10)*2.0);
                Vec3 at=grip.add(side.scale(Math.sin(angle)*radius)).add(0,Math.cos(angle)*radius*.28,0);
                return at.add(forward.scale(.8-t*(2.8+speed*5)));
            };
            double width=.11+n*.025;
            ribbon(f,silk,UP,width,0xA72B35,0xED9C71,power*(n==0?.72f:.30f));
            radiantRibbon(f,t->silk.apply(t).add(0,width*.72,0),UP,.018,0xFFB178,power*(n==0?.32f:.12f));
            if(n==0)radiantRibbon(f,t->silk.apply(t).add(0,-width*.72,0),UP,.014,0xFF5364,power*.18f);
        }
        if(age>=72&&age<=84){
            double progress=(age-72)/12;
            crescent(f,grip,forward,side,7+progress*5,returnTurn,.16,0xD66551,0xFFE6BE,(float)(Math.sin(progress*Math.PI)*.7));
        }
    }

    private static void crystal(Frame f,Vec3 base,Vec3 direction,double height,double radius,int body,int light,float alpha){
        Vec3 axis=direction.normalize(),tip=base.add(axis.scale(height)),middle=base.add(axis.scale(height*.58));
        Vec3 side=axis.cross(new Vec3(.17,0,1)).normalize();if(side.lengthSqr()<.01)side=X;
        Vec3 other=axis.cross(side).normalize();Vec3[] rim={middle.add(side.scale(radius)),middle.add(other.scale(radius)),middle.subtract(side.scale(radius)),middle.subtract(other.scale(radius))};
        for(int i=0;i<4;i++){
            Vec3 a=rim[i],b=rim[(i+1)%4];int color=i%2==0?light:body;
            quad(f,f.solid,tip,a,b,b,color,alpha*.78f,alpha*.45f,alpha*.45f,alpha*.45f);
            quad(f,f.solid,base,b,a,a,body,alpha*.12f,alpha*.35f,alpha*.35f,alpha*.35f);
            if(i%2==0)lightRay(f,tip,a,.012,light,alpha*.17f);
        }
    }

    private static void star(Frame f,Vec3 at,Vec3 side,Vec3 up,double radius,int shell,int core,float alpha){
        glint(f,at,side,up,radius*.9,core,alpha*.52f);
        quad(f,f.plume,at.add(up.scale(radius*2)),at.add(side.scale(radius*.3)),at.subtract(up.scale(radius*2)),at.subtract(side.scale(radius*.3)),shell,0,alpha*.22f,0,alpha*.22f);
        quad(f,f.solid,at.add(up.scale(radius)),at.add(side.scale(radius*.23)),at.subtract(up.scale(radius)),at.subtract(side.scale(radius*.23)),core,alpha*.1f,alpha,alpha*.1f,alpha);
        quad(f,f.solid,at.add(side.scale(radius*.8)),at.add(up.scale(radius*.17)),at.subtract(side.scale(radius*.8)),at.subtract(up.scale(radius*.17)),core,alpha*.1f,alpha*.85f,alpha*.1f,alpha*.85f);
    }

    private static void crescent(Frame f,Vec3 center,Vec3 a,Vec3 b,double radius,double rotation,double width,int shell,int core,float alpha){
        int segments=f.segments;
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,u=(i+1)/(double)segments;
            double x=rotation+(t-.5)*Math.PI*1.72,y=rotation+(u-.5)*Math.PI*1.72;
            Vec3 da=a.scale(Math.cos(x)).add(b.scale(Math.sin(x))),db=a.scale(Math.cos(y)).add(b.scale(Math.sin(y)));
            double wa=width*Math.sin(t*Math.PI),wb=width*Math.sin(u*Math.PI);
            quad(f,f.plume,center.add(da.scale(radius+wa)),center.add(db.scale(radius+wb)),center.add(db.scale(radius-wb*2)),center.add(da.scale(radius-wa*2)),shell,alpha*.10f,alpha*.10f,0,0);
            quad(f,f.solid,center.add(da.scale(radius)),center.add(db.scale(radius)),center.add(db.scale(radius-wb)),center.add(da.scale(radius-wa)),core,alpha*.75f,alpha*.75f,alpha*.10f,alpha*.10f);
            double edgeA=wa*.26,edgeB=wb*.26;
            quad(f,f.light,center.add(da.scale(radius+edgeA)),center.add(db.scale(radius+edgeB)),center.add(db.scale(radius-edgeB)),center.add(da.scale(radius-edgeA)),core,alpha*.30f,alpha*.30f,0,0);
        }
    }

    private static void ribbon(Frame f,DoubleFunction<Vec3> path,Vec3 normal,double width,int shell,int core,float alpha){
        if(alpha<=.001f)return;Vec3 n=normal.normalize();int segments=f.segments;
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,u=(i+1)/(double)segments;Vec3 a=path.apply(t),b=path.apply(u);
            float aa=alpha*(float)Math.sin(t*Math.PI),ab=alpha*(float)Math.sin(u*Math.PI);
            quad(f,f.plume,a.add(n.scale(width)),a.subtract(n.scale(width)),b.subtract(n.scale(width)),b.add(n.scale(width)),shell,aa*.5f,aa*.5f,ab*.5f,ab*.5f);
            quad(f,f.solid,a.add(n.scale(width*.09)),a.subtract(n.scale(width*.09)),b.subtract(n.scale(width*.09)),b.add(n.scale(width*.09)),core,aa*.65f,aa*.65f,ab*.65f,ab*.65f);
        }
    }
    private static Vec3 rigidHeadPoint(Vec3 anchor,Vec3 right,Vec3 up,Vec3 forward,double x,double y,double z){
        return anchor.add(right.scale(x*.85)).add(up.scale(y*.85)).add(forward.scale(z*.85));
    }
    private static void glint(Frame f,Vec3 at,Vec3 right,Vec3 up,double radius,int color,float alpha){
        if(alpha<=.001f)return;Vec3 r=right.normalize(),u=up.normalize();
        quad(f,f.light,at.add(u.scale(radius*2)),at.add(r.scale(radius*.65)),at.subtract(u.scale(radius*2)),at.subtract(r.scale(radius*.65)),color,0,alpha*.08f,0,alpha*.08f);
        quad(f,f.light,at.add(u.scale(radius*1.35)),at.add(r.scale(radius*.16)),at.subtract(u.scale(radius*1.35)),at.subtract(r.scale(radius*.16)),color,0,alpha,0,alpha);
        quad(f,f.light,at.add(r.scale(radius)),at.add(u.scale(radius*.13)),at.subtract(r.scale(radius)),at.subtract(u.scale(radius*.13)),color,0,alpha*.75f,0,alpha*.75f);
    }
    private static void lightRay(Frame f,Vec3 a,Vec3 b,double width,int color,float alpha){
        if(alpha<=.001f||a.distanceToSqr(b)<.00001)return;
        Vec3 n=b.subtract(a).cross(a.lerp(b,.5).subtract(f.camera)).normalize();if(n.lengthSqr()<.01)n=UP;
        quad(f,f.light,a.add(n.scale(width*2.6)),a.subtract(n.scale(width*2.6)),b.subtract(n.scale(width*2.6)),b.add(n.scale(width*2.6)),color,0,alpha*.09f,alpha*.09f,0);
        quad(f,f.light,a.add(n.scale(width*.25)),a.subtract(n.scale(width*.25)),b.subtract(n.scale(width*.25)),b.add(n.scale(width*.25)),color,alpha*.55f,alpha*.55f,alpha*.55f,alpha*.55f);
    }
    private static void radiantRibbon(Frame f,DoubleFunction<Vec3> path,Vec3 normal,double width,int color,float alpha){
        if(alpha<=.001f)return;Vec3 n=normal.normalize();int segments=Math.min(24,Math.max(10,f.segments/2));
        Vec3 a=path.apply(0);
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,u=(i+1)/(double)segments;Vec3 b=path.apply(u);
            float aa=alpha*(float)Math.sin(t*Math.PI),ab=alpha*(float)Math.sin(u*Math.PI);
            quad(f,f.light,a.add(n.scale(width*2.5)),a.subtract(n.scale(width*2.5)),b.subtract(n.scale(width*2.5)),b.add(n.scale(width*2.5)),color,aa*.06f,aa*.06f,ab*.06f,ab*.06f);
            quad(f,f.light,a.add(n.scale(width*.18)),a.subtract(n.scale(width*.18)),b.subtract(n.scale(width*.18)),b.add(n.scale(width*.18)),color,aa*.55f,aa*.55f,ab*.55f,ab*.55f);a=b;
        }
    }
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static void quad(Frame f,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float aa,float ab,float ac,float ad){
        vertex(out,a,f.camera,color,aa*f.alpha);vertex(out,b,f.camera,color,ab*f.alpha);vertex(out,c,f.camera,color,ac*f.alpha);vertex(out,d,f.camera,color,ad*f.alpha);
    }
    private static void vertex(VertexConsumer out,Vec3 p,Vec3 camera,int color,float alpha){out.vertex(p.x-camera.x,p.y-camera.y,p.z-camera.z).color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha).endVertex();}
}
