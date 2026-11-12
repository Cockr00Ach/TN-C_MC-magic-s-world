package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorHeavyGrowthTest {
    @Test void lightWeaponPhantomsGrowThroughTheAttackAndUseTheSameEightfoldPeak(){
        for(var a:new WarriorAnimation[]{WarriorAnimation.SLASH_LEFT,WarriorAnimation.SWORD_BREAKER,
                WarriorAnimation.SWORD_SPIN,WarriorAnimation.SPEAR_BREAK,WarriorAnimation.SPEAR_FLOW}){
            assertEquals(1,WarriorHeavyGrowth.phantomScale(a,false,0,10));
            assertEquals(8,WarriorHeavyGrowth.phantomScale(a,false,.6f,10),1e-6);
            assertEquals(1,WarriorHeavyGrowth.phantomScale(a,false,1,10),1e-6);
            assertEquals(1,WarriorHeavyGrowth.phantomScale(a,true,.6f,10));
        }
        assertEquals(1,WarriorHeavyGrowth.phantomScale(WarriorAnimation.SWORD_STANCE,false,.5f,10));
    }
    @Test void temperingRaisesPeakLinearlyFromThreeToEightWithoutUsingTalentCap(){
        assertEquals(3,WarriorHeavyGrowth.peak(0));
        assertEquals(5.5f,WarriorHeavyGrowth.peak(5000));
        assertEquals(8,WarriorHeavyGrowth.peak(10000));
        assertEquals(3,WarriorHeavyGrowth.peak(Integer.MIN_VALUE));
        assertEquals(8,WarriorHeavyGrowth.peak(Integer.MAX_VALUE));
        float previous=3;
        for(int points=0;points<=10000;points++){
            float peak=WarriorHeavyGrowth.peak(points);assertTrue(peak>=previous && peak<=8);previous=peak;
        }
    }
    @Test void eightfoldGrowthStillPreservesIdleAndRecoveryAcrossEveryHeavyAttack(){
        for(var family:new WarriorWeaponFamily[]{WarriorWeaponFamily.GREATSWORD,WarriorWeaponFamily.HAMMER})
            for(var animation:WarriorAnimation.values())if(WarriorHeavyGrowth.applies(animation,family,false)){
                assertEquals(1,WarriorHeavyGrowth.scale(animation,family,false,0,10));
                assertEquals(8,WarriorHeavyGrowth.scale(animation,family,false,WarriorHeavyGrowth.liftEnd(animation),10),.00001);
                assertEquals(1,WarriorHeavyGrowth.scale(animation,family,false,1,10),.00001);
                for(int i=0;i<=100;i++){
                    float size=WarriorHeavyGrowth.scale(animation,family,false,i/100f,1000);
                    assertTrue(Float.isFinite(size) && size>=1 && size<=8);
                }
            }
    }
    @Test void everyHeavyAttackGrowsTheSolidWeaponAndRecoversWithoutOvershooting(){
        for(var family:new WarriorWeaponFamily[]{WarriorWeaponFamily.GREATSWORD,WarriorWeaponFamily.HAMMER}){
            assertTrue(WarriorHeavyGrowth.usesEntityGrowth(family),"heavy family always bypasses phantoms, even while idle");
            for(var animation:new WarriorAnimation[]{WarriorAnimation.SLASH_LEFT,WarriorAnimation.SLASH_RIGHT,
                    WarriorAnimation.SLASH_HORIZONTAL,WarriorAnimation.OVERHEAD_SMASH,WarriorAnimation.GROUND_QUAKE,
                    WarriorAnimation.GS_SWEEP,WarriorAnimation.GS_SMASH,WarriorAnimation.GS_WALL,WarriorAnimation.GS_RIFT,
                    WarriorAnimation.GS_ADVANCE,WarriorAnimation.HM_CRUSH,WarriorAnimation.HM_QUAKE,WarriorAnimation.HM_CUT,
                    WarriorAnimation.HM_RUIN,WarriorAnimation.HM_DRIVE,WarriorAnimation.SCYTHE_POUNCE,
                    WarriorAnimation.SCYTHE_REAP,WarriorAnimation.SCYTHE_HOOK,WarriorAnimation.SCYTHE_EXECUTE,WarriorAnimation.SCYTHE_WHIRL}){
                assertTrue(WarriorHeavyGrowth.applies(animation,family,false),animation.name());
                assertEquals(1,WarriorHeavyGrowth.scale(animation,family,false,0));
                assertEquals(3,WarriorHeavyGrowth.scale(animation,family,false,WarriorHeavyGrowth.liftEnd(animation)),.00001);
                assertEquals(3,WarriorHeavyGrowth.scale(animation,family,false,.60f),.00001);
                assertEquals(1,WarriorHeavyGrowth.scale(animation,family,false,1),.00001);
                float last=1;
                for(int i=0;i<=1000;i++){
                    float size=WarriorHeavyGrowth.scale(animation,family,false,i/1000f);
                    assertTrue(Float.isFinite(size) && size>=1 && size<=3);
                    assertTrue(Math.abs(size-last)<.025f,"growth and recovery must stay continuous");last=size;
                }
            }
        }
    }
    @Test void lightWeaponsAndNonAttackPosesKeepTheirCurrentBehaviour(){
        for(var family:new WarriorWeaponFamily[]{WarriorWeaponFamily.LONGSWORD,WarriorWeaponFamily.SPEAR,WarriorWeaponFamily.BOW}){
            assertFalse(WarriorHeavyGrowth.usesEntityGrowth(family));
            for(var a:WarriorAnimation.values())assertEquals(1,WarriorHeavyGrowth.scale(a,family,false,.4f));
        }
        for(var a:new WarriorAnimation[]{WarriorAnimation.GS_STANCE,WarriorAnimation.HM_STANCE,
                WarriorAnimation.GS_CHARGE,WarriorAnimation.HM_CHARGE,WarriorAnimation.SCYTHE_GUARD,WarriorAnimation.EVADE})
            assertEquals(1,WarriorHeavyGrowth.scale(a,WarriorWeaponFamily.GREATSWORD,false,.4f));
        assertEquals(1,WarriorHeavyGrowth.scale(WarriorAnimation.GS_SWEEP,WarriorWeaponFamily.GREATSWORD,true,.4f));
        assertEquals(1,WarriorHeavyGrowth.scale(null,WarriorWeaponFamily.GREATSWORD,false,.4f));
        assertEquals(1,WarriorHeavyGrowth.scale(WarriorAnimation.HM_DRIVE,WarriorWeaponFamily.HAMMER,false,Float.NaN));
    }
}
