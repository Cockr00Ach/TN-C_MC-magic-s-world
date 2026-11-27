package com.tnc.tnc.magic.free;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterAliasSpeechTest {
    @Test void repairsHomophonesOfAlternativeNames() {
        assertEquals("水弹", WaterSpeech.normalize("谁秋"));
        assertEquals("水弹", WaterSpeech.normalize("税球"));
        assertEquals("释放水弹！", WaterSpeech.normalize("释放税　求！"));
        assertEquals("水枪", WaterSpeech.normalize("睡猫")); // 水矛 and 水毛 share one ability.
        assertEquals("水剑", WaterSpeech.normalize("常见"));
        assertEquals("水剑", WaterSpeech.normalize("长件"));
        assertEquals("连续水弹", WaterSpeech.normalize("连序税球"));
        assertEquals("水弹齐发", WaterSpeech.normalize("谁秋齐发"));
    }

    @Test void preservesExactSpellingsAndUnresolvedHomophones() {
        assertEquals("水弹", WaterSpeech.normalize("水球"));
        assertEquals("水剑和水箭", WaterSpeech.normalize("水剑和水箭"));
        assertEquals("睡间", WaterSpeech.normalize("睡间"));
        assertEquals("税，球", WaterSpeech.normalize("税，球"));
        assertEquals("这是很常见的动物", WaterSpeech.normalize("这是很常见的动物"));
    }
}
