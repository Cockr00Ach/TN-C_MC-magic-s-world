package com.tnc.tnc.boss.fire.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossBarAnimationTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void enteringAnExistingFightDoesNotInventDamage() {
        BossBarAnimation animation = normal(0.37F);
        BossBarAnimation.Frame frame = normalFrame(animation, 0.37F, 0);
        assertEquals(0.37F, frame.progress(), EPSILON);
        assertEquals(frame.progress(), frame.trail(), EPSILON);
        assertEquals(0.0F, frame.flash());
    }

    @Test
    void damageUpdatesRealHealthImmediatelyThenTheTrailCoolsAway() {
        BossBarAnimation animation = normal(1.0F);
        BossBarAnimation.Frame hit = normalFrame(animation, 0.4F, 1);
        assertEquals(0.4F, hit.progress(), EPSILON);
        assertEquals(1.0F, hit.trail(), EPSILON);
        assertTrue(hit.flash() > 0);
        assertEquals(1.0F, normalFrame(animation, 0.4F, 5).trail(), EPSILON);
        BossBarAnimation.Frame fading = normalFrame(animation, 0.4F, 10);
        assertTrue(fading.trail() < 1.0F && fading.trail() > fading.progress());
        BossBarAnimation.Frame settled = normalFrame(animation, 0.4F, 40);
        assertEquals(settled.progress(), settled.trail(), EPSILON);
        assertEquals(0.0F, settled.flash());
    }

    @Test
    void skippedFramesDoNotExtendTheDamageTrail() {
        BossBarAnimation sparse = normal(1.0F);
        BossBarAnimation frequent = normal(1.0F);
        normalFrame(sparse, 0.2F, 1);
        normalFrame(frequent, 0.2F, 1);
        for (int frame = 5; frame < 60; frame++) {
            normalFrame(frequent, 0.2F, frame / 4.0);
        }
        BossBarAnimation.Frame sparseResult = normalFrame(sparse, 0.2F, 15);
        BossBarAnimation.Frame frequentResult = normalFrame(frequent, 0.2F, 15);
        assertEquals(sparseResult.trail(), frequentResult.trail(), EPSILON);
        assertEquals(sparseResult.flash(), frequentResult.flash(), EPSILON);
        assertEquals(sparseResult.flow(), frequentResult.flow(), EPSILON);
    }

    @Test
    void healingRemovesTheOldDamageTrailAndFlash() {
        BossBarAnimation animation = normal(1.0F);
        normalFrame(animation, 0.2F, 1);
        BossBarAnimation.Frame healed = normalFrame(animation, 0.65F, 2);
        assertEquals(0.65F, healed.progress(), EPSILON);
        assertEquals(healed.progress(), healed.trail(), EPSILON);
        assertEquals(0.0F, healed.flash());
    }

    @Test
    void combatStateUsesSyncedFlagsRatherThanGuessingFromHealth() {
        BossBarAnimation animation = normal(0.8F);
        BossBarAnimation.Frame transitioning = animation.update(0.8F, true, true, false, 5);
        assertTrue(transitioning.phase() > 0 && transitioning.phase() < 1);
        assertTrue(transitioning.rage() > 0 && transitioning.rage() < 1);
        BossBarAnimation.Frame rage = animation.update(0.8F, true, true, false, 10);
        assertEquals(1.0F, rage.rage());
        BossBarAnimation.Frame weak = animation.update(0.8F, true, false, true, 20);
        assertEquals(0.0F, weak.rage());
        assertEquals(1.0F, weak.weak());
        assertNotEquals(rage.lavaTint(), weak.lavaTint());
        double flowBefore = weak.flow();
        BossBarAnimation.Frame still = animation.update(0.8F, true, false, true, 20);
        assertEquals(flowBefore, still.flow());
    }

    @Test
    void zeroHealthNeverLeavesAPermanentBrightTrail() {
        BossBarAnimation animation = normal(1.0F);
        normalFrame(animation, 0.0F, 1);
        BossBarAnimation.Frame dead = normalFrame(animation, 0.0F, 60);
        assertEquals(0.0F, dead.progress());
        assertEquals(0.0F, dead.trail());
    }

    @Test
    void simultaneousBossesHaveIndependentDamageState() {
        BossBarAnimation damaged = normal(1.0F);
        BossBarAnimation untouched = normal(0.8F);
        normalFrame(damaged, 0.1F, 1);
        BossBarAnimation.Frame other = normalFrame(untouched, 0.8F, 2);
        assertEquals(other.progress(), other.trail(), EPSILON);
        assertEquals(0.0F, other.flash());
    }

    @Test
    void clockRewindResetsPresentationInsteadOfFreezingTheTrail() {
        BossBarAnimation animation = normal(1.0F);
        normalFrame(animation, 0.2F, 100);
        BossBarAnimation.Frame reset = normalFrame(animation, 0.2F, 0);
        assertEquals(reset.progress(), reset.trail(), EPSILON);
        assertEquals(0.0F, reset.flash());
    }

    private static BossBarAnimation normal(float progress) {
        return new BossBarAnimation(progress, false, false, false, 0);
    }

    private static BossBarAnimation.Frame normalFrame(BossBarAnimation animation, float progress, double tick) {
        return animation.update(progress, false, false, false, tick);
    }
}
