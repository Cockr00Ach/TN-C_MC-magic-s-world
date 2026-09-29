package com.tnc.tnc.adventure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdventureRulesTest {
    @Test void levelsStartAtOneAndRespectEveryBoundary(){
        assertEquals(1,AdventureRules.level(0));assertEquals(2,AdventureRules.level(61));
        for(int level=2;level<=100;level++) {
            long xp=AdventureRules.xpAtLevel(level);
            assertEquals(level-1,AdventureRules.level(xp-1));assertEquals(level,AdventureRules.level(xp));
        }
        assertEquals(100,AdventureRules.level(Long.MAX_VALUE));
    }
    @Test void crossingMultipleTenLevelBandsAndRepeatedUpdatesDoNotMintPoints(){
        var p=new AdventureProfile();assertEquals(4,p.learningPoints());
        p.addXp(AdventureRules.xpAtLevel(31));assertEquals(19,p.learningPoints());
        p.addXp(0);p.addXp(-20);assertEquals(19,p.learningPoints());
        p.addXp(Long.MAX_VALUE);assertEquals(54,p.learningPoints());assertEquals(100,p.level());
    }
    @Test void rankEligibilityEnforcesOneGradeAndOneHigherContract(){
        assertTrue(AdventureRules.canAccept(0,0,0,0));assertTrue(AdventureRules.canAccept(0,1,0,0));
        assertFalse(AdventureRules.canAccept(0,2,0,0));assertFalse(AdventureRules.canAccept(0,1,1,1));
        assertFalse(AdventureRules.canAccept(0,0,3,0));assertFalse(AdventureRules.canAccept(0,-1,0,0));
        assertEquals(5,AdventureRules.reputation(10,1,0));assertEquals(0,AdventureRules.reputation(10,2,0));
    }
    @Test void activeContractsSurviveRefreshAndCannotBeAcceptedTwice(){
        var p=new AdventureProfile();p.registered=true;
        assertTrue(p.accept(ContractCatalog.find("iron"),0));assertFalse(p.accept(ContractCatalog.find("creeper"),0));
        assertTrue(p.accept(ContractCatalog.find("logs"),0));assertFalse(p.accept(ContractCatalog.find("logs"),1));
        assertEquals(2,p.contracts.size());assertEquals(0,p.contracts.get("iron").epoch);
    }
    @Test void moneyRejectsNegativeOverdraftAndOverflowAndKeepsFiveEntries(){
        var p=new AdventureProfile();assertFalse(p.credit(-1,"forged"));assertFalse(p.debit(-1,"forged"));
        assertTrue(p.credit(100,"commission"));assertFalse(p.debit(101,"forged"));
        assertEquals(100,p.coins());assertTrue(p.debit(100,"smith"));assertEquals(0,p.coins());
        assertTrue(p.credit(AdventureRules.MAX_COINS,"cap"));assertFalse(p.credit(1,"overflow"));
        for(int i=0;i<10;i++)p.record("test"+i);assertEquals(5,p.ledger.size());
    }
}
