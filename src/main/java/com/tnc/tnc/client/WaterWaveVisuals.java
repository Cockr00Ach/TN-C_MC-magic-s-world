package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.OceanWaveRules;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Curled surf meshes with a deep face, translucent lip and broken moving foam. */
final class WaterWaveVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int SEGMENTS=48;
    // Include the complete inward fold, rather than truncating the shared twelve-point profile.
    private static final int[] BREAKER_POINTS={0,2,4,6,7,8,10,11};
    private static final double[] ROLL_DEPTH={-.95,-.78,-.52,-.20,.35,.92,1.05,.89,.48};
    private static final double[] ROLL_HEIGHT={0,.13,.36,.65,.87,1,.96,.80,.64};

    static void breakingWave(VertexConsumer out,Matrix4f pose,double front,double height,double limit,double age,float alpha,float[] tint) {
        if(front<=0||height<=0||alpha<=0)return;
        Vec3[][] columns=new Vec3[SEGMENTS+1][BREAKER_POINTS.length];
        for(int i=0;i<=SEGMENTS;i++) {
            double angle=i*Math.PI*2/SEGMENTS;
            for(int j=0;j<BREAKER_POINTS.length;j++)
                columns[i][j]=breakerProfile(angle,BREAKER_POINTS[j],front,height,limit,age);
        }
        surf(out,pose,columns,4,6,height,age,alpha,tint);
    }

    static float[] frontTint(int slot) {
        float t=Math.min(1,slot/(float)Math.max(1,OceanWaveRules.MAX_FRONTS-1));
        return new float[]{.015F+t*.025F,.18F+t*.18F,.58F+t*.23F};
    }

    private static Vec3 breakerProfile(double angle,int point,double front,double height,double limit,double age) {
        double r=OceanWaveRules.profileRadius(point,front,height,limit);
        double y=OceanWaveRules.profileHeight(point,height)*OceanWaveRules.waveLift(angle,age);
        double ripple=Math.sin(angle*13-age*.09)*Math.sin(Math.PI*point/11)*Math.min(.22,height*.025);
        return polar(angle,Math.max(.15,Math.min(limit,r+ripple)),y);
    }

    static void rollingWave(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha,double bulk) {
        if(radius<=0||height<=0||alpha<=0)return;
        Vec3 right=WaterSpellRules.right(dir);
        Vec3[][] columns=new Vec3[SEGMENTS+1][ROLL_DEPTH.length];
        for(int i=0;i<=SEGMENTS;i++) {
            double angle=-Math.PI*.48+i*Math.PI*.96/SEGMENTS;
            for(int j=0;j<ROLL_DEPTH.length;j++)
                columns[i][j]=profile(center,dir,right,UP,angle,j/8D,radius,height,time,1.6*bulk);
        }
        surf(out,pose,columns,5,7,height,time,alpha,new float[]{.018F,.25F,.71F});
    }

    private static Vec3 profile(Vec3 center,Vec3 dir,Vec3 right,Vec3 up,double a,double t,double r,double h,double time,double depth) {
        double f=Math.max(0,Math.min(1,t))*8;int j=Math.min(7,(int)f);double u=f-j;
        double x=ROLL_DEPTH[j]+(ROLL_DEPTH[j+1]-ROLL_DEPTH[j])*u;
        double y=ROLL_HEIGHT[j]+(ROLL_HEIGHT[j+1]-ROLL_HEIGHT[j])*u;
        double taper=Math.pow(Math.max(0,Math.cos(a)),.7);
        double shimmer=1+.045*Math.sin(a*11-time*.11)+.018*Math.cos(a*23+time*.07);
        return center.add(right.scale(Math.sin(a)*r))
                .add(up.scale(.06+h*y*taper*shimmer))
                .add(dir.scale((Math.cos(a)*.35+x)*depth));
    }

    static void circularWave(VertexConsumer out,Matrix4f pose,double radius,double height,double age,float alpha) {
        if(radius<=0||height<=0||alpha<=0)return;
        Vec3[][] columns=new Vec3[SEGMENTS+1][BREAKER_POINTS.length];
        for(int i=0;i<=SEGMENTS;i++) {
            double a=i*Math.PI*2/SEGMENTS;
            for(int j=0;j<BREAKER_POINTS.length;j++) {
                double t=BREAKER_POINTS[j]/11D;
                double x=sample(ROLL_DEPTH,t),y=sample(ROLL_HEIGHT,t);
                columns[i][j]=polar(a,Math.max(.05,radius+x*Math.max(.22,height*.6)),
                        .08+height*y*(.94+.06*Math.sin(a*9-age*.11)));
            }
        }
        surf(out,pose,columns,4,6,height,age,alpha,new float[]{.015F,.27F,.70F});
    }
    private static double sample(double[] values,double t) {
        double f=t*(values.length-1);int i=Math.min(values.length-2,(int)f);
        return values[i]+(values[i+1]-values[i])*(f-i);
    }
    private static Vec3 polar(double a,double r,double y){return new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);}

    /** Single surface, fixed subdivision budget; foam follows the same curled geometry. */
    private static void surf(VertexConsumer out,Matrix4f pose,Vec3[][] columns,int crest,int lip,double height,double age,float alpha,float[] tint) {
        int count=columns.length-1;
        for(int i=0;i<count;i++) {
            var a=columns[i];var b=columns[i+1];
            for(int j=0;j<a.length-1;j++) {
                float rise=Math.min(1,j/(float)crest);
                float flow=(float)(.5+.5*Math.sin(i*.82+j*2.1-age*.11));
                float fold=j>=crest?.14F:0;
                WaterGeometry.quad(out,pose,a[j],b[j],b[j+1],a[j+1],
                        tint[0]+rise*.045F+fold*.3F,
                        tint[1]+rise*.28F+flow*.035F+fold,
                        Math.min(1,tint[2]+rise*.18F+flow*.025F),
                        alpha*(.42F+rise*.19F-fold*.5F));
            }
            // Uneven whitecaps wrap over the lip; gaps stop it reading as a glowing wire ring.
            if(i%5!=0) {
                double fa=.58+.14*Math.sin(i*1.7-age*.13),fb=.58+.14*Math.sin((i+1)*1.7-age*.13);
                Vec3 p=a[crest-1].lerp(a[crest],fa).add(0,.025,0);
                Vec3 q=b[crest-1].lerp(b[crest],fb).add(0,.025,0);
                Vec3 r=b[crest].lerp(b[lip],.45+fb*.35).add(0,.025,0);
                Vec3 s=a[crest].lerp(a[lip],.45+fa*.35).add(0,.025,0);
                float foam=alpha*(float)(.82+.16*Math.sin(i*2.1-age*.09)*Math.sin(i*2.1-age*.09));
                WaterGeometry.quad(out,pose,p,q,r,s,.94F,.99F,1,foam);
            }
            if(i%4==1) {
                Vec3 p=a[Math.max(1,crest-2)],q=a[crest-1];
                WaterGeometry.stroke(out,pose,UP,p.lerp(q,.2),q,Math.min(.055,height*.025),.66F,.91F,1,alpha*.45F);
            }
            if(i%6==2) {
                double phase=(age*.037+i*.173)%1;
                Vec3 p=a[crest].lerp(b[crest],.5);
                Vec3 forward=a[lip].subtract(a[crest]).multiply(1,0,1).normalize();
                Vec3 spray=p.add(forward.scale(phase*Math.min(1.6,height*.24)))
                        .add(0,Math.sin(phase*Math.PI)*Math.min(1.8,height*.28),0);
                double size=Math.min(.14,Math.max(.045,height*.02));
                WaterGeometry.stroke(out,pose,UP,spray,spray.add(0,size*2.5,0),size,.84F,.98F,1,alpha*(float)(1-phase)*.75F);
            }
        }
    }

    static void goldenBreakingStripes(VertexConsumer out,Matrix4f pose,double front,double height,double limit,double age,float alpha) {
        if(front<=0||height<=0||alpha<=0)return;
        for(int band:new int[]{2,5})for(int i=0;i<SEGMENTS;i++) {
            double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS;
            WaterGeometry.stroke(out,pose,UP,breakerProfile(a,band,front,height,limit,age),breakerProfile(b,band,front,height,limit,age),
                    .035,.81F,.60F,.20F,alpha*(band==5?.48F:.28F));
        }
    }
    static void goldenRollingStripes(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha,double bulk) {
        Vec3 right=WaterSpellRules.right(dir);
        for(int i=0;i<SEGMENTS;i++) {
            double a=-Math.PI*.48+i*Math.PI*.96/SEGMENTS,b=-Math.PI*.48+(i+1)*Math.PI*.96/SEGMENTS;
            for(double t:new double[]{.26,.52})
                WaterGeometry.stroke(out,pose,UP,profile(center,dir,right,UP,a,t,radius,height,time,1.6*bulk),
                        profile(center,dir,right,UP,b,t,radius,height,time,1.6*bulk),.025*bulk,.86F,.63F,.20F,alpha*.38F);
        }
    }
    static void rippleGlow(VertexConsumer out,Matrix4f pose,double radius,double age,float alpha) {
        for(int i=0;i<SEGMENTS;i++)if(i%4!=0) {
            double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS;
            WaterGeometry.stroke(out,pose,UP,polar(a,radius,.19),polar(b,radius,.19),.028,.42F,.86F,1,alpha*.30F);
        }
    }
    static void rollingGlow(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha,double bulk) {
        Vec3 right=WaterSpellRules.right(dir);
        for(int i=0;i<SEGMENTS;i++)if(i%5!=0) {
            double a=-Math.PI*.48+i*Math.PI*.96/SEGMENTS,b=-Math.PI*.48+(i+1)*Math.PI*.96/SEGMENTS;
            WaterGeometry.stroke(out,pose,UP,profile(center,dir,right,UP,a,.625,radius,height,time,1.6*bulk),
                    profile(center,dir,right,UP,b,.625,radius,height,time,1.6*bulk),.035,.65F,.91F,1,alpha*.24F);
        }
    }
    static void breakingGlow(VertexConsumer out,Matrix4f pose,double front,double height,double limit,double age,float alpha,float[] tint) {
        if(front<=0||height<=0||alpha<=0)return;
        for(int i=0;i<SEGMENTS;i++)if(i%5!=0) {
            double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS;
            WaterGeometry.stroke(out,pose,UP,breakerProfile(a,7,front,height,limit,age),breakerProfile(b,7,front,height,limit,age),
                    .045,.58F,.88F,1,alpha*.24F);
        }
    }
    static void crescent(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double r,double time,float alpha,double roll) {
        // Tapered three-dimensional water blade: pointed tips, a dark swept belly and a pearl cutting edge.
        Vec3[] basis=WaterGeometry.rolledBasis(dir,roll);Vec3 up=basis[1];
        int segments=36;double limit=Math.PI*.58;
        for(int i=0;i<segments;i++) {
            double a=-limit+i*limit*2/segments,b=-limit+(i+1)*limit*2/segments;
            double ta=i/(double)segments,tb=(i+1)/(double)segments;
            double wa=.035+.78*Math.pow(Math.sin(Math.PI*ta),1.45);
            double wb=.035+.78*Math.pow(Math.sin(Math.PI*tb),1.45);
            Vec3 p=center.add(WaterSpellRules.slashArc(dir,roll,a,r));
            Vec3 q=center.add(WaterSpellRules.slashArc(dir,roll,b,r));
            Vec3 v=p.subtract(dir.scale(wa)),u=q.subtract(dir.scale(wb));
            Vec3 thickness=up.scale(.10+.035*Math.sin(Math.PI*(ta+tb)*.5));
            WaterGeometry.quad(out,pose,p,q,u,v,.015F,.30F,.82F,alpha*.70F);
            WaterGeometry.quad(out,pose,p.subtract(thickness),v.subtract(thickness),u.subtract(thickness),q.subtract(thickness),.02F,.16F,.55F,alpha*.56F);
            WaterGeometry.quad(out,pose,p,q,q.subtract(thickness),p.subtract(thickness),.62F,.94F,1,alpha*.92F);
            WaterGeometry.stroke(out,pose,up,p,q,.040+.018*Math.sin(Math.PI*ta),.90F,.99F,1,alpha);
            if(i%6==2&&i>4&&i<segments-5) {
                double flutter=.10+.08*Math.sin(time*.32+i);
                Vec3 tail=v.subtract(dir.scale(.14+flutter));
                WaterGeometry.stroke(out,pose,up,v,tail,.028,.32F,.82F,1,alpha*.46F);
            }
        }
    }
    static void crescentGlow(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double r,double time,float alpha,double roll) {
        // 与 crescent 用同一套滚转基 ✓，否则亮线会和刃面错开 ✗
        Vec3[] basis=WaterGeometry.rolledBasis(dir,roll);Vec3 right=basis[0],up=basis[1];
        for(int i=0;i<24;i++) {
            double a=-Math.PI*.58+i*Math.PI*1.16/24,b=-Math.PI*.58+(i+1)*Math.PI*1.16/24;
            Vec3 p=center.add(WaterSpellRules.slashArc(dir,roll,a,r)),q=center.add(WaterSpellRules.slashArc(dir,roll,b,r));
            float taper=(float)Math.sin(Math.PI*(i+.5)/24);
            WaterGeometry.stroke(out,pose,up,p,q,.032+.022*taper,.82F,.98F,1,alpha*(.42F+.28F*taper));
            if(i%6==2)WaterGeometry.stroke(out,pose,up,p,p.subtract(dir.scale(.20+.18*taper)),.026,.45F,.90F,1,alpha*.35F);
        }
    }
}
