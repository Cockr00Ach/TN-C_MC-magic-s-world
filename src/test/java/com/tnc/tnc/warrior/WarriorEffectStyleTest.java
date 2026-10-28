package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorEffectStyleTest {
    @Test void eightElementVariantsAndSteelHaveDistinctColorsAndStableIds(){
        var colors=new java.util.HashSet<Integer>();assertEquals(9,WarriorEffectStyle.values().length);
        for(var s:WarriorEffectStyle.values()){assertSame(s,WarriorEffectStyle.find(s.id.toUpperCase(java.util.Locale.ROOT)));assertTrue(colors.add(s.color));}
        assertNull(WarriorEffectStyle.find("invalid"));assertNull(WarriorEffectStyle.find(null));
    }
    @Test void particleBudgetsStayBounded(){assertTrue(WarriorEffectStyle.budget("slash")<=30);assertTrue(WarriorEffectStyle.budget("block")<=24);}
}
