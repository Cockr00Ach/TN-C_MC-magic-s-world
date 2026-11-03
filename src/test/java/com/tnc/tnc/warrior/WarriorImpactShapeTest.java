package com.tnc.tnc.warrior;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorImpactShapeTest {
    @Test void allShapesAreBoundedFiniteAndDisappearAtEnd(){
        for(int kind=0;kind<5;kind++){
            assertFalse(WarriorImpactShape.build(kind,0,0x88CCFF).isEmpty());
            for(float t:new float[]{0,.25f,.5f,.9f})for(var q:WarriorImpactShape.build(kind,t,0x88CCFF)){
                assertTrue(q.alpha()>=0&&q.alpha()<=1);
                for(var v:new net.minecraft.world.phys.Vec3[]{q.a(),q.b(),q.c(),q.d()})assertTrue(Double.isFinite(v.lengthSqr())&&v.length()<4.1);
            }
            assertTrue(WarriorImpactShape.build(kind,1,0).isEmpty());
        }
        assertNotEquals(WarriorImpactShape.build(0,.25f,0),WarriorImpactShape.build(1,.25f,0));
        assertNotEquals(WarriorImpactShape.build(3,.25f,0),WarriorImpactShape.build(4,.25f,0));
    }
    @Test void exportActualMeshContactSheet() throws Exception {
        var svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='1200' height='300' viewBox='0 0 1200 300'><rect width='1200' height='300' fill='#151923'/>");
        String[] names={"HIT","PARRY","COUNTER SWEEP","HEAVY HIT","STANCE BREAK"};
        for(int k=0;k<5;k++){
            svg.append("<text x='").append(20+k*240).append("' y='28' fill='#cbd9e5' font-family='sans-serif' font-size='16'>").append(names[k]).append("</text>");
            for(var q:WarriorImpactShape.build(k,.25f,0x78C9F5)){
                svg.append("<polygon points='");
                for(var v:new net.minecraft.world.phys.Vec3[]{q.a(),q.b(),q.c(),q.d()})svg.append(120+k*240+v.x*55).append(',').append(162-v.y*55).append(' ');
                svg.append("' fill='#").append(String.format("%06x",q.color())).append("' opacity='").append(q.alpha()).append("'/>");
            }
        }
        svg.append("</svg>");var p=Path.of("docs/previews/warrior-impact-mesh-20261104.svg");Files.createDirectories(p.getParent());Files.writeString(p,svg);
    }
}
