package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorWaveVisual;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorWaveRestorationTest {
    @Test void offensiveFinishersHaveWavesButGuardBreakersAndEvasionDoNot() {
        for(var s:WarriorCombos.all()) {
            if(s.family()==WarriorWeaponFamily.BOW || s.slot()==1 || s.slot()==2 || s.backsteps())assertNull(WarriorWaves.specFor(s),s.id());
            else assertNotNull(WarriorWaves.specFor(s),s.id());
        }
    }
    @Test void airCrescentHasVisibleWidthFromCastersDirectionAndAgeStartsOnReceipt() {
        var span=WarriorWaveVisual.span(new Vec3(1,0,0),new Vec3(0,1,0),1.3,false);
        assertEquals(1.3,span.x,1e-8);assertTrue(span.y>.4);
        Vec3 dir=new Vec3(0,0,1);
        var outer=WarriorWaveVisual.point(Vec3.ZERO,dir,span,0,0,false);
        var inner=WarriorWaveVisual.point(Vec3.ZERO,dir,span,0,.32,false);
        assertEquals(.32,Math.abs(outer.y-inner.y),1e-8,"The face needs screen height, not merely flight-axis depth");
        assertEquals(.5,WarriorWaveVisual.age(1000,1000,.5f));
        assertEquals(0,WarriorWaveVisual.age(0,1000,0));
    }
}
