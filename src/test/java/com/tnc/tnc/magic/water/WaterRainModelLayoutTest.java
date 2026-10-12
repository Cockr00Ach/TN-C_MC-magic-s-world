package com.tnc.tnc.magic.water;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WaterRainModelLayoutTest {
    @Test void luminousDropsStayInsideTheRainFieldAndFall() {
        for(int tier=1;tier<=5;tier++) {
            double r=WaterSpellRules.fieldRadius(3,tier),h=WaterSpellRules.fieldHeight(3,tier);
            assertTrue(WaterRainModelLayout.count(tier,true)<=46);
            for(int i=0;i<WaterRainModelLayout.count(tier,true);i++)for(int tick=0;tick<600;tick+=7) {
                var p=WaterRainModelLayout.drop(i,r,h,tick);
                assertTrue(p.horizontalDistanceSqr()<r*r);assertTrue(p.y>=.25&&p.y<=h+.25);
            }
            assertTrue(WaterRainModelLayout.drop(0,r,h,1).y<WaterRainModelLayout.drop(0,r,h,0).y);
            assertTrue(WaterRainModelLayout.count(tier,false)<WaterRainModelLayout.count(tier,true));
        }
    }
}
