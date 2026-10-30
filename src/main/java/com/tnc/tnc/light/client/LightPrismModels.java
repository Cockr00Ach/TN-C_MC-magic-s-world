package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Cached real geometry, standard NEW_ENTITY vertices; no custom shader or particle shell. */
public final class LightPrismModels {
    public record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, Vec3 normal) {
        public Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){this(a,b,c,d,color,b.subtract(a).cross(c.subtract(a)).normalize());}
    }
    public static final int GOLD=0xb98e42, IVORY=0xeae1ca, DARK=0x302638;
    private LightPrismModels() {}
    public static List<Face> mirror(int tier) {
        List<Face> mesh=new ArrayList<>();
        // Curved silver backing, engraved optical rings, chased gold ribs and openwork curls.
        for(int side:new int[]{-1,1}) {
            for(int ring=0;ring<8;ring++)for(int i=0;i<32;i++) {
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16,r=ring/8.,s=(ring+1)/8.;
                int color=tint(0x779caf,.72+ring*.047+.09*Math.sin(a*2+ring*.7));
                Vec3 p=oval(a,r,side*.085),q=oval(b,r,side*.085),u=oval(b,s,side*.085),v=oval(a,s,side*.085);
                mesh.add(side>0?new Face(p,v,u,q,color):new Face(p,q,u,v,color));
            }
            for(int band=0;band<5;band++) ring(mesh,.76+band*.036,1.13+band*.045,side*(.09+band*.009),band==2?.031:.011,band%2==0?GOLD:0x514434,48);
            for(int i=0;i<24;i++) {
                double a=i*Math.PI/12;
                rod(mesh,oval(a,.91,side*.105),oval(a,1.0,side*.12),.008,0xead7a0);
            }
            ring(mesh,.48,.73,side*.1,.006,0xc0dbe2,48);
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6;
                Vec3 at=oval(a,1.12,side*.13);
                gem(mesh,at,.045,.075,.04,i%3==0?0xf5deb0:0x68543a);
                // S-scrolls remain detached from the glass; genuine holes between metalwork.
                Vec3 last=null;
                for(int j=0;j<=12;j++) {
                    double t=j/12.,curl=a+t*2.1,rad=1.1+Math.sin(t*Math.PI)*.17;
                    Vec3 next=oval(curl,rad,side*.08);
                    if(last!=null)rod(mesh,last,next,.009,j%3==0?0xf1d8a0:GOLD);last=next;
                }
            }
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4;
                rod(mesh,oval(a,1.03,0),oval(a,1.35,0),.018,0x58412c);
                gem(mesh,oval(a,1.31,0),.055,.14,.065,GOLD);
            }
        }
        for(int i=0;i<tier+2;i++) {
            double a=(i+.5)*Math.PI*2/(tier+2);
            Vec3 at=oval(a,1.33,0);
            gem(mesh,at,.075,.16,.08,com.tnc.tnc.light.LightMirrorRules.color(i));
            ring(mesh,.09,.19,.005,.008,GOLD,12,at);
        }
        // Glass already has a polar material grid; avoid subdividing its thin annuli again.
        return List.copyOf(mesh);
    }
    public static List<Face> acolyteBody() {
        List<Face> m=new ArrayList<>();
        box(m,-.27,.65,-.15,.54,.92,.3,IVORY);
        // Four open robe panels, layered seams and gold filigree instead of one blank slab.
        for(int i=0;i<6;i++) {
            double x=-.34+i*.112;
            box(m,x,.2,-.22,.105,.62,.44,i%2==0?0xbcb5b3:IVORY);
            box(m,x+.038,.24,-.233,.018,.56,.025,GOLD);
        }
        box(m,-.29,1.05,-.18,.58,.13,.36,GOLD);
        box(m,-.1,.7,-.19,.2,.88,.045,DARK);
        for(int i=0;i<7;i++) {
            box(m,-.025,.77+i*.103,-.224,.05,.035,.032,GOLD);
            box(m,-.23,1.15+i*.045,-.18,.1,.018,.028,0xa99465);
            box(m,.13,1.15+i*.045,-.18,.1,.018,.028,0xa99465);
        }
        for(int side:new int[]{-1,1}) {
            box(m,side*.3-.11,1.43,-.2,.22,.15,.4,DARK);
            box(m,side*.3-.1,1.55,-.21,.2,.045,.42,GOLD);
            for(int layer=0;layer<3;layer++) {
                double x=side*(.25+layer*.037),y=1.53-layer*.062;
                gem(m,new Vec3(x,y,-.13),.145-layer*.014,.105,.14,layer%2==0?0x9a8152:0x4c4142);
                rod(m,new Vec3(x-.1,y,-.265),new Vec3(x+.1,y,-.265),.008,0xd6c096);
            }
            // Split stoles, needlework and heraldic vines down both sides of the robe.
            for(int j=0;j<13;j++) {
                double y=.26+j*.093,x=side*(.22+.018*Math.sin(j*.7));
                rod(m,new Vec3(x,y,-.248),new Vec3(x+side*.029,y+.043,-.25),.005,0xb39b67);
                if(j<8)rod(m,new Vec3(side*.14,1.16+j*.046,-.194),new Vec3(side*.24,1.18+j*.046,-.195),.004,0x706147);
            }
        }
        // Segmented mantle, tapered hanging cloth, belt clasps and a raised sun reliquary.
        for(int panel=0;panel<7;panel++) {
            double x=(panel-3)*.086;
            rod(m,new Vec3(x,1.5,.18),new Vec3(x*1.2,.31+Math.abs(panel-3)*.035,.28),.04,panel%2==0?0x675c63:0x8a807b);
            for(int y=0;y<10;y++)box(m,x-.027,.42+y*.095,.323,.054,.006,.008,0x9c8d78);
        }
        ring(m,.095,.095,-.228,.012,GOLD,16,new Vec3(0,1.34,0));
        gem(m,new Vec3(0,1.34,-.25),.045,.065,.04,0xdde6e1);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;rod(m,new Vec3(Math.cos(a)*.11,1.34+Math.sin(a)*.11,-.235),new Vec3(Math.cos(a)*.14,1.34+Math.sin(a)*.14,-.235),.006,GOLD);}
        gem(m,new Vec3(0,1.1,-.235),.083,.07,.035,GOLD);
        for(int i=0;i<5;i++)gem(m,new Vec3((i-2)*.1,1.065,-.205),.027,.033,.026,0xdbc49b);
        return finish(m);
    }
    public static List<Face> acolyteHead() {
        List<Face> m=new ArrayList<>();
        // Eight-panel hood instead of a naked cube; layered cheek guards frame the visor.
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            Vec3 p=new Vec3(Math.cos(a)*.205,.015,Math.sin(a)*.19),q=new Vec3(Math.cos(b)*.205,.015,Math.sin(b)*.19);
            m.add(new Face(p,p.scale(.88).add(0,.38,0),q.scale(.88).add(0,.38,0),q,i%2==0?0xb8ad94:IVORY));
            rod(m,p,q,.009,0x6e6048);
        }
        box(m,-.155,.06,-.205,.31,.24,.025,DARK);
        box(m,-.11,.18,-.223,.075,.024,.015,0xffedbd);
        box(m,.035,.18,-.223,.075,.024,.015,0xffedbd);
        box(m,-.026,.065,-.237,.052,.2,.034,GOLD);
        for(int side:new int[]{-1,1}) {
            rod(m,new Vec3(side*.16,.28,-.22),new Vec3(side*.11,.04,-.25),.022,GOLD);
            rod(m,new Vec3(side*.13,.23,-.244),new Vec3(side*.033,.21,-.252),.014,0xe3d1a4);
            for(int i=0;i<4;i++)box(m,side*.12-.025,.065+i*.035,-.25,.05,.008,.018,0x9b855b);
        }
        for(int i=0;i<7;i++) {
            double x=(i-3)*.061;
            gem(m,new Vec3(x,.39+(3-Math.abs(i-3))*.027,-.12),.034,.095+(.014*(3-Math.abs(i-3))),.042,i%2==0?GOLD:0xf0dfb1);
        }
        gem(m,new Vec3(0,.32,-.218),.033,.055,.022,0xd9f6f0);
        ring(m,.34,.38,.225,.014,0x725332,40,new Vec3(0,.25,0));
        ring(m,.38,.42,.225,.009,0xe9d4a2,40,new Vec3(0,.25,0));
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6;
            Vec3 start=new Vec3(Math.cos(a)*.37,.25+Math.sin(a)*.41,.225);
            rod(m,start,new Vec3(Math.cos(a)*.44,.25+Math.sin(a)*.49,.225),.009,GOLD);
            gem(m,start,.017,.024,.018,0xf7e8b9);
        }
        return finish(m);
    }
    public static List<Face> limb(boolean arm) {
        List<Face> m=new ArrayList<>();
        box(m,-.09,-(arm?.55:.35),-.1,.18,arm?.55:.35,.2,arm?IVORY:DARK);
        box(m,-.098,arm?-.49:-.31,-.108,.196,.06,.216,GOLD);
        if(arm) box(m,-.065,-.64,-.07,.13,.1,.14,0xc4b491);
        for(int i=0;i<(arm?7:4);i++) {
            double y=-.05-i*.065;
            box(m,-.096,y,-.116,.192,.012,.015,i%2==0?0x9d8860:0x66594e);
            for(int side:new int[]{-1,1})rod(m,new Vec3(side*.063,y,-.12),new Vec3(side*.035,y-.03,-.12),.004,0xead7a8);
        }
        if(arm)for(int i=0;i<4;i++)box(m,-.057+i*.031,-.633,-.09,.023,.064,.032,0x9d927d);
        return finish(m);
    }
    private static Vec3 oval(double a,double r,double z){return new Vec3(Math.cos(a)*.73*r,Math.sin(a)*1.1*r,z);}
    private static void ring(List<Face> m,double rx,double ry,double z,double width,int color,int segments){ring(m,rx,ry,z,width,color,segments,Vec3.ZERO);}
    private static void ring(List<Face> m,double rx,double ry,double z,double width,int color,int segments,Vec3 at){
        for(int i=0;i<segments;i++){double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            rod(m,at.add(Math.cos(a)*rx,Math.sin(a)*ry,z),at.add(Math.cos(b)*rx,Math.sin(b)*ry,z),width,color);}
    }
    private static void gem(List<Face> m,Vec3 p,double x,double y,double z,int color){
        Vec3 top=p.add(0,y,0),bottom=p.add(0,-y,0);
        Vec3[] rim={p.add(x,0,0),p.add(0,0,z),p.add(-x,0,0),p.add(0,0,-z)};
        for(int i=0;i<4;i++){Vec3 a=rim[i],b=rim[(i+1)%4];m.add(new Face(top,b,a,top,tint(color,.8+i*.07)));m.add(new Face(bottom,a,b,b,tint(color,.72+i*.06)));}
    }
    private static int tint(int color,double amount){int r=(int)Math.min(255,(color>>16&255)*amount),g=(int)Math.min(255,(color>>8&255)*amount),b=(int)Math.min(255,(color&255)*amount);return r<<16|g<<8|b;}
    /** Bake fine material variation once. Broad cloth/metal faces no longer have one flat fill. */
    private static List<Face> finish(List<Face> faces){
        List<Face> out=new ArrayList<>();
        for(Face f:faces){
            double w=f.b.distanceTo(f.a),h=f.d.distanceTo(f.a);
            int nx=w*h>.018?Math.min(12,Math.max(1,(int)Math.ceil(w/.055))):1;
            int ny=w*h>.018?Math.min(20,Math.max(1,(int)Math.ceil(h/.055))):1;
            for(int i=0;i<nx;i++)for(int j=0;j<ny;j++){
                Vec3 a=surface(f,i/(double)nx,j/(double)ny),b=surface(f,(i+1.)/nx,j/(double)ny),c=surface(f,(i+1.)/nx,(j+1.)/ny),d=surface(f,i/(double)nx,(j+1.)/ny);
                double grain=Math.sin(a.x*187+a.z*213)*Math.sin(a.y*171)*.024;
                double seam=(i%4==0||j%6==0)&&nx>2&&ny>2?-.055:0;
                if(b.subtract(a).cross(c.subtract(a)).lengthSqr()>1E-14)
                    out.add(new Face(a,b,c,d,tint(f.color,.96+grain+seam)));
            }
        }
        return List.copyOf(out);
    }
    private static Vec3 surface(Face f,double u,double v){return f.a.scale((1-u)*(1-v)).add(f.b.scale(u*(1-v))).add(f.c.scale(u*v)).add(f.d.scale((1-u)*v));}
    private static void box(List<Face> m,double x,double y,double z,double w,double h,double d,int color) {
        Vec3 a=new Vec3(x,y,z),b=new Vec3(x+w,y,z),c=new Vec3(x+w,y+h,z),e=new Vec3(x,y+h,z);
        Vec3 f=a.add(0,0,d),g=b.add(0,0,d),j=c.add(0,0,d),k=e.add(0,0,d);
        m.add(new Face(e,c,b,a,color));m.add(new Face(f,g,j,k,color));m.add(new Face(a,f,k,e,color));
        m.add(new Face(c,j,g,b,color));m.add(new Face(e,k,j,c,color));m.add(new Face(b,g,f,a,color));
    }
    private static void rod(List<Face> m,Vec3 a,Vec3 b,double r,int color) {
        Vec3 axis=b.subtract(a).normalize(),u=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(r);
        Vec3 v=axis.cross(u).normalize().scale(r);
        Vec3[] near={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v)};
        Vec3 delta=b.subtract(a);
        for(int i=0;i<4;i++) { Vec3 p=near[i],q=near[(i+1)%4]; m.add(new Face(p,q,q.add(delta),p.add(delta),color)); }
        m.add(new Face(near[3],near[2],near[1],near[0],color));
        m.add(new Face(near[0].add(delta),near[1].add(delta),near[2].add(delta),near[3].add(delta),color));
    }
    public static void render(List<Face> mesh,PoseStack pose,VertexConsumer out,int light) {
        for(Face f:mesh) {
            Vec3 normal=f.normal;
            vertex(f.a,0,0,f.color,normal,pose,out,light);vertex(f.b,1,0,f.color,normal,pose,out,light);
            vertex(f.c,1,1,f.color,normal,pose,out,light);vertex(f.d,0,1,f.color,normal,pose,out,light);
        }
    }
    private static void vertex(Vec3 p,float u,float v,int color,Vec3 n,PoseStack pose,VertexConsumer out,int light) {
        out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color>>16&255,color>>8&255,color&255,255)
                .uv(.25F+u*.5F,.25F+v*.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
