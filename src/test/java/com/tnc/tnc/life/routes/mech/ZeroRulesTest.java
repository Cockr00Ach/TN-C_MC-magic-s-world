package com.tnc.tnc.life.routes.mech;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZeroRulesTest {
    @Test void fullReactorExceedsFifteenSeconds(){assertEquals(166,ZeroRules.shots(12000));assertEquals(16.6,ZeroRules.seconds(12000),.0001);assertTrue(ZeroRules.seconds(12000)>=15);assertTrue(ZeroRules.COST>MechRules.shotCost(false));}
    @Test void fiveFormsMatchTheRequestedExistingSpells(){assertArrayEquals(new String[]{"water_cannon","great_fireball","earth_ball","great_thunder_orb","wind_ball_2"},java.util.Arrays.stream(ZeroRules.Form.values()).map(v->v.spell).toArray(String[]::new));assertEquals(ZeroRules.Form.WATER,ZeroRules.Form.of(5));assertEquals(ZeroRules.Form.WIND,ZeroRules.Form.of(-1));}
    @Test void PartialChargeOnlyPromisesAffordableFullShots(){assertEquals(0,ZeroRules.shots(71));assertEquals(0,ZeroRules.seconds(-1));assertEquals(1,ZeroRules.shots(72));assertEquals(.1,ZeroRules.seconds(72),.0001);}
}
