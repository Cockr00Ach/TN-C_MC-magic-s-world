package com.tnc.tnc.skyacademy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AcademyRulesTest {
 @Test void dungeonPracticeRequiresFirstSight(){assertFalse(AcademyRules.unlocked(AcademyRules.ARRIVAL,"wind"));assertTrue(AcademyRules.unlocked(3,"wind"));assertEquals("stargaze",AcademyRules.next(1));}
 @Test void titleAndLevelNeverGateLessons(){assertTrue(AcademyRules.unlocked(7,"farm"));assertTrue(AcademyRules.unlocked(15,"workshop"));assertEquals("complete",AcademyRules.next(31));}
 @Test void guessingPhysicalOrderDoesNotSolve(){assertEquals(0,AcademyRules.rune(0,0));assertEquals(1,AcademyRules.rune(0,1));assertEquals(2,AcademyRules.rune(1,0));assertEquals(3,AcademyRules.rune(2,2));assertEquals(0,AcademyRules.rune(1,2));assertEquals(0,AcademyRules.rune(3,2));}
 @Test void nightWindowWrapsAcrossDays(){assertFalse(AcademyRules.night(6000));assertTrue(AcademyRules.night(18000));assertFalse(AcademyRules.night(23000));assertTrue(AcademyRules.night(42000));}
}
