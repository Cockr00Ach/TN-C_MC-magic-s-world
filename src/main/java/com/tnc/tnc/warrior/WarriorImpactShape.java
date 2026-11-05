package com.tnc.tnc.warrior;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Local XY mesh, transformed to the camera plane or horizontal sweep plane. */
public final class WarriorImpactShape {
    public record Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha){}
    private WarriorImpactShape(){}
    public static int duration(int kind){return kind==2?10:kind==3?5:kind==0?4:kind==4?11:7;}
    public static List<Quad> build(int kind,float t,int color){
        return build(kind,t,color,0,true);
    }
    public static List<Quad> build(int kind,float t,int color,int seed,boolean metal){
        if(!Float.isFinite(t)||t<0||t>=1)return List.of();
        if(kind==0||kind==3)return contact(kind==3,t,color,seed,metal);
        var out=new ArrayList<Quad>();float fade=(1-t)*(1-t);
        if(kind==2){
            ring(out,.5+3.5*t,.18,color,fade,32);
            ring(out,.4+3.1*t,.045,0xFFF8DF,fade*.8f,32);
            return out;
        }
        double radius=(kind==1?.65:kind==3?.7:kind==4?.8:.43)*(1+t*.75);
        if(kind==1){ring(out,radius,.10,color,fade,4);ring(out,radius*.72,.045,0xFFF8DF,fade,4);}
        else if(kind==4){
            for(int i=0;i<4;i++){
                double a=Math.PI/4+i*Math.PI/2;var center=radial(a,radius*(.4+t));
                var r=radial(a,.20);var side=radial(a+Math.PI/2,.12);
                out.add(new Quad(center.add(r).add(side),center.subtract(r).add(side),center.subtract(r).subtract(side),center.add(r).subtract(side),color,fade));
            }
        } else ring(out,radius,.045,color,fade*.55f,4);
        int spikes=kind==3?6:4;
        for(int i=0;i<spikes;i++){
            double angle=Math.PI/4+i*Math.PI*2/spikes;
            double length=radius*(kind==3?(i%2==0?1.9:1.25):1.6);
            var axis=radial(angle,1);var side=radial(angle+Math.PI/2,kind==3?.095:.055);
            var base=axis.scale(.08);var tip=axis.scale(length);
            out.add(new Quad(base.add(side),tip,base.subtract(side),Vec3.ZERO,0xFFF8DF,fade));
        }
        return out;
    }
    // Seeded, asymmetric slivers in a surface-local frame. No camera-facing star or ring.
    private static List<Quad> contact(boolean heavy,float t,int color,int seed,boolean metal){
        var out=new ArrayList<Quad>();var random=new Random(seed);
        double angle=random.nextDouble()*Math.PI;
        var axis=radial(angle,1);var side=radial(angle+Math.PI/2,1);
        float fade=(1-t)*(1-t);
        double length=(heavy?.38:.24)*(1+.4*t),width=.032*(1-t);
        if(t<.45){
            float flash=(float)(1-t/.45);
            var a=axis.scale(-length);var b=axis.scale(length);
            out.add(new Quad(a,b.add(side.scale(width)),b,a.subtract(side.scale(width)),color,flash*.85f));
            out.add(new Quad(a.scale(.65),b.scale(.65).add(side.scale(width*.4)),b.scale(.65),a.scale(.65).subtract(side.scale(width*.4)),0xFFF1CE,flash));
        }
        int count=metal?(heavy?12:8):(heavy?6:4);
        for(int i=0;i<count;i++){
            double a=angle+(random.nextDouble()-.5)*1.7+(i%3==0?Math.PI:0);
            double speed=(.18+random.nextDouble()*.44)*(heavy?1.25:1)*(metal?1:.65);
            var direction=radial(a,1);
            double travel=speed*(1-Math.exp(-3*t));
            var head=direction.scale(.025+travel).add(0,-.12*t*t,(.02+random.nextDouble()*.18)*t);
            double streak=(.035+random.nextDouble()*.11)*(1-t);
            var tail=head.subtract(direction.scale(streak));
            var across=radial(a+Math.PI/2,(.006+random.nextDouble()*.008)*(1-t));
            int tint=metal?(t<.22?0xFFF1C1:t<.55?0xE9AE57:0x90603C):color;
            out.add(new Quad(head,tail.add(across),tail.subtract(across),head,tint,fade));
            // A second, angled face makes the fragment visible from grazing angles.
            var depth=new Vec3(0,0,across.length());
            out.add(new Quad(head,tail.add(depth),tail.subtract(depth),head,tint,fade*.7f));
        }
        return out;
    }
    private static Vec3 radial(double angle,double radius){return new Vec3(Math.cos(angle)*radius,Math.sin(angle)*radius,0);}
    private static void ring(List<Quad> out,double radius,double width,int color,float alpha,int segments){
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            out.add(new Quad(radial(a,radius),radial(b,radius),radial(b,radius-width),radial(a,radius-width),color,alpha));
        }
    }
}
