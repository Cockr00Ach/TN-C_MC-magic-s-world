package com.tnc.tnc.world.skyhaven;

import java.util.ArrayList;
import java.util.List;

/** Closed 3D storm walls and swept helices. No entities, particles or per-vertex allocations. */
public final class SeaTornadoGeometry {
    public static final int MAX_VISIBLE = 10;
    public static final double VIEW_DISTANCE = 360;
    public enum Detail {
        NEAR(22,32,48), MID(14,24,32), FAR(10,16,24);
        final int rows, sides, helix;
        Detail(int rows,int sides,int helix){this.rows=rows;this.sides=sides;this.helix=helix;}
        public int quads(){return 2*rows*sides+15*sides+4*helix;}
    }
    @FunctionalInterface public interface Sink {
        void vertex(double x,double y,double z,float u,float v,int rgb,int alpha,double nx,double ny,double nz);
    }
    private static final double TAU=Math.PI*2;
    private SeaTornadoGeometry(){}

    /** Low offshore funnels leave roughly sixty blocks of clear air below the island. */
    public static double height(SeaGyreLayout.Gyre g){return 42+20*(.5+.5*Math.sin(g.phase()*1.73));}
    public static double radius(SeaGyreLayout.Gyre g,double t){
        t=Math.max(0,Math.min(1,t));
        double body=.5+.5*Math.sin(g.phase()*2.31+1.4);
        return g.radius()*(.065+.09*body+(.88+.12*body)*Math.pow(t,.85+.50*(1-body)));
    }
    /** Only the closest two use the full mesh, even when ten storms share the view. */
    public static Detail detail(int nearestRank,double distance){
        return nearestRank<2&&distance<155?Detail.NEAR:nearestRank<5&&distance<250?Detail.MID:Detail.FAR;
    }
    public static List<SeaGyreLayout.Gyre> select(List<SeaGyreLayout.Gyre> eligible){
        var out=new ArrayList<SeaGyreLayout.Gyre>(MAX_VISIBLE);
        for(var g:eligible){
            if(out.size()>=MAX_VISIBLE)break;
            if(out.contains(g))continue;
            boolean separated=true;
            for(var chosen:out)if(Math.hypot(g.x()-chosen.x(),g.z()-chosen.z())<96){separated=false;break;}
            if(separated)out.add(g);
            if(out.size()==MAX_VISIBLE)break;
        }
        return List.copyOf(out);
    }
    public static double distanceFade(double horizontalDistance){
        double t=Math.max(0,Math.min(1,(VIEW_DISTANCE-horizontalDistance)/100));return t*t*(3-2*t);
    }

    public static void build(SeaGyreLayout.Gyre g,double seconds,Detail detail,double opacity,Sink sink){
        if(!Double.isFinite(seconds)||!Double.isFinite(opacity)||opacity<=0)return;
        opacity=Math.min(1,opacity);
        wall(g,seconds,detail,.74,true,opacity,sink);
        wall(g,seconds,detail,1,false,opacity,sink);
        blanket(g,seconds,detail,0,8,opacity,sink);
        blanket(g,seconds,detail,1,7,opacity,sink);
        for(int arm=0;arm<4;arm++)for(int i=0;i<detail.helix;i++){
            double t=i/(double)detail.helix,u=(i+1)/(double)detail.helix;
            helix(g,seconds,arm,t,-1,opacity,sink);helix(g,seconds,arm,u,-1,opacity,sink);
            helix(g,seconds,arm,u,1,opacity,sink);helix(g,seconds,arm,t,1,opacity,sink);
        }
    }
    private static void wall(SeaGyreLayout.Gyre g,double time,Detail d,double scale,boolean core,double opacity,Sink sink){
        for(int row=0;row<d.rows;row++)for(int side=0;side<d.sides;side++){
            double t=row/(double)d.rows,u=(row+1)/(double)d.rows,a=side*TAU/d.sides,b=(side+1)*TAU/d.sides;
            surface(g,time,t,a,scale,core,opacity,sink);surface(g,time,u,a,scale,core,opacity,sink);
            surface(g,time,u,b,scale,core,opacity,sink);surface(g,time,t,b,scale,core,opacity,sink);
        }
    }
    private static void surface(SeaGyreLayout.Gyre g,double time,double t,double a,double scale,boolean core,double opacity,Sink sink){
        double spin=time*g.direction()*.48+g.phase();
        double r=radius(g,t)*fold(a,t,spin)*scale;
        double leanX=Math.sin(t*2.5+g.phase()+time*.07)*g.radius()*.14*t;
        double leanZ=Math.cos(t*2.1+g.phase()+time*.06)*g.radius()*.10*t;
        double y=t*height(g)+Math.sin(a*3+t*10-spin)*1.2*t*(1-t);
        double fade=Math.pow(Math.max(0,Math.sin(Math.PI*t)),.12);
        double shade=.5+.5*Math.sin(a-t*9+spin);
        int rgb=core?0x010207:rgb(5+(int)(shade*7),7+(int)(shade*7),11+(int)(shade*11));
        int alpha=(int)((core?255:205)*fade*opacity);
        sink.vertex(leanX+Math.cos(a)*r,y,leanZ+Math.sin(a)*r,(float)(a/TAU*3-spin*.075),(float)(t*4-time*.045),rgb,alpha,Math.cos(a),-.18,Math.sin(a));
    }
    /** Low lifting sea spray and a broad, billowing storm shoulder, both real curved surfaces. */
    private static void blanket(SeaGyreLayout.Gyre g,double time,Detail d,int cap,int rows,double opacity,Sink sink){
        for(int row=0;row<rows;row++)for(int side=0;side<d.sides;side++){
            double q=row/(double)rows,r=(row+1)/(double)rows,a=side*TAU/d.sides,b=(side+1)*TAU/d.sides;
            cloud(g,time,q,a,cap,opacity,sink);cloud(g,time,r,a,cap,opacity,sink);
            cloud(g,time,r,b,cap,opacity,sink);cloud(g,time,q,b,cap,opacity,sink);
        }
    }
    private static void cloud(SeaGyreLayout.Gyre g,double time,double q,double a,int cap,double opacity,Sink sink){
        double spin=time*g.direction()*.38+g.phase();
        double r=g.radius()*(cap==0?.22+q*1.4:.50+q*.55);
        r*=1+.13*Math.sin(a*3+spin+q*5)+.08*Math.sin(a*5-spin*.7-q*7);
        double y=cap==0?height(g)*.09*Math.pow(1-q,1.5)+Math.sin(a*3+spin+q*4)*7*q*Math.sin(Math.PI*q)
                :height(g)*(.91+Math.sin(q*Math.PI/2)*.08)+Math.sin(a*4-spin+q*8)*1.6*q;
        double fade=Math.pow(1-q,.70)*Math.min(1,q*9+.35);
        if(cap==0)fade*=.30+.70*Math.pow(.5+.5*Math.sin(a*3+spin*2-q*5),2);
        sink.vertex(Math.cos(a)*r,Math.max(.02,y),Math.sin(a)*r,(float)(a/TAU*2+spin*.08),(float)(q*2-time*.06),cap==0?0x03050b:0x050710,(int)((cap==0?225:160)*fade*opacity),Math.cos(a)*.3,1,Math.sin(a)*.3);
    }
    private static void helix(SeaGyreLayout.Gyre g,double time,int arm,double t,int edge,double opacity,Sink sink){
        double a=g.phase()+time*g.direction()*.72+arm*TAU/4+t*TAU*2.4+edge*.30;
        double spin=time*g.direction()*.48+g.phase();
        double r=radius(g,t)*fold(a,t,spin)*1.025+.35;
        double x=Math.sin(t*2.5+g.phase()+time*.07)*g.radius()*.14*t+Math.cos(a)*r;
        double z=Math.cos(t*2.1+g.phase()+time*.06)*g.radius()*.10*t+Math.sin(a)*r;
        int alpha=(int)(Math.pow(Math.max(0,Math.sin(Math.PI*t)),.35)*(edge<0?210:55)*opacity);
        sink.vertex(x,t*height(g),z,edge<0?0:1,(float)(t*4-time*.09),edge<0?0x0d0f1a:0x050812,alpha,Math.cos(a),0,Math.sin(a));
    }
    private static double fold(double a,double t,double spin){return 1+.08*Math.sin(a*3-t*9+spin*.8)+.10*Math.sin(a*5-t*14+spin)+.06*Math.sin(a*9+t*21-spin*.7);}
    private static int rgb(int r,int g,int b){return r<<16|g<<8|b;}
}
