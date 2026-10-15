package com.tnc.tnc.boss;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeviathanSpellRulesTest {
    @Test void giantWaterOrbRemainsTheFrequentAttackAndAllSpecialsAppear() {
        for (boolean phaseTwo : new boolean[]{false,true}) {
            int tick=0, basics=0, next=0, cooldown=0, normal=0, special=0;
            var seen=new HashSet<String>();
            while (tick<2400) {
                String spell=LeviathanSpellRules.next(basics,cooldown,next);
                int elapsed=LeviathanSpellRules.windup(spell,phaseTwo)+LeviathanSpellRules.recovery(phaseTwo);
                if (spell.equals(LeviathanSpellRules.BASIC)) { basics=Math.min(3,basics+1); normal++; }
                else { seen.add(spell); special++; basics=0; next++;
                    cooldown=LeviathanSpellRules.specialCooldown(spell,phaseTwo); }
                cooldown=Math.max(0,cooldown-elapsed);
                tick+=elapsed;
            }
            assertEquals(java.util.Set.of("dragon_roar","dragon_ruin","sea_god_crypt"),seen);
            assertTrue(normal>=special*3,"Giant water orbs must dominate the cast schedule");
        }
    }

    @Test void largeBeamCannotBeReplacedBeforeItsFullChannelEnds() {
        assertTrue(LeviathanSpellRules.specialCooldown("dragon_ruin",false)>264);
        assertTrue(LeviathanSpellRules.specialCooldown("dragon_ruin",true)>264);
        assertEquals(LeviathanSpellRules.BASIC,LeviathanSpellRules.next(3,1,0));
        assertTrue(LeviathanSpellRules.windup("dragon_ruin",false)
                >LeviathanSpellRules.windup(LeviathanSpellRules.BASIC,false));
    }
}
