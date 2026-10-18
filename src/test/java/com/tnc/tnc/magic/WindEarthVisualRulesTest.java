package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindEarthVisualRulesTest {
    @Test void everyWindAndEarthChainTierHasADistinctPresentation() {
        int count=0;
        for(var entry:SpellCatalog.all())if((entry.element()==Element.WIND||entry.element()==Element.EARTH)&&entry.chain()!=SpellCatalog.Chain.WIND_BALL&&entry.chain()!=SpellCatalog.Chain.WIND_WEAPON) {
            var spec=WindEarthVisualRules.find(entry.id().getPath());
            assertNotNull(spec,entry.displayName());assertEquals(entry.tier(),spec.tier());
            int expected=switch(entry.chain()) {
                case FLIGHT -> 0; case SUMMON, WIND_BALL -> 1; case WIND_WEAPON -> 7; case GALE -> 2;
                case EARTH_SHOT -> 3; case EARTH_MOVE -> 4; case EARTH_MUD -> 5; case EARTH_RING -> 6;
                default -> throw new AssertionError("Unexpected chain");
            };
            assertEquals(expected,spec.kind());assertTrue(spec.radius()>0&&spec.life()>0);count++;
        }
        assertEquals(30,count);assertEquals(count,WindEarthVisualRules.all().size());
        assertNull(WindEarthVisualRules.find("night_hand"));
    }
    @Test void allTierCloudsKeepTheirActualGameplayFootprintAndLifetime() {
        String[][] ids={{"earth_stir","earth_spike","rock_burst","earth_rift","star_quake"},
                {"mud","pit","mire","earth_flow","earth_core_burst"}};
        double[][] radii={{4,4.5,5.5,7,9},{8,9,11,13,16}};
        // The source-data audit additionally compares these tables against live spell JSON.
        for(int kind=0;kind<2;kind++)for(int tier=0;tier<5;tier++) {
            var spec=WindEarthVisualRules.find(ids[kind][tier]);
            assertEquals(radii[kind][tier],spec.radius());assertFalse(spec.followsOwner());
            assertEquals(kind==0?164+tier*40:245,spec.life());
        }
    }
    @Test void aCompleteFieldHasReadableHoldAndSmoothArrivalAndDeparture() {
        assertEquals(0,WindEarthVisualRules.envelope(0,240));
        assertEquals(1,WindEarthVisualRules.envelope(8,240));
        assertEquals(1,WindEarthVisualRules.envelope(200,240));
        assertEquals(0,WindEarthVisualRules.envelope(240,240));
        float last=0;
        for(int i=0;i<=80;i++){float value=WindEarthVisualRules.envelope(i/10.0,240);assertTrue(value>=last);last=value;}
    }
    @Test void windLordReadsAsALeaderWithSmallerEscorts() {
        assertTrue(WindEarthVisualRules.spiritScale(5,0)>WindEarthVisualRules.spiritScale(5,1));
        assertEquals(1,WindEarthVisualRules.spiritScale(4,0));
    }
}
