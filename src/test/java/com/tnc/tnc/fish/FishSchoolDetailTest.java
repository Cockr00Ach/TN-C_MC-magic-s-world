package com.tnc.tnc.fish;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishSchoolDetailTest {
    private JsonObject spec() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("model-source/fish-school/detail-spec.json"))).getAsJsonObject();
    }
    @Test void uvNetsArePaddedNonOverlappingAndInsideOneAtlas() throws Exception {
        var s=spec();int size=s.get("texture_size").getAsInt();assertEquals(8,s.get("density").getAsInt());
        List<double[]> boxes=new ArrayList<>();Set<String> names=new HashSet<>();
        for (var raw:s.getAsJsonArray("parts")) {
            var p=raw.getAsJsonObject();assertTrue(names.add(p.get("name").getAsString()));
            var u=p.getAsJsonArray("uv");var d=p.getAsJsonArray("uv_size");
            double x=u.get(0).getAsDouble(),y=u.get(1).getAsDouble(),w=d.get(0).getAsDouble(),h=d.get(1).getAsDouble();
            assertTrue(x>=1 && y>=1 && x+w<size && y+h<size);
            for (var b:boxes) assertFalse(x<b[0]+b[2] && x+w>b[0] && y<b[1]+b[3] && y+h>b[1]);
            boxes.add(new double[]{x,y,w,h});
        }
    }
    @Test void selectedDetailCostStaysBoundedForTwelveFish() throws Exception {
        var parts=spec().getAsJsonArray("parts");
        for (String variant:new String[]{"living","metal"}) {
            int n=0;
            for (var raw:parts) {String v=raw.getAsJsonObject().get("variants").getAsString();if(v.equals("both")||v.equals(variant))n++;}
            assertTrue(n>=40 && n<=90);assertTrue(n*FishSchoolGeometry.COUNT<=1080);
        }
    }
    @Test void sampledTailAndAllDetailVerticesFitTheUnchangedPhysicalHull() throws Exception {
        for (String variant:new String[]{"living","metal"}) for (var raw:spec().getAsJsonArray("parts")) {
            var p=raw.getAsJsonObject();String v=p.get("variants").getAsString();if(!v.equals("both")&&!v.equals(variant))continue;
            var c=p.getAsJsonArray("center");var d=p.getAsJsonArray("size");var r=p.getAsJsonArray("rotation");
            double rx=Math.toRadians(r.get(0).getAsDouble()),ry=Math.toRadians(r.get(1).getAsDouble()),rz=Math.toRadians(r.get(2).getAsDouble());
            for (int frame=0;frame<=64;frame++) for (int sx:new int[]{-1,1}) for (int sy:new int[]{-1,1}) for (int sz:new int[]{-1,1}) {
                double x=d.get(0).getAsDouble()*sx/2,y=d.get(1).getAsDouble()*sy/2,z=d.get(2).getAsDouble()*sz/2;
                double yy=y*Math.cos(rx)-z*Math.sin(rx),zz=y*Math.sin(rx)+z*Math.cos(rx);y=yy;z=zz;
                double xx=x*Math.cos(ry)+z*Math.sin(ry);zz=-x*Math.sin(ry)+z*Math.cos(ry);x=xx;z=zz;
                xx=x*Math.cos(rz)-y*Math.sin(rz);yy=x*Math.sin(rz)+y*Math.cos(rz);x=xx+c.get(0).getAsDouble();y=yy+c.get(1).getAsDouble();z+=c.get(2).getAsDouble();
                if (p.get("tail").getAsBoolean()) {
                    double a=Math.sin(frame*Math.PI/32)*(variant.equals("living")?.35:.12);
                    xx=x*Math.cos(a)+(z+3)*Math.sin(a);zz=-x*Math.sin(a)+(z+3)*Math.cos(a)-3;x=xx;z=zz;
                }
                assertTrue(Math.abs(x*.7/16)<=FishSchoolGeometry.HALF_WIDTH+1e-6,p.get("name").getAsString());
                assertTrue(Math.abs(y*.7/16)<=FishSchoolGeometry.HALF_HEIGHT+1e-6,p.get("name").getAsString());
                assertTrue(Math.abs(z*.7/16)<=FishSchoolGeometry.HALF_LENGTH+1e-6,p.get("name").getAsString());
            }
        }
    }
}
