package com.tnc.tnc.dungeon.mirror.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.dungeon.mirror.EchoCore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the authored Blockbench mesh. Each independently hinged part stays in asset space. */
public final class EchoCoreRenderer extends EntityRenderer<EchoCore> {
    private static final ResourceLocation ATLAS=ResourceLocation.parse("tnc:textures/entity/mirror/core.png");
    private static final ResourceLocation MODEL=ResourceLocation.parse("tnc:mirror/core.json");
    private static final Vector3f OUTER=new Vector3f(-.0845653f,-.3978485f,.9135455f);
    private static final Vector3f CROSS=new Vector3f(.7240981f,-.6519808f,.2249511f);
    private final List<Part> parts;
    private final Map<String,float[][]> unseal;

    public EchoCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
        parts=read();
        unseal=readUnseal();
        shadowRadius=0;
    }
    private record Part(String bone, boolean glow, float[] pivot, float[][] v, int[][] faces, float[][] normals) {}
    private static float[] floats(JsonArray a) {
        float[] values=new float[a.size()];
        for(int i=0;i<a.size();i++) values[i]=a.get(i).getAsFloat();
        return values;
    }
    private static List<Part> read() {
        try (var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(MODEL).openAsReader()) {
            var result=new ArrayList<Part>();
            for(var entry:JsonParser.parseReader(stream).getAsJsonObject().getAsJsonArray("parts")) {
                JsonObject o=entry.getAsJsonObject();
                var va=o.getAsJsonArray("vertices");
                float[][] points=new float[va.size()][];
                for(int i=0;i<va.size();i++) points[i]=floats(va.get(i).getAsJsonArray());
                var tris=o.getAsJsonArray("triangles");
                int[][] indices=new int[tris.size()][3]; float[][] normals=new float[tris.size()][3];
                for(int i=0;i<tris.size();i++){
                    var tri=tris.get(i).getAsJsonArray(); int[] face=indices[i];
                    for(int j=0;j<3;j++) face[j]=tri.get(j).getAsInt();
                    float[] a=points[face[0]],b=points[face[1]],c=points[face[2]];
                    var normal=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2])
                            .cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]);
                    if(normal.lengthSquared()<1e-12f) normal.set(0,1,0); else normal.normalize();
                    normals[i][0]=normal.x;normals[i][1]=normal.y;normals[i][2]=normal.z;
                }
                String name=o.get("name").getAsString();
                result.add(new Part(name.replaceFirst("_glow$",""),o.get("emissive").getAsBoolean(),
                        floats(o.getAsJsonArray("pivot")),points,indices,normals));
            }
            return List.copyOf(result);
        } catch (IOException e) {throw new IllegalStateException("Unable to load echo core mesh",e);}
    }
    private static Map<String,float[][]> readUnseal() {
        var path=ResourceLocation.parse("tnc:mirror/unseal.json");
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(path).openAsReader()){
            var channels=JsonParser.parseReader(stream).getAsJsonObject().getAsJsonObject("channels");
            var tracks=new HashMap<String,float[][]>();
            for(var entry:channels.entrySet()) {
                var rows=entry.getValue().getAsJsonArray();float[][] frames=new float[rows.size()][];
                for(int i=0;i<rows.size();i++) frames[i]=floats(rows.get(i).getAsJsonArray());
                tracks.put(entry.getKey(),frames);
            }
            return Map.copyOf(tracks);
        } catch(IOException e) {throw new IllegalStateException("Unable to load echo core unseal motion",e);}
    }
    private static float smooth(float from,float to,float age) {
        float f=Mth.clamp((age-from)/(to-from),0,1);return f*f*(3-2*f);
    }
    private static float spin(float now,float age,float period) {
        float frozen=age<0?now:now-age+Math.min(age,8)-Math.min(age,8)*Math.min(age,8)/16;
        return (frozen%period)*(Mth.TWO_PI/period);
    }
    @Override public void render(EchoCore core,float yaw,float partial,PoseStack ps,MultiBufferSource buffers,int light) {
        long gameTime=core.level().getGameTime();
        float now=(gameTime%111600)+partial;
        float age=core.openingAge()<0?-1:core.openingAge()+partial;
        ps.pushPose();
        // Entity origin is .90 blocks above water, at the lower edge of the final target.
        ps.translate(0,-.90,0);
        ps.mulPose(Axis.YP.rotationDegrees(-core.getYRot()));
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        int lit=LightTexture.pack(Math.max(9,LightTexture.block(light)),Math.max(5,LightTexture.sky(light)));
        for(Part p:parts) {
            boolean mirror=p.bone.equals("true_mirror");
            if(mirror && age<36 || age>=40 && (p.bone.startsWith("orbit_") || p.bone.equals("shell_front"))) continue;
            float luminosity=p.glow && age>=0 && p.bone.startsWith("orbit_") ? 1-.88f*smooth(7,18,age) : 1;
            ps.pushPose();
            transform(ps,p,now,age);
            draw(p,ps.last(),out,p.glow||mirror?LightTexture.FULL_BRIGHT:lit,luminosity);
            ps.popPose();
        }
        if(age<22) MirrorEffects.blackWater(ps,buffers,now,age<0?1:1-smooth(2,22,age));
        ps.popPose();
        super.render(core,yaw,partial,ps,buffers,light);
    }
    private void transform(PoseStack p,Part part,float time,float age) {
        String bone=part.bone; float[] v=part.pivot;
        p.translate(v[0],v[1],v[2]);
        if(age>=0) {
            var track=unseal.get(bone);
            if(track!=null) {
                int frame=Math.min(track.length-1,(int)age);
                float[] a=track[frame], b=track[Math.min(frame+1,track.length-1)];
                float t=age-(int)age;
                p.translate(Mth.lerp(t,a[0],b[0]),Mth.lerp(t,a[1],b[1]),Mth.lerp(t,a[2],b[2]));
                p.mulPose(new Quaternionf(a[3],a[4],a[5],a[6]).slerp(new Quaternionf(b[3],b[4],b[5],b[6]),t));
                p.scale(Math.max(.0001f,Mth.lerp(t,a[7],b[7])),Math.max(.0001f,Mth.lerp(t,a[8],b[8])),Math.max(.0001f,Mth.lerp(t,a[9],b[9])));
            }
        }
        if(bone.startsWith("orbit_")) {
            boolean outer=bone.startsWith("orbit_outer");
            p.mulPose(new Quaternionf().rotationAxis(spin(time,age,outer?360:620)*(outer?1:-1),outer?OUTER:CROSS));
        } else if(bone.startsWith("shard_")) {
            int i=bone.charAt(bone.length()-1)-'0';
            p.translate(0,.095*Mth.sin(time*.024f+i*2.4f),0);
            p.mulPose(Axis.YP.rotationDegrees(18*Mth.sin(time*.017f+i)));
            p.mulPose(Axis.ZP.rotationDegrees(8*Mth.sin(time*.029f+i)));
        }
        p.translate(-v[0],-v[1],-v[2]);
    }
    private static void draw(Part part,PoseStack.Pose pose,VertexConsumer out,int light,float lum) {
        int c=Math.round(255*lum);
        for(int i=0;i<part.faces.length;i++) {
            int[] face=part.faces[i];float[] normal=part.normals[i];
            for(int j=0;j<4;j++) {
                float[] point=part.v[face[Math.min(j,2)]];
                out.vertex(pose.pose(),point[0],point[1],point[2]).color(c,c,c,255).uv(point[3],point[4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.normal(),normal[0],normal[1],normal[2]).endVertex();
            }
        }
    }
    @Override public ResourceLocation getTextureLocation(EchoCore e) {return ATLAS;}
}
