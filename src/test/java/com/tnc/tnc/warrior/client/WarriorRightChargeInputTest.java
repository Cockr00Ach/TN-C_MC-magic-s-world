package com.tnc.tnc.warrior.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tnc.tnc.warrior.client.WarriorRightChargeInput.Action.*;
class WarriorRightChargeInputTest {
    @Test void rightClickWaitsForThresholdThenPulsesAndReleasesOnce(){
        var s=new WarriorRightChargeInput();assertTrue(s.begin(100));assertFalse(s.begin(101));
        assertEquals(NONE,s.update(true,true,109));assertEquals(HOLD,s.update(true,true,110));
        assertEquals(NONE,s.update(true,true,119));assertEquals(KEEPALIVE,s.update(true,true,120));
        assertEquals(RELEASE,s.update(false,true,121));assertEquals(NONE,s.update(false,true,122));
    }
    @Test void shortRightClickOnlyReleasesPendingCharge(){var s=new WarriorRightChargeInput();s.begin(0);assertEquals(RELEASE,s.update(false,true,2));assertFalse(s.active());}
    @Test void pressingAnotherAttackCannotRestartChargeUntilMouseReleased(){
        var s=new WarriorRightChargeInput();s.begin(0);s.cancel(true);assertFalse(s.begin(3));
        assertEquals(NONE,s.update(true,true,30));s.update(false,true,31);assertTrue(s.begin(32));
    }
    @Test void focusOrGuiLossCancelsRatherThanReleasingAnAttack(){
        var s=new WarriorRightChargeInput();s.begin(0);assertEquals(CANCEL,s.update(true,false,15));assertFalse(s.begin(16));
        s.update(false,false,17);assertTrue(s.begin(18));
    }
}
