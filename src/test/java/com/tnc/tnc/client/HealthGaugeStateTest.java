package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthGaugeStateTest {
    @Test void damageHoldsThenCatchesUpWithoutChangingActualHealth() {
        var s=new HealthGaugeState();s.tick(20,20);s.tick(8,20);
        assertEquals(.4F,s.fraction());assertEquals(1,s.trailFraction());
        for(int i=0;i<9;i++)s.tick(8,20);assertEquals(1,s.trailFraction());
        for(int i=0;i<40;i++)s.tick(8,20);assertEquals(.4F,s.trailFraction());
    }
    @Test void healingAndEquipmentMaximumChangesDoNotProduceStaleDamage() {
        var s=new HealthGaugeState();s.tick(8,20);s.tick(18,20);assertEquals(.9F,s.trailFraction());
        s.tick(60,120);assertEquals(.5F,s.fraction());assertEquals(.5F,s.trailFraction());
    }
    @Test void deathAndMalformedValuesStayWithinTheTrough() {
        var s=new HealthGaugeState();s.tick(Float.NaN,0);assertEquals(0,s.fraction());
        s.tick(900,20);assertEquals(1,s.fraction());s.tick(-1,20);assertEquals(0,s.fraction());
    }
}
