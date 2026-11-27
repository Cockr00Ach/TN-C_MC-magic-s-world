package com.tnc.tnc.magic.free.client;

import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.free.ElementalIntent;
import com.tnc.tnc.magic.free.ElementalSpeech;
import com.tnc.tnc.magic.free.FreeMagicIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceKeywordHintTest {
    @Test
    void acceptsCanonicalLabelsFromWaterAndAllElementalSchools() {
        for (var shape : FreeMagicIntent.Shape.values()) {
            assertEquals(shape.label, FreeMagicVoice.validHint(shape.label));
        }
        for (var ability : ElementalIntent.Ability.values()) {
            assertEquals(ability.label, FreeMagicVoice.validHint(ability.label));
        }
        assertEquals("", FreeMagicVoice.validHint("焰蛇"));
        assertEquals("", FreeMagicVoice.validHint("释放火蛇"));
        assertEquals("", FreeMagicVoice.validHint(""));
    }

    @Test
    void acousticFallbackIsLimitedToTheCurrentElement() {
        String hint = FreeMagicVoice.validHint("火蛇");
        assertEquals("火蛇", ElementalSpeech.resolve("", hint, Element.FIRE));
        assertEquals("", ElementalSpeech.resolve("", hint, Element.WIND));
    }
}
