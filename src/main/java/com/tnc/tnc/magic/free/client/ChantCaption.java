package com.tnc.tnc.magic.free.client;

import java.util.ArrayList;
import java.util.List;

/** Keeps stable letters alive across streaming revisions and final-only recognition. */
public final class ChantCaption {
    private long token, expires;
    private String text="";
    private final List<Double> arrivals=new ArrayList<>();

    public void update(long nextToken,String next,long now,boolean terminal){
        int[] before=text.codePoints().toArray(),after=next.codePoints().limit(60).toArray();
        int shared=0;
        if(token==nextToken)while(shared<Math.min(before.length,after.length)&&before[shared]==after[shared])shared++;
        while(arrivals.size()>shared)arrivals.remove(arrivals.size()-1);
        for(int i=shared;i<after.length;i++)arrivals.add(now+(i-shared)*.7);
        token=nextToken;text=new String(after,0,after.length);expires=now+(terminal?100:400);
    }
    public long token(){return token;}
    public String text(){return text;}
    public boolean visible(double now){return !text.isBlank()&&now<expires;}
    public double alpha(int index,double now){
        if(index<0||index>=arrivals.size()||!visible(now))return 0;
        return Math.max(0,Math.min(1,(now-arrivals.get(index))/5))*Math.min(1,(expires-now)/20);
    }
    public void finish(long now){expires=now+100;}
    public void cancel(long now){expires=Math.min(expires,now+12);}
}
