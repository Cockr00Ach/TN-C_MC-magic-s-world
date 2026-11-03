package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorEffectStyleTest {
    @Test void eightElementVariantsAndSteelHaveDistinctColorsAndStableIds(){
        var colors=new java.util.HashSet<Integer>();assertEquals(9,WarriorEffectStyle.values().length);
        for(var s:WarriorEffectStyle.values()){assertSame(s,WarriorEffectStyle.find(s.id.toUpperCase(java.util.Locale.ROOT)));assertTrue(colors.add(s.color));}
        assertNull(WarriorEffectStyle.find("invalid"));assertNull(WarriorEffectStyle.find(null));
    }
    /** 只剩格挡光圈还在用粒子预算；未知档位退回默认值，别让调用方炸在 switch 上。 */
    @Test void guardRingBudgetStaysBounded(){
        assertTrue(WarriorEffectStyle.budget("block")<=24);
        assertEquals(12,WarriorEffectStyle.budget("anything-else"));
    }
}
