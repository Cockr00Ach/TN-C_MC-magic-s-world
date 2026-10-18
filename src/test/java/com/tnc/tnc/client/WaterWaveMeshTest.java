package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class WaterWaveMeshTest {
    private List<double[]> capture(Consumer<VertexConsumer> draw) {
        var vertices=new ArrayList<double[]>();
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("vertex")) {
                double[] v=new double[7];
                for(int j=0;j<3;j++){v[j]=((Number)args[args.length-3+j]).doubleValue();assertTrue(Double.isFinite(v[j]));}
                vertices.add(v);
            } else if(method.getName().equals("color")) {
                double[] v=vertices.get(vertices.size()-1);
                for(int j=0;j<4;j++){v[j+3]=((Number)args[j]).doubleValue();assertTrue(v[j+3]>=0&&v[j+3]<=1);}
            }
            return method.getReturnType()==void.class?null:proxy;
        });
        draw.accept(out);assertFalse(vertices.isEmpty());assertEquals(0,vertices.size()%4);
        return vertices;
    }
    @Test void animatedSurfStaysFiniteAndExportsActualMeshForVisualReview() throws Exception {
        var pose=new Matrix4f();var forward=new Vec3(0,0,1);
        var meshes=new java.util.LinkedHashMap<String,List<double[]>>();
        for(double age:new double[]{0,.5,40,120}) {
            var small=capture(out->WaterWaveVisuals.rollingWave(out,pose,Vec3.ZERO,forward,5,2,age,.9F,1));
            var large=capture(out->WaterWaveVisuals.rollingWave(out,pose,Vec3.ZERO,forward,12,8,age,.9F,2));
            var ocean=capture(out->WaterWaveVisuals.breakingWave(out,pose,36,24,64,age,.9F,WaterWaveVisuals.frontTint(0)));
            capture(out->WaterWaveVisuals.circularWave(out,pose,12,3,age,.9F));
            if(age==40){meshes.put("water_wave",small);meshes.put("tsunami",large);meshes.put("ocean",ocean);}
        }
        Files.createDirectories(Path.of("work"));
        Files.writeString(Path.of("work/water-surf-remodel-mesh.json"),new com.google.gson.Gson().toJson(meshes));
    }
}
