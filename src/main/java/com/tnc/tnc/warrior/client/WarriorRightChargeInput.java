package com.tnc.tnc.warrior.client;
/** Right-use lifecycle is independent of immediate combo taps. */
public final class WarriorRightChargeInput {
    public enum Action { NONE,HOLD,KEEPALIVE,RELEASE,CANCEL }
    private boolean active,held,blocked;
    private long start,lastPulse;
    public boolean begin(long now){if(active||blocked)return false;active=true;held=false;start=lastPulse=now;return true;}
    public boolean active(){return active;}
    public boolean blocked(){return blocked;}
    public void cancel(boolean down){active=false;held=false;blocked=down;}
    public Action update(boolean down,boolean ready,long now){
        if(!down)blocked=false;
        if(!active)return Action.NONE;
        if(!ready){cancel(down);return Action.CANCEL;}
        if(!down){cancel(false);return Action.RELEASE;}
        if(!held&&now-start>=10){held=true;lastPulse=now;return Action.HOLD;}
        if(held&&now-lastPulse>=10){lastPulse=now;return Action.KEEPALIVE;}
        return Action.NONE;
    }
}
