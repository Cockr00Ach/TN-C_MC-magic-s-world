package com.tnc.tnc.world.ink;

import java.nio.*;
import java.util.*;

/** Lossless topology: attribute-welded vertices and original-diagonal surface quads. */
public final class InkDragonTopology {
    private InkDragonTopology() {}
    private record Key(int x,int y,int z,int rgba) {}
    public record Mesh(float[] xyz,int[] rgba,int[] samples,int[] corners,float[] normals,int originalTriangles,int pairedQuads) {
        public int vertices(){return rgba.length;}
        public int quads(){return corners.length/4;}
    }
    public static Mesh build(InkDragonMeshData.Group group,int lod) {
        var input=ByteBuffer.wrap(group.levels().get(lod)).order(ByteOrder.BIG_ENDIAN);
        int count=group.vertices(lod);var keys=new HashMap<Key,Integer>();var unique=new ArrayList<Key>();int[] ids=new int[count];
        for(int i=0;i<count;i++){
            var key=new Key(input.getInt(),input.getInt(),input.getInt(),input.getInt());
            Integer id=keys.get(key);if(id==null){id=unique.size();unique.add(key);keys.put(key,id);}ids[i]=id;
        }
        float[] xyz=new float[unique.size()*3];int[] rgba=new int[unique.size()],samples=new int[unique.size()];
        for(int i=0;i<unique.size();i++){
            var key=unique.get(i);xyz[i*3]=Float.intBitsToFloat(key.x);xyz[i*3+1]=Float.intBitsToFloat(key.y);xyz[i*3+2]=Float.intBitsToFloat(key.z);
            rgba[i]=key.rgba;samples[i]=Math.max(0,Math.min(2048,Math.round((xyz[i*3]+32)*16)));
        }
        int[] corners=new int[count/3*4];float[] normals=new float[count];int quads=0,paired=0;
        for(int i=0;i<count;){
            int a=ids[i],b=ids[i+1],c=ids[i+2];
            // Only combine (a,b,c)+(a,c,d). The rasterizer keeps the exact authored
            // diagonal. No deleted faces, rounded positions or flattened scales.
            boolean pair=i+5<count&&a==ids[i+3]&&c==ids[i+4]&&ids[i+5]!=a&&ids[i+5]!=b&&ids[i+5]!=c;
            int k=quads*4;corners[k]=a;corners[k+1]=b;corners[k+2]=c;corners[k+3]=pair?ids[i+5]:c;
            int x=a*3,y=b*3,z=c*3;float ax=xyz[y]-xyz[x],ay=xyz[y+1]-xyz[x+1],az=xyz[y+2]-xyz[x+2];
            float bx=xyz[z]-xyz[x],by=xyz[z+1]-xyz[x+1],bz=xyz[z+2]-xyz[x+2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            int n=quads*3;normals[n]=len>1e-7?nx/len:0;normals[n+1]=len>1e-7?ny/len:1;normals[n+2]=len>1e-7?nz/len:0;
            if(pair)paired++;i+=pair?6:3;quads++;
        }
        return new Mesh(xyz,rgba,samples,Arrays.copyOf(corners,quads*4),Arrays.copyOf(normals,quads*3),count/3,paired);
    }
}
