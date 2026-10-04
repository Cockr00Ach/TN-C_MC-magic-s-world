package com.tnc.tnc.tavern;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TavernMusicTimingTest {
    @Test void waitsExactlyTenSecondsAndChoosesOnceForTheWholeVisit(){
        var timing=new TavernMusicTiming();var calls=new AtomicInteger();
        for(int i=1;i<200;i++)assertEquals(-1,timing.tick(true,false,()->{calls.incrementAndGet();return 1;}));
        assertEquals(1,timing.tick(true,false,()->{calls.incrementAndGet();return 1;}));
        for(int i=0;i<24000;i++)assertEquals(1,timing.tick(true,false,()->{calls.incrementAndGet();return 0;}));
        assertEquals(1,calls.get());
    }
    @Test void leavingResetsEvenIfNoMusicStartedAndReentryCanChooseTheOtherTrack(){
        var t=new TavernMusicTiming();for(int i=0;i<190;i++)t.tick(true,false,()->0);
        assertEquals(-1,t.tick(false,false,()->1));assertEquals(0,t.ticks());
        for(int i=0;i<199;i++)assertEquals(-1,t.tick(true,false,()->1));
        assertEquals(1,t.tick(true,false,()->1));t.tick(false,false,()->1);
        for(int i=0;i<200;i++)t.tick(true,false,()->0);
        assertEquals(0,t.tick(true,false,()->1));
    }
    @Test void pauseDoesNotCountAsTimeInside(){
        var t=new TavernMusicTiming();for(int i=0;i<100;i++)t.tick(true,false,()->0);
        for(int i=0;i<400;i++)assertEquals(-1,t.tick(true,true,()->0));assertEquals(100,t.ticks());
        for(int i=0;i<100;i++)t.tick(true,false,()->0);assertEquals(0,t.tick(true,true,()->1));
    }
    @Test void resettingOnDisconnectDiscardsSelectedMusic(){
        var t=new TavernMusicTiming();for(int i=0;i<200;i++)t.tick(true,false,()->0);t.reset();
        assertEquals(-1,t.tick(true,false,()->1));assertEquals(1,t.ticks());
    }
}
