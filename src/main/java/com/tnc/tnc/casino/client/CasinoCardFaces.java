package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.*;

/** Physical suit pixels stay legible without relying on a font's Unicode coverage. */
public final class CasinoCardFaces {
    private static final String[][] MASKS={
            {"0110110","1111111","1111111","0111110","0011100","0001000","0000000"},
            {"0001000","0011100","0111110","1111111","0111110","0011100","0001000"},
            {"0011100","0011100","1101011","1111111","1111111","0001000","0011100"},
            {"0001000","0011100","0111110","1111111","1111111","0001000","0011100"}};
    private CasinoCardFaces() {}
    public static int color(int suit) {return suit<2?CasinoPalette.CHIP_RED:CasinoPalette.PIP;}
    public static void suit(PoseStack p,VertexConsumer b,int suit,float x,float z,float pixel,int light) {
        String[] rows=MASKS[Math.floorMod(suit,4)];
        for(int row=0;row<7;row++) for(int col=0;col<7;col++) if(rows[row].charAt(col)=='1') {
            float px=x+(col-3.5F)*pixel,pz=z+(row-3.5F)*pixel;
            TNCasinoGeometry.box(p,b,px,0.014F,pz,px+pixel,0.016F,pz+pixel,color(suit),light);
        }
    }
}
