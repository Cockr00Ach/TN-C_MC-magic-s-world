package com.tnc.tnc.dungeon.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.alchemy.AlchemyContent;
import com.tnc.tnc.dungeon.DungeonRealm;
import com.tnc.tnc.dungeon.leyline.LeylineKind;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Room-scoped F8 optics. Nothing in this renderer changes collisions, rewards, or server state. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class LeylineClient {
    private record Node(String role,int index,Vec3 pos) {}
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(262144));
    private static ClientLevel owner;
    private static LeylineKind kind;
    private static CompoundTag state=new CompoundTag();
    private static List<Node> nodes=List.of();
    private static BlockPos origin=BlockPos.ZERO;
    private static long received;
    private static final int CYAN=0x80e8e2,GOLD=0xffd886,RED=0xf05c75,PURPLE=0xb67cf0,GREEN=0x83dfae;
    private static final int[] NOTES=com.tnc.tnc.dungeon.leyline.LeylinePuzzleRules.NOTE_COLORS;
    private LeylineClient() {}

    /** Called on the Minecraft thread by the existing dungeon network dispatch. */
    public static void accept(CompoundTag packet){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!DungeonRealm.inside(mc.level)||!packet.contains("Kind")){clear();return;}
        LeylineKind next;
        try{next=LeylineKind.valueOf(packet.getString("Kind"));}catch(IllegalArgumentException ignored){clear();return;}
        owner=mc.level;kind=next;origin=BlockPos.of(packet.getLong("Origin"));state=packet.getCompound("State").copy();
        var list=new ArrayList<Node>();
        for(Tag raw:state.getList("Nodes",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)raw;BlockPos p=BlockPos.of(n.getLong("Pos"));
            if(p.distSqr(origin)>96*96||list.size()>=384)continue;
            list.add(new Node(n.getString("Role"),n.getInt("Index"),Vec3.atLowerCornerOf(p.subtract(origin)).add(.5,.5,.5)));
        }
        nodes=List.copyOf(list);received=owner.getGameTime();
    }
    public static void clear(){kind=null;nodes=List.of();state=new CompoundTag();owner=null;}
    public static String auditSummary(){return "kind="+kind+", nodes="+nodes.size()+", f8="+ManaSightClient.enabled()+", depth=LEQUAL, transparentDepthWrites=false";}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=owner||mc.level!=null&&(mc.level.getGameTime()-received>60||!DungeonRealm.inside(mc.level)))clear();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER||kind==null||owner==null)return;
        var mc=Minecraft.getInstance();if(mc.level!=owner||mc.player==null)return;
        Vec3 camera=event.getCamera().getPosition(),base=Vec3.atLowerCornerOf(origin);
        if(camera.distanceToSqr(base)>72*72)return;
        boolean sight=ManaSightClient.enabled();
        if(!sight&&kind!=LeylineKind.DUAL_BRIDGE)return;
        // The late pass already contains the view rotation; only translate world -> camera here.
        Matrix4f pose=new Matrix4f().translation((float)(base.x-camera.x),(float)(base.y-camera.y),(float)(base.z-camera.z));
        double time=owner.getGameTime()+event.getPartialTick();
        if(kind==LeylineKind.DUAL_BRIDGE&&!sight){
            Mesh stone=new Mesh(BUFFERS.getBuffer(LeylineRenderTypes.STONE),pose,camera.subtract(base),event.getCamera().rotation(),time);
            for(Node n:nodes)if(n.role.equals("phase_a"))stone.slab(n.pos.add(-.5,-.5,-.5),3,2,0xced1cd,1);
            BUFFERS.endBatch(LeylineRenderTypes.STONE);return;
        }
        Mesh mesh=new Mesh(BUFFERS.getBuffer(LeylineRenderTypes.GLOW),pose,camera.subtract(base),event.getCamera().rotation(),time);
        arteries(mesh);mechanism(mesh);
        if(!state.getBoolean("Solved"))ward(mesh);
        BUFFERS.endBatch(LeylineRenderTypes.GLOW);
    }
    private static List<Node> role(String name){return nodes.stream().filter(n->n.role.equals(name)).toList();}
    private static Node first(String name){return nodes.stream().filter(n->n.role.equals(name)).findFirst().orElse(null);}
    private static int echoStep(){var p=Minecraft.getInstance().player;return p==null?0:state.getCompound("Player_"+p.getUUID()).getInt("EchoStep");}
    private static Vec3 at(String name,Vec3 fallback){Node n=first(name);return n==null?fallback:n.pos;}
    private static int color(){return state.getBoolean("SourceCut")?0x576d73:state.getInt("Poison")==2?RED:state.getInt("Poison")==1?PURPLE:CYAN;}

    private static void arteries(Mesh m){
        var trails=role("trail");
        for(int i=0;i+1<trails.size();i+=2)m.flow(List.of(trails.get(i).pos.add(0,-.35,0),trails.get(i+1).pos.add(0,-.35,0)),CYAN,.065,true);
        Node source=first("source");if(source==null)return;
        int c=color();m.heart(source.pos,c,.55);
        for(Node nest:role("nest")){
            List<Vec3> path=List.of(source.pos,source.pos.add(0,1.3,0),new Vec3(source.pos.x,4,nest.pos.z),nest.pos.add(0,1,0),nest.pos);
            m.flow(path,c,state.getBoolean("SourceCut")?.025:.12,!state.getBoolean("SourceCut"));
            m.heart(nest.pos,c,.72);
        }
    }
    private static void ward(Mesh m){
        // Sparse anchored golden stitches, not a full-screen overlay or an x-ray wall shader.
        for(int x:new int[]{-10,10})for(int z:new int[]{-8,8}){
            Vec3 p=new Vec3(x,.035,z);
            m.segment(p.add(-.55,0,-.55),p.add(.55,0,.55),.025,GOLD,.24);
            m.segment(p.add(.55,0,-.55),p.add(-.55,0,.55),.025,GOLD,.24);
        }
    }
    private static void mechanism(Mesh m){
        boolean solved=state.getBoolean("Solved");
        switch(kind){
            case BRIDGE,DUAL_BRIDGE -> {
                for(Node n:nodes){
                    if(Set.of("bridge","phase_b","phase_a").contains(n.role)){
                        boolean material=n.role.equals("phase_a");
                        m.bridgePlatform(n.pos.add(1,.52,.5),material?0xb2bbc4:CYAN,material?.12:.66);
                    }else if(n.role.equals("drain"))m.whirlpool(n.pos.add(0,.54,0),PURPLE,1.45);
                }
            }
            case FALSE_VAULT -> {
                Vec3 prism=at("prism",new Vec3(.5,8.5,.5));m.heart(prism,PURPLE,.55);
                for(Node n:role("vault")){
                    boolean real=n.index==state.getInt("TrueIndex");
                    if(real){m.heart(n.pos.add(0,.65,0),GOLD,.28);m.ring(n.pos.add(0,-.46,0),.8,GOLD,.8,0);}
                    else if(!solved)m.flow(List.of(prism,prism.add((n.pos.x-prism.x)*.6,-1.5,(n.pos.z-prism.z)*.6),n.pos.add(0,-.48,0)),0x684970,.025,true);
                }
            }
            case TIDE -> {
                double phase=(state.getInt("TidePhase")+(m.time-received))%100,z=at("plant",new Vec3(.5,1.5,8.5)).z-.5-phase*.22;
                if(!solved){for(int band=0;band<3;band++)m.wave(z+band*.18,RED,.35-band*.08);}
                for(Node n:role("cover")){m.ring(n.pos.add(0,-.46,0),1.25,0x386ba4,.75,0);m.sprite(n.pos.add(0,.1,0),.8,0x386ba4,.18);}
                m.heart(at("plant",new Vec3(.5,1.5,8.5)),GOLD,.45);
            }
            case ECHO -> {
                var player=Minecraft.getInstance().player;
                if(player!=null&&AlchemyContent.ECHO!=null&&player.hasEffect(AlchemyContent.ECHO)){
                    for(Node n:role("footprint")){
                        double a=n.index<=echoStep()?.9:.4;
                        m.footprint(n.pos.add(-.18,.53,-.15),CYAN,a);m.footprint(n.pos.add(.18,.53,.2),CYAN,a);
                    }
                    Node last=role("footprint").stream().filter(n->n.index==echoStep()).findFirst().orElse(first("footprint"));
                    if(last!=null){m.sprite(last.pos.add(0,1.8,0),.65,CYAN,.18);m.flow(List.of(last.pos.add(0,1,0),at("echo_wall",Vec3.ZERO)),CYAN,.025,true);}
                }
            }
            case LENS -> {
                Set<Integer> visible=new HashSet<>();for(int i:state.getIntArray("LensVisible"))visible.add(i);
                for(Node n:role("lens_wall"))if(visible.contains(n.index)){
                    m.platformVertical(n.pos.add(0,0,-.515),.46,CYAN,.25);
                    if(n.index==state.getInt("CoreIndex"))m.heart(n.pos.add(0,0,-.6),GOLD,.34);
                }
                if(visible.isEmpty())m.sprite(new Vec3(.5,5.5,4.94),2.4,0xdde5df,.085);
            }
            case SUNDIAL -> {
                int hour=state.getInt("Hour");Vec3 sun=at("sun",new Vec3(.5,7.5,.5));
                double a=hour*Math.PI/2;m.heart(sun.add(Math.sin(a)*3,0,Math.cos(a)*3),GOLD,.6);
                for(Node n:role("dial_door")){
                    int c=n.index==state.getInt("TreasureHour")?GOLD:RED;m.ring(n.pos.add(0,-1.46,0),1.1,c,.6,0);
                    if(n.index==hour)m.flow(List.of(new Vec3(.5,.06,.5),n.pos.add(0,-1.44,0)),PURPLE,.06,true);
                }
            }
            case FACTIONS -> {
                if(!state.getBoolean("BarrierBroken")){
                    for(int z=-7;z<=7;z+=2)m.flow(List.of(new Vec3(.52,.1,z+.5),new Vec3(.52,5,z+.5)),CYAN,.035,true);
                    m.heart(at("barrier_core",Vec3.ZERO),GOLD,.5);
                }
            }
            case STOMACH -> {
                Vec3 vent=at("vent",new Vec3(-7.5,2.5,-5.5));
                for(int i=0;i<6;i++){
                    Vec3 end=new Vec3(Math.cos(i)*6,.7,Math.sin(i)*6);
                    m.flow(List.of(vent,vent.lerp(end,.5).add(0,1,0),end),state.getBoolean("Sedated")?0xf2a6ca:0x804d64,.045,true);
                }
                m.heart(vent,state.getBoolean("Sedated")?0xf2a6ca:CYAN,.4);
            }
            case VINES,POLLUTION -> {
                String basin=kind==LeylineKind.VINES?"basin":"pool";Vec3 src=at(basin,new Vec3(-5.5,1.5,-3.5));
                int c=solved?(kind==LeylineKind.POLLUTION?PURPLE:GREEN):(kind==LeylineKind.POLLUTION?GOLD:0x739268);
                for(int i=-4;i<=4;i+=2)m.flow(List.of(src,new Vec3(i,.08,-1),new Vec3(i,.08,5),new Vec3(i,2.2,5.4)),c,.055,!solved);
                m.heart(src,c,.4);
            }
            case PUPPETS -> {
                Vec3 master=at("puppeteer",new Vec3(-7.5,8.5,6.5));
                for(Node n:role("string"))if((state.getInt("CutStrings")&(1<<n.index))==0){
                    for(double side:new double[]{-.35,.35})m.flow(List.of(master,n.pos.add(side,0,0),n.pos.add(side,-3.5,0)),PURPLE,.018,true);
                    m.heart(n.pos,PURPLE,.16);
                }
                m.heart(master,RED,.18);
            }
            case CHESS -> {
                m.whirlpool(at("drain",new Vec3(2.5,.5,2.5)).add(0,.53,0),CYAN,.8);
                int cell=state.contains("MirrorCell")?state.getInt("MirrorCell"):4;
                for(Node n:role("chess_cell"))if(n.index==cell&&!solved){
                    m.heart(n.pos.add(0,1.8,0),0xc9e8ef,.45);m.ring(n.pos.add(0,.53,0),.75,0xddeef6,.8,0);
                }
            }
            case REFRACTION -> {
                var route=new ArrayList<Vec3>();route.add(at("beam_source",Vec3.ZERO));
                role("mirror_base").stream().sorted(Comparator.comparingInt(Node::index)).forEach(n->{Vec3 p=n.pos.add(0,1.3,0);route.add(p);m.ring(n.pos.add(0,.53,0),.7,GOLD,.55,0);});
                route.add(at("lock",new Vec3(.5,2.5,8.5)));m.flow(route,solved?GREEN:CYAN,.06,true);
                m.heart(route.get(route.size()-1),solved?GREEN:GOLD,.4);
            }
            case ALCHEMY -> {
                Vec3 pot=at("cauldron",new Vec3(-.5,1.5,3.5)).add(.5,.65,.5);
                int temp=state.contains("Temperature")?state.getInt("Temperature"):780;
                for(int i=0;i<3;i++)m.ring(pot.add(0,i*.18,0),.35+i*.25,temp>=360&&temp<=620?GREEN:RED,.8,m.time*.025*(i+1));
                m.heart(pot.add(0,.35,0),solved?GREEN:RED,.35);
            }
            case MEAL -> {
                List<Node> plates=role("plate");int[] colors={RED,GOLD,0x85b9ef,GREEN,0xbd9766};
                for(int i=0;i<plates.size();i++){
                    Node p=plates.get(i),next=plates.get((i+1)%plates.size());m.heart(p.pos,colors[Math.floorMod(p.index,5)],.3);
                    m.flow(List.of(p.pos,next.pos),solved?GREEN:i<2?PURPLE:colors[Math.floorMod(p.index,5)],.035,true);
                }
                m.ring(new Vec3(.5,3.5,.5),1.1,solved?GREEN:GOLD,.5,m.time*.025);
            }
            case MUSIC -> {
                int[] sequence=state.getIntArray("Notes");if(sequence.length==0)sequence=new int[]{1,3,6};
                int active=sequence[(int)(m.time/20)%sequence.length];
                for(Node n:role("crystal")){int c=NOTES[Math.floorMod(n.index,8)];m.heart(n.pos.add(0,1,0),c,n.index==active?.5:.22);}
                if(!solved)for(int i=0;i<3;i++)m.ring(new Vec3(.5,1+i*.35,.5),1.4+Math.sin(m.time*.06+i)*.12,NOTES[Math.floorMod(active,8)],.65,m.time*.03+i);
            }
            case GRAVITY -> {
                boolean up=state.getBoolean("GravityActive");
                for(Node n:role("gravity_node")){
                    Vec3 low=n.pos,high=n.pos.add(0,22,0);m.flow(up?List.of(low,high):List.of(high,low),PURPLE,.13,true);
                    for(int y=4;y<=23;y+=4)m.chevron(new Vec3(n.pos.x,y,n.pos.z),up,PURPLE);
                }
                m.heart(at("ceiling_goal",new Vec3(.5,25.5,4.5)),GOLD,.5);
            }
            case RAPIDS -> {
                Vec3 a=at("current",new Vec3(-3.5,2.5,-4.5)),b=at("rapids_goal",new Vec3(.5,10.5,8.5));
                for(int lane=0;lane<3;lane++){
                    var path=new ArrayList<Vec3>();
                    for(int point=0;point<=8;point++){double progress=point/8d;path.add(a.lerp(b,progress).add((lane-1)*2,Math.sin(progress*Math.PI)*1.6,0));}
                    m.flow(path,CYAN,.18,true);
                    if(lane==(int)(m.time/40%3))m.whirlpool(a.lerp(b,.52).add((lane-1)*2,1.6,0),RED,.75);
                }
                m.heart(at("rapids_goal",new Vec3(.5,10.5,8.5)),GOLD,.45);
            }
            case SCALE -> {
                Vec3 hand=at("scale",new Vec3(-4.5,2.5,3.5)),treasure=at("treasure",new Vec3(5.5,2.5,3.5));
                m.flow(List.of(hand,new Vec3(.5,5.8,3.5),treasure),GOLD,.05,state.getInt("ScaleTicks")>0);
                m.whirlpool(hand,PURPLE,.8);m.heart(treasure,GOLD,.35);
            }
            case CAULDRON -> {
                Vec3 mouth=at("mouth",new Vec3(.5,2.5,3.5));boolean spicy=state.getInt("Craving")==0;
                m.ring(mouth.add(0,2.5,0),1,spicy?RED:GREEN,.65,0);
                m.heart(mouth.add(-.36,2.5,0),spicy?RED:GREEN,.33);if(spicy)m.heart(mouth.add(.4,2.5,0),PURPLE,.27);
            }
            case EXHIBITION -> {
                for(Node n:role("amber"))if((state.getInt("Melted")&(1<<n.index))==0){
                    boolean treasure=(state.getInt("TreasureMask")&(1<<n.index))!=0;
                    m.heart(n.pos.add(0,0,-.54),treasure?GOLD:RED,treasure?.3:.22);
                    if(!treasure)m.flow(List.of(n.pos.add(0,-1.45,-.54),n.pos.add(.8,-1.45,-.54),n.pos.add(.8,.6,-.54)),PURPLE,.03,true);
                }
            }
            case PAWN -> {
                m.whirlpool(at("pawn",new Vec3(-4.5,2.5,5.5)).add(0,.6,0),PURPLE,.8);
                m.ring(at("spring",new Vec3(5.5,.5,4.5)).add(0,-.42,0),2.2,CYAN,.5,0);
                for(int i=0;i<5;i++)m.flow(List.of(new Vec3(5.5,-.2,4.5),new Vec3(5.5+Math.sin(i)*2,1.2,4.5+Math.cos(i)*2)),CYAN,.035,true);
            }
        }
    }

    @SubscribeEvent public static void gui(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(kind==null||!ManaSightClient.enabled()||mc.options.hideGui||mc.player==null)return;
        var g=event.getGuiGraphics();String label=label();
        if(label.isEmpty())return;int width=mc.font.width(label);
        g.fill(g.guiWidth()/2-width/2-9,g.guiHeight()-58,g.guiWidth()/2+width/2+9,g.guiHeight()-40,0x990b171e);
        g.drawCenteredString(mc.font,Component.literal(label),g.guiWidth()/2,g.guiHeight()-53,state.getBoolean("Failed")?RED:0xc5e3dc);
    }
    private static String label(){
        if(state.getBoolean("Solved"))return "地脉已安定";
        if(state.getBoolean("Failed"))return "回路已封死";
        return switch(kind){
            case ALCHEMY -> "药鼎 · "+(state.contains("Temperature")?state.getInt("Temperature"):780)+"°  /  安定区 360–620°";
            case MUSIC -> "共鸣 · "+state.getInt("NoteStep")+" / 3";
            case EXHIBITION -> "解冻余量 · "+Math.max(0,2-state.getInt("Thaws"));
            case GRAVITY -> state.getBoolean("GravityActive")?"魔力上行 · 注意回落":"魔力下行";
            case SCALE -> "献祭 · "+state.getInt("ScaleSeconds")+" 秒";
            case ECHO -> Minecraft.getInstance().player.hasEffect(AlchemyContent.ECHO)?"记忆回声 · "+echoStep()+" / 4":"记忆沉寂 · 回声显影剂";
            default -> "";
        };
    }

    /** Soft sprites and narrow curved surfaces; each effect has world scale and a readable direction. */
    private static final class Mesh {
        final VertexConsumer out;final Matrix4f pose;final Vec3 eye;final Quaternionf rotation;final double time;
        Mesh(VertexConsumer out,Matrix4f pose,Vec3 eye,Quaternionf rotation,double time){this.out=out;this.pose=pose;this.eye=eye;this.rotation=rotation;this.time=time;}
        void vertex(Vec3 p,int c,double a,float u,float v){out.vertex(pose,(float)p.x,(float)p.y,(float)p.z).color((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(float)a).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0,1,0).endVertex();}
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,double alpha){vertex(a,color,alpha,0,0);vertex(b,color,alpha,0,1);vertex(c,color,alpha,1,1);vertex(d,color,alpha,1,0);}
        void sprite(Vec3 p,double r,int c,double alpha){
            if(p.distanceToSqr(eye)<.25)return;
            Vector3f right=new Vector3f(1,0,0).rotate(rotation).mul((float)r),up=new Vector3f(0,1,0).rotate(rotation).mul((float)r);
            Vec3 u=new Vec3(right.x,right.y,right.z),v=new Vec3(up.x,up.y,up.z);
            quad(p.subtract(u).subtract(v),p.subtract(u).add(v),p.add(u).add(v),p.add(u).subtract(v),c,alpha);
        }
        void heart(Vec3 p,int c,double r){double pulse=1+.1*Math.sin(time*.1+p.x);sprite(p,r*2.2*pulse,c,.18);sprite(p,r*pulse,c,.86);sprite(p,r*.25,0xf3fff4,.9);}
        void segment(Vec3 a,Vec3 b,double width,int c,double alpha){
            Vec3 along=b.subtract(a);if(along.lengthSqr()<1e-8)return;
            Vec3 side=along.cross(eye.subtract(a.lerp(b,.5))).normalize().scale(width);
            if(side.lengthSqr()<1e-8)side=new Vec3(width,0,0);
            quad(a.subtract(side),a.add(side),b.add(side),b.subtract(side),c,alpha);
        }
        void flow(List<Vec3> controls,int c,double width,boolean moving){
            if(controls.size()<2)return;
            var path=new ArrayList<Vec3>();
            for(int i=0;i<controls.size()-1;i++){
                Vec3 a=controls.get(Math.max(0,i-1)),b=controls.get(i),d=controls.get(i+1),e=controls.get(Math.min(controls.size()-1,i+2));
                for(int j=0;j<10;j++){double t=j/10d,t2=t*t,t3=t2*t;path.add(b.scale(2).add(d.subtract(a).scale(t)).add(a.scale(2).subtract(b.scale(5)).add(d.scale(4)).subtract(e).scale(t2)).add(a.scale(-1).add(b.scale(3)).subtract(d.scale(3)).add(e).scale(t3)).scale(.5));}
            }
            path.add(controls.get(controls.size()-1));
            for(int i=1;i<path.size();i++){segment(path.get(i-1),path.get(i),width*2.7,c,.18);segment(path.get(i-1),path.get(i),width,c,.5);}
            if(moving)for(int packet=0;packet<4;packet++){
                double f=((time*.012+packet*.25)%1)*(path.size()-1);int i=(int)f;
                Vec3 p=path.get(i).lerp(path.get(Math.min(i+1,path.size()-1)),f-i);sprite(p,width*2+.07,c,.9);
            }
        }
        void ring(Vec3 p,double r,int c,double alpha,double angle){
            for(int i=0;i<48;i++){double a=i*Math.PI/24+angle,b=(i+1)*Math.PI/24+angle;
                if(i%12==0)continue;segment(p.add(Math.cos(a)*r,0,Math.sin(a)*r),p.add(Math.cos(b)*r,0,Math.sin(b)*r),.045,c,alpha);}
        }
        void whirlpool(Vec3 p,int c,double r){
            for(int arm=0;arm<3;arm++)for(int i=0;i<32;i++){
                double a=i*.19+arm*Math.PI*2/3-time*.04,b=a+.19,r0=r*i/32,r1=r*(i+1)/32;
                segment(p.add(Math.cos(a)*r0,0,Math.sin(a)*r0),p.add(Math.cos(b)*r1,.006,Math.sin(b)*r1),.055,c,.7);
            }
            heart(p,c,.16);
        }
        void platform(Vec3 p,int c,double alpha){
            quad(p.add(-.96,0,-.96),p.add(-.96,0,.96),p.add(.96,0,.96),p.add(.96,0,-.96),c,alpha*.45);
            for(int i=0;i<4;i++){double a=i*Math.PI/2+Math.PI/4,b=a+Math.PI/2;segment(p.add(Math.cos(a)*1.25,.015,Math.sin(a)*1.25),p.add(Math.cos(b)*1.25,.015,Math.sin(b)*1.25),.06,c,alpha);}
            ring(p.add(0,.02,0),.5,c,alpha*.7,0);
        }
        void bridgePlatform(Vec3 p,int c,double alpha){
            Vec3 a=p.add(-1.46,0,-.96),b=p.add(-1.46,0,.96),d=p.add(1.46,0,.96),e=p.add(1.46,0,-.96);
            quad(a,b,d,e,c,alpha*.45);
            segment(a,b,.06,c,alpha);segment(b,d,.06,c,alpha);segment(d,e,.06,c,alpha);segment(e,a,.06,c,alpha);
            ring(p.add(0,.02,0),.5,c,alpha*.7,0);
        }
        void platformVertical(Vec3 p,double r,int c,double alpha){quad(p.add(-r,-r,0),p.add(-r,r,0),p.add(r,r,0),p.add(r,-r,0),c,alpha);}
        void footprint(Vec3 p,int c,double alpha){quad(p.add(-.11,0,-.25),p.add(-.11,0,.25),p.add(.11,0,.25),p.add(.11,0,-.25),c,alpha);}
        void wave(double z,int c,double alpha){for(int i=-9;i<9;i++){double y=1.7+.3*Math.sin(i*.6+time*.12);segment(new Vec3(i,y,z),new Vec3(i+1,1.7+.3*Math.sin((i+1)*.6+time*.12),z),.18,c,alpha);segment(new Vec3(i,.15,z),new Vec3(i+1,.15,z),.06,c,alpha);}}
        void chevron(Vec3 p,boolean up,int c){double dy=up?-.7:.7;segment(p.add(-.45,dy,0),p,.05,c,.8);segment(p,p.add(.45,dy,0),.05,c,.8);}
        void slab(Vec3 p,double width,double depth,int c,double alpha){
            Vec3 a=p,b=p.add(width,0,0),d=p.add(0,0,depth),e=p.add(width,0,depth);
            quad(a.add(0,1,0),d.add(0,1,0),e.add(0,1,0),b.add(0,1,0),c,alpha);
            quad(a,b,b.add(0,1,0),a.add(0,1,0),c,alpha);quad(d,d.add(0,1,0),e.add(0,1,0),e,c,alpha);
            quad(a,a.add(0,1,0),d.add(0,1,0),d,c,alpha);quad(b,e,e.add(0,1,0),b.add(0,1,0),c,alpha);
        }
    }
}
