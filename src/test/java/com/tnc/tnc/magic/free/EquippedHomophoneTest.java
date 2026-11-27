package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.free.client.FreeMagicVoice;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquippedHomophoneTest {
    @Test void acousticFallbackRequiresTranscriptSupportInTheActiveElement() {
        assertEquals("地球", ElementalSpeech.resolve("地球","雷球",Element.LIGHTNING));
        assertEquals("我想换个座位", ElementalSpeech.resolve("我想换个座位","风步",Element.WIND));
        assertEquals("火蛇", ElementalSpeech.resolve("或者","火蛇",Element.FIRE));
        assertEquals("紫火", ElementalSpeech.resolve("死火","紫火",Element.FIRE));
        assertEquals("或者", ElementalSpeech.resolve("或者","火蛇",Element.WIND));
    }
    @Test void fullHomophonesReachTheEquippedSkillBeforeWaterAliases() {
        var data = new MagicStoneData();
        var ball = ResourceLocation.fromNamespaceAndPath("tnc", "water_ball");
        data.learn(ball);
        for (int slot = 0; slot < MagicStoneData.LOADOUT_SLOTS; slot++) data.clearSlot(slot);
        var heard = new FreeMagicVoice.Update(FreeMagicVoice.Kind.FINAL, "谁 秋", "水弹");
        assertEquals("谁 秋", heard.forLoadout(data).text());
        assertEquals(FreeMagicIntent.Shape.BALL, FreeMagicIntent.parse(heard.text()).orElseThrow().shape());
        data.setSlot(10, ball);
        assertEquals("水球", heard.forLoadout(data).text());
        assertEquals("水弹", heard.forLoadout(data).shapeHint());
        assertEquals(10, EquippedSpellVoice.match(heard.forLoadout(data).text(),data).orElseThrow().slot());
        assertEquals("技能水球", EquippedSpellVoice.repairName("技能税球",data));
        assertEquals("巨大谁秋", EquippedSpellVoice.repairName("巨大谁秋",data));
        assertTrue(FreeMagicIntent.parse("巨大谁秋").orElseThrow().has(FreeMagicIntent.GIANT));
        assertEquals("追求", EquippedSpellVoice.repairName("追求",data));
        assertEquals("谁去取快递", EquippedSpellVoice.repairName("谁去取快递",data));
    }

    @Test void learnedFireHandUsesItsActualSpokenName() {
        var data = new MagicStoneData();
        var spell = ResourceLocation.fromNamespaceAndPath("tnc", "flame_grip");
        data.learn(spell);
        data.setSlot(0, spell);
        assertEquals("焚握", EquippedSpellVoice.repairName("坟卧", data));
    }
}
