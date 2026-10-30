package com.tnc.tnc.bosshud;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BossHudRulesTest {
    @Test void allThirteenBossesHaveDistinctIdentityPaletteAndLore(){
        assertEquals(13,BossHudTheme.values().length);var types=new HashSet<String>();var colors=new HashSet<Integer>();
        for(var theme:BossHudTheme.values()){assertTrue(types.add(theme.type()));assertTrue(colors.add(theme.accent));assertSame(theme,BossHudTheme.forType(theme.type()));assertFalse(theme.subtitleZh.isBlank());assertFalse(theme.subtitleEn.isBlank());}
        for(String ignored:List.of("minecraft:ender_dragon","minecraft:wither","tnc:ink_crane_phantom","tnc:ink_tiger_effect","tnc:canglan_echo","tnc:apollo_acolyte","other:apollo"))assertNull(BossHudTheme.forType(ignored));
    }
    @Test void healthArithmeticCannotDrawNanOrOverfilledBars(){
        for(float health:new float[]{-2,0,1,50,Float.MAX_VALUE,Float.NaN,Float.POSITIVE_INFINITY})for(float maximum:new float[]{0,-1,20,2500,Float.MAX_VALUE,Float.NaN}){
            float p=BossHudRules.progress(health,maximum);assertTrue(Float.isFinite(p)&&p>=0&&p<=1);
            assertFalse(BossHudRules.amount(health).contains("NaN"));assertTrue(BossHudRules.amount(health).length()<12);
        }
        assertEquals(.5,BossHudRules.progress(50,100),.0001);assertEquals("2.5M",BossHudRules.amount(2_500_000));
    }
    @Test void inkFormsKeepTheThreeStageEncounterAndPhantomsStayExcluded(){
        assertEquals(1,BossHudTheme.INK_CRANE.initialPhase);assertEquals(2,BossHudTheme.INK_TIGER.initialPhase);assertEquals(3,BossHudTheme.INK_DRAGON.initialPhase);
        for(var theme:List.of(BossHudTheme.INK_CRANE,BossHudTheme.INK_TIGER,BossHudTheme.INK_DRAGON))assertEquals(3,theme.phases);
        assertEquals(1,BossHudRules.phase(-4,2));assertEquals(2,BossHudRules.phase(99,2));
    }
    @Test void responsiveFramesStayInsideTheWindowAtSupportedGuiSizes(){
        for(int screen:new int[]{240,320,427,640,960,1920}){int width=BossHudRules.width(screen);assertTrue(width>=64&&width<=326);assertTrue(width+70<=screen);}
        assertTrue(BossHudRules.rowHeight(true)<BossHudRules.rowHeight(false));
    }
    @Test void damageTrailLingersThenCatchesUpAndPhaseChangesResetIt(){
        var state=new BossHudRules.Trail();state.update(1,1,1000);state.update(.5F,1,1050);assertTrue(state.ghost()>state.shown());
        for(int i=1;i<80;i++)state.update(.5F,1,1050+i*50);assertEquals(.5,state.shown(),.001);assertEquals(.5,state.ghost(),.003);
        state.update(1,2,5100);assertEquals(1,state.shown());assertEquals(1,state.ghost());
        state.update(Float.NaN,2,5200);assertTrue(Float.isFinite(state.shown()));
    }
}
