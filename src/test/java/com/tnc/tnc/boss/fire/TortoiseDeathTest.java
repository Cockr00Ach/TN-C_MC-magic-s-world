package com.tnc.tnc.boss.fire;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TortoiseDeathTest {
    @Test void bodyIsAlreadyTransparentBeforeFinalParticlesExpire() {
        assertEquals(0, TortoiseDeath.opacity(TortoiseDeath.SCATTER_END));
        assertEquals(0, TortoiseDeath.opacity(TortoiseDeath.REMAINS_AT));
    }
    @Test void coolingNeverFlashesOrReignites() {
        float previous = 1;
        for (float age = 0; age <= TortoiseDeath.DURATION; age += .125F) {
            float glow = TortoiseDeath.glow(age);
            assertTrue(glow >= 0 && glow <= previous);
            previous = glow;
            if (age >= 60) assertEquals(0, glow);
        }
        assertEquals(1, TortoiseDeath.glow(0));
    }
    @Test void phaseOneGlowStartsAtItsUsualBrightness() {
        assertEquals(.55F, .55F * TortoiseDeath.glow(0));
        assertEquals(.275F, .55F * TortoiseDeath.glow(30), .0001F);
    }
    @Test void fadeBeginsDuringTheLastSecondOfTheBow() {
        for (int age = 0; age <= TortoiseDeath.FADE_START; age++) assertEquals(1, TortoiseDeath.opacity(age));
        assertEquals(1, TortoiseDeath.cooling(TortoiseDeath.FADE_START));
        assertTrue(TortoiseDeath.opacity(TortoiseDeath.BOW_END) < 1);
        assertTrue(TortoiseDeath.opacity(TortoiseDeath.BOW_END) > .7F);
    }
    @Test void wholeBodyFadeIsSlowMonotonicAndComplete() {
        assertEquals(116, TortoiseDeath.FADE_END - TortoiseDeath.FADE_START);
        assertEquals(.25F, TortoiseDeath.opacity(158), .0001F);
        float previous = 1;
        for (float age = 100; age <= 245; age += .125F) {
            float opacity = TortoiseDeath.opacity(age);
            assertTrue(opacity >= 0 && opacity <= previous);
            previous = opacity;
        }
        assertEquals(0, TortoiseDeath.opacity(TortoiseDeath.FADE_END));
    }
    @Test void smokeEndsBeforeTheBowAndFade() {
        for (int age = 60; age < TortoiseDeath.SCATTER_START; age++) assertEquals(0, TortoiseDeath.particleBudget(age));
        for (int age = 0; age < 60; age++) assertTrue(TortoiseDeath.particleBudget(age) <= 2);
    }
    @Test void livingRenderHasFullOpacity() {
        assertEquals(1, TortoiseDeath.opacity(-1));
        assertEquals(0, TortoiseDeath.cooling(-1));
    }
    @Test void bodyFadesInSmallStepsBeforeItDisappears() {
        float previous = 1;
        for (int tick = TortoiseDeath.FADE_START; tick <= TortoiseDeath.FADE_END; tick++) {
            float alpha = TortoiseDeath.opacity(tick);
            assertTrue(previous - alpha < .025F);
            previous = alpha;
        }
        assertTrue(TortoiseDeath.opacity(190) > 0);
        assertTrue(TortoiseDeath.opacity(190) < .05F);
    }
    @Test void manySurfaceParticlesExpireBeforeTheRockPileAppears() {
        int count = 0;
        for (int tick = TortoiseDeath.SCATTER_START; tick < TortoiseDeath.SCATTER_END; tick++) {
            int particles = TortoiseDeath.particleBudget(tick);
            assertTrue(particles >= 12 && particles <= 40);
            count += particles;
        }
        assertTrue(count > 1500);
        assertEquals(0, TortoiseDeath.particleBudget(TortoiseDeath.SCATTER_END));
        assertTrue(TortoiseDeath.SCATTER_END - 1 + TortoiseDeath.MAX_PARTICLE_LIFE < TortoiseDeath.REMAINS_AT);
        assertTrue(TortoiseDeath.REMAINS_AT > TortoiseDeath.FADE_END);
        assertTrue(TortoiseDeath.DURATION > TortoiseDeath.REMAINS_AT);
    }
}
