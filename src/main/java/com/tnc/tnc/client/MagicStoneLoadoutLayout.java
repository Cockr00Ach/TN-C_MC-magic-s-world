package com.tnc.tnc.client;

/** Shared draw and hit-test rectangles, in fitted panel coordinates. */
public final class MagicStoneLoadoutLayout {
    public record Rect(int x,int y,int width,int height){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    private MagicStoneLoadoutLayout(){}
    public static Rect slot(int left,int top,int panelWidth,int panelHeight,int index){
        if(index<0||index>=9)throw new IllegalArgumentException("Slot outside page");
        int cell=(panelWidth-24)/9;
        return new Rect(left+12+index*cell,top+panelHeight-80,cell-4,42);
    }
}
