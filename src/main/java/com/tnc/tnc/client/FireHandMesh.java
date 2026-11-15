package com.tnc.tnc.client;

import java.util.ArrayList;
import java.util.List;
import static com.tnc.tnc.client.FireHandPose.*;

/** Anatomical plasma mesh; offline exports submit these exact same quads, not a separate concept model. */
public final class FireHandMesh {
    public enum Layer {STRUCTURE,PLASMA,CORONA}
    public record Color(float r,float g,float b,float a){}
    public record Quad(V a,V b,V c,V d,Color color,Layer layer){}
    private final List<Quad> quads=new ArrayList<>(2300);
    private final double age,heat,pulse,fade;
    private final FireFlamePalette.Palette palette;
    private final boolean ghost;
    private FireHandMesh(double age,double heat,int tier){palette=FireFlamePalette.forTier(tier);ghost=tier==4;this.age=age;this.heat=Math.max(0,Math.min(1,heat/100));pulse=FireHandPose.pulse(age);fade=smooth(age/6)*(1-smooth((age-67)/9));}
    public static List<Quad> build(double age,double heat,boolean aerial){return build(age,heat,aerial,3);}
    public static List<Quad> build(double age,double heat,boolean aerial,boolean blueGhost){return build(age,heat,aerial,blueGhost?4:3);}
    public static List<Quad> build(double age,double heat,boolean aerial,int tier){FireHandMesh m=new FireHandMesh(age,heat,tier);m.hand();if(aerial)m.feed();return m.quads;}
    private static V v(double x,double y,double z){return new V(x,y,z);}
    private double noise(V p){return Math.sin(p.x()*11.7+p.y()*7.3+p.z()*13.1)+.55*Math.sin(p.x()*25.3-p.y()*16.7+p.z()*9.1);}
    private Color hot(double strength,double alpha){
        double t=Math.max(0,Math.min(1,strength)),blend;int from,to;
        double bodyEnd=ghost?.65:palette==FireFlamePalette.ION?.78:.4;
        double glowEnd=ghost?.93:palette==FireFlamePalette.ION?.95:.82;
        if(t<bodyEnd){from=palette.fringe();to=palette.body();blend=t/bodyEnd;}
        else if(t<glowEnd){from=palette.body();to=palette.glow();blend=(t-bodyEnd)/(glowEnd-bodyEnd);}
        else{from=palette.glow();to=palette.core();blend=(t-glowEnd)/(1-glowEnd);}
        return new Color((float)(((from>>16&255)*(1-blend)+(to>>16&255)*blend)/255),
            (float)(((from>>8&255)*(1-blend)+(to>>8&255)*blend)/255),
            (float)(((from&255)*(1-blend)+(to&255)*blend)/255),(float)(alpha*fade));
    }
    private void add(V a,V b,V c,V d,Color color,Layer layer){if(color.a()>=.002)quads.add(new Quad(a,b,c,d,color,layer));}
    private void skin(V a,V b,V c,V d,double seed){
        V center=a.add(b).add(c).add(d).mul(.25);double flow=.5+.5*Math.sin(center.y()*10-age*.23*palette.motion()+center.x()*3+seed);
        double grain=noise(center),seam=Math.pow(Math.max(0,1-Math.abs(Math.sin(grain*2.6+seed*.17))),8);
        boolean island=grain+Math.sin(seed*1.37)>.18&&seam<.45;
        add(a,b,c,d,hot(.20+heat*.13+pulse*.17+flow*.14+seam*.36,(island?.07:.22)+flow*.10+pulse*.055),Layer.PLASMA);
        if(Math.sin(seed*17.13)>.72){
            V drift=v(Math.sin(age*.10+seed)*.035,.055+flow*.045,Math.cos(age*.13*palette.motion()+seed)*.035);
            add(a.add(drift),b.add(drift),c.add(drift),d.add(drift),hot(.28+flow*.20,.055),Layer.CORONA);
        }
        // Broken dark islands keep the silhouette readable; only the thin fissures approach white.
        if(island){double shade=.55+.45*flow;add(a,b,c,d,new Color((float)((ghost?.025:.18)*shade),(float)((ghost?.045:.045)*shade),(float)(ghost?.16:.025),(float)(fade*(.50+.15*flow))),Layer.STRUCTURE);}
        if(seam>.33)add(a,b,c,d,hot(.85+heat*.15,.12+seam*.22),Layer.PLASMA);
    }
    private void ellipsoid(V center,V radius,int sides,int rows,double seed){
        for(int row=0;row<rows;row++)for(int side=0;side<sides;side++)skin(
            ell(center,radius,side*Math.PI*2/sides,-Math.PI/2+row*Math.PI/rows),ell(center,radius,(side+1)*Math.PI*2/sides,-Math.PI/2+row*Math.PI/rows),
            ell(center,radius,(side+1)*Math.PI*2/sides,-Math.PI/2+(row+1)*Math.PI/rows),ell(center,radius,side*Math.PI*2/sides,-Math.PI/2+(row+1)*Math.PI/rows),seed+side*.23+row*.41);
    }
    private static V ell(V c,V r,double a,double b){return c.add(v(Math.cos(a)*Math.cos(b)*r.x(),Math.sin(b)*r.y(),Math.sin(a)*Math.cos(b)*r.z()));}
    private void tube(V[] points,double[] radii,int sides,double depth,double seed){
        V previous=null;
        for(int k=0;k<points.length-1;k++){
            V delta=points[k+1].sub(points[k]).unit(),ref=Math.abs(delta.y())>.9?v(1,0,0):v(0,1,0);
            V right=ref.cross(delta).unit(),up=delta.cross(right).unit();
            if(previous!=null&&right.x()*previous.x()+right.y()*previous.y()+right.z()*previous.z()<0){right=right.mul(-1);up=up.mul(-1);}previous=right;
            for(int side=0;side<sides;side++){
                double a=side*Math.PI*2/sides,b=(side+1)*Math.PI*2/sides;
                skin(ring(points[k],right,up,radii[k],depth,a),ring(points[k],right,up,radii[k],depth,b),ring(points[k+1],right,up,radii[k+1],depth,b),ring(points[k+1],right,up,radii[k+1],depth,a),seed+k*.73+side*.19);
            }
        }
    }
    private static V ring(V c,V right,V up,double r,double depth,double a){return c.add(right.mul(Math.cos(a)*r)).add(up.mul(Math.sin(a)*r*depth));}
    private void line(V a,V b,double width,double strength,double alpha){
        V n=b.sub(a).cross(v(0,0,1)).unit().mul(width),z=v(0,0,width);
        add(a.sub(n),a.add(n),b.add(n),b.sub(n),hot(strength,alpha),Layer.PLASMA);add(a.sub(z),a.add(z),b.add(z),b.sub(z),hot(strength,alpha*.7),Layer.PLASMA);
    }
    private void hand(){
        V[] arm=new V[9];double[] radii=new double[9];for(int i=0;i<9;i++){double t=i/8.;arm[i]=v(.035*Math.sin(t*5),-1.62+t*1.03,-.38);radii[i]=(.25+.11*Math.sin(t*Math.PI*.8))*1.7;}tube(arm,radii,14,.78,8);
        ellipsoid(v(0,-.04,-.52),v(.845,.64,.406),20,10,1);
        ellipsoid(v(-.56,-.25,-.23),v(.31,.40,.308),12,7,3);ellipsoid(v(.56,-.23,-.35),v(.28,.37,.266),10,6,7);
        for(int digit=0;digit<4;digit++)digit(FireHandPose.finger(age,digit),digit);digit(FireHandPose.thumb(age),4);
        // Four long metacarpal tendons, then three curved palm creases.
        for(int i=0;i<4;i++){V root=FireHandPose.finger(age,i)[0],a=v((i-1.5)*.075,-.66,-.77),b=v(root.x()*.73,-.12,-.94),c=root.add(v(0,0,-.15));line(a,b,.016,.65,.66);line(b,c,.024,.87,.72);}
        for(int row=0;row<3;row++)for(int i=0;i<8;i++){double t=i/8.,u=(i+1)/8.;line(v(-.58+t*1.14,-.32+row*.21+Math.sin(t*2.7)*.10,-.105),v(-.58+u*1.14,-.32+row*.21+Math.sin(u*2.7)*.10,-.105),.012,.9,.52);}
        // Phase-offset, split flame membranes give the outer silhouette a diffuse turbulent edge.
        for(int i=0;i<24;i++){double a=i*Math.PI*2/24,y=-.72+(i%7)*.24;flame(v(Math.cos(a)*(.51+(y+.72)*.22),y,-.52+Math.sin(a)*.40),i,.64+(i%4)*.15,.15);}
    }
    private void digit(V[] joints,int digit){
        double radius=digit==4?.222:1.35*(digit==1?.145:digit==3?.115:.135);
        for(int segment=0;segment<3;segment++){
            V a=joints[segment],b=joints[segment+1];V[] centers=new V[5];double[] r=new double[5];
            for(int i=0;i<5;i++){double t=i/4.;centers[i]=a.mix(b,t);r[i]=radius*(segment==0?1.05:segment==1?.94:.76)*(1+.11*Math.sin(t*Math.PI*2));}tube(centers,r,10,.83,30+digit*4+segment);
            V back=v(0,0,-radius*.88);line(a.add(back),b.add(back),.013,.88,.68);
            V direction=b.sub(a).unit(),right=v(1,0,0),up=direction.cross(right).unit();
            for(int side=0;side<5;side++){double aa=Math.PI*.12+side*Math.PI*.16,bb=aa+Math.PI*.16;line(ring(a.mix(b,.18),right,up,radius*1.07,.86,aa),ring(a.mix(b,.18),right,up,radius*1.07,.86,bb),.008,.78,.56);}
        }
        V tip=joints[3],dir=tip.sub(joints[2]).unit();tube(new V[]{tip.sub(dir.mul(.12)),tip,tip.add(dir.mul(.07))},new double[]{radius*.63,radius*.48,.006},10,.65,70+digit);flame(tip.add(v(0,0,-.05)),40+digit,.42,.075);
    }
    private void flame(V root,int seed,double height,double width){
        double time=age*.13*palette.motion()+seed*2.39996;V[] spine=new V[6];
        for(int i=0;i<6;i++){double t=i/5.;spine[i]=root.add(v(Math.sin(time-t*3.4)*t*.19,t*height*(1+.23*pulse+.15*heat),Math.cos(time*.83-t*4)*t*.13));}
        for(int side=0;side<2;side++)for(int i=0;i<5;i++){
            double t=i/5.,next=(i+1)/5.,flutter=.67+.33*Math.sin(time*1.7+i*1.9);V direction=side==0?v(1,0,.26):v(.22,0,1),a=direction.mul(width*(1-t)*flutter),b=direction.mul(width*(1-next)*flutter);
            add(spine[i].sub(a),spine[i].add(a),spine[i+1].add(b),spine[i+1].sub(b),hot(.48-.26*t+.19*pulse,(.16+.08*heat)*(1-t)),Layer.CORONA);
            if(i==3){V branch=spine[i].add(v(Math.sin(time)*.12,.18,Math.cos(time)*.12));add(spine[i].sub(a.mul(.5)),spine[i].add(a.mul(.5)),branch,branch,hot(.66,.12),Layer.CORONA);}
        }
    }
    private void feed(){for(int i=0;i<8;i++)for(int j=0;j<5;j++){double a=i*Math.PI/4+age*.035;flame(v(Math.cos(a)*.2,-1.7+j*.23,-.38+Math.sin(a)*.18),70+i*5+j,.5,.075);}}
}
