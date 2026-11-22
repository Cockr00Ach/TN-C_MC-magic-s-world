package com.tnc.tnc.magic.free.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import java.io.*;
import java.util.*;

/** Authored element meshes retain their own materials; animation is driven by cast phase. */
final class ElementalMeshes {
    private record Face(float[] vertices,Vector3f normal,String material,float reveal){}
    private static final Map<String,List<Face>> CACHE=new HashMap<>();
    private static final Map<String,RenderType> PASSES=new HashMap<>();
    static void clear(){CACHE.clear();PASSES.clear();}
    static void draw(PoseStack pose,MultiBufferSource buffers,String model,double progress,double time){
        if(progress<=0)return;float amount=(float)Math.min(1,progress);boolean snake=model.equals("fire-snake");
        for(var face:CACHE.computeIfAbsent(model,ElementalMeshes::load)){
            float open=Math.max(0,Math.min(1,(amount-face.reveal*.7f)/.3f));if(open<=0)continue;
            boolean translucent=face.material.equals("flame")||face.material.equals("violet")||face.material.equals("veil")||open<.99f;
            String element=model.contains("/")?model.substring(0,model.indexOf('/')):"fire";
            String key=element+"/"+face.material+(translucent?"_fade":"_solid");
            var pass=PASSES.computeIfAbsent(key,k->{var texture=ResourceLocation.fromNamespaceAndPath("tnc","textures/effects/free_"+element+"/"+face.material+".png");return translucent?WaterEffectPass.create(texture):RenderType.entityCutoutNoCull(texture);});
            var out=buffers.getBuffer(pass);float[] v=face.vertices;var normal=face.normal;
            int light=face.material.equals("ember")||face.material.equals("lava")?0x00B000B0:LightTexture.FULL_BRIGHT;
            for(int n=0;n<20;n+=5){
                float x=v[n],y=v[n+1],z=v[n+2];
                if(snake){double d=Math.max(0,.095-z),weight=Math.min(1,d/3.205);x+=(float)(.22*weight*Math.sin(time*.22-d*3.2));}
                else if(face.material.equals("flame")||face.material.equals("violet"))x+=(float)(.035*Math.sin(time*.13+z*4+y*3));
                float grow=.75f+.25f*open;
                out.vertex(pose.last().pose(),x*grow,y*grow,z*grow).color(1f,1f,1f,open*.94f)
                        .uv(v[n+3],v[n+4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(pose.last().normal(),normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
    private static List<Face> load(String name){
        String element=name.contains("/")?name.substring(0,name.indexOf('/')):"fire";String file=name.contains("/")?name.substring(name.indexOf('/')+1):name;
        var id=ResourceLocation.fromNamespaceAndPath("tnc","models/effects/free_"+element+"/"+file+".json");
        try(var reader=Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).openAsReader()){
            JsonArray meshes=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("meshes");
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for(var raw:meshes)for(var point:raw.getAsJsonObject().getAsJsonArray("points")){float z=point.getAsJsonArray().get(2).getAsFloat();min=Math.min(min,z);max=Math.max(max,z);}
            List<Face> result=new ArrayList<>();
            for(var raw:meshes){var mesh=raw.getAsJsonObject();var points=mesh.getAsJsonArray("points");String material=mesh.get("material").getAsString();
                for(var rawFace:mesh.getAsJsonArray("faces")){var face=rawFace.getAsJsonObject();var indices=face.getAsJsonArray("v");var uv=face.getAsJsonArray("uv");float[] vertices=new float[20];
                    for(int i=0;i<4;i++){int k=Math.min(i,indices.size()-1);var point=points.get(indices.get(k).getAsInt()).getAsJsonArray();var tex=uv.get(k).getAsJsonArray();for(int axis=0;axis<3;axis++)vertices[i*5+axis]=point.get(axis).getAsFloat();vertices[i*5+3]=tex.get(0).getAsFloat();vertices[i*5+4]=tex.get(1).getAsFloat();}
                    var normal=new Vector3f(vertices[5]-vertices[0],vertices[6]-vertices[1],vertices[7]-vertices[2]).cross(vertices[10]-vertices[0],vertices[11]-vertices[1],vertices[12]-vertices[2]);
                    if(normal.lengthSquared()<1e-8)continue;normal.normalize();
                    float center=(vertices[2]+vertices[7]+vertices[12]+vertices[17])*.25f;
                    result.add(new Face(vertices,normal,material,(max-center)/Math.max(.001f,max-min)));
                }
            }return List.copyOf(result);
        }catch(IOException error){throw new UncheckedIOException("Cannot load authored fire form "+name,error);}
    }
    private ElementalMeshes(){}
}
