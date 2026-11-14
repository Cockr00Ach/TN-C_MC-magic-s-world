package com.tnc.tnc.warrior;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Authored choreography shared by server hit frames, camera and blade echoes. */
public final class WarriorUltimates {
    public static final int DURATION=96, INTRO=22, COOLDOWN=0;
    public record Stroke(int tick,String shape,double yaw,double radius,float multiplier) {}
    public record Art(String id,String name,String keys,String description,List<Stroke> strokes) {}
    private static final Map<String,Art> ARTS=load();
    private static Map<String,Art> load(){
        String path="assets/tnc/warrior/ultimates.json";
        try {
            var stream=WarriorUltimates.class.getResourceAsStream("/"+path);
            if(stream==null)stream=java.nio.file.Files.newInputStream(net.minecraftforge.fml.ModList.get().getModContainerById("tnc").orElseThrow().getModInfo().getOwningFile().getFile().findResource(path));
            try(var in=stream){
                Art[] arts=new Gson().fromJson(new String(in.readAllBytes(),StandardCharsets.UTF_8),Art[].class);
                Map<String,Art> out=new LinkedHashMap<>();for(var art:arts)out.put(art.id(),art);return Collections.unmodifiableMap(out);
            }
        }catch(Exception e){throw new IllegalStateException("Cannot load weapon ultimates",e);}
    }
    public static Collection<Art> all(){return ARTS.values();}
    public static Art forId(String id){
        String local=id.startsWith("tnc:")?id.substring(4):id;
        if(ARTS.containsKey(local))return ARTS.get(local);
        return ARTS.get(id.contains("bow")?"bow":"longsword");
    }
    public static Vec3 direction(float yaw,Stroke stroke){return Vec3.directionFromRotation(0,(float)(yaw+stroke.yaw()));}
    public static boolean contains(Stroke s,Vec3 relative,Vec3 forward){
        double along=relative.dot(forward),side=Math.abs(relative.x*forward.z-relative.z*forward.x);
        if(Math.abs(relative.y)>3 || relative.lengthSqr()>s.radius()*s.radius())return false;
        return switch(s.shape()){
            case "T","B" -> along>=0 && side<1.1;
            case "H" -> along>=-.5 && side<2.3;
            case "O" -> true;
            default -> along>=-1.2;
        };
    }
}
