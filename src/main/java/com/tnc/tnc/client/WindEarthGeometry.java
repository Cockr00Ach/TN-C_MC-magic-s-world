package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Continuous airflow and faceted geology. Fixed mesh budgets at every tier. */
final class WindEarthGeometry {
    interface Ground {double y(double x,double z);}
    static final Ground FLAT=(x,z)->0;
    private static final int N=48;
    private static final double[] C=new double[N+1],S=new double[N+1];
    static {for(int i=0;i<=N;i++){C[i]=Math.cos(i*Math.PI*2/N);S[i]=Math.sin(i*Math.PI*2/N);}}
    private WindEarthGeometry(){}
    static void spell(VertexConsumer body,VertexConsumer glow,Matrix4f m,int kind,int tier,double r,double time,float alpha,Ground ground) {
        if(alpha<=0||r<=0)return;
        if(kind<3) {
            ring(glow,m,r*.86,.024,time*.035,alpha*.72F,147,226,222,ground);
            ring(body,m,r,.055,-time*.022,alpha*.42F,199,235,229,ground);
            feathers(glow,m,r,time,alpha,tier,ground);
            if(kind==0) {
                for(int i=0;i<2+tier/2;i++)helix(body,glow,m,.5+tier*.07,.35,1.9,time*.11+i*2.1,alpha*.6F,32);
                if(tier>=4) wings(body,glow,m,tier,time,alpha);
                for(int i=0;i<3;i++)stream(body,m,-.3+i*.3,.25+i*.43,-.3,-(1.3+tier*.22),time+i,alpha*.38F);
            } else if(kind==1) {
                for(int i=0;i<3;i++)helix(body,glow,m,r*.5,r*.3,1.4,time*.06+i*2.1,alpha*.55F,24);
            } else {
                for(int i=0;i<3;i++)arc(body,m,r*.95,time*.06+i*2.1,.08,alpha*.42F,195,226,225);
            }
            return;
        }
        ring(glow,m,r,.03,0,alpha*.65F,174,135,72,ground);
        if(kind==3) {seal(body,glow,m,r,tier,time,alpha);return;}
        if(kind==4) {
            cracks(body,glow,m,r,tier,time,alpha,ground);
            int count=tier==1?5:4+tier*2;
            for(int i=0;i<count;i++) {
                double angle=i*Math.PI*2/count, radius=r*(tier<4?.55:.77);
                double x=Math.cos(angle)*radius,z=Math.sin(angle)*radius;
                double h=tier==1?.14:.4+tier*.24;
                double rise=smooth(Math.min(1,time/(9+i%4)));
                rock(body,m,x,ground.y(x,z)+.025,z,.16+tier*.065,h*rise,angle,alpha,tier);
            }
            if(tier>=4)rift(body,glow,m,r,time,alpha,ground);
            if(tier==5) {
                double pulse=.35+.65*((time%45)/45);
                ring(glow,m,r*pulse,.06,0,alpha*(float)(1-pulse),204,157,81,ground);
                ring(body,m,r*(.92+.015*Math.sin(time*.12)),.12,0,alpha*.46F,125,106,75,ground);
            }
        } else if(kind==5) {
            pool(body,m,r,alpha,ground);
            ring(body,m,r*.95,.12,0,alpha*.9F,104,79,49,ground);
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4;
                double x=Math.cos(a)*r*.9,z=Math.sin(a)*r*.9;
                rock(body,m,x,ground.y(x,z),z,.22,.16+tier*.05,a,alpha,1);
            }
            if(tier<=3)for(int i=0;i<tier+1;i++)ring(body,m,r*(.2+.22*i)+Math.sin(time*.05+i)*.08,.03,-time*.012,alpha*.6F,118,94,61,ground);
            if(tier==4)for(int i=0;i<7;i++)mudFlow(body,m,r,(i-3)*r/4,time+i*7,alpha,ground);
            if(tier==5) {
                double lift=.5+smooth(Math.min(1,time/24))*1.8;
                rock(body,m,0,lift,0,.85,1.25,time*.008,alpha,4);
                for(int i=0;i<12;i++) {
                    double a=i*Math.PI/6-time*.024, orbit=1.25+.15*Math.sin(i);
                    rock(body,m,Math.cos(a)*orbit,lift+.25+Math.sin(a*2+i)*.35,Math.sin(a)*orbit,
                            .12+.06*(i%3),.2+.1*(i%2),a,alpha,3);
                }
                cracks(body,glow,m,r,3,time,alpha*.65F,ground);
            }
        } else {
            int count=4+tier*2;
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/count;
                double x=Math.cos(a)*r*.85,z=Math.sin(a)*r*.85;
                double h=tier==1?.28:tier==2?.85:1.0+(i%3)*.19;
                rock(body,m,x,ground.y(x,z),z,.22+tier*.025,h*smooth(Math.min(1,time/10)),a,alpha,tier>=4?3:2);
            }
            seal(body,glow,m,r,tier,time,alpha*.78F);
            if(tier>=4) {
                for(int i=0;i<8;i++) {
                    double a=i*Math.PI/4;
                    root(glow,m,a,r,time,alpha,ground);
                }
                ring(glow,m,r*.69,.025,0,alpha*.7F,154,185,102,ground);
            }
        }
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    static void tornado(VertexConsumer body,VertexConsumer glow,Matrix4f m,double scale,double phase,float alpha) {
        for(int i=0;i<3;i++)helix(body,glow,m,.844*scale,.25*scale,1.9375*scale,phase+i*Math.PI*2/3,alpha*.7F,64);
        ring(body,m,.8*scale,.055*scale,-phase,alpha*.5F,199,224,214,FLAT);
        ring(glow,m,.89*scale,.018*scale,phase,alpha*.42F,173,228,219,FLAT);
    }
    static void spirit(VertexConsumer body,VertexConsumer glow,Matrix4f m,double scale,int tier,double time) {
        if(tier<=2) {orb(body,glow,m,scale,time);return;}
        for(int i=0;i<(tier>=3?3:2);i++) {
            helix(body,glow,m,.4*scale,.23*scale,.9*scale,time*.075+i*2.1,.38F,24);
        }
        if(tier==5)ring(glow,m,.72*scale,.025,time*.04,.62F,223,240,178,FLAT);
    }
    private static void orb(VertexConsumer body,VertexConsumer glow,Matrix4f m,double scale,double time) {
        double radius=.45*scale;
        for(int plane=0;plane<3;plane++)for(int i=0;i<N;i++) {
            double a=i*Math.PI*2/N+time*.06,b=(i+1)*Math.PI*2/N+time*.06;
            double x=Math.cos(a)*radius,y=Math.sin(a)*radius,nx=Math.cos(b)*radius,ny=Math.sin(b)*radius;
            double w=.025*scale;
            if(plane==0) {
                quad(body,m,x,.4*scale-w,y,nx,.4*scale-w,ny,nx,.4*scale+w,ny,x,.4*scale+w,y,173,221,212,.48F);
                quad(glow,m,x,.4*scale,y,nx,.4*scale,ny,nx,.4*scale+w,ny,x,.4*scale+w,y,208,244,232,.7F);
            } else if(plane==1) {
                quad(body,m,x,y+.4*scale,-w,nx,ny+.4*scale,-w,nx,ny+.4*scale,w,x,y+.4*scale,w,173,221,212,.48F);
                quad(glow,m,x,y+.4*scale,0,nx,ny+.4*scale,0,nx,ny+.4*scale,w,x,y+.4*scale,w,208,244,232,.7F);
            } else {
                quad(body,m,-w,y+.4*scale,x,-w,ny+.4*scale,nx,w,ny+.4*scale,nx,w,y+.4*scale,x,173,221,212,.48F);
                quad(glow,m,0,y+.4*scale,x,0,ny+.4*scale,nx,w,ny+.4*scale,nx,w,y+.4*scale,x,208,244,232,.7F);
            }
        }
    }
    static void blade(VertexConsumer body,VertexConsumer glow,Matrix4f m,double time) {
        for(int i=0;i<28;i++) {
            double a=-1.46+i*2.92/28,b=-1.46+(i+1)*2.92/28;
            double wa=.018+.09*Math.sin((i+.0)/28*Math.PI),wb=.018+.09*Math.sin((i+1.0)/28*Math.PI);
            double ax=Math.sin(a)*.58,ay=-Math.cos(a)*.58+.22;
            double bx=Math.sin(b)*.58,by=-Math.cos(b)*.58+.22;
            double dx=Math.sin(a)*wa,dy=-Math.cos(a)*wa,ex=Math.sin(b)*wb,ey=-Math.cos(b)*wb;
            quad(body,m,ax-dx,ay-dy,0,bx-ex,by-ey,0,bx+ex,by+ey,0,ax+dx,ay+dy,0,181,231,224,.58F);
            quad(glow,m,ax+dx*.72,ay+dy*.72,.006,bx+ex*.72,by+ey*.72,.006,bx+ex,by+ey,.006,ax+dx,ay+dy,.006,218,249,240,.9F);
        }
        for(int i=0;i<2;i++)stream(body,m,-.15,.15-i*.3,-.15,-1.5,time+i,.26F);
    }
    private static void helix(VertexConsumer body,VertexConsumer glow,Matrix4f m,double bottom,double top,double height,double phase,float alpha,int segments) {
        for(int i=0;i<segments;i++) {
            double u=i/(double)segments,v=(i+1.0)/segments;
            double ra=bottom+(top-bottom)*u,rb=bottom+(top-bottom)*v;
            double a=-phase-u*Math.PI*2.8,b=-phase-v*Math.PI*2.8;
            double width=height*(.018+.027*Math.sin(u*Math.PI));
            double x=Math.cos(a)*ra,z=Math.sin(a)*ra,nx=Math.cos(b)*rb,nz=Math.sin(b)*rb;
            double y=height*u,ny=height*v;
            quad(body,m,x,y-width,z,nx,ny-width,nz,nx,ny+width,nz,x,y+width,z,181,214,204,alpha);
            quad(glow,m,x,y+width*.65,z,nx,ny+width*.65,nz,nx,ny+width,nz,x,y+width,z,178,239,227,alpha*.52F);
        }
    }
    private static void wings(VertexConsumer body,VertexConsumer glow,Matrix4f m,int tier,double time,float alpha) {
        for(int side:new int[]{-1,1})for(int i=0;i<4;i++) {
            double x=side*(.3+i*.28), y=1.3+i*.14, z=-.18-i*.13;
            quad(body,m,x,y,z,x+side*.6,y+.2,z-.2,x+side*.25,y-.05,z-.75,x,y-.09,z-.4,186,231,215,alpha*.32F);
            quad(glow,m,x,y,z,x+side*.6,y+.2,z-.2,x+side*.59,y+.17,z-.23,x,y-.025,z-.03,221,240,201,alpha*.55F);
        }
    }
    private static void stream(VertexConsumer out,Matrix4f m,double x,double y,double start,double end,double time,float alpha) {
        for(int i=0;i<16;i++) {
            double a=i/16.0,b=(i+1.0)/16;
            double ya=y+Math.sin(a*5-time*.15)*.06,yb=y+Math.sin(b*5-time*.15)*.06;
            double za=start+(end-start)*a,zb=start+(end-start)*b;
            quad(out,m,x,ya,za,x,yb,zb,x+.025,yb,zb,x+.05,ya,za,178,224,222,alpha*(float)(1-a));
        }
    }
    private static void ring(VertexConsumer out,Matrix4f m,double r,double width,double phase,float alpha,int red,int green,int blue,Ground ground) {
        double cp=Math.cos(phase),sp=Math.sin(phase);
        for(int i=0;i<N;i++) {
            double ca=C[i]*cp-S[i]*sp,sa=S[i]*cp+C[i]*sp,cb=C[i+1]*cp-S[i+1]*sp,sb=S[i+1]*cp+C[i+1]*sp;
            double x=ca*r,z=sa*r,nx=cb*r,nz=sb*r;
            quad(out,m,ca*(r-width),ground.y(x,z)+.045,sa*(r-width),cb*(r-width),ground.y(nx,nz)+.045,sb*(r-width),
                    cb*(r+width),ground.y(nx,nz)+.045,sb*(r+width),ca*(r+width),ground.y(x,z)+.045,sa*(r+width),red,green,blue,alpha);
        }
    }
    private static void feathers(VertexConsumer out,Matrix4f m,double r,double time,float alpha,int tier,Ground ground) {
        for(int i=0;i<6+tier;i++) {
            double a=i*Math.PI*2/(6+tier)+time*.025;
            double x=Math.cos(a)*r*.75,z=Math.sin(a)*r*.75,dx=Math.cos(a),dz=Math.sin(a);
            quad(out,m,x-dz*r*.02,ground.y(x,z)+.055,z+dx*r*.02,x+dx*r*.19,ground.y(x,z)+.055,z+dz*r*.19,
                    x+dz*r*.025,ground.y(x,z)+.055,z-dx*r*.025,x-dx*r*.13,ground.y(x,z)+.055,z-dz*r*.13,190,239,225,alpha*.7F);
        }
    }
    private static void arc(VertexConsumer out,Matrix4f m,double r,double phase,double width,float alpha,int red,int green,int blue) {
        for(int i=0;i<16;i++) {
            double a=phase+i*.08,b=phase+(i+1)*.08;
            quad(out,m,Math.cos(a)*(r-width),.1,Math.sin(a)*(r-width),Math.cos(b)*(r-width),.1,Math.sin(b)*(r-width),
                    Math.cos(b)*(r+width),.1,Math.sin(b)*(r+width),Math.cos(a)*(r+width),.1,Math.sin(a)*(r+width),red,green,blue,alpha*(1-i/17F));
        }
    }
    private static void seal(VertexConsumer body,VertexConsumer glow,Matrix4f m,double r,int tier,double time,float alpha) {
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            double x=Math.cos(a)*r*.74,z=Math.sin(a)*r*.74,nx=Math.cos(b)*r*.74,nz=Math.sin(b)*r*.74;
            line(glow,m,x,.065,z,nx,.065,nz,.023,192,151,77,alpha);
            double bx=Math.cos(a)*r*.88,bz=Math.sin(a)*r*.88;
            rock(body,m,bx,.03,bz,.06,.08,a,alpha,3);
        }
        ring(glow,m,r*.42,.018,0,alpha*.75F,164,126,65,FLAT);
        if(tier>=4)ring(glow,m,r*.61,.024,0,alpha*.7F,194,169,103,FLAT);
    }
    private static void rock(VertexConsumer out,Matrix4f m,double x,double y,double z,double radius,double height,double spin,float alpha,int tier) {
        int red=tier<=1?115:tier<=3?115:156, green=tier<=1?83:tier<=3?110:143,blue=tier<=1?49:tier<=3?92:112;
        for(int layer=0;layer<2;layer++)for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+spin,b=(i+1)*Math.PI/4+spin;
            double bottom=radius*(layer==0?1:.88),top=radius*(layer==0?.88:.35);
            double y0=y+height*layer*.58,y1=y+height*(layer==0?.58:1);
            double shade=.7+(i%3)*.12;
            quad(out,m,x+Math.cos(a)*bottom,y0,z+Math.sin(a)*bottom,x+Math.cos(b)*bottom,y0,z+Math.sin(b)*bottom,
                    x+Math.cos(b)*top,y1,z+Math.sin(b)*top,x+Math.cos(a)*top,y1,z+Math.sin(a)*top,
                    (int)(red*shade),(int)(green*shade),(int)(blue*shade),alpha);
        }
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+spin,b=(i+1)*Math.PI/4+spin;
            quad(out,m,x,y+height,z,x+Math.cos(a)*radius*.35,y+height,z+Math.sin(a)*radius*.35,
                    x+Math.cos(b)*radius*.35,y+height,z+Math.sin(b)*radius*.35,x,y+height,z,red+16,green+16,blue+12,alpha);
        }
    }
    private static void cracks(VertexConsumer body,VertexConsumer glow,Matrix4f m,double r,int tier,double time,float alpha,Ground ground) {
        for(int arm=0;arm<5+tier;arm++) {
            double angle=arm*Math.PI*2/(5+tier),x=0,z=0;
            for(int step=1;step<=5;step++) {
                double radial=r*.83*step/5,offset=Math.sin(step*2.5+arm)*r*.035;
                double nx=Math.cos(angle)*radial-Math.sin(angle)*offset,nz=Math.sin(angle)*radial+Math.cos(angle)*offset;
                line(body,m,x,ground.y(x,z)+.03,z,nx,ground.y(nx,nz)+.03,nz,.045,47,35,23,alpha*.85F);
                line(glow,m,x,ground.y(x,z)+.035,z,nx,ground.y(nx,nz)+.035,nz,.013,158,106,46,alpha*.38F);
                x=nx;z=nz;
            }
        }
    }
    private static void rift(VertexConsumer body,VertexConsumer glow,Matrix4f m,double r,double time,float alpha,Ground ground) {
        double x=0,z=-r*.8;
        for(int i=1;i<=14;i++) {
            double nx=Math.sin(i*1.7)*r*.035,nz=-r*.8+i*r*1.6/14;
            line(body,m,x,ground.y(x,z)+.035,z,nx,ground.y(nx,nz)+.035,nz,.17,37,29,23,alpha);
            line(glow,m,x-.1,ground.y(x,z)+.04,z,nx-.1,ground.y(nx,nz)+.04,nz,.026,192,132,59,alpha*.6F);
            x=nx;z=nz;
        }
    }
    private static void pool(VertexConsumer out,Matrix4f m,double r,float alpha,Ground ground) {
        for(int band=0;band<4;band++)for(int i=0;i<N;i++) {
            double ra=r*band/4,rb=r*(band+1.0)/4;
            double x=C[i]*ra,z=S[i]*ra,nx=C[i+1]*ra,nz=S[i+1]*ra;
            double xx=C[i]*rb,zz=S[i]*rb,nnx=C[i+1]*rb,nnz=S[i+1]*rb;
            quad(out,m,x,ground.y(x,z)+.026,z,nx,ground.y(nx,nz)+.026,nz,nnx,ground.y(nnx,nnz)+.026,nnz,xx,ground.y(xx,zz)+.026,zz,
                    54+band*7,40+band*6,28+band*4,alpha*.82F);
        }
    }
    private static void mudFlow(VertexConsumer out,Matrix4f m,double r,double x,double time,float alpha,Ground ground) {
        double z=-Math.sqrt(Math.max(0,r*r-x*x))*.8;
        for(int i=0;i<20;i++) {
            double nz=z+r*.075,xx=x+Math.sin(i*.4-time*.04)*.08;
            line(out,m,xx,ground.y(xx,z)+.045,z,xx,ground.y(xx,nz)+.045,nz,.03,131,100,60,alpha*.58F);
            z=nz;
        }
    }
    private static void root(VertexConsumer out,Matrix4f m,double angle,double r,double time,float alpha,Ground ground) {
        double x=0,z=0;
        for(int i=1;i<=7;i++) {
            double a=angle+Math.sin(i*1.4)*.13,nx=Math.cos(a)*r*i/8,nz=Math.sin(a)*r*i/8;
            line(out,m,x,ground.y(x,z)+.06,z,nx,ground.y(nx,nz)+.06,nz,.023,138,174,87,alpha*.8F);
            if(i>3)line(out,m,nx,ground.y(nx,nz)+.06,nz,nx-Math.sin(a)*.17,ground.y(nx,nz)+.22,nz+Math.cos(a)*.17,.045,118,165,79,alpha*.8F);
            x=nx;z=nz;
        }
    }
    private static void line(VertexConsumer out,Matrix4f m,double x,double y,double z,double nx,double ny,double nz,double width,int r,int g,int b,float alpha) {
        double dx=nx-x,dz=nz-z,length=Math.sqrt(dx*dx+dz*dz);
        if(length<1e-6)return;
        double ox=-dz/length*width,oz=dx/length*width;
        quad(out,m,x+ox,y,z+oz,nx+ox,ny,nz+oz,nx-ox,ny,nz-oz,x-ox,y,z-oz,r,g,b,alpha);
    }
    private static void quad(VertexConsumer out,Matrix4f m,double x,double y,double z,double x1,double y1,double z1,double x2,double y2,double z2,double x3,double y3,double z3,int r,int g,int b,float alpha) {
        int a=Math.max(0,Math.min(255,Math.round(alpha*255)));
        v(out,m,x,y,z,r,g,b,a);v(out,m,x1,y1,z1,r,g,b,a);v(out,m,x2,y2,z2,r,g,b,a);v(out,m,x3,y3,z3,r,g,b,a);
    }
    private static void v(VertexConsumer out,Matrix4f m,double x,double y,double z,int r,int g,int b,int a) {
        out.vertex(m,(float)x,(float)y,(float)z).color(r,g,b,a).endVertex();
    }
}
