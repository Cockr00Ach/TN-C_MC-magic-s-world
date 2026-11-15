package com.tnc.tnc.client;

/** Pure hand rig shared by runtime and exported previews. All coordinates are relative to the grip. */
public final class FireHandPose {
    public record V(double x,double y,double z) {
        public V add(V p){return new V(x+p.x,y+p.y,z+p.z);}
        public V mul(double s){return new V(x*s,y*s,z*s);}
        public V sub(V p){return add(p.mul(-1));}
        public V cross(V p){return new V(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public V unit(){double n=length();return n<1e-8?new V(0,1,0):mul(1/n);}
        public V mix(V p,double t){return mul(1-t).add(p.mul(t));}
    }
    public static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static double scale(int tier){return tier>=5?4.4:.7+.3*Math.max(1,tier);}
    public static double emergence(double age){return -2.8*(1-smooth(age/14));}
    public static double pulse(double age){double out=0;for(int beat:new int[]{36,46,56}){double d=(age-beat)/2.1;out=Math.max(out,Math.exp(-d*d));}return out;}
    public static double curl(double age,int digit){
        double delay=digit*.65;
        if(age<14)return .32;
        if(age<24)return .32*(1-smooth((age-14)/10));
        if(age<35)return .88*smooth((age-24-delay)/(10-delay));
        if(age<62)return .87+.12*pulse(age);
        return .94*(1-smooth((age-62)/10));
    }
    public static double spread(double age){return smooth((age-10)/12)*(1-.86*smooth((age-24)/10));}
    /** Fingers curl in the YZ plane toward the palm, not sideways across each other. */
    public static V[] finger(double age,int digit){
        double[] roots={-.47,-.15,.18,.46},lengths={1.33,1.49,1.36,1.06};
        double spread=spread(age),curl=curl(age,digit);
        V[] joints=new V[4];joints[0]=new V(roots[digit]*1.30,.44+(digit==1?.06:0),-.40);
        double[] fractions={.43,.34,.23},angles={0,0,0};
        angles[0]=curl*1.18-.10;angles[1]=angles[0]+curl*1.45;angles[2]=angles[1]+curl*.92;
        for(int i=0;i<3;i++){double n=lengths[digit]*fractions[i];joints[i+1]=joints[i].add(new V((digit-1.45)*.10*spread*n,Math.cos(angles[i])*n,Math.sin(angles[i])*n));}
        return joints;
    }
    /** Independent opposable thumb travels across the front of the fist. */
    public static V[] thumb(double age){
        double c=smooth(curl(age,4));
        V[] open={new V(-.67,-.32,-.33),new V(-1.13,-.06,-.02),new V(-1.31,.27,.12),new V(-1.27,.55,.23)};
        V[] shut={new V(-.67,-.32,-.33),new V(-.77,-.03,.30),new V(-.30,.17,.68),new V(.14,.20,.65)};
        V[] result=new V[4];for(int i=0;i<4;i++)result[i]=open[i].mix(shut[i],c);return result;
    }
    private FireHandPose(){}
}
