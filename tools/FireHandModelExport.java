import com.tnc.tnc.client.FireHandMesh;
import com.tnc.tnc.client.FireHandPose;
import java.nio.file.*;
import java.util.*;

/** Exports the actual runtime mesh without Minecraft, for visual QA and editable GLB conversion. */
public final class FireHandModelExport {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        int maximum=0;
        for(double age:new double[]{14,22,28,32,36,46,56,62,66,72}){
            var mesh=FireHandMesh.build(age,80,false,3);maximum=Math.max(maximum,mesh.size());
            if(mesh.size()>3200)throw new AssertionError("Ground hand exceeds fixed geometry budget: "+mesh.size());
            write(out.resolve("pose-"+(int)age+".json"),age,3,mesh);
        }
        write(out.resolve("pose-blue-36.json"),36,4,FireHandMesh.build(36,80,false,true));
        write(out.resolve("pose-ion-36.json"),36,5,FireHandMesh.build(36,80,false,5));
        for(int tier=1;tier<=5;tier++)for(int age=0;age<=76;age++){
            double scale=FireHandPose.scale(tier);
            if(!Double.isFinite(scale)||scale<1||scale>2.2+1e-6)throw new AssertionError("Invalid tier size");
            for(int finger=0;finger<4;finger++){
                var joints=FireHandPose.finger(age,finger);
                for(int i=0;i<3;i++)if(joints[i+1].sub(joints[i]).length()<.1)throw new AssertionError("Collapsed phalanx");
            }
        }
        for(int beat:new int[]{36,46,56})if(Math.abs(FireHandPose.pulse(beat)-1)>1e-6)throw new AssertionError("Crush pulse out of time");
        var open=FireHandPose.finger(22,1);var closed=FireHandPose.finger(36,1);
        if(open[3].y()<1.8||closed[3].z()<.25||closed[3].y()>.5)throw new AssertionError("Fingers do not wrap toward the palm");
        var thumb=FireHandPose.thumb(36);
        if(thumb[3].x()<0||thumb[3].z()<.5)throw new AssertionError("Thumb does not oppose the fingers");
        int aerial=FireHandMesh.build(36,100,true).size();
        if(aerial>3800)throw new AssertionError("Aerial hand exceeds budget");
        Files.writeString(out.resolve("verification.json"),"{\"finiteQuads\":true,\"posesChecked\":10,\"tierPoseSamples\":385,\"crushBeats\":[36,46,56],\"maximumGroundQuads\":"+maximum+",\"aerialQuads\":"+aerial+",\"previewUsesRuntimeMesh\":true,\"nativeGameTest\":false}");
        System.out.println("PASS runtime mesh, 385 tier/pose samples, palm opposition, finite quads; ground max="+maximum+", aerial="+aerial);
    }
    private static void write(Path path,double age,int tier,List<FireHandMesh.Quad> mesh)throws Exception {
        StringBuilder json=new StringBuilder("{\"age\":"+age+",\"heat\":80,\"tier\":"+tier+",\"quads\":[");
        boolean comma=false;
        for(var q:mesh){
            if(comma)json.append(',');comma=true;
            json.append("{\"layer\":\"").append(q.layer()).append("\",\"rgba\":[").append(q.color().r()).append(',').append(q.color().g()).append(',').append(q.color().b()).append(',').append(q.color().a()).append("],\"xyz\":[");
            int i=0;
            for(var p:List.of(q.a(),q.b(),q.c(),q.d())){
                if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))throw new AssertionError("Non-finite geometry");
                if(i++>0)json.append(',');json.append('[').append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(']');
            }
            json.append("]}");
        }
        json.append("]}");Files.writeString(path,json);
    }

}
