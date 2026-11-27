package com.tnc.tnc.sanctum.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Samples the animators' Blockbench-space keys after the SCM2 joint pivot has been applied. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SanctumSentinelPose {
    private SanctumSentinelPose(){}
    private record Key(float t,float x,float y,float z,float rx,float ry,float rz){}
    private record Clip(float duration,boolean loop,Map<String,Key[]> tracks){}
    private static Map<String,Map<String,Clip>> library=Map.of();
    private static final Map<LivingEntity,Travel> TRAVEL=new WeakHashMap<>();
    private static final class Travel {Vec3 last;int tick,hitTick=-1000,lastHurt;float before,total;}
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(new SimplePreparableReloadListener<Map<String,Map<String,Clip>>>(){
            @Override protected Map<String,Map<String,Clip>> prepare(ResourceManager manager,ProfilerFiller profiler){
                return Map.of("gate",load(manager,"gate"),"shade",load(manager,"shade"),"gargoyle",load(manager,"gargoyle"));
            }
            @Override protected void apply(Map<String,Map<String,Clip>> prepared,ResourceManager manager,ProfilerFiller profiler){
                library=prepared;TRAVEL.clear();
            }
        });
    }
    private static Map<String,Clip> load(ResourceManager manager,String model) {
        var id=ResourceLocation.fromNamespaceAndPath("tnc","sanctum_motion/"+model+".json");
        Map<String,Clip> result=new HashMap<>();
        try(var in=manager.getResourceOrThrow(id).openAsReader()){
            var clips=JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("clips");
            for(var entry:clips.entrySet()){
                JsonObject c=entry.getValue().getAsJsonObject();
                var tracks=new HashMap<String,Key[]>();
                for(var track:c.getAsJsonObject("tracks").entrySet()){
                    JsonArray keys=track.getValue().getAsJsonArray();Key[] frames=new Key[keys.size()];
                    for(int i=0;i<frames.length;i++){
                        var k=keys.get(i).getAsJsonObject();JsonArray p=k.getAsJsonArray("p"),r=k.getAsJsonArray("r");
                        frames[i]=new Key(k.get("t").getAsFloat(),v(p,0),v(p,1),v(p,2),v(r,0),v(r,1),v(r,2));
                    }
                    tracks.put(track.getKey(),frames);
                }
                result.put(entry.getKey(),new Clip(c.get("duration").getAsFloat(),c.has("loop")&&c.get("loop").getAsBoolean(),tracks));
            }
        }catch(IOException|RuntimeException ex){com.mojang.logging.LogUtils.getLogger().error("Could not load sanctum keyframes {}",id,ex);}
        return result;
    }
    private static float v(JsonArray a,int i){return a==null?0:a.get(i).getAsFloat();}
    private static void sample(Map<String,Clip> clips,String clip,String bone,float elapsed,boolean eased,Matrix4f m){
        Clip c=clips.get(clip);if(c==null)return;
        Key[] keys=c.tracks.get(bone);if(keys==null||keys.length==0)return;
        float t=c.loop?(elapsed%c.duration+c.duration)%c.duration:Mth.clamp(elapsed,0,c.duration);
        int left=0,right=keys.length-1;
        while(right-left>1){int mid=(left+right)>>>1;if(keys[mid].t<=t)left=mid;else right=mid;}
        Key a=keys[left],b=keys[right];
        float f=a.t==b.t?0:Mth.clamp((t-a.t)/(b.t-a.t),0,1);if(eased)f=f*f*(3-2*f);
        m.translate(Mth.lerp(f,a.x,b.x)/16,Mth.lerp(f,a.y,b.y)/16,Mth.lerp(f,a.z,b.z)/16)
         .rotateZYX(Mth.lerp(f,a.rz,b.rz)*Mth.DEG_TO_RAD,Mth.lerp(f,a.ry,b.ry)*Mth.DEG_TO_RAD,Mth.lerp(f,a.rx,b.rx)*Mth.DEG_TO_RAD);
    }
    private static Travel travel(LivingEntity e){
        var walk=TRAVEL.computeIfAbsent(e,k->new Travel());
        if(walk.last==null){walk.last=e.position();walk.tick=e.tickCount;}
        if(walk.tick!=e.tickCount){
            double length=e.position().subtract(walk.last).horizontalDistance();walk.before=walk.total;
            if(length<2.5)walk.total+=(float)(length*(24.0/2.5));
            if(e.hurtTime>walk.lastHurt)walk.hitTick=e.tickCount;
            walk.lastHurt=e.hurtTime;walk.last=e.position();walk.tick=e.tickCount;
        }
        return walk;
    }
    static float stride(LivingEntity e,float partial){var w=travel(e);return Mth.lerp(partial,w.before,w.total);}
    static float injury(LivingEntity e,float partial){var w=travel(e);float age=e.tickCount-w.hitTick+partial;return age<17?age:-1;}
    static boolean animate(SanctumPose pose,String b,Matrix4f m){
        String model=pose.form(),action=pose.action();float t=pose.t();
        if(model.equals("gargoyle")){
            var lib=library.getOrDefault(model,Map.of());
            String clip=switch(action){case "stone_windup"->"charge";case "stone_dive"->"dive";case "stone_land"->"exposed";case "stone_climb"->"rise";default->"flight";};
            Clip c=lib.get(clip);if(c!=null)sample(lib,clip,b,clip.equals("flight")?pose.age():t*c.duration,true,m);
            if(action.equals("stone_hit"))sample(lib,"hurt",b,t*10,true,m);
            else if(pose.speed()>=0&&pose.speed()<10)sample(lib,"hurt",b,pose.speed(),true,m);
            return true;
        }
        if(model.equals("shade")){
            var lib=library.getOrDefault(model,Map.of());
            String clip=switch(action){case "shade_lunge"->"pounce";case "shade_hit"->"hurt";case "shade_recover"->"recover";default->"walk";};
            Clip c=lib.get(clip);if(c!=null)sample(lib,clip,b,clip.equals("walk")?pose.age():t*c.duration,false,m);
            if(action.equals("shade_veil") && (b.equals("左臂")||b.equals("右臂")))
                m.rotateX(-.68f*SanctumPose.ease(t,0,.4f));
            return true;
        }
        if(!model.equals("gate_left")&&!model.equals("gate_right"))return false;
        var lib=library.getOrDefault("gate",Map.of());
        String side=action.endsWith("_L")?"_L":action.endsWith("_R")?"_R":model.equals("gate_left")?"_L":"_R";
        String clip=action.startsWith("gate_stamp")?"slam":action.startsWith("gate_pinned")?"lock":action.startsWith("gate_recover")?"recover":"guard";
        Clip c=lib.get(clip+side);
        if(c!=null)sample(lib,clip+side,b,clip.equals("guard")?pose.age()+(model.equals("gate_right")?12:0):t*c.duration,false,m);
        float sign=side.equals("_L")?-1:1,wind=SanctumPose.ease(t,0,.45f),strike=SanctumPose.ease(t,.50f,.67f),settle=SanctumPose.ease(t,.71f,1);
        if(action.equals("gate_sweep")){
            if(b.equals("gate_chest"))m.rotateY(sign*(-.42f*wind+1.12f*strike-.7f*settle));
            if(b.equals("gate_upper_arm"+side))m.rotateX(-.38f*(wind-strike)).rotateY(sign*.60f*(strike-settle));
        }
        if(action.equals("gate_lash")){
            if(b.equals("gate_mouth"))m.rotateX(-.26f*(wind-settle));
            if(b.equals("gate_tongue"))m.translate(0,0,8*(strike-settle));
        }
        if(action.equals("gate_fall")&&b.equals("root"))m.rotateZ(sign*.86f*SanctumPose.ease(t,0,1));
        boolean freeJoint=b.startsWith("gate_orbit")||b.startsWith("gate_arm")||b.startsWith("gate_hand")||b.equals("gate_head")||b.equals("gate_tongue");
        if(pose.speed()>=0 && (!clip.equals("lock")||freeJoint))sample(lib,"hurt"+side,b,pose.speed(),false,m);
        return true;
    }
}

