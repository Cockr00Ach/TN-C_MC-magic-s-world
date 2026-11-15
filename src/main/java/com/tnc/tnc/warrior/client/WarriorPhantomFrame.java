package com.tnc.tnc.warrior.client;

import java.util.ArrayList;
import java.util.List;

/** One render window only: queued mounts never survive a GUI pass, a new frame or a level change. */
final class WarriorPhantomFrame<T> {
    private final int limit;
    private final List<T> draws=new ArrayList<>();
    private Object level;

    WarriorPhantomFrame(int limit){
        if(limit<0)throw new IllegalArgumentException("Negative phantom frame limit");
        this.limit=limit;
    }
    void begin(Object level){
        clear();this.level=level;
    }
    boolean add(Object level,T draw){
        if(this.level==null||this.level!=level||draw==null||draws.size()>=limit)return false;
        draws.add(draw);return true;
    }
    List<T> end(Object level){
        List<T> snapshot=this.level!=null&&this.level==level?List.copyOf(draws):List.of();
        clear();return snapshot;
    }
    void clear(){
        level=null;draws.clear();
    }
}
