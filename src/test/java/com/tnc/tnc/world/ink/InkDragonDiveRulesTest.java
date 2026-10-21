package com.tnc.tnc.world.ink;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InkDragonDiveRulesTest {
    private final Vec3 high=new Vec3(0,50,0),target=new Vec3(12,0,6);
    @Test void phantomIsExactlyThreeTimes(){assertEquals(3,InkDragonDiveRules.PHANTOM_SCALE);}
    @Test void chargesBelowDragonBeforeDiving(){
        var a=InkDragonDiveRules.sample(60,high,target);assertEquals(high,a.orb());
        assertTrue(a.radius()>0&&a.radius()<InkDragonDiveRules.ORB_RADIUS);assertFalse(a.diving());assertTrue(a.alpha()>0);
    }
    @Test void divesToExactTargetAndDisappearsOnHit(){
        var a=InkDragonDiveRules.sample(95,high,target);assertTrue(a.diving());assertTrue(a.orb().y<50&&a.orb().y>0);
        var b=InkDragonDiveRules.sample(110,high,target);assertEquals(target,b.orb());assertEquals(0,b.alpha());assertEquals(0,b.radius());
        assertTrue(InkDragonDiveRules.sample(125,high,target).burst()>0);
    }
    @Test void neverOvershootsAndChargeDoesNotAppearEarly(){
        assertEquals(0,InkDragonDiveRules.sample(20,high,target).radius());
        for(int i=0;i<=200;i++){var f=InkDragonDiveRules.sample(i,high,target);assertTrue(f.radius()>=0&&f.radius()<=InkDragonDiveRules.ORB_RADIUS);assertTrue(f.orb().y>=0&&f.orb().y<=50);}
    }
}
