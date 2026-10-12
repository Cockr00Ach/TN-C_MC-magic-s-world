package com.tnc.tnc.magic;

import java.util.ArrayList;
import java.util.List;

/** Solid, textured model placements. No generated ribbons, circles or translucent surfaces. */
public final class EarthModelLayout {
    public record Piece(String model,double x,double y,double z,double width,double height,double yaw) {}
    private EarthModelLayout() {}
    public static List<Piece> create(int kind,int tier,double radius) {
        if(kind<3||kind>6||tier<1||tier>5||radius<=0)throw new IllegalArgumentException("Earth chain and tier required");
        var pieces=new ArrayList<Piece>();
        if(kind==6) {
            for(int i=0;i<10+tier*2;i++) {
                double a=i*Math.PI*2/(10+tier*2);
                pieces.add(new Piece("earth_pillar",Math.cos(a)*radius*.78,.07,Math.sin(a)*radius*.78,.38,.18,a*180/Math.PI));
            }
            for(int i=0;i<6;i++){double a=i*Math.PI/3+.2;pieces.add(new Piece("earth_shell",Math.cos(a)*radius*.46,.06,Math.sin(a)*radius*.46,.2,.08,i*60));}
            if(tier>=4)for(int i=0;i<6;i++){double a=i*Math.PI/3;pieces.add(new Piece("earth_root",Math.cos(a)*radius*.6,.025,Math.sin(a)*radius*.6,.3,.15,i*60));}
            return List.copyOf(pieces);
        }
        if(kind==3) {
            for(int i=0;i<4+tier;i++) {
                double a=i*Math.PI*2/(4+tier);
                pieces.add(new Piece("earth_clod",Math.cos(a)*.65,.65+Math.sin(a)*.4,Math.sin(a)*.65,.3,.35,i*43));
            }
        } else if(kind==4) {
            int count=5+tier*2;
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/count+.13, r=radius*(.42+.22*(i%3)/2);
                pieces.add(new Piece(tier==1?"earth_clod":"earth_pillar",Math.cos(a)*r,0,Math.sin(a)*r,
                        .35+tier*.11,(tier==1?.23:.45+tier*.27)*(1+.13*(i%3)),i*29));
            }
            for(int arm=0;arm<3;arm++)for(int i=1;i<=tier;i++) {
                double a=arm*Math.PI*2/3+.08*i, r=radius*i/(tier+1);
                pieces.add(new Piece("earth_clod",Math.cos(a)*r,-.16,Math.sin(a)*r,.5,.3,arm*120+i*17));
            }
        } else if(kind==5) {
            // Terraced mud islands with exposed stone, leaving gaps rather than an opaque disc.
            pieces.add(new Piece("earth_mud_plate",0,0,0,radius*.85,.7,12));
            for(int i=0;i<8+tier;i++) {
                double a=i*Math.PI*2/(8+tier),r=radius*(i%2==0?.69:.4);
                pieces.add(new Piece("earth_mud_plate",Math.cos(a)*r,.025*(i%3),Math.sin(a)*r,radius*.42,.5+(i%3)*.1,i*37));
            }
            if(tier>=4)for(int i=0;i<4;i++)pieces.add(new Piece("earth_clod",(i-1.5)*radius*.35,.14,Math.sin(i)*.25,.55,.35,i*30));
        } else {
            int count=6+tier;
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/count;
                pieces.add(new Piece("earth_pillar",Math.cos(a)*radius*.67,0,Math.sin(a)*radius*.67,
                        .32+tier*.065,.38+tier*.23,i*360.0/count));
            }
            for(int i=0;i<6;i++){double a=i*Math.PI/3+.2;pieces.add(new Piece("earth_shell",Math.cos(a)*radius*.46,.06,Math.sin(a)*radius*.46,.2,.08,i*60));}
            if(tier>=4)for(int i=0;i<6;i++) {
                double a=i*Math.PI/3;
                pieces.add(new Piece("earth_root",Math.cos(a)*radius*.55,0,Math.sin(a)*radius*.55,.55,.6+tier*.1,i*60));
            }
        }
        return List.copyOf(pieces);
    }
}
