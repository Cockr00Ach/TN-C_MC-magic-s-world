package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorComboAnimations;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorPerformanceTest {
    @Test void combosHaveDistinctTrajectoriesRatherThanJustDistinctNames(){
        var trajectories=new HashSet<String>();
        for(var skill:WarriorCombos.all()){
            var clip=WarriorComboAnimations.clip(skill.animation());var key=new StringBuilder();
            for(float phase:new float[]{.1f,.22f,.34f,.46f,.58f,.78f})
                key.append(java.util.Arrays.toString(clip.rotation("bone",phase*clip.lengthSeconds()))).append(java.util.Arrays.toString(clip.rotation("armRight",phase*clip.lengthSeconds())));
            assertTrue(trajectories.add(key.toString()),skill.id()+" reuses another combo trajectory");
        }
    }
    @Test void heavyWindupAndStrikeKeepBothHandsTogether(){
        for(var animation: new WarriorAnimation[]{WarriorAnimation.GS_SMASH,WarriorAnimation.GS_RIFT,
                WarriorAnimation.HM_CRUSH,WarriorAnimation.HM_RUIN}){
            var clip=WarriorComboAnimations.clip(animation);
            for(float phase:new float[]{.10f,.22f,.34f,.46f,.58f,.78f}){
                var right=wrist(clip.rotation("armRight",phase*clip.lengthSeconds()),-5);
                var left=wrist(clip.rotation("armLeft",phase*clip.lengthSeconds()),5);
                assertTrue(right.distance(left)<.02f,animation+" support grip at "+phase);
            }
        }
    }
    private static org.joml.Vector3f wrist(float[] degrees,float shoulder){
        float rad=(float)(Math.PI/180);
        return new org.joml.Matrix4f().rotationZYX(degrees[2]*rad,degrees[1]*rad,degrees[0]*rad)
                .transformPosition(new org.joml.Vector3f(0,10,0)).add(shoulder,0,0);
    }
    @Test void everyComboHasItsOwnAuthoredFullBodyMotion(){
        var unique=new HashSet<WarriorAnimation>();
        for(var skill:WarriorCombos.all()){
            assertTrue(unique.add(skill.animation()),skill.id()+" must have its own animation");
            var clip=WarriorComboAnimations.clip(skill.animation());assertNotNull(clip,skill.id());
            assertTrue(clip.moves("armRight")&&clip.moves("armLeft")&&clip.moves("legLeft")&&clip.moves("legRight"),skill.id());
        }
        assertEquals(29,unique.size());
    }
    @Test void heavyWeaponsTakeShortStepsWhileBowAndSpearReposition(){
        for(var skill:WarriorCombos.forFamily(WarriorWeaponFamily.GREATSWORD))assertTrue(skill.dash()<=.65);
        var bow=WarriorCombos.byId("bow_combo_3");assertEquals(6,bow.backstep());
        var spear=WarriorCombos.byId("spear_combo_2");assertEquals(2.8,spear.dash());
    }
    @Test void scytheHasDistinctMultiTurnMovesWithoutChangingItsUnlockOrDamage(){
        for(var s:WarriorCombos.forFamily(WarriorWeaponFamily.GREATSWORD)){
            var scythe=WarriorPerformance.forId(s,"tnc:doom_scythe");
            assertNotEquals(s.animation(),scythe.animation());assertEquals(s.codexId(),scythe.codexId());
            assertEquals(s.damageMultiplier(),scythe.damageMultiplier());assertEquals(s.cooldownTicks(),scythe.cooldownTicks());
            var clip=WarriorComboAnimations.clip(scythe.animation());assertNotNull(clip);
            if(s.slot()>=4)assertTrue(clip.rotation("bone",clip.lengthSeconds())[1]>=360);
        }
    }
    @Test void spearExtensionReleasesSupportHandAndChargeHoldsTheFullDraw(){
        var clip=WarriorComboAnimations.clip(WarriorAnimation.SPEAR_DRAGON);
        assertTrue(clip.rotation("armRight",clip.lengthSeconds()*.58f)[0]<-80);
        assertTrue(clip.rotation("armLeft",clip.lengthSeconds()*.58f)[0]>0);
        var charge=WarriorComboAnimations.clip(WarriorAnimation.BOW_CHARGE);
        assertTrue(charge.rotation("armLeft",charge.lengthSeconds())[1]<=-90);
        assertTrue(charge.rotation("armRight",charge.lengthSeconds())[0]<=-85);
        assertEquals(0,BowChargeRules.power(-10));assertEquals(1,BowChargeRules.power(Long.MAX_VALUE));
        assertEquals(3,BowChargeRules.arrows(WarriorMove.RIGHT));assertEquals(1,BowChargeRules.arrows(WarriorMove.LEFT));
    }
}
