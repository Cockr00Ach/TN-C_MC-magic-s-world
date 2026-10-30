package com.tnc.tnc.world.wind;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One authored pose for server attachment, client bones and the editable GeckoLib clip. */
public final class CanglanExecutionPose {
    public static final int MOVE=15,CONTACT=22,RELEASE=72,FINISH=100,DURATION=112;
    public static final int WINDUP=0,PINNED=1,FLUNG=2,END=3;
    private record Node(String name,String parent,double[] pivot,double[] base,double[] times,double[][] keys){}
    private static final Map<String,Node> NODES=load();
    private CanglanExecutionPose(){}
    private static double[] vector(JsonArray array){return new double[]{array.get(0).getAsDouble(),array.get(1).getAsDouble(),array.get(2).getAsDouble()};}
    private static Map<String,Node> load(){
        try(var stream=CanglanExecutionPose.class.getResourceAsStream("/assets/tnc/poses/canglan_execution.json")){
            if(stream==null)throw new IOException("Missing execution pose");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,Node> result=new LinkedHashMap<>();
            for(var element:json.getAsJsonArray("nodes")){
                var b=element.getAsJsonObject();var keys=b.getAsJsonObject("keys");
                var entries=new ArrayList<>(keys.entrySet());entries.sort(Comparator.comparingDouble(e->Double.parseDouble(e.getKey())));
                double[] times=new double[entries.size()];double[][] values=new double[entries.size()][];
                for(int i=0;i<times.length;i++){times[i]=Double.parseDouble(entries.get(i).getKey());values[i]=vector(entries.get(i).getValue().getAsJsonArray());}
                var node=new Node(b.get("name").getAsString(),b.get("parent").getAsString(),vector(b.getAsJsonArray("pivot")),vector(b.getAsJsonArray("base")),times,values);
                result.put(node.name,node);
            }
            return Collections.unmodifiableMap(result);
        }catch(IOException|RuntimeException failure){throw new ExceptionInInitializerError(failure);}
    }
    public static Set<String> bones(){return NODES.keySet();}
    /** Absolute Bedrock degrees. Smooth interpolation is shared, not independently guessed by the client. */
    public static double[] rotation(String bone,double age){
        Node n=NODES.get(bone);double[] value=n.base.clone();if(n.times.length==0)return value;
        int hi=0;while(hi<n.times.length-1&&n.times[hi]<age)hi++;int lo=Math.max(0,hi-1);
        double f=hi==lo?0:Math.max(0,Math.min(1,(age-n.times[lo])/(n.times[hi]-n.times[lo])));f=f*f*(3-2*f);
        for(int i=0;i<3;i++)value[i]+=n.keys[lo][i]*(1-f)+n.keys[hi][i]*f;
        return value;
    }
    private static Matrix4f boneMatrix(String name,double age,Matrix4f frame){
        Node n=NODES.get(name);Matrix4f m=n.parent.isEmpty()?new Matrix4f(frame):boneMatrix(n.parent,age,frame);
        double[] r=rotation(name,age),p=n.pivot;
        return m.translate((float)-p[0]/16,(float)p[1]/16,(float)p[2]/16)
                .rotateZ((float)Math.toRadians(r[2])).rotateY((float)Math.toRadians(-r[1])).rotateX((float)Math.toRadians(-r[0]))
                .translate((float)p[0]/16,(float)-p[1]/16,(float)-p[2]/16);
    }
    public static Vec3 offset(double age,float yaw,float scale){
        var frame=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(scale).translate(0,.01F,0);
        var p=NODES.get("weapon_1").pivot;
        var matrix=boneMatrix("weapon_1",age,frame).translate((float)-p[0]/16,(float)p[1]/16,(float)p[2]/16)
                .rotateZ((float)Math.toRadians(4)).rotateX((float)Math.toRadians(-80)).translate(0,0,1.2F).scale(2.4F);
        var point=matrix.transformPosition(new Vector3f(0,0,1.069375F));
        return new Vec3(point.x,point.y,point.z);
    }
    public static Vec3 tip(CanglanWarlordEntity boss,double age){return boss.position().add(offset(age,boss.yBodyRot,boss.battleScale()));}
    public static Vec3 launch(float yaw,float scale){
        Vec3 tangent=offset(RELEASE,yaw,scale).subtract(offset(RELEASE-3,yaw,scale)).multiply(1,0,1).normalize();
        Vec3 facing=Vec3.directionFromRotation(0,yaw);
        if(tangent.lengthSqr()<.1||tangent.dot(facing)<.2)tangent=facing;
        return tangent.scale(Math.min(4,2.8+.15*scale)).add(0,1.15,0);
    }
    public static boolean pinned(double age){return age>=CONTACT&&age<RELEASE;}
}
