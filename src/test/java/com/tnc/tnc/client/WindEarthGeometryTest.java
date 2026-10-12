package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.WindEarthVisualRules;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.*;

class WindEarthGeometryTest {
    private static final class Mesh {
        final List<double[]> vertices=new ArrayList<>();
        double[] point;
        final VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("vertex")) {
                int start=args[0] instanceof Matrix4f?1:0;
                point=new double[]{((Number)args[start]).doubleValue(),((Number)args[start+1]).doubleValue(),((Number)args[start+2]).doubleValue(),0,0,0,0};
                for(int i=0;i<3;i++)assertTrue(Double.isFinite(point[i]));
            }
            if(method.getName().equals("color"))for(int i=0;i<4;i++)point[3+i]=((Number)args[i]).doubleValue();
            if(method.getName().equals("endVertex"))vertices.add(point);
            return method.getReturnType()==void.class?null:proxy;
        });
    }
    @Test void everyChainAndTierHasBoundedFiniteGeometry() throws Exception {
        Map<String,Object> preview=new TreeMap<>();
        for(var entry:WindEarthVisualRules.all().entrySet()) {
            var spec=entry.getValue();Mesh mesh=new Mesh();
            WindEarthGeometry.spell(mesh.out,mesh.out,new Matrix4f(),spec.kind(),spec.tier(),spec.radius(),24,1,WindEarthGeometry.FLAT);
            assertTrue(mesh.vertices.size()>0,entry.getKey());
            assertTrue(mesh.vertices.size()<=6500,entry.getKey()+" exceeds budget: "+mesh.vertices.size());
            assertEquals(0,mesh.vertices.size()%4);
            for(double[] point:mesh.vertices)assertTrue(point[6]>=0&&point[6]<=255);
            if(spec.tier()==1||spec.tier()==3||spec.tier()==5)preview.put(entry.getKey(),Map.of("kind",spec.kind(),"tier",spec.tier(),"radius",spec.radius(),"vertices",mesh.vertices));
            System.out.println("Wind/earth mesh "+entry.getKey()+": "+mesh.vertices.size());
        }
        Path output=Path.of("work/wind-earth-geometry-preview.json");Files.createDirectories(output.getParent());Files.writeString(output,new Gson().toJson(preview));
    }
    @Test void tornadoAndFlyingBladesStayWithinTheirPerEntityBudgets() {
        for(double scale:new double[]{2,5,8}) {
            Mesh mesh=new Mesh();WindEarthGeometry.tornado(mesh.out,mesh.out,new Matrix4f(),scale,15,.9F);
            assertTrue(mesh.vertices.size()<=2000);
        }
        Mesh blade=new Mesh();WindEarthGeometry.blade(blade.out,blade.out,new Matrix4f(),40);
        assertTrue(blade.vertices.size()<=400);
        for(int tier=1;tier<=5;tier++) {
            Mesh spirit=new Mesh();WindEarthGeometry.spirit(spirit.out,spirit.out,new Matrix4f(),1.3,tier,40);
            assertTrue(spirit.vertices.size()<=1250);
        }
    }
    @Test void terrainHeightIsUsedWithoutScanningTheWorldFromTheMesh() {
        Mesh mesh=new Mesh();WindEarthGeometry.spell(mesh.out,mesh.out,new Matrix4f(),5,3,5.5,20,1,(x,z)->2);
        assertTrue(mesh.vertices.stream().allMatch(v->v[1]>=2),"Mire follows the supplied ground plane");
    }
}
