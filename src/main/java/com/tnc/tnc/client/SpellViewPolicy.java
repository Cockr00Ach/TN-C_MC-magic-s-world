package com.tnc.tnc.client;
/** Pure visual policy; never changes collision, damage or spell lifetime. */
public final class SpellViewPolicy {
 public enum Mode {FULL,MODERATE,SIMPLE}
 public static double clearance(Mode mode,double radius){return mode==Mode.FULL?0:Math.max(mode==Mode.SIMPLE?4:2,Math.max(0,radius)*1.35+.5);}
 public static float opacity(Mode mode,double distance,double radius){double end=clearance(mode,radius);if(end==0)return 1;double t=Math.max(0,Math.min(1,(distance-end*.45)/(end*.55)));return (float)(t*t*(3-2*t));}
 private SpellViewPolicy(){}
}
