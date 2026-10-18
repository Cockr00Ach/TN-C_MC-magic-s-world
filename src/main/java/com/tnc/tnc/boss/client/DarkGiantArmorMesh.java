package com.tnc.tnc.boss.client;

import net.minecraft.core.Direction;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import java.util.*;

/** Chamfer the baked armor itself; animation, UVs and emissive layers share the resulting mesh. */
public final class DarkGiantArmorMesh {
    private static final Set<BakedGeoModel> SHAPED=Collections.newSetFromMap(new WeakHashMap<>());
    private DarkGiantArmorMesh(){}
    public static void apply(BakedGeoModel model){
        if(!SHAPED.add(model))return;
        model.topLevelBones().forEach(DarkGiantArmorMesh::bone);
    }
    private static void bone(GeoBone bone){
        String name=bone.getName();
        if(name.equals("body")||name.startsWith("pauldron")||name.startsWith("arm")
                ||name.startsWith("shin")||name.startsWith("foot")){
            var cubes=bone.getCubes();
            for(int i=0;i<cubes.size();i++)cubes.set(i,chamfer(cubes.get(i)));
        }
        bone.getChildBones().forEach(DarkGiantArmorMesh::bone);
    }
    public static GeoCube chamfer(GeoCube cube){
        if(cube.quads().length!=6)return cube;
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX;
        float maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        GeoQuad front=null,back=null,side=null;
        for(var quad:cube.quads()){
            if(quad==null)continue;
            if(quad.direction()==Direction.NORTH)front=quad;
            if(quad.direction()==Direction.SOUTH)back=quad;
            if(quad.direction()==Direction.EAST)side=quad;
            for(var vertex:quad.vertices()){
                var p=vertex.position();minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);
                minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);
            }
        }
        if(front==null||back==null||side==null||maxX-minX<.03F||maxY-minY<.03F||maxZ-minZ<.005F)return cube;
        float cut=Math.min(maxX-minX,maxY-minY)*.18F;
        float[][] ring={{minX+cut,minY},{maxX-cut,minY},{maxX,minY+cut},{maxX,maxY-cut},
                {maxX-cut,maxY},{minX+cut,maxY},{minX,maxY-cut},{minX,minY+cut}};
        var faces=new ArrayList<GeoQuad>(20);
        // Triangles are represented as quads with a repeated final vertex, as expected by the entity buffer.
        for(int i=1;i<7;i++){
            faces.add(face(new Vector3f[]{point(ring[0],minZ),point(ring[i+1],minZ),point(ring[i],minZ)},front,minX,maxX,minY,maxY));
            faces.add(face(new Vector3f[]{point(ring[0],maxZ),point(ring[i],maxZ),point(ring[i+1],maxZ)},back,minX,maxX,minY,maxY));
        }
        for(int i=0;i<8;i++){
            int next=(i+1)%8;
            faces.add(face(new Vector3f[]{point(ring[i],minZ),point(ring[next],minZ),point(ring[next],maxZ),point(ring[i],maxZ)},side,minX,maxX,minY,maxY));
        }
        return new GeoCube(faces.toArray(GeoQuad[]::new),cube.pivot(),cube.rotation(),cube.size(),cube.inflate(),cube.mirror());
    }
    private static Vector3f point(float[] xy,float z){return new Vector3f(xy[0],xy[1],z);}
    private static GeoQuad face(Vector3f[] points,GeoQuad original,float minX,float maxX,float minY,float maxY){
        var vertices=new GeoVertex[4];
        float minU=1,maxU=0,minV=1,maxV=0;
        for(var v:original.vertices()){
            minU=Math.min(minU,v.texU());maxU=Math.max(maxU,v.texU());minV=Math.min(minV,v.texV());maxV=Math.max(maxV,v.texV());
        }
        for(int i=0;i<4;i++){
            var p=points[Math.min(i,points.length-1)];
            float u=minU+(p.x-minX)/(maxX-minX)*(maxU-minU);
            float v=maxV-(p.y-minY)/(maxY-minY)*(maxV-minV);
            if(points.length==4){u=original.vertices()[i].texU();v=original.vertices()[i].texV();}
            vertices[i]=new GeoVertex(new Vector3f(p),u,v);
        }
        var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();
        return new GeoQuad(vertices,normal,original.direction());
    }
}
