package com.tnc.tnc.magic.water;

/** Shared travelling breakers, not an always-damaging invisible cylinder. */
public final class OceanWaveRules {
    public static final double RADIUS=64, HEIGHT=40, SPEED=1.4;
    public static final int LIFE=600, PERIOD=16, MAX_FRONTS=4;
    private static final double[] RADIAL={-.32,-.28,-.23,-.17,-.09,0,.10,.19,.25,.27,.24,.16};
    private static final double[] VERTICAL={0,.08,.22,.40,.60,.80,.94,1,.99,.93,.83,.72};
    private OceanWaveRules() {}
    public static double frontRadius(double age,int slot) {
        if(age<0 || age>=LIFE || slot<0 || slot>=MAX_FRONTS)return -1;
        double frontAge=age%PERIOD+slot*PERIOD;
        if(frontAge>age)return -1;
        double r=2+frontAge*SPEED;return r<=RADIUS?r:-1;
    }
    public static double frontHeight(double radius) {return HEIGHT*Math.min(1,Math.max(0,radius)/12);}
    public static int profilePoints(){return RADIAL.length;}
    public static double profileRadius(int i,double front,double height,double limit) {
        return Math.max(.15,Math.min(limit,front+RADIAL[i]*height));
    }
    public static double profileHeight(int i,double height){return .1+VERTICAL[i]*height;}
    public static double waveLift(double angle,double time){return .92+.08*Math.sin(angle*7+time*.16);}
    public static boolean hits(double distance,double height,double age) {
        return hits(distance,height,0,age);
    }
    public static boolean hits(double distance,double height,double angle,double age) {
        if(distance<0 || distance>RADIUS || height<-.25)return false;
        for(int slot=0;slot<MAX_FRONTS;slot++) {
            double r=frontRadius(age,slot);if(r<0)continue;double h=frontHeight(r);
            double top=-1;
            for(int i=0;i<RADIAL.length-1;i++) {
                double a=profileRadius(i,r,h,RADIUS),b=profileRadius(i+1,r,h,RADIUS);
                if(distance<Math.min(a,b) || distance>Math.max(a,b))continue;
                double ya=profileHeight(i,h),yb=profileHeight(i+1,h);
                double y=Math.abs(b-a)<1e-6?Math.max(ya,yb):ya+(yb-ya)*(distance-a)/(b-a);
                top=Math.max(top,y*waveLift(angle,age+r*.1));
            }
            if(top>=0 && height<=top+.25)return true;
        }
        return false;
    }
}
