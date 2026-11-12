package com.tnc.tnc.warrior;

/** Independent rolling recognizer: ordinary finishers never consume an ultimate's prefix. */
public final class WarriorUltimateInput {
    private String buffer="", weapon="";
    private long last=Long.MIN_VALUE, first;
    public void clear(){buffer="";weapon="";last=Long.MIN_VALUE;}
    public boolean push(String id,String sequence,char key,long now){
        if(!id.equals(weapon)||last==Long.MIN_VALUE||now-last>18||now-first>120){buffer="";first=now;}
        if(last!=Long.MIN_VALUE && now-last<2)return false;
        weapon=id;last=now;buffer+=key;
        while(!sequence.startsWith(buffer)&&!buffer.isEmpty())buffer=buffer.substring(1);
        if(buffer.length()==1)first=now;
        if(buffer.equals(sequence)){buffer="";return true;}
        return false;
    }
}
