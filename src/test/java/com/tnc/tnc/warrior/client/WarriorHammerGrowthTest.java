package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import com.tnc.tnc.warrior.WarriorGripGeometry;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorHammerGrowthTest {
    @Test void genericHammerSmashActuallyLiftsBeforeTheGiantDownstroke(){
        for(var a:new WarriorAnimation[]{WarriorAnimation.OVERHEAD_SMASH,WarriorAnimation.GROUND_QUAKE}){
            float start=WarriorHammerGrowth.fallbackPose(a,0,false).rightArmX();
            float raised=WarriorHammerGrowth.fallbackPose(a,.34f,false).rightArmX();
            float struck=WarriorHammerGrowth.fallbackPose(a,.60f,false).rightArmX();
            assertTrue(raised<start-2 && struck>raised+2);
            assertEquals(3,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,.34f));
            assertEquals(3,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,.60f));
        }
    }
    @Test void hammerReachesGiantSizeBeforeDownstrokeAndShrinksOnlyDuringRecovery(){
        for(var a:new WarriorAnimation[]{WarriorAnimation.HM_CRUSH,WarriorAnimation.HM_QUAKE,WarriorAnimation.HM_RUIN,
                WarriorAnimation.OVERHEAD_SMASH,WarriorAnimation.GROUND_QUAKE}){
            float end=WarriorHammerGrowth.liftEnd(a),previous=1;
            for(int i=0;i<=100;i++){
                float scale=WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,end*i/100);
                assertTrue(scale>=previous && scale<=3);previous=scale;
            }
            assertEquals(3,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,end));
            assertEquals(3,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,.65f));
            assertTrue(WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,.85f)<3);
            assertEquals(1,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,1));
        }
    }
    @Test void unrelatedAttacksHoldsAndInvalidProgressNeverLeaveAnEnlargedWeapon(){
        for(var family:WarriorWeaponFamily.values())if(family!=WarriorWeaponFamily.HAMMER)
            assertEquals(1,WarriorHammerGrowth.scale(WarriorAnimation.HM_CRUSH,family,false,.5f));
        for(var a:new WarriorAnimation[]{WarriorAnimation.HM_DRIVE,WarriorAnimation.HM_STANCE,WarriorAnimation.SLASH_LEFT})
            assertEquals(1,WarriorHammerGrowth.scale(a,WarriorWeaponFamily.HAMMER,false,.5f));
        assertEquals(1,WarriorHammerGrowth.scale(WarriorAnimation.HM_QUAKE,WarriorWeaponFamily.HAMMER,true,.5f));
        assertEquals(1,WarriorHammerGrowth.scale(WarriorAnimation.HM_QUAKE,WarriorWeaponFamily.HAMMER,false,Float.NaN));
    }
    @Test void enlargedHeadKeepsTheGripFixedForBothHandsAndEveryIntermediateSize(){
        for(int sign:new int[]{-1,1})for(float scale=1;scale<=10;scale+=.1f){
            var mount=new Matrix4f().translation(.4f*sign,1.3f,-.6f).rotateXYZ(.7f,-.9f*sign,.3f);
            var item=new Matrix4f().translation(.05f*sign,.22f,.02f).rotateXYZ(0,-1.57f*sign,.6f).scale(1.7f);
            var anchor=WarriorGripGeometry.anchor(item,.1875f,.1875f,.5f);
            var before=mount.transformPosition(new Vector3f(anchor));
            var correction=WarriorGripGeometry.compensation(anchor,scale);
            var after=new Matrix4f(mount).translate(correction).scale(scale).transformPosition(new Vector3f(anchor));
            assertEquals(0,before.distance(after),3e-6);
        }
    }
}
