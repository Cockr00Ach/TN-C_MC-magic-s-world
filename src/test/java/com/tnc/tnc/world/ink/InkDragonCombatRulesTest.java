package com.tnc.tnc.world.ink;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonCombatRulesTest {
    @Test void beamDistancesAreClampedToSegment(){
        assertEquals(0,InkDragonCombatRules.segmentDistanceSquared(new Vec3(5,0,0),Vec3.ZERO,new Vec3(10,0,0)),1e-8);
        assertEquals(25,InkDragonCombatRules.segmentDistanceSquared(new Vec3(15,0,0),Vec3.ZERO,new Vec3(10,0,0)),1e-8);
        assertEquals(9,InkDragonCombatRules.segmentDistanceSquared(new Vec3(5,3,0),Vec3.ZERO,new Vec3(10,0,0)),1e-8);
        assertEquals(4,InkDragonCombatRules.segmentDistanceSquared(new Vec3(0,2,0),Vec3.ZERO,Vec3.ZERO),1e-8);
    }
}
