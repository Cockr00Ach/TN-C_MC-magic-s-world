package com.tnc.tnc.fish.client;

import java.util.ArrayList;
import java.util.List;

/** A shared 3D drawing. Tail animation deforms these points, never copies whole frames. */
public final class FishSchoolLineArt {
    public record Point(double x,double y,double z) {}
    public record Stroke(List<Point> points,boolean accent) {}
    public record Triangle(Point a,Point b,Point c) {}
    public static final List<Stroke> STROKES;
    public static final List<Triangle> FILL;
    private static Point p(double x,double y,double z) { return new Point(x,y,z); }
    private static void stroke(List<Stroke> out,boolean accent,Point... points) { out.add(new Stroke(List.of(points),accent)); }
    static {
        List<Stroke> lines=new ArrayList<>();List<Triangle> fill=new ArrayList<>();
        for (int sign:new int[]{-1,1}) {
            Point[] rim={p(0,.01,.31),p(sign*.065,.105,.23),p(sign*.105,.14,.1),
                    p(sign*.115,.12,-.06),p(sign*.08,.07,-.19),p(sign*.025,.025,-.25),
                    p(sign*.025,-.025,-.25),p(sign*.07,-.075,-.16),p(sign*.1,-.1,.02),
                    p(sign*.09,-.085,.16),p(sign*.045,-.04,.27),p(0,.01,.31)};
            stroke(lines,false,rim);
            Point center=p(sign*.08,.005,.05);
            for (int i=0;i<rim.length-1;i++) fill.add(new Triangle(center,rim[i],rim[i+1]));
            stroke(lines,true,p(sign*.098,.057,.19),p(sign*.111,.073,.168),p(sign*.112,.052,.145),p(sign*.111,.035,.168),p(sign*.098,.057,.19));
            stroke(lines,false,p(sign*.09,.1,.13),p(sign*.119,.047,.095),p(sign*.11,-.015,.09),p(sign*.09,-.07,.12));
            stroke(lines,false,p(sign*.11,.04,.08),p(sign*.118,.025,-.015),p(sign*.1,.015,-.1),p(sign*.045,0,-.22));
            stroke(lines,true,p(sign*.11,.005,.07),p(sign*.16,-.045,-.035),p(sign*.09,-.025,-.07));
            fill.add(new Triangle(p(sign*.11,.005,.07),p(sign*.16,-.045,-.035),p(sign*.09,-.025,-.07)));
        }
        stroke(lines,false,p(0,.01,.31),p(0,.12,.21),p(0,.155,.06),p(0,.13,-.1),p(0,.025,-.25));
        stroke(lines,false,p(0,.01,.31),p(0,-.1,.13),p(0,-.12,-.03),p(0,-.08,-.17),p(0,-.025,-.25));
        stroke(lines,true,p(0,.145,.08),p(0,.215,-.01),p(0,.13,-.14),p(0,.145,.08));
        stroke(lines,false,p(0,.215,-.01),p(0,.14,-.055));
        fill.add(new Triangle(p(0,.145,.08),p(0,.215,-.01),p(0,.13,-.14)));
        stroke(lines,false,p(0,-.1,0),p(0,-.17,-.11),p(0,-.075,-.18));
        Point root=p(0,0,-.235),upper=p(0,.16,-.335),notch=p(0,0,-.292),lower=p(0,-.16,-.335);
        stroke(lines,true,root,upper,notch,lower,root);
        stroke(lines,false,p(0,.018,-.25),p(0,.085,-.31));stroke(lines,false,p(0,-.018,-.25),p(0,-.085,-.31));
        fill.add(new Triangle(root,upper,notch));fill.add(new Triangle(root,notch,lower));
        STROKES=List.copyOf(lines);FILL=List.copyOf(fill);
    }
    private FishSchoolLineArt() {}
    public static Point animate(Point p,double age,int lane,boolean metal) {
        double tail=Math.max(0,Math.min(1,(-p.z()+.025)/.36));
        double wave=Math.sin(age*(metal?.62:.84)+lane*2.399+p.z()*6);
        return new Point(p.x()+wave*tail*tail*(metal?.025:.04),p.y(),p.z());
    }
    public static int segments() { return STROKES.stream().mapToInt(s->s.points().size()-1).sum(); }
}
