package com.tnc.tnc.inkshader;

import java.util.Objects;

/** Client-local lease on shader settings. Never recapture the temporary state during a respawn or mode change. */
public final class InkShaderSession {
    public enum Mode {OFF,INK}
    public record State(boolean enabled,String pack){}
    public interface Backend {State read() throws Exception;void apply(State state) throws Exception;}
    public interface Journal {void save(State original) throws Exception;void clear() throws Exception;}
    public static final String INK_PACK="ink-wash-shaderpack.zip";
    private final Backend backend;private final Journal journal;
    private State original;private Mode mode=Mode.OFF;private boolean failed;private int changes;
    public InkShaderSession(Backend backend,Journal journal){this.backend=backend;this.journal=journal;}
    public void mode(Mode value){mode=Objects.requireNonNull(value);}
    public Mode mode(){return mode;}public boolean active(){return original!=null;}
    public boolean failed(){return failed;}public int changes(){return changes;}
    public State original(){return original;}
    public void recover(State saved) throws Exception {original=Objects.requireNonNull(saved);run(false);}
    public void tick(boolean inInk) throws Exception {if(!failed)run(inInk);}
    private void run(boolean inInk) throws Exception {
        try {
            if(inInk){
                if(original==null){State before=backend.read();journal.save(before);original=before;}
                apply(mode==Mode.INK?new State(true,INK_PACK):new State(false,original.pack()));
            }else if(original!=null){apply(original);journal.clear();original=null;}
        }catch(Exception failure){
            failed=true;
            // A half-written or failed shader load must not become the player's new permanent preference.
            if(original!=null)try {apply(original);journal.clear();original=null;}
            catch(Exception rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
    private void apply(State state) throws Exception {if(!state.equals(backend.read())){backend.apply(state);changes++;}}
}
