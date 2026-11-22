package com.tnc.tnc.mana.sight.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Translucent splats sampled across curved skin, fin membranes and soft tissue in body lengths. */
final class SpiritShape {
    enum Kind { LANTERN, FANG, RIBBON, SUNFISH, JELLY, OCTOPUS, WHALE, FRY }
    enum Ink { SKIN, BELLY, FIN, IRIS, SHADE }
    record Grain(double x,double y,double z,double radius,Ink ink,double opacity) {}
    private final List<Grain> grains=new ArrayList<>();
    private final Random random;

    private SpiritShape(Kind kind){random=new Random(7319L+kind.ordinal()*31L);}

    static Grain[] make(Kind kind){
        var s=new SpiritShape(kind);
        switch(kind){
            case WHALE -> s.whale();
            case JELLY,OCTOPUS -> s.medusa(kind==Kind.OCTOPUS);
            case RIBBON -> s.ribbon();
            case FRY -> s.fry();
            default -> s.fish(kind);
        }
        // Interleave near/far skin and all the membranes. The coarser LOD still samples every face.
        java.util.Collections.shuffle(s.grains,s.random);
        return s.grains.toArray(Grain[]::new);
    }

    private double unit(){return random.nextDouble();}
    private double signed(){return unit()*2-1;}
    private void grain(double x,double y,double z,double size,Ink ink,double alpha){
        grains.add(new Grain(x,y,z,size*(.74+.56*unit()),ink,alpha*(.72+.45*unit())));
    }

    // A soft skin made of irregular islands of colour. Broad splats establish the face,
    // fine motes catch the current. Stray grains soften its edge when seen against the sky.
    private void skin(double[][] stations,int samples,double radius) {
        double start=stations[0][0],end=stations[stations.length-1][0];
        for(int i=0;i<samples;i++){
            double x=start+(end-start)*unit();
            int j=1;
            while(j<stations.length-1 && x>stations[j][0])j++;
            double[] a=stations[j-1],b=stations[j];
            double u=(x-a[0])/(b[0]-a[0]),theta=unit()*2*Math.PI;
            double h=a[1]+u*(b[1]-a[1]),w=a[2]+u*(b[2]-a[2]),cy=a[3]+u*(b[3]-a[3]);
            double c=Math.cos(theta),s=Math.sin(theta);
            double layer=i%13==0?1.10+.11*unit():.82+.20*unit();
            double y=cy+h*layer*c,z=w*layer*s;
            double mottle=Math.sin(x*18+2*s)+.65*Math.cos(3*theta-9*x);
            Ink ink=c<-.34+.16*mottle?Ink.BELLY:mottle>.88?Ink.FIN:c>.36?Ink.SHADE:Ink.SKIN;
            boolean mist=i%4==0;
            grain(x,y,z,radius*(mist?1.75:i%7==0?.43:1),ink,layer>1.05?.17:mist?.23:.61);
        }
    }

    // A rounded tapering sheet, swept between a root and a tip. Cross section grows and then narrows.
    private void fin(double ax,double ay,double az,double bx,double by,double bz,
                     double chordX,double chordY,double chordZ,int samples,double radius,Ink ink){
        for(int i=0;i<samples;i++){
            double f=unit(),v=signed();
            double width=(.36*(1-f)+.75*Math.sin(Math.PI*f))*v;
            double fold=.014*Math.sin(f*4+v*1.8)*Math.sin(Math.PI*f);
            grain(ax+(bx-ax)*f+width*chordX,
                  ay+(by-ay)*f+width*chordY+fold,
                  az+(bz-az)*f+width*chordZ,
                  radius*(.86-.42*f)*(i%5==0?1.6:1),i%8==0?Ink.BELLY:ink,i%5==0?.26:.59);
        }
    }

    private void eye(double x,double y,double z,double size) {
        for(int side:new int[]{-1,1}){
            grain(x,y,z*side,size*1.65,Ink.SHADE,.82);
            grain(x,y,z*side+side*.006,size,Ink.IRIS,.92);
            grain(x+size*.16,y+size*.22,z*side+side*.010,size*.35,Ink.BELLY,.78);
        }
    }

    private void whale(){
        skin(new double[][]{{-.44,.016,.020,.008},{-.31,.032,.048,.002},{-.18,.065,.083,.012},
                {-.02,.123,.139,.022},{.13,.161,.180,.027},{.27,.148,.167,.016},
                {.39,.117,.15,-.002},{.475,.083,.115,-.010},{.513,.044,.065,-.014},{.528,.009,.016,-.015}},4180,.023);
        for(int side:new int[]{-1,1}){
            fin(-.39,.008,side*.012,-.56,.076,side*.28,.084,.014,side*.014,480,.019,Ink.FIN);
            fin(.13,-.042,side*.145,-.14,-.226,side*.41,.073,.030,side*.020,570,.021,Ink.SKIN);
            // A scattering of pale throat pleats, broken into patches like light through water.
            for(int i=0;i<100;i++){
                double f=unit(),lane=random.nextInt(4)/5.+.10;
                double arc=Math.sin(Math.PI*f);
                grain(.45-.45*f,-.068-.061*arc+.025*lane,side*(.065+.080*arc)*lane,
                        .012,Ink.BELLY,.34);
            }
        }
        fin(-.18,.072,0,-.27,.163,0,.055,0,.008,95,.020,Ink.SHADE);
        eye(.275,.023,.166,.014);
    }

    private void fish(Kind kind){
        boolean sun=kind==Kind.SUNFISH,fang=kind==Kind.FANG;
        double h=sun?.34:fang?.225:.165,w=sun?.095:fang?.17:.125;
        skin(new double[][]{{sun?-.31:-.35,.025,.022,0},{-.20,h*.62,w*.61,.009},
                {.01,h,w,.010},{.19,h*.95,w*.97,.014},{.32,h*.72,w*.82,fang?.049:.008},
                {.451,h*.26,w*.4,fang?.060:0},{.47,.01,.025,fang?.05:0}},fang?760:420,.043);
        if(sun){
            for(int s:new int[]{-1,1}){
                fin(-.03,s*.24,0,-.14,s*.64,.008,.105,s*.015,.013,145,.042,Ink.FIN);
                fin(-.21,s*.10,0,-.33,s*.23,0,.04,.06,0,50,.041,Ink.SKIN);
            }
        }else{
            for(int s:new int[]{-1,1}){
                fin(-.31,0,.006,-.58,s*(fang?.225:.195),0,.045,s*.048,.010,88,.042,Ink.FIN);
            }
            fin(.005,h*.76,0,-.095,h*(fang?1.84:1.66),0,.14,.014,.006,130,.039,Ink.FIN);
        }
        for(int side:new int[]{-1,1}){
            fin(.13,-.026,side*w*.88,-.11,-.15,side*(w+.125),.06,.014,0,78,.037,Ink.FIN);
            if(fang){
                fin(.16,-.14,side*.07,.446,-.10,side*.012,.025,.024,.055,90,.031,Ink.BELLY);
                for(int k=0;k<3;k++){
                    double x=.307+.049*k,z=side*(.113-k*.03);
                    fin(x,.013,z,x+.008,-.054+k*.006,z,.012,0,.006,11,.014,Ink.IRIS);
                }
            }
        }
        eye(.293,fang?.073:.042,w*.82,fang?.037:.029);
    }

    private void fry(){
        // A small filled teardrop and two fins survive at the scale of a hundred-fish shoal.
        for(int i=0;i<29;i++){
            double f=unit(),r=Math.sin(f*Math.PI);
            grain(-.32+.77*f,signed()*.11*r,signed()*.07*r,.133,
                    i%4==0?Ink.BELLY:Ink.SKIN,.71);
        }
        for(int side:new int[]{-1,1})
            fin(-.25,0,0,-.54,side*.20,0,.043,side*.046,0,9,.10,Ink.FIN);
    }

    private void ribbon(){
        for(int i=0;i<570;i++){
            double f=unit(),s=Math.sin(Math.PI*f),y=signed()*(.020+.047*s);
            grain(-.63+f*1.14,y,signed()*(.01+.026*s),.031,y<0?Ink.BELLY:Ink.SKIN,.62);
            if(i%4==0)grain(-.63+f*1.14,.035+.05*s+unit()*.03*Math.sin(2+f*26),0,.030,Ink.FIN,.43);
        }
        eye(.442,.010,.023,.021);
        for(int side:new int[]{-1,1})fin(.42,0,0,.23,-.14,side*.034,.012,0,.009,55,.021,Ink.FIN);
    }

    private void medusa(boolean octopus){
        double width=octopus?.22:.35,height=octopus?.39:.25;
        int arms=octopus?8:9;
        for(int i=0;i<740;i++){
            double a=2*Math.PI*unit(),v=unit(),r=width*Math.sqrt(1-v*v);
            if(octopus)r*=.63+.70*v;
            double scallop=.020*Math.cos(a*7.0)*Math.pow(1-v,3);
            grain(r*Math.cos(a),height*v+scallop,r*Math.sin(a),.046,
                    v<.14?Ink.BELLY:i%5==0?Ink.FIN:Ink.SKIN,octopus?.55:.36);
        }
        for(int i=0;i<95;i++){
            double a=2*Math.PI*unit(),r=Math.sqrt(unit())*(.093+.021*Math.cos(a*4));
            grain(r*Math.cos(a),.052+signed()*.026,r*Math.sin(a),.035,Ink.IRIS,.35);
        }
        for(int i=0;i<arms;i++){
            double a=i*2*Math.PI/arms+signed()*.18,length=(octopus?.69:.54)*(.71+.48*unit());
            for(int j=0;j<(octopus?95:57);j++){
                double f=unit(),b=a+f*f*(octopus?1.2+.7*Math.sin(a):.56);
                double r=octopus?width*.53+.32*Math.sin(Math.PI*f*.82)+.08*f*Math.sin(f*7+a):
                        width*.58+f*.04+.06*f*Math.sin(f*6+a);
                double thick=(octopus?.053:.032)*(1-f)+.009;
                double y=-length*f+(octopus?.11*Math.sin(f*6+a)*f:0);
                grain(r*Math.cos(b)+signed()*thick,y,r*Math.sin(b)+signed()*thick,
                        thick*1.25,i%3==0?Ink.BELLY:Ink.FIN,.55);
            }
        }
        if(octopus)eye(.029,.037,.145,.032);
    }
}

