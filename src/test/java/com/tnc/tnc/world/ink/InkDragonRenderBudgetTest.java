package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class InkDragonRenderBudgetTest {
    @Test void closeCameraRestoresEveryAuthoredScaleAndFilament() {
        for(String group:new String[]{"head","jaw","body","whiskers","tail","mane"}){
            assertEquals(0,InkDragonRenderBudget.lod(group,0,false));
            assertEquals(0,InkDragonRenderBudget.lod(group,191,false));
            assertEquals(1,InkDragonRenderBudget.lod(group,192,false));
            assertEquals(2,InkDragonRenderBudget.lod(group,320,false));
            assertEquals(1,InkDragonRenderBudget.lod(group,0,true));
        }
    }

    @Test void structureNotDeletedDetailControlsCloseSubmission() throws Exception {
        try(var input=Files.newInputStream(Path.of("src/main/resources/assets/tnc/meshes/ink_celestial_dragon.mesh"))){
            var mesh=InkDragonMeshData.read(input);
            int near=mesh.groups().stream().mapToInt(g->g.vertices(InkDragonRenderBudget.lod(g.name(),0,false))/3).sum();
            assertEquals(mesh.triangles(0),near,"Visible detail must not be deleted again");
            assertTrue(mesh.triangles(2)<40000);
            System.out.println("Dragon visible triangles restored="+near+", shadow triangles="+mesh.triangles(2));
        }
    }
}
