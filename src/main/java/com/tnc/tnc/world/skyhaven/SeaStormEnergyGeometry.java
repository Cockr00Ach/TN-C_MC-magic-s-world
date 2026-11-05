package com.tnc.tnc.world.skyhaven;

import java.util.List;

/** World-space mana currents. Crossed soft ribbons keep their volume from every viewing angle. */
public final class SeaStormEnergyGeometry {
    private static final double TAU=Math.PI*2;
    private SeaStormEnergyGeometry(){}

    private static int strands(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->8;case MID->7;case FAR->5;};}
    private static int steps(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->28;case MID->20;case FAR->14;};}
    private static int feeders(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->6;case MID->5;case FAR->3;};}
    private static int seaSteps(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->20;case MID->16;case FAR->12;};}
    private static int tears(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->4;case MID->3;case FAR->2;};}
    private static int tearSteps(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->8;case MID->7;case FAR->6;};}
    private static int rings(SeaTornadoGeometry.Detail d){return d==SeaTornadoGeometry.Detail.FAR?1:2;}
    private static int ringSteps(SeaTornadoGeometry.Detail d){return switch(d){case NEAR->40;case MID->24;case FAR->20;};}
    public static int quads(SeaTornadoGeometry.Detail d){
        return 4*(strands(d)*steps(d)+feeders(d)*seaSteps(d)+tears(d)*tearSteps(d)+rings(d)*ringSteps(d));
    }
    public static int linkQuads(){return 2*20*4;}

    public static void build(SeaGyreLayout.Gyre g,double time,SeaTornadoGeometry.Detail detail,double opacity,SeaTornadoGeometry.Sink sink){
        if(!Double.isFinite(time)||!Double.isFinite(opacity)||opacity<=0)return;
        opacity=Math.min(1,opacity);
        for(int strand=0;strand<strands(detail);strand++)climbing(g,time,strand,steps(detail),opacity,sink);
        for(int arm=0;arm<feeders(detail);arm++)feeding(g,time,arm,seaSteps(detail),opacity,sink);
        for(int ring=0;ring<rings(detail);ring++)surge(g,time,ring,ringSteps(detail),opacity,sink);
        for(int tear=0;tear<tears(detail);tear++)shear(g,time,tear,tearSteps(detail),opacity,sink);
    }

    private static void climbing(SeaGyreLayout.Gyre g,double time,int strand,int steps,double opacity,SeaTornadoGeometry.Sink sink){
        double offset=g.phase()+strand*2.3999632297, speed=.34+(strand%4)*.055;
        for(int i=0;i<steps;i++){
            double t=.025+i*.93/steps,u=.025+(i+1)*.93/steps;
            double a=offset+time*g.direction()*speed+t*TAU*(1.10+(strand%3)*.22)+.12*Math.sin(t*17+time*.7+strand);
            double b=offset+time*g.direction()*speed+u*TAU*(1.10+(strand%3)*.22)+.12*Math.sin(u*17+time*.7+strand);
            double ra=flowRadius(g,t,a,time,strand),rb=flowRadius(g,u,b,time,strand);
            double pulse=.26+.74*Math.pow(.5+.5*Math.sin(t*19-time*(1.5+strand*.07)+offset),5);
            double envelope=Math.pow(Math.sin(Math.PI*t),.38);
            int color=strand%5==0?0x9ba8ef:strand%3==0?0xd49efa:0x975bdf;
            stroke(Math.cos(a)*ra,t*SeaTornadoGeometry.height(g),Math.sin(a)*ra,
                    Math.cos(b)*rb,u*SeaTornadoGeometry.height(g),Math.sin(b)*rb,
                    .65+(strand%3)*.22,3.4+(strand%3)*.5,color,(int)(245*pulse*envelope*opacity),sink);
        }
    }
    private static double flowRadius(SeaGyreLayout.Gyre g,double t,double angle,double time,int strand){
        // The outer flow stays beyond the folded black wall, with different orbital speeds.
        return SeaTornadoGeometry.radius(g,t)*(1.20+.09*Math.sin(angle*3-t*9+time*.31))
                +5+(strand%4)*2.6+Math.sin(t*18-time*.8+strand)*2;
    }
    private static void feeding(SeaGyreLayout.Gyre g,double time,int arm,int steps,double opacity,SeaTornadoGeometry.Sink sink){
        double offset=g.phase()+arm*2.3999632297+time*g.direction()*.13;
        for(int i=0;i<steps;i++){
            double t=i/(double)steps,u=(i+1)/(double)steps;
            double a=offset+t*TAU*.86+.05*Math.sin(t*27-time),b=offset+u*TAU*.86+.05*Math.sin(u*27-time);
            double ra=g.radius()*(2.12-1.88*t),rb=g.radius()*(2.12-1.88*u);
            double ya=2+12*t*t+3*Math.sin(t*15-time*1.2+arm)*Math.sin(Math.PI*t);
            double yb=2+12*u*u+3*Math.sin(u*15-time*1.2+arm)*Math.sin(Math.PI*u);
            double pulse=.32+.68*Math.pow(.5+.5*Math.sin(t*23-time*2.8+arm),5);
            int color=arm%4==0?0x78a7d7:0xa475e7;
            stroke(Math.cos(a)*ra,ya,Math.sin(a)*ra,Math.cos(b)*rb,yb,Math.sin(b)*rb,
                    .55+(arm%3)*.22,3.8,color,(int)(220*pulse*Math.pow(Math.sin(Math.PI*t),.30)*opacity),sink);
        }
    }
    private static void surge(SeaGyreLayout.Gyre g,double time,int ring,int steps,double opacity,SeaTornadoGeometry.Sink sink){
        double expansion=fract(time*.085+ring*.37+g.phase()/TAU);
        double radius=g.radius()*(.45+expansion*1.85);
        double strength=Math.pow(Math.sin(Math.PI*expansion),1.5);
        for(int i=0;i<steps;i++){
            double a=i*TAU/steps,b=(i+1)*TAU/steps;
            double ra=radius*(1+.08*Math.sin(a*5-time*.8+ring)),rb=radius*(1+.08*Math.sin(b*5-time*.8+ring));
            double broken=.12+.88*Math.pow(.5+.5*Math.sin(a*4+time*.7+ring),3);
            stroke(Math.cos(a)*ra,1.1+expansion*2,Math.sin(a)*ra,
                    Math.cos(b)*rb,1.1+expansion*2,Math.sin(b)*rb,
                    .38,3.3,0x8668c9,(int)(175*strength*broken*opacity),sink);
        }
    }
    private static void shear(SeaGyreLayout.Gyre g,double time,int tear,int steps,double opacity,SeaTornadoGeometry.Sink sink){
        double progress=fract(time*(.18+tear*.013)+tear*.173+g.phase()/TAU);
        double envelope=Math.pow(Math.sin(Math.PI*progress),2);
        double angle=g.phase()+tear*2.3999632297+time*g.direction()*.42;
        double level=.20+(tear%4)*.18;
        double start=SeaTornadoGeometry.radius(g,level)*1.15+8;
        for(int i=0;i<steps;i++){
            double t=i/(double)steps,u=(i+1)/(double)steps;
            double a=angle+t*.52,b=angle+u*.52;
            double ra=start+t*(16+g.radius()*.35)*progress,rb=start+u*(16+g.radius()*.35)*progress;
            double ya=level*SeaTornadoGeometry.height(g)+Math.sin(t*11+time*3+tear)*2+t*8;
            double yb=level*SeaTornadoGeometry.height(g)+Math.sin(u*11+time*3+tear)*2+u*8;
            stroke(Math.cos(a)*ra,ya,Math.sin(a)*ra,Math.cos(b)*rb,yb,Math.sin(b)*rb,
                    .55,3,0xd3a5ef,(int)(235*envelope*(1-t)*opacity),sink);
        }
    }

    /** Each root chooses at most two neighbours. The undirected union has at most 2N links. */
    public static boolean linked(List<SeaGyreLayout.Gyre> storms,int i,int j){
        if(i==j)return false;
        var a=storms.get(i);var b=storms.get(j);
        double distance=distance(a,b);
        if(distance>215||distance<1)return false;
        int rankA=0,rankB=0;
        for(int k=0;k<storms.size();k++){
            if(k!=i&&k!=j){
                double da=distance(a,storms.get(k)),db=distance(b,storms.get(k));
                if(da<distance||(da==distance&&precedes(storms.get(k),b)))rankA++;
                if(db<distance||(db==distance&&precedes(storms.get(k),a)))rankB++;
            }
        }
        return rankA<2||rankB<2;
    }
    private static double distance(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b){return Math.hypot(a.x()-b.x(),a.z()-b.z());}
    private static boolean precedes(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b){return a.x()<b.x()||(a.x()==b.x()&&a.z()<b.z());}

    /** Link vertices use world X/Z, relative to the same Y49 sea datum as the local effects. */
    public static void buildLink(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b,double time,double opacity,SeaTornadoGeometry.Sink sink){
        if(!Double.isFinite(time)||!Double.isFinite(opacity)||opacity<=0)return;
        opacity=Math.min(1,opacity);
        // Rendering order depends on the camera; a current's direction and phase must not.
        if(precedes(b,a)){var swap=a;a=b;b=swap;}
        double dx=b.x()-a.x(),dz=b.z()-a.z(),length=Math.hypot(dx,dz);
        if(length<1||length>215)return;
        double px=-dz/length,pz=dx/length,phase=a.phase()+b.phase();
        for(int strand=0;strand<2;strand++)for(int i=0;i<20;i++){
            double t=i/20.,u=(i+1)/20.;
            double swayT=Math.sin(Math.PI*t)*(Math.sin(t*9-time*.9+phase+strand)*7+(strand==0?14:-14));
            double swayU=Math.sin(Math.PI*u)*(Math.sin(u*9-time*.9+phase+strand)*7+(strand==0?14:-14));
            double yt=10+strand*5+Math.sin(Math.PI*t)*(23+Math.sin(phase)*7)+Math.sin(t*16-time*1.7)*3*Math.sin(Math.PI*t);
            double yu=10+strand*5+Math.sin(Math.PI*u)*(23+Math.sin(phase)*7)+Math.sin(u*16-time*1.7)*3*Math.sin(Math.PI*u);
            double pulse=.18+.82*Math.pow(.5+.5*Math.sin(t*24-time*(2+strand*.6)+phase),6);
            stroke(a.x()+dx*t+px*swayT,yt,a.z()+dz*t+pz*swayT,
                    a.x()+dx*u+px*swayU,yu,a.z()+dz*u+pz*swayU,
                    .6,3.8,strand==0?0xad7de8:0x718dd2,(int)(230*pulse*opacity),sink);
        }
    }

    /** Two crossed planes for the halo, two for the narrow traveling core: four quads. */
    private static void stroke(double ax,double ay,double az,double bx,double by,double bz,double width,double haloWidth,int rgb,int alpha,SeaTornadoGeometry.Sink sink){
        double dx=bx-ax,dy=by-ay,dz=bz-az;
        double horizontal=Math.hypot(dx,dz),length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double nx=horizontal>1e-6?-dz/horizontal:1,nz=horizontal>1e-6?dx/horizontal:0;
        double sx=length>1e-6?dy*nz/length:0,sy=length>1e-6?(dz*nx-dx*nz)/length:1,sz=length>1e-6?-dy*nx/length:0;
        plane(ax,ay,az,bx,by,bz,nx*haloWidth,0,nz*haloWidth,0x4d2485,(int)(alpha*.34),sink);
        plane(ax,ay,az,bx,by,bz,sx*haloWidth,sy*haloWidth,sz*haloWidth,0x4d2485,(int)(alpha*.34),sink);
        plane(ax,ay,az,bx,by,bz,nx*width,0,nz*width,rgb,alpha,sink);
        plane(ax,ay,az,bx,by,bz,sx*width,sy*width,sz*width,rgb,alpha,sink);
    }
    private static void plane(double ax,double ay,double az,double bx,double by,double bz,double nx,double ny,double nz,int rgb,int alpha,SeaTornadoGeometry.Sink sink){
        vertex(ax-nx,ay-ny,az-nz,0,0,rgb,alpha,sink);vertex(bx-nx,by-ny,bz-nz,0,1,rgb,alpha,sink);
        vertex(bx+nx,by+ny,bz+nz,1,1,rgb,alpha,sink);vertex(ax+nx,ay+ny,az+nz,1,0,rgb,alpha,sink);
    }
    private static void vertex(double x,double y,double z,float u,float v,int rgb,int alpha,SeaTornadoGeometry.Sink sink){
        // Include the ribbon's actual thickness in the bound, not just its centerline.
        sink.vertex(x,Math.max(.15,Math.min(119.5,y)),z,u,v,rgb,alpha,0,1,0);
    }
    private static double fract(double value){return value-Math.floor(value);}
}
