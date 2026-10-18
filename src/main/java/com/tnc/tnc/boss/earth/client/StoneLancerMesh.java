package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.client.DarkGiantArmorMesh;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import java.util.*;

/** Beveled stone plates and true pyramidal lance points, baked only once per resource load. */
public final class StoneLancerMesh {
    private static final Set<BakedGeoModel> BAKED=Collections.newSetFromMap(new WeakHashMap<>());
    public static void apply(BakedGeoModel model){if(BAKED.add(model))model.topLevelBones().forEach(StoneLancerMesh::bone);}
    private static void bone(GeoBone bone){
        var cubes=bone.getCubes();
        for(int i=0;i<cubes.size();i++){
            var original=cubes.get(i);
            var shaped=bone.getName().contains("point_")?point(original,bone.getName().contains("point_up")):DarkGiantArmorMesh.chamfer(original);
            cubes.set(i,projectUvs(shaped,original));
        }
        bone.getChildBones().forEach(StoneLancerMesh::bone);
    }
    public static GeoCube point(GeoCube cube,boolean up){
        float x=Float.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=-x,Z=-x;
        GeoQuad sample=null;
        for(var q:cube.quads())if(q!=null){sample=q;for(var v:q.vertices()){
            var p=v.position();x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);
        }}
        if(sample==null)return cube;
        float base=up?y:Y;var apex=new Vector3f((x+X)*.5F,up?Y:y,(z+Z)*.5F);
        Vector3f[] ring={new Vector3f(x,base,z),new Vector3f(X,base,z),new Vector3f(X,base,Z),new Vector3f(x,base,Z)};
        var faces=new ArrayList<GeoQuad>();
        for(int i=0;i<4;i++)faces.add(face(up?new Vector3f[]{ring[(i+1)%4],ring[i],apex}:new Vector3f[]{ring[i],ring[(i+1)%4],apex},sample));
        faces.add(face(up?ring:new Vector3f[]{ring[3],ring[2],ring[1],ring[0]},sample));
        return new GeoCube(faces.toArray(GeoQuad[]::new),cube.pivot(),cube.rotation(),cube.size(),cube.inflate(),cube.mirror());
    }
    private static GeoQuad face(Vector3f[] p,GeoQuad sample){
        var normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0])).normalize();
        var v=new GeoVertex[4];
        for(int i=0;i<4;i++){var uv=sample.vertices()[i];v[i]=new GeoVertex(new Vector3f(p[Math.min(i,p.length-1)]),uv.texU(),uv.texV());}
        return new GeoQuad(v,normal,sample.direction());
    }
    private static GeoCube projectUvs(GeoCube shaped,GeoCube original){
        var faces=new ArrayList<GeoQuad>();
        for(var face:shaped.quads()){
            if(face==null)continue;
            GeoQuad source=null;float best=-Float.MAX_VALUE;
            for(var candidate:original.quads())if(candidate!=null){
                float score=face.normal().dot(candidate.normal());
                if(score>best){best=score;source=candidate;}
            }
            if(source==null){faces.add(face);continue;}
            var a=source.vertices()[0];var b=source.vertices()[1];var d=source.vertices()[3];
            var ab=new Vector3f(b.position()).sub(a.position());var ad=new Vector3f(d.position()).sub(a.position());
            if(ab.lengthSquared()<1e-10||ad.lengthSquared()<1e-10){faces.add(face);continue;}
            var vertices=new GeoVertex[4];
            for(int i=0;i<4;i++){
                var at=face.vertices()[i].position();var delta=new Vector3f(at).sub(a.position());
                float s=delta.dot(ab)/ab.lengthSquared(),t=delta.dot(ad)/ad.lengthSquared();
                vertices[i]=new GeoVertex(new Vector3f(at),a.texU()+s*(b.texU()-a.texU())+t*(d.texU()-a.texU()),
                        a.texV()+s*(b.texV()-a.texV())+t*(d.texV()-a.texV()));
            }
            faces.add(new GeoQuad(vertices,face.normal(),source.direction()));
        }
        return new GeoCube(faces.toArray(GeoQuad[]::new),shaped.pivot(),shaped.rotation(),shaped.size(),shaped.inflate(),shaped.mirror());
    }
}
