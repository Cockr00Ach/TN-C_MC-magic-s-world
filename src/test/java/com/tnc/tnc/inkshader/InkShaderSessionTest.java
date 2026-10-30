package com.tnc.tnc.inkshader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class InkShaderSessionTest {
    static final String PACK="Complementary.zip";
    static final class Backend implements InkShaderSession.Backend {
        InkShaderSession.State state;int applies;boolean fail;
        Backend(boolean enabled){state=new InkShaderSession.State(enabled,PACK);}
        public InkShaderSession.State read(){return state;}
        public void apply(InkShaderSession.State value)throws Exception {applies++;if(fail)throw new java.io.IOException("Test failure");state=value;}
    }
    @TempDir Path folder;
    @Test void originalOnRestoresOnceAndDoesNotRecaptureDuringRespawn()throws Exception {
        var b=new Backend(true);var files=new InkShaderFiles(folder);var s=new InkShaderSession(b,files);
        s.tick(true);assertFalse(b.state.enabled());assertTrue(files.journalExists());
        for(int i=0;i<100;i++)s.tick(true);assertEquals(1,b.applies);
        assertEquals(new InkShaderSession.State(true,PACK),s.original());
        s.tick(false);s.tick(false);assertTrue(b.state.enabled());assertEquals(PACK,b.state.pack());assertEquals(2,b.applies);assertFalse(files.journalExists());
    }
    @Test void originalOffNeverGetsEnabledOnExit()throws Exception {
        var b=new Backend(false);var s=new InkShaderSession(b,new InkShaderFiles(folder));s.tick(true);s.tick(false);
        assertFalse(b.state.enabled());assertEquals(0,b.applies);
    }
    @Test void changingModesKeepsTheOriginalPackAndState()throws Exception {
        var b=new Backend(true);var s=new InkShaderSession(b,new InkShaderFiles(folder));s.tick(true);
        s.mode(InkShaderSession.Mode.INK);s.tick(true);assertEquals(new InkShaderSession.State(true,InkShaderSession.INK_PACK),b.state);
        s.mode(InkShaderSession.Mode.OFF);s.tick(true);s.tick(false);assertEquals(new InkShaderSession.State(true,PACK),b.state);
    }
    @Test void explicitlyTestingInkWhileOriginallyOffStillRestoresOff()throws Exception {
        var b=new Backend(false);var s=new InkShaderSession(b,new InkShaderFiles(folder));s.mode(InkShaderSession.Mode.INK);
        s.tick(true);assertTrue(b.state.enabled());s.tick(false);assertEquals(new InkShaderSession.State(false,PACK),b.state);
    }
    @Test void startupRecoversAnInterruptedSessionBeforeCapturingAgain()throws Exception {
        var files=new InkShaderFiles(folder);files.save(new InkShaderSession.State(true,PACK));
        var b=new Backend(false);var s=new InkShaderSession(b,files);s.recover(files.saved().orElseThrow());
        assertTrue(b.state.enabled());assertFalse(files.journalExists());s.tick(true);assertEquals(new InkShaderSession.State(true,PACK),s.original());
    }
    @Test void reloadFailureDoesNotLoopOrLoseTheOriginalPreference()throws Exception {
        var b=new Backend(true);var files=new InkShaderFiles(folder);var s=new InkShaderSession(b,files);b.fail=true;
        assertThrows(java.io.IOException.class,()->s.tick(true));assertTrue(s.failed());
        int calls=b.applies;for(int i=0;i<100;i++)s.tick(true);assertEquals(calls,b.applies);
        // The fake backend did not mutate its original state, so no rollback reload was necessary.
        assertEquals(new InkShaderSession.State(true,PACK),b.state);
    }
    @Test void failedRollbackLeavesTheOriginalStateOnDisk()throws Exception {
        var files=new InkShaderFiles(folder);
        var b=new InkShaderSession.Backend(){
            InkShaderSession.State state=new InkShaderSession.State(true,PACK);int calls;
            public InkShaderSession.State read(){return state;}
            public void apply(InkShaderSession.State value)throws Exception {if(++calls==1)state=value;throw new java.io.IOException("Driver reload failed");}
        };
        var s=new InkShaderSession(b,files);assertThrows(java.io.IOException.class,()->s.tick(true));
        assertTrue(s.failed());assertTrue(files.journalExists());assertEquals(new InkShaderSession.State(true,PACK),files.saved().orElseThrow());
        var healthy=new Backend(false);new InkShaderSession(healthy,files).recover(files.saved().orElseThrow());
        assertTrue(healthy.state.enabled());assertFalse(files.journalExists());
    }
    @Test void journalRoundTripsAnAbsentPackAndPolicyIsIndependent()throws Exception {
        var files=new InkShaderFiles(folder);files.save(new InkShaderSession.State(false,null));files.mode(InkShaderSession.Mode.INK);
        assertEquals(new InkShaderSession.State(false,null),files.saved().orElseThrow());assertEquals(InkShaderSession.Mode.INK,files.mode());
        files.clear();assertTrue(files.saved().isEmpty());assertEquals(InkShaderSession.Mode.INK,files.mode());
    }
}
