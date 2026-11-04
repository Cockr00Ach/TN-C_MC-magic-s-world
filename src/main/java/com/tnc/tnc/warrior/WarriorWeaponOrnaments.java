package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;

/** Animated, weapon-local cuboids, in model pixels. Shared with offline previews. */
public final class WarriorWeaponOrnaments {
    public record Box(double x,double y,double z,double w,double h,double d,int color) {}
    private WarriorWeaponOrnaments() {}
    public static List<Box> build(String id,double ticks,boolean active) {
        var out=new ArrayList<Box>();
        double t=ticks*.035, spread=active?1.35:1;
        switch(id) {
            case "dragon_sword" -> {
                for(int i=0;i<22;i++) {
                    double a=t-i*.29,z=6+i*.35,x=7+Math.cos(a)*1.1*spread,y=.5+Math.sin(a)*1.1*spread;
                    box(out,x,y,z,.32,.30,.42,0x9F5261);
                    box(out,x,y+.25,z,.19,.17,.25,0xD5B27B);
                    if(i%3==0)box(out,x+.23,y,z,.14,.16,.22,0xB78648);
                }
                double a=t-21*.29,x=7+Math.cos(a)*1.1*spread,y=.5+Math.sin(a)*1.1*spread;
                box(out,x,y,13.75,.7,.55,.8,0x713B49);
                box(out,x,y-.12,14.3,.5,.25,.55,0xD5B27B);
                for(int s:new int[]{-1,1}) {
                    box(out,x+s*.28,y+.25,13.85,.16,.18,.25,0xFFD789);
                    for(int j=0;j<3;j++)box(out,x+s*(.23+j*.09),y+.42+j*.13,13.6-j*.12,.14,.18,.18,0xD5B27B);
                }
            }
            case "frost_katana" -> {
                for(int i=0;i<6;i++) {
                    double a=t+i*Math.PI/3,z=8+i*1.7,x=7.7+Math.cos(a)*1.1*spread,y=.3+Math.sin(a)*1.1*spread;
                    crystal(out,x,y,z,0x9FE8FF);
                }
            }
            case "star_rapier" -> {
                for(int ring=0;ring<2;ring++)for(int i=0;i<16;i++) {
                    double a=(ring==0?t:-t)+i*Math.PI/8,r=(i%4==0?1.15:.75)*spread;
                    box(out,7.3+Math.cos(a)*r,.3+Math.sin(a)*r,6+ring*8,.2,.2,i%4==0?.55:.22,
                            i%4==0?0xFFE9A0:0xB99954);
                }
            }
            case "crimson_warblade" -> {
                for(int i=0;i<4;i++) {
                    double z=8+i*3,y=1.45+Math.sin(t+i)*.15*spread;
                    seal(out,7,y,z,0xAD5740,0xFFB16A);
                }
            }
            case "doom_scythe" -> {
                for(int side:new int[]{-1,1})for(int i=0;i<12;i++) {
                    double a=t*.5+i*.18,x=7.5+side*(1+Math.sin(a)*.25),y=.5+Math.cos(a)*.8;
                    // Alternating link planes create a recognisable articulated chain.
                    box(out,x,y,12-i*.38,i%2==0?.38:.14,i%2==0?.14:.38,.22,0xB897DE);
                }
                for(int i=0;i<3;i++)crystal(out,6.8+i*.45,2+Math.sin(t+i)*.15,13.3,0xC98BFF);
            }
            case "earthshaker_hammer" -> {
                for(int s:new int[]{-1,1})for(int i=0;i<4;i++) {
                    double y=3.85+Math.sin(t+i*.4)*.12*spread;
                    box(out,6+i*.9,y,13+s*.7,.65,.25,.5,0xAD874F);
                    box(out,6+i*.9,y+.17,13+s*.7,.25,.15,.25,0xFFD98A);
                }
            }
            case "sky_halberd" -> {
                for(int s:new int[]{-1,1})for(int i=0;i<12;i++) {
                    double z=10+i*.55,x=7.3+s*(.9+Math.sin(i*.23+t)*.35)*spread;
                    box(out,x,.5+Math.cos(i*.23+t)*.8,z,.3,.22,.65,i%3==0?0xC2F1FF:0x79B6CC);
                }
            }
            case "bow", "moon_bow", "storm_bow", "ember_bow" -> {
                int color=id.equals("ember_bow")?0xFFAE66:id.equals("moon_bow")?0xD5BDFF:0x9FE8FF;
                // Centred on the arrow rest, aligned with the bow's own model axes.
                for(int i=0;i<12;i++) {
                    double a=t+i*Math.PI/6,r=(active?1.7:1.15);
                    box(out,8+Math.cos(a)*r,8+Math.sin(a)*r,8,.22,.22,.32,color);
                }
                if(active)for(int i=0;i<8;i++) {
                    double z=8+(i*.7-ticks*.08)%4;
                    box(out,8,8,z,.16,.16,.45,color);
                }
            }
            default -> {}
        }
        return out;
    }
    private static void box(List<Box> out,double x,double y,double z,double w,double h,double d,int c) {
        out.add(new Box(x-w/2,y-h/2,z-d/2,w,h,d,c));
    }
    private static void crystal(List<Box> out,double x,double y,double z,int c) {
        for(int j=0;j<5;j++) {double w=.16+(2-Math.abs(j-2))*.15;box(out,x,y,z+j*.2,w,w,.2,c);}
        box(out,x-.18,y-.18,z+.3,.18,.18,.35,0x5B7F99);
    }
    private static void seal(List<Box> out,double x,double y,double z,int c,int core) {
        for(int s:new int[]{-1,1})for(int i=-2;i<=2;i++) {
            box(out,x+i*.22,y,z+s*.55,.2,.16,.2,c);
            box(out,x+s*.55,y,z+i*.22,.2,.16,.2,c);
        }
        box(out,x,y+.08,z,.28,.18,.48,core);
    }
}
