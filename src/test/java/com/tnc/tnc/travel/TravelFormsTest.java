package com.tnc.tnc.travel;

import com.tnc.tnc.magic.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 元素附身的纯数值规则。
 *
 * <p>这里钉住的是设计稿里最容易手滑的几件事：
 * 法杖阶位不能超过亲和力、暗/水的低速不能被原版 0.05 的飞行速度下限抬成 10.9 格/秒、
 * 七系消耗倍率与持续时间修正不能互相覆盖。
 */
class TravelFormsTest {

    private static final double EPS = 1.0E-6D;

    @Test
    void effectiveTierIsMinOfStaffAndAffinity() {
        assertEquals(3, TravelForms.effectiveTier(5, 3));
        assertEquals(2, TravelForms.effectiveTier(2, 5));
        assertEquals(0, TravelForms.effectiveTier(5, 0));
        assertEquals(0, TravelForms.effectiveTier(0, 5));
        assertEquals(5, TravelForms.effectiveTier(5, 5));
    }

    @Test
    void durationMatchesElementModifiers() {
        assertEquals(200, TravelForms.durationTicks(Element.LIGHTNING, 1));
        assertEquals(600, TravelForms.durationTicks(Element.LIGHTNING, 5));
        assertEquals(240, TravelForms.durationTicks(Element.DARK, 1));   // +20%
        assertEquals(720, TravelForms.durationTicks(Element.DARK, 5));
        assertEquals(180, TravelForms.durationTicks(Element.FIRE, 1));   // -10%
        assertEquals(540, TravelForms.durationTicks(Element.FIRE, 5));
        assertEquals(720, TravelForms.durationTicks(Element.WIND, 5));   // +20%
    }

    @Test
    void speedTableIsMonotonicAndSlowFormsStaySlow() {
        for (Element element : Element.values()) {
            double previous = -1.0D;
            for (int tier = 1; tier <= 5; tier++) {
                double speed = TravelForms.speed(element, tier);
                assertTrue(speed > previous, element + " speed must rise with tier");
                previous = speed;
            }
        }
        // 七系里暗最慢、光最快（土/光这一版先按飞行形态放出来试外观）。
        assertTrue(TravelForms.speed(Element.DARK, 5) <= TravelForms.speed(Element.EARTH, 1));
        assertTrue(TravelForms.speed(Element.LIGHT, 5) > TravelForms.speed(Element.LIGHTNING, 5));

        // 暗 t1 = 6 格/秒，换算后必须低于原版 0.05（约 10.9 格/秒）。
        float dark = TravelForms.flyingSpeed(TravelForms.speed(Element.DARK, 1));
        assertTrue(dark < 0.05F, "dark travel must not be clamped up to vanilla speed");
        assertEquals(0.02F, TravelForms.flyingSpeed(0.0D), EPS);
        assertEquals(0.14F, TravelForms.flyingSpeed(1000.0D), EPS);
    }

    @Test
    void drainUsesElementMultiplier() {
        assertEquals(1.2D, TravelForms.drainPercentPerSecond(Element.LIGHTNING, 1), EPS);
        assertEquals(2.4D, TravelForms.drainPercentPerSecond(Element.DARK, 5), EPS);
        assertEquals(2.4D * 0.25D, TravelForms.drainPercentPerSecond(Element.EARTH, 5), EPS);
        assertEquals(2.4D * 0.25D, TravelForms.drainPercentPerSecond(Element.LIGHT, 5), EPS);
        assertEquals(2.4D * 0.8D, TravelForms.drainPercentPerSecond(Element.WIND, 5), EPS);
        assertEquals(2.4D * 1.15D, TravelForms.drainPercentPerSecond(Element.FIRE, 5), EPS);
    }

    @Test
    void costsAreFractionsOfMaxManaWithMinimumOne() {
        assertEquals(17, TravelForms.entryMana(210));   // ceil(16.8)
        assertEquals(17, TravelForms.skillMana(Element.LIGHTNING, 210));
        assertEquals(21, TravelForms.skillMana(Element.DARK, 210));
        assertEquals(1, TravelForms.entryMana(1));
    }

    @Test
    void phaseAndDistanceTablesMatchDesign() {
        assertEquals(2, TravelForms.phaseBlocks(1));
        assertEquals(6, TravelForms.phaseBlocks(5));
        assertEquals(64, TravelForms.earthDistance(5));
        assertEquals(45, TravelForms.lightDistance(5));
    }

    @Test
    void allSevenElementsAreOpen() {
        for (Element element : Element.values()) {
            assertTrue(TravelForms.supported(element), element + " should be open");
            assertTrue(TravelForms.airForm(element), element + " should fly in this build");
            assertTrue(TravelForms.skillMana(element, 210) >= 1);
        }
        assertEquals("雷驰", TravelForms.formName(Element.LIGHTNING));
        assertEquals("幽行", TravelForms.formName(Element.DARK));
        assertEquals("暗", TravelForms.elementName(Element.DARK));
    }

    @Test
    void skillTablesCoverAllSevenElements() {
        for (Element element : Element.values()) {
            double multiplier = TravelForms.skillSpeedMultiplier(element);
            assertTrue(multiplier >= 1.0D, element + " multiplier must never slow you down");
            assertTrue(TravelForms.skillCooldownTicks(element) >= 0);
            // 有加速段就必须有冷却，否则能无限连按（影返是用一次，不靠冷却）。
            if (TravelForms.skillBoostTicks(element) > 0) {
                assertTrue(TravelForms.skillCooldownTicks(element) > 0,
                        element + " boost needs a cooldown");
            }
        }
        assertEquals(1.6D, TravelForms.skillSpeedMultiplier(Element.FIRE), EPS);
        assertEquals(0, TravelForms.skillCooldownTicks(Element.DARK), "影返每次附身一次");
        assertEquals(0, TravelForms.skillBoostTicks(Element.EARTH), "土遁是瞬移，不吃加速");
        assertEquals(0, TravelForms.skillBoostTicks(Element.LIGHT), "光跃是瞬移，不吃加速");
        assertTrue(TravelForms.skillCooldownTicks(Element.EARTH)
                > TravelForms.skillCooldownTicks(Element.WIND), "土遁位移最远，冷却也应最长");
    }

    @Test
    void environmentRulesMatchElementIdentity() {
        // 水：水里最快，岸上反而变慢（作者："水的在水里快，陆地上没那么快"）。
        assertTrue(TravelForms.environmentSpeed(Element.WATER, true, true) > 1.2D);
        assertTrue(TravelForms.environmentSpeed(Element.WATER, false, false) < 1.0D);
        // 雷：下水更快（不再是"入水短路"）。
        assertTrue(TravelForms.environmentSpeed(Element.LIGHTNING, true, true) > 1.0D);
        assertEquals(1.0D, TravelForms.environmentSpeed(Element.LIGHTNING, false, false), EPS);
        // 其余四系不受水影响。
        for (Element element : new Element[]{Element.FIRE, Element.WIND,
                Element.EARTH, Element.LIGHT}) {
            assertEquals(1.0D, TravelForms.environmentSpeed(element, true, true), EPS);
        }
        assertEquals(TravelForms.WIND_WATER_DISSOLVE_TICKS, 60);
    }

    @Test
    void entryBlockedFollowsEnvironmentRules() {
        // 火：在水里或下雨进不去。
        assertNotNull(TravelForms.entryBlocked(Element.FIRE, true, true, true, true, false));
        assertNotNull(TravelForms.entryBlocked(Element.FIRE, false, true, true, true, false));
        assertNull(TravelForms.entryBlocked(Element.FIRE, false, false, true, true, false));
        // 暗：白天且在地表进不去；夜里或地下可以。
        assertNotNull(TravelForms.entryBlocked(Element.DARK, false, false, false, false, false));
        assertNull(TravelForms.entryBlocked(Element.DARK, false, false, true, false, false));
        assertNull(TravelForms.entryBlocked(Element.DARK, false, false, false, true, false));
        // 光：无条件。
        assertNull(TravelForms.entryBlocked(Element.LIGHT, true, true, false, false, false));
        // 下界：谁都不行。
        for (Element element : Element.values()) {
            assertNotNull(TravelForms.entryBlocked(element, false, false, true, true, true));
        }
    }
}
