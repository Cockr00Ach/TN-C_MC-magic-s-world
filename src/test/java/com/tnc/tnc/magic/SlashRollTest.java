package com.tnc.tnc.magic;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
class SlashRollTest {
    @Test void rapidSequenceContainsTwentyDistinctAngles() {
        var angles=new HashSet<Float>();
        for(int i=0;i<WaterSpellRules.SLASH_COUNT;i++)angles.add(WaterSpellRules.slashRoll(i));
        assertEquals(20,angles.size());assertEquals(1,WaterSpellRules.SLASH_VOLLEY_INTERVAL);
        assertEquals(20,WaterSpellRules.SLASH_CHANNEL_TICKS);
    }
    @Test void curvedLeadingFacePointsForwardAtEveryRoll() {
        for(Vec3 dir:new Vec3[]{new Vec3(0,0,1),new Vec3(1,1,0).normalize(),new Vec3(0,1,0)})
            for(int i=0;i<20;i++) {
                double roll=WaterSpellRules.slashRollRadians(WaterSpellRules.slashRoll(i),10);
                Vec3 middle=WaterSpellRules.slashArc(dir,roll,0,4);
                assertTrue(middle.dot(dir)>1,"Arc bulges towards aim");
                Vec3 left=WaterSpellRules.slashArc(dir,roll,-Math.PI/2,4);
                Vec3 right=WaterSpellRules.slashArc(dir,roll,Math.PI/2,4);
                assertEquals(0,left.dot(dir),1e-8);assertEquals(8,left.distanceTo(right),1e-8);
            }
    }
    @Test void bladesRetainTheirIndividualRollInFlight() {
        assertEquals(WaterSpellRules.slashRollRadians(72,0),WaterSpellRules.slashRollRadians(72,20));
        assertEquals(1,WaterSpellRules.slashDirections(37).size());
        assertEquals(Vec3.directionFromRotation(0,37),WaterSpellRules.slashDirections(37).get(0));
    }
}
