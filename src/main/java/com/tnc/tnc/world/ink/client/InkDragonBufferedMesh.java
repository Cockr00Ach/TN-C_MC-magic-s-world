package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Indexed CPU skinning with standard, shader-compatible quad submission. */
final class InkDragonBufferedMesh {
    record Batch(InkDragonMeshData.Group group,InkDragonTopology.Mesh mesh,float[] transformed,java.nio.ByteBuffer packed){}
    static Batch decode(InkDragonMeshData.Group group,int lod){
        var mesh=InkDragonTopology.build(group,lod);return new Batch(group,mesh,new float[mesh.xyz().length],
                InkDragonBulkSubmit.available()?InkDragonVertexPacking.allocate(mesh.corners().length,OverlayTexture.NO_OVERLAY):null);
    }
    static float[][] wave(float age,boolean grounded){
        float[][] result=new float[2][2049];if(grounded)return result;
        for(int i=0;i<=2048;i++){double x=i/16.0-32,t=Math.max(0,Math.min(1,(x+4)/12)),w=t*t*(3-2*t);
            double tail=Math.max(0,Math.min(1,(x-8)/54));tail=tail*tail*(3-2*tail);
            double sin=Math.sin((x+4)*.17-age*.085)*w;result[0][i]=(float)(sin*(1.2+1.6*tail));result[1][i]=(float)(sin*1.4);
        }return result;
    }
    static void draw(Batch batch,float[][] wave,PoseStack.Pose pose,VertexConsumer out,int light,float opacity){
        var mesh=batch.mesh;var xyz=mesh.xyz();var transformed=batch.transformed;var position=new Vector3f();var normal=new Vector3f();
        for(int v=0;v<mesh.vertices();v++){
            int k=v*3,s=mesh.samples()[v];pose.pose().transformPosition(xyz[k],xyz[k+1]+wave[1][s],xyz[k+2]+wave[0][s],position);
            transformed[k]=position.x;transformed[k+1]=position.y;transformed[k+2]=position.z;
        }
        var corners=mesh.corners();var normals=mesh.normals();
        Object writer=batch.packed==null?null:InkDragonBulkSubmit.writer(out);
        if(writer!=null) {
            var packed=batch.packed;
            for(int q=0;q<mesh.quads();q++) {
                int n=q*3;pose.normal().transform(normals[n],normals[n+1],normals[n+2],normal).normalize();
                int packedNormal=InkDragonVertexPacking.normal(normal.x,normal.y,normal.z);
                for(int corner=0;corner<4;corner++) {
                    int index=q*4+corner,v=corners[index],k=v*3,p=index*InkDragonVertexPacking.STRIDE;
                    packed.putFloat(p,transformed[k]);packed.putFloat(p+4,transformed[k+1]);packed.putFloat(p+8,transformed[k+2]);
                    packed.putInt(p+12,InkDragonVertexPacking.color(mesh.rgba()[v],opacity));
                    packed.putInt(p+28,light);packed.putInt(p+32,packedNormal);
                }
            }
            InkDragonBulkSubmit.push(writer,packed,corners.length);
            return;
        }
        InkDragonBulkSubmit.legacy(corners.length);
        for(int q=0;q<mesh.quads();q++){
            int n=q*3;pose.normal().transform(normals[n],normals[n+1],normals[n+2],normal).normalize();
            for(int corner=0;corner<4;corner++){
                int v=corners[q*4+corner],k=v*3,c=mesh.rgba()[v];
                // Constant white texels, but finite UV derivatives for shader tangents.
                float u=corner==0||corner==3?.25F:.75F,vv=corner<2?.25F:.75F;
                out.vertex(transformed[k],transformed[k+1],transformed[k+2],((c>>>24)&255)/255F,((c>>>16)&255)/255F,((c>>>8)&255)/255F,
                        Math.round((c&255)*opacity)/255F,u,vv,OverlayTexture.NO_OVERLAY,light,normal.x,normal.y,normal.z);
            }
        }
    }
}
