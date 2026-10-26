package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WindInfusionRulesTest {
    @Test void cooldownBoundaryAndRealmRange() {
        assertFalse(WindInfusionRules.ready(159,160));
        assertTrue(WindInfusionRules.ready(160,160));
        assertTrue(WindInfusionRules.range(true)>WindInfusionRules.range(false));
    }
}
