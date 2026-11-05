package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Bounded swept blade volume. No camera-facing sheets, duplicated back faces or world particles. */
public final class WarriorTrailVolume {
    public static final int NEAR_POINTS=96,FAR_POINTS=40,HISTORY=64;
    public record Point(Vec3 hilt,Vec3 tip,double at) {}
    @FunctionalInterface public interface Sink {
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alphaA,float alphaB,float alphaC,float alphaD);
        default void gradient(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int ca,int cb,int cc,int cd,float aa,float ab,float ac,float ad){
            quad(a,b,c,d,cb,aa,ab,ac,ad);
        }
    }
    private record Ring(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 featherA,Vec3 featherB,Vec3 coreA,Vec3 coreB,float alpha) {}
    private WarriorTrailVolume() {}
    public static int maxQuads(int points){return Math.max(0,points-1)*7+2;}
    /** Arc interpolation preserves blade radius even when a frame spans a large turn. */
    public static Point interpolate(Point a,Point b,double t) {
        Vec3 av=a.tip.subtract(a.hilt),bv=b.tip.subtract(b.hilt);
        double al=av.length(),bl=bv.length();Vec3 h=a.hilt.lerp(b.hilt,t);
        if(al<1e-8 || bl<1e-8)return new Point(h,a.tip.lerp(b.tip,t),a.at+(b.at-a.at)*t);
        Vec3 u=av.scale(1/al),v=bv.scale(1/bl);double dot=Math.max(-1,Math.min(1,u.dot(v)));
        Vec3 direction;
        if(dot>.9995)direction=u.lerp(v,t).normalize();
        else {
            Vec3 axis=u.cross(v);
            if(axis.lengthSqr()<1e-10)axis=u.cross(Math.abs(u.y)<.8?new Vec3(0,1,0):new Vec3(1,0,0));
            direction=WarriorBladeGeometry.rotate(u,axis,Math.acos(dot)*t);
        }
        return new Point(h,h.add(direction.scale(al+(bl-al)*t)),a.at+(b.at-a.at)*t);
    }
    public static List<Point> resample(List<Point> input,int budget) {
        if(input.size()<2)return List.copyOf(input);
        // Keep the newest raw points if a distant-player budget is smaller than
        // the history. In normal play 64 history points fit the near budget.
        int first=Math.max(0,input.size()-Math.max(2,budget));
        var in=input.subList(first,input.size());int n=in.size()-1;int[] steps=new int[n];
        for(int i=0;i<n;i++){
            var a=in.get(i);var b=in.get(i+1);Vec3 av=a.tip.subtract(a.hilt),bv=b.tip.subtract(b.hilt);
            double dot=av.lengthSqr()<1e-9||bv.lengthSqr()<1e-9?1:Math.max(-1,Math.min(1,av.normalize().dot(bv.normalize())));
            steps[i]=Math.max(1,Math.min(24,(int)Math.ceil(Math.acos(dot)/Math.toRadians(3))));
        }
        steps=WarriorTrailCurve.limit(steps,Math.max(n,budget-1));
        var out=new ArrayList<Point>(budget);out.add(in.get(0));
        for(int i=0;i<n;i++)for(int s=1;s<=steps[i];s++)out.add(s==steps[i]?in.get(i+1):interpolate(in.get(i),in.get(i+1),s/(double)steps[i]));
        return out;
    }
    public static int emit(List<Point> points,double now,double life,double start,double ghostScale,
                           WarriorTrailAppearance.Style style,float intensity,Sink sink) {
        if(points.size()<2)return 0;
        Ring previous=null,first=null;Vec3 carried=null;int quads=0;
        for(int i=0;i<points.size();i++) {
            Point p=points.get(i);double fade=Math.max(0,Math.min(1,1-(now-p.at)/life));
            if(fade<=0)continue;
            Vec3 blade=p.tip.subtract(p.hilt);
            if(blade.lengthSqr()<1e-10)continue;
            Vec3 tangent=points.get(Math.min(points.size()-1,i+1)).tip.subtract(points.get(Math.max(0,i-1)).tip);
            Vec3 normal=blade.cross(tangent);
            if(normal.lengthSqr()<1e-10)normal=carried==null?blade.cross(Math.abs(blade.normalize().y)<.8?new Vec3(0,1,0):new Vec3(1,0,0)):carried;
            normal=normal.normalize();
            if(carried!=null && normal.dot(carried)<0)normal=normal.scale(-1);
            carried=normal;
            double depth=style.thickness()*.5*(.3+.7*Math.sqrt(fade));Vec3 offset=normal.scale(depth);
            Vec3 inner=p.hilt.lerp(p.tip,start+(1-fade)*(.92-start));
            Vec3 outer=p.hilt.add(blade.scale(ghostScale));
            Vec3 feather=p.hilt.add(blade.scale(ghostScale+.10));
            float alpha=(float)(intensity*fade*fade*(.25+.75*Math.min(1,(p.at-points.get(0).at)/55)));
            Ring ring=new Ring(inner.add(offset),outer.add(offset),outer.subtract(offset),inner.subtract(offset),
                    feather.add(offset.scale(.2)),feather.subtract(offset.scale(.2)),p.hilt.add(blade.scale(ghostScale-.065)),p.hilt.add(blade.scale(ghostScale+.015)),alpha);
            if(first==null)first=ring;
            if(previous!=null) {
                Ring a=previous,b=ring;int color=style.shell(),base=style.base();
                sink.gradient(a.a,a.b,b.b,b.a,base,color,color,base,a.alpha*.24f,a.alpha,b.alpha,b.alpha*.24f);
                sink.gradient(a.d,b.d,b.c,a.c,base,base,color,color,a.alpha*.24f,b.alpha*.24f,b.alpha,a.alpha);
                sink.quad(a.b,a.c,b.c,b.b,color,a.alpha*.72f,a.alpha*.72f,b.alpha*.72f,b.alpha*.72f);
                sink.quad(a.a,b.a,b.d,a.d,base,a.alpha*.07f,b.alpha*.07f,b.alpha*.07f,a.alpha*.07f);
                sink.quad(a.b,a.featherA,b.featherA,b.b,color,a.alpha,0,0,b.alpha);
                sink.quad(a.c,b.c,b.featherB,a.featherB,color,a.alpha,b.alpha,0,0);
                // Keep a readable narrow light core while the broad shell stays transparent.
                sink.quad(a.coreA,a.coreB,b.coreB,b.coreA,WarriorTrailAppearance.mix(style.shell(),0xFFFFFF,.30),a.alpha*.35f,Math.min(.92f,a.alpha*1.4f),Math.min(.92f,b.alpha*1.4f),b.alpha*.35f);
                quads+=7;
            }
            previous=ring;
        }
        if(first!=null && previous!=first){
            for(Ring r:new Ring[]{first,previous})sink.gradient(r.a,r.d,r.c,r.b,style.base(),style.base(),style.shell(),style.shell(),r.alpha*.24f,r.alpha*.24f,r.alpha,r.alpha);
            quads+=2;
        }
        return quads;
    }
}
