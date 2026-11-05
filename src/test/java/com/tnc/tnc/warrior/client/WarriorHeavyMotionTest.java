package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorHeavyMotionTest {
    private float sample(float t){return WarriorHeavyMotion.progress(WarriorAnimation.GS_SMASH,WarriorWeaponFamily.GREATSWORD,false,t);}
    @Test void heavyStrokeAcceleratesAndLingersWithoutReversingOrExtendingDuration(){
        float last=0;
        for(int i=0;i<=1000;i++){
            float next=sample(i/1000f);
            assertTrue(Float.isFinite(next) && next>=last && next<=1);
            assertTrue(next-last<.003f,"continuous pose sampling at phase boundaries");
            last=next;
        }
        assertEquals(0,sample(0));assertEquals(1,sample(1));
        assertEquals(.58f,sample(.58f),.00001);
        float windup=sample(.18f)-sample(.16f),stroke=sample(.47f)-sample(.45f),settle=sample(.69f)-sample(.67f);
        assertTrue(stroke>windup*2 && stroke>settle*3);
        assertTrue(sample(.34f)<.34f && sample(.78f)<.78f);
        assertTrue(WarriorHeavyMotion.brace(.2f)<0 && WarriorHeavyMotion.brace(.63f)>.18f);
    }
    @Test void lightWeaponsAndHeldOrDefensivePosesKeepOriginalTimeline(){
        for(float t:new float[]{0,.15f,.34f,.58f,.8f,1}){
            assertEquals(t,WarriorHeavyMotion.progress(WarriorAnimation.SLASH_LEFT,WarriorWeaponFamily.LONGSWORD,false,t));
            assertEquals(t,WarriorHeavyMotion.progress(WarriorAnimation.GS_STANCE,WarriorWeaponFamily.GREATSWORD,true,t));
            assertEquals(t,WarriorHeavyMotion.progress(WarriorAnimation.EVADE,WarriorWeaponFamily.HAMMER,false,t));
        }
        assertTrue(WarriorHeavyMotion.applies(WarriorAnimation.SLASH_LEFT,WarriorWeaponFamily.HAMMER,false));
    }
}
