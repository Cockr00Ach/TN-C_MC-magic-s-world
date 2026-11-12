package com.tnc.tnc.client;

/** A press alone never edits a loadout; only an intentional drag can be dropped. */
public final class SpellDragGesture<T> {
    private T payload;
    private double startX,startY;
    private boolean dragged;
    public void begin(T value,double x,double y){payload=value;startX=x;startY=y;dragged=false;}
    public void move(double x,double y){if(payload!=null && Math.hypot(x-startX,y-startY)>=4)dragged=true;}
    public boolean active(){return payload!=null;}
    public boolean dragging(){return active()&&dragged;}
    public T payload(){return payload;}
    public T finish(boolean validTarget){T result=dragging()&&validTarget?payload:null;cancel();return result;}
    public void cancel(){payload=null;dragged=false;}
}
