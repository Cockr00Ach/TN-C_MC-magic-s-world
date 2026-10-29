package com.tnc.tnc.boss.fire;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TortoiseDeathTest {
    @Test void lateTrackingAndReloadUseTheSameClampedClock() {
        assertEquals(-1, TortoiseDeath.age(-1, 6000, .5F));
        assertEquals(50.5F, TortoiseDeath.age(1000, 1050, .5F));
        assertEquals(180, TortoiseDeath.age(1000, 2000, .5F));
    }
    @Test void coolingNeverFlashesOrReignites() {
        float previous = 1;
        for (float age = 0; age <= 180; age += .125F) {
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
    @Test void bodyStaysOpaqueUntilBowFinishes() {
        for (int age = 0; age <= TortoiseDeath.BOW_END; age++) assertEquals(1, TortoiseDeath.opacity(age));
        assertEquals(1, TortoiseDeath.cooling(TortoiseDeath.FADE_START));
    }
    @Test void wholeBodyFadeIsSlowMonotonicAndComplete() {
        assertEquals(60, TortoiseDeath.DURATION - TortoiseDeath.FADE_START);
        assertEquals(.5F, TortoiseDeath.opacity(150), .0001F);
        float previous = 1;
        for (float age = 120; age <= 181; age += .125F) {
            float opacity = TortoiseDeath.opacity(age);
            assertTrue(opacity >= 0 && opacity <= previous);
            previous = opacity;
        }
        assertEquals(0, TortoiseDeath.opacity(180));
    }
    @Test void smokeEndsBeforeTheBowAndFade() {
        for (int age = 60; age <= 180; age++) assertEquals(0, TortoiseDeath.particleBudget(age));
        for (int age = 0; age < 60; age++) assertTrue(TortoiseDeath.particleBudget(age) <= 2);
    }
    @Test void livingRenderHasFullOpacity() {
        assertEquals(1, TortoiseDeath.opacity(-1));
        assertEquals(0, TortoiseDeath.cooling(-1));
    }
}
