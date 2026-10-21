package com.tnc.tnc.world.ink;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Platform-independent bounded decoder; no client or OpenGL classes on a dedicated server. */
public record InkDragonMeshData(List<Group> groups) {
    public static final int STRIDE=16, LODS=3;
    public record Group(String name,float x,float y,float z,List<byte[]> levels){
        public int vertices(int lod){return levels.get(lod).length/STRIDE;}
    }
    public int triangles(int lod){return groups.stream().mapToInt(g->g.vertices(lod)/3).sum();}
    public static int lod(double distance){return distance<96?0:distance<192?1:2;}
    public static InkDragonMeshData read(InputStream source)throws IOException{
        var in=new DataInputStream(new BufferedInputStream(source));
        if(in.readInt()!=0x49444D31||in.readInt()!=1)throw new IOException("Unsupported ink dragon mesh header");
        int count=in.readInt(),lods=in.readInt();if(count<1||count>32||lods!=LODS)throw new IOException("Invalid mesh batch count");
        List<Group> groups=new ArrayList<>();Set<String> names=new HashSet<>();long total=0;
        for(int i=0;i<count;i++){
            String name=in.readUTF();float x=in.readFloat(),y=in.readFloat(),z=in.readFloat();
            if(name.length()>80||!names.add(name)||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IOException("Invalid mesh group");
            List<byte[]> levels=new ArrayList<>();
            for(int lod=0;lod<LODS;lod++){
                int vertices=in.readInt();if(vertices<0||vertices>1500000||vertices%3!=0)throw new IOException("Invalid triangle count");
                int bytes=Math.multiplyExact(vertices,STRIDE);total+=bytes;if(total>32*1024*1024)throw new IOException("Mesh exceeds memory budget");
                byte[] data=in.readNBytes(bytes);if(data.length!=bytes)throw new EOFException("Incomplete dragon mesh");
                ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                while(b.hasRemaining()){
                    for(int a=0;a<3;a++){float v=b.getFloat();if(!Float.isFinite(v)||Math.abs(v)>256)throw new IOException("Invalid mesh position");}
                    b.getInt();
                }
                levels.add(data);
            }
            groups.add(new Group(name,x,y,z,List.copyOf(levels)));
        }
        if(in.read()!=-1)throw new IOException("Unexpected mesh trailing data");
        return new InkDragonMeshData(List.copyOf(groups));
    }
}
