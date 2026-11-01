package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import java.nio.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class InkDragonTopologyTest {
    @Test void reconstructedFacesKeepEveryPositionColourAndOriginalDiagonal()throws Exception {
        try(var in=Files.newInputStream(Path.of("src/main/resources/assets/tnc/meshes/ink_celestial_dragon.mesh"))){
            var data=InkDragonMeshData.read(in);int original=0,submitted=0,welded=0,paired=0;
            for(var group:data.groups())for(int lod=0;lod<3;lod++){
                var mesh=InkDragonTopology.build(group,lod);
                var expected=ByteBuffer.wrap(group.levels().get(lod)).order(ByteOrder.BIG_ENDIAN);int triangles=0;
                for(int q=0;q<mesh.quads();q++){
                    var ids=mesh.corners();int k=q*4;
                    for(int corner:new int[]{0,1,2})sameVertex(expected,mesh,ids[k+corner]);triangles++;
                    if(ids[k+2]!=ids[k+3]){for(int corner:new int[]{0,2,3})sameVertex(expected,mesh,ids[k+corner]);triangles++;}
                }
                assertFalse(expected.hasRemaining(),group.name()+" lost faces");
                assertEquals(group.vertices(lod)/3,triangles);
                for(float n:mesh.normals())assertTrue(Float.isFinite(n));
                if(lod==0){original+=group.vertices(lod);submitted+=mesh.corners().length;welded+=mesh.vertices();paired+=mesh.pairedQuads();}
            }
            assertTrue(submitted<original,"Quad submission must be below the original triangle vertex stream");
            assertTrue(welded<original/2,"Shared-vertex animation must remove repeated transform work");
            System.out.println("LOSSLESS dragon: originalTriangleCorners="+original+", formerDegenerateQuadCorners="+original/3*4+
                    ", newSubmittedCorners="+submitted+", uniqueAnimatedVertices="+welded+", pairedQuads="+paired);
        }
    }
    private void sameVertex(ByteBuffer expected,InkDragonTopology.Mesh mesh,int index){
        for(int axis=0;axis<3;axis++)assertEquals(expected.getInt(),Float.floatToRawIntBits(mesh.xyz()[index*3+axis]));
        assertEquals(expected.getInt(),mesh.rgba()[index]);
    }
    @Test void ordinaryInkEntryNeverRunsPostProcessingBeforeItemRendering()throws Exception {
        var source=Files.readString(Path.of("src/main/java/com/tnc/tnc/world/ink/client/InkScreenEffects.java"));
        assertFalse(source.contains("RenderGuiEvent.Pre"));assertFalse(source.contains("realm()"));
        assertTrue(source.contains("if(cursed())process(partial)"));
        assertTrue(source.contains("RenderSystem.depthMask(depthWrite)"));assertTrue(source.contains("RenderSystem.blendFuncSeparate(src,dst,srcAlpha,dstAlpha)"));
    }
}
