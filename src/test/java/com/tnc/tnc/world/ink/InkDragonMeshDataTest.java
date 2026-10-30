package com.tnc.tnc.world.ink;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonMeshDataTest {
    @Test void exactApprovedMeshLoadsAndLodsOnlyRemoveDetails()throws Exception{
        try(var in=Files.newInputStream(Path.of("src/main/resources/assets/tnc/meshes/ink_celestial_dragon.mesh"))){
            var mesh=InkDragonMeshData.read(in);assertEquals(11,mesh.groups().size());assertTrue(mesh.triangles(0)>140414&&mesh.triangles(0)<180000);
            assertTrue(mesh.groups().stream().anyMatch(g->g.name().equals("jaw")&&g.vertices(0)>0));
            assertTrue(mesh.triangles(2)<mesh.triangles(1)&&mesh.triangles(1)<mesh.triangles(0));
            for(var group:mesh.groups())for(int lod=0;lod<3;lod++)assertEquals(0,group.vertices(lod)%3);
        }
    }
    @Test void distanceLodsHaveExplicitBoundaries(){assertEquals(0,InkDragonMeshData.lod(95));assertEquals(1,InkDragonMeshData.lod(96));assertEquals(2,InkDragonMeshData.lod(192));}
    @Test void runtimePoseIsExtendedAndLowNotTheOldVerticalCoil()throws Exception{
        try(var in=Files.newInputStream(Path.of("src/main/resources/assets/tnc/meshes/ink_celestial_dragon.mesh"))){
            var mesh=InkDragonMeshData.read(in);double minX=999,maxX=-999,minY=999,maxY=-999;
            for(var g:mesh.groups()){
                var b=java.nio.ByteBuffer.wrap(g.levels().get(0)).order(java.nio.ByteOrder.BIG_ENDIAN);
                while(b.hasRemaining()){float x=b.getFloat(),y=b.getFloat();b.getFloat();b.getInt();minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            }
            assertTrue(maxX-minX>70);assertTrue(maxY-minY<12);assertTrue(mesh.triangles(2)<40000);
        }
    }
    @Test void rejectsWrongMagicTruncationAndMemoryBomb()throws Exception{
        assertThrows(IOException.class,()->InkDragonMeshData.read(new ByteArrayInputStream(new byte[64])));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0x49444D31);out.writeInt(1);out.writeInt(1);out.writeInt(3);out.writeUTF("body");for(int i=0;i<3;i++)out.writeFloat(0);
        out.writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class,()->InkDragonMeshData.read(new ByteArrayInputStream(bytes.toByteArray())));
        out.flush();assertThrows(IOException.class,()->InkDragonMeshData.read(new ByteArrayInputStream(bytes.toByteArray(),0,16)));
    }
}
