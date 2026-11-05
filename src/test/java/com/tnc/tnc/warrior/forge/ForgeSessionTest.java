package com.tnc.tnc.warrior.forge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeSessionTest {
    static ForgeSession ready(ForgePattern pattern){
        var s=new ForgeSession(pattern);s.heat=750;assertTrue(s.takeFromFire());
        for(int i=0;i<12;i++){
            for(int t=0;t<28;t++)s.tick();
            assertEquals(100,s.strike(pattern.zone(i),pattern.heavy(i)?16:3),.0001);
        }
        assertTrue(s.toQuench());assertTrue(s.finishQuench(80,pattern.pose()));assertTrue(s.assemble(20,true));return s;
    }
    @Test void allFourSilhouettesCanReachDivineWithoutRandomness(){
        for(var pattern:ForgePattern.values()){
            var s=ready(pattern);assertEquals(ForgeQuality.PRIMORDIAL,s.quality());assertEquals(100,s.score(),.001);
        }
    }
    @Test void eachProcessCanPreventDivineEvenWhenAllOthersArePerfect(){
        for(int i=0;i<5;i++){
            double[] scores={100,100,100,100,100};scores[i]=70;
            assertNotEquals(ForgeQuality.PRIMORDIAL,ForgeQuality.grade(scores[0],scores[1],scores[2],scores[3],scores[4],false));
        }
    }
    @Test void qualityHonorsEveryTierAndWeakestProcess(){
        for(var q:ForgeQuality.values())assertEquals(q,ForgeQuality.grade(q.threshold,q.threshold,q.threshold,q.threshold,q.threshold,false));
        assertEquals(ForgeQuality.FINE,ForgeQuality.grade(100,100,100,40,100,false));
        assertEquals(ForgeQuality.BOTCHED,ForgeQuality.grade(100,100,100,100,100,true));
        assertEquals(ForgeQuality.BOTCHED,ForgeQuality.grade(Double.NaN,100,100,100,100,false));
    }
    @Test void wrongLocationAndForceAffectHammerScoreNotJustParticles(){
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=750;s.takeFromFire();
        assertTrue(s.strike(2,2)<50);assertTrue(s.deviation[2]>0);
        for(int i=1;i<12;i++){for(int t=0;t<28;t++)s.tick();s.strike(s.pattern.zone(i),s.pattern.heavy(i)?16:2);}
        assertTrue(s.hammerScore<96);assertTrue(s.shapeScore<100);
    }
    @Test void missedBeatStillChangesShapeAndProgress(){
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=750;s.takeFromFire();s.strike(0,16);
        for(int t=0;t<80;t++)s.tick();
        assertTrue(s.strike(2,16)>=85);assertEquals(2,s.strokes);
    }
    @Test void repeatedPacketsAndImpossibleRegionsCannotAddStrokes(){
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=750;s.takeFromFire();
        assertEquals(-1,s.strike(-1,16));assertEquals(-1,s.strike(3,16));assertEquals(0,s.strokes);
        s.strike(0,16);assertEquals(-1,s.strike(2,16));assertEquals(1,s.strokes);
    }
    @Test void coldUnfinishedPieceCanReheatWithoutLosingWorkOrErasingMistakes(){
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=650;s.takeFromFire();s.strike(2,2);
        for(int i=0;i<10;i++)s.tick();assertEquals(-1,s.strike(2,16));
        double score=s.strikeTotal,offset=s.deviation[2];assertTrue(s.reheat());
        assertEquals(1,s.strokes);assertEquals(score,s.strikeTotal);assertEquals(offset,s.deviation[2]);
        assertTrue(s.strike(s.pattern.zone(1),16)>=0);
    }
    @Test void noQuenchingUnshapedOrFinishingWithoutRealAssembly(){
        var s=new ForgeSession(ForgePattern.JIAN);assertFalse(s.toQuench());assertFalse(s.finishQuench(80,0));assertFalse(s.assemble(20,true));
        s.heat=750;s.takeFromFire();assertFalse(s.toQuench());
    }
    @Test void reQuenchCannotImproveEarlierProcessScoresOrMultiplyResults(){
        var s=ready(ForgePattern.SPEAR);s.stage=ForgeSession.Stage.ASSEMBLY;
        double h=s.heatScore,p=s.hammerScore,shape=s.shapeScore;
        assertTrue(s.requench());assertTrue(s.finishQuench(25,0));
        assertEquals(h,s.heatScore);assertEquals(p,s.hammerScore);assertEquals(shape,s.shapeScore);
        assertFalse(s.finishQuench(80,s.pattern.pose()));
    }
    @Test void correctionIsFiniteAndDoesNotEraseHammerMistakes(){
        var s=ready(ForgePattern.MACE);s.stage=ForgeSession.Stage.SHAPING;
        s.deviation[0]=90;s.deviation[1]=90;double p=s.hammerScore;
        assertTrue(s.correct(0));assertTrue(s.correct(1));assertFalse(s.correct(2));assertEquals(p,s.hammerScore);
    }
    @Test void eightHundredFiftyIsSafeAndSustainedOverheatIsVisibleDefect(){
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=900;
        for(int i=0;i<100;i++)s.tick();assertTrue(s.burned);s.takeFromFire();assertEquals(ForgeQuality.BOTCHED,s.quality());
    }
    @Test void quenchAndAssemblyRejectZeroOrUnboundedHold(){
        var s=ready(ForgePattern.JIAN);s.stage=ForgeSession.Stage.QUENCHING;
        assertFalse(s.finishQuench(0,0));assertFalse(s.finishQuench(100000,0));
        s.stage=ForgeSession.Stage.ASSEMBLY;assertFalse(s.assemble(0,true));assertFalse(s.assemble(10000,true));
    }
    @Test void holdingTooLongFinishesWithPoorTimingInsteadOfEvadingWaterCost(){
        var s=ready(ForgePattern.JIAN);s.stage=ForgeSession.Stage.QUENCHING;
        assertTrue(s.finishQuench(600,0));assertTrue(s.quenchScore<70);assertEquals(ForgeSession.Stage.ASSEMBLY,s.stage);
    }
}
