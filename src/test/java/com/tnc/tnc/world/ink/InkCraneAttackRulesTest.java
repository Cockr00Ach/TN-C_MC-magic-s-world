package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class InkCraneAttackRulesTest {
    @Test void twelveLargePaintingsHaveActualGapsAndWiderRadius() {
        assertEquals(12,InkCraneAttackRules.PAINTING_FACES);
        double spacing=2*InkCraneAttackRules.PAINTING_RADIUS*Math.sin(Math.PI/12);
        assertTrue(spacing>42,"Even cloud layers have a gap between adjacent faces");
        assertTrue(InkCraneAttackRules.PAINTING_WIDTH>25);
        assertTrue(InkCraneAttackRules.PAINTING_HEIGHT>16);
        assertTrue(InkCraneAttackRules.EXPLOSION_VISUAL_SCALE>1);
    }
    @Test void attractionIsStrongBoundedAndOnlyDuringBallGrowth() {
        assertEquals(0,InkCraneAttackRules.pullAcceleration(31,8,0));
        assertEquals(0,InkCraneAttackRules.pullAcceleration(100,8,0));
        assertEquals(0,InkCraneAttackRules.pullAcceleration(80,24,0));
        assertEquals(0,InkCraneAttackRules.pullAcceleration(80,.2,0));
        assertEquals(0,InkCraneAttackRules.pullAcceleration(80,8,.46));
        for(int age=32;age<100;age++)for(double speed=-.5;speed<.6;speed+=.025) {
            double a=InkCraneAttackRules.pullAcceleration(age,8,speed);
            assertTrue(a>=0 && a<=InkCraneAttackRules.PULL_ACCELERATION);
            assertTrue(speed+a<=Math.max(speed,InkCraneAttackRules.PULL_SPEED_CAP)+1e-9);
        }
        assertTrue(InkCraneAttackRules.pullAcceleration(80,8,0)>.12);
        assertEquals(.45,InkCraneAttackRules.PULL_SPEED_CAP,.001);
    }
    @Test void barrageReleasesExactlyTenSeparatedPhantomsAfterWindup() {
        var shots = new ArrayList<Integer>();
        for (int tick = -10; tick < InkCraneAttackRules.duration(1) + 30; tick++) {
            if (InkCraneAttackRules.firesAt(tick)) shots.add(tick);
        }
        assertEquals(10, shots.size());
        assertEquals(24, shots.get(0));
        assertEquals(78, shots.get(9));
        for (int i = 1; i < shots.size(); i++) assertEquals(6, shots.get(i) - shots.get(i-1));
    }

    @Test void longWingHitsOnlyItsForwardSectorAndReach() {
        assertTrue(InkCraneAttackRules.inSweep(0, 13.9, 0, 1));
        assertTrue(InkCraneAttackRules.inSweep(7, 3, 0, 1));
        assertFalse(InkCraneAttackRules.inSweep(0, -3, 0, 1));
        assertFalse(InkCraneAttackRules.inSweep(14, 0, 0, 1));
        assertFalse(InkCraneAttackRules.inSweep(0, 14.1, 0, 1));
        assertTrue(InkCraneAttackRules.inSweep(0, 0, 0, 1));
    }

    @Test void blackBallChargesAfterImpactAndGrowsContinuouslyUntilExplosion() {
        assertEquals(.36, InkCraneAttackRules.ballRadius(0), 1e-6);
        assertEquals(.36, InkCraneAttackRules.ballRadius(32), 1e-6);
        double previous = 0;
        for (int age = 0; age <= 100; age++) {
            double radius = InkCraneAttackRules.ballRadius(age);
            assertTrue(radius >= previous);
            assertTrue(radius <= 12.061);
            previous = radius;
        }
        assertEquals(12.06, InkCraneAttackRules.ballRadius(100), 1e-6);
        assertTrue(InkCraneAttackRules.SKY_RELEASE + InkCraneAttackRules.DIVE_TICKS + InkCraneAttackRules.BALL_EXPLODE + 18
                < InkCraneAttackRules.duration(3));
    }

    @Test void allFourAttacksHaveBoundedDurations() {
        for (int move = 1; move <= 4; move++) {
            assertTrue(InkCraneAttackRules.duration(move) >= 60);
            assertTrue(InkCraneAttackRules.duration(move) < 240);
        }
        assertThrows(IllegalArgumentException.class, () -> InkCraneAttackRules.duration(0));
    }

    @Test void threeSeparatedSweepWindowsMatchTheAlternatingAnimation() {
        for(int pass=0;pass<3;pass++) {
            int start=82+pass*26;
            assertEquals(pass,InkCraneAttackRules.sweepPass(start));
            assertEquals(pass,InkCraneAttackRules.sweepPass(start+14));
            assertEquals(-1,InkCraneAttackRules.sweepPass(start+15));
        }
        assertEquals(-1,InkCraneAttackRules.sweepPass(81));
        assertEquals(-1,InkCraneAttackRules.sweepPass(160));
        assertEquals(.84F,InkCraneAttackRules.BODY_SCALE);
    }

    @Test void avatarCannotSweepBeforeTheCryAndFormationFinish() {
        assertTrue(InkCraneAttackRules.ROAR_TICKS >= 40);
        assertTrue(InkCraneAttackRules.SWEEP_HIT_START > InkCraneAttackRules.ROAR_TICKS
                + InkCraneAttackRules.AVATAR_FORM_TICKS);
        assertTrue(InkCraneAttackRules.SWEEP_HIT_END < InkCraneAttackRules.duration(2));
    }
}
