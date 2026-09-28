package com.tnc.tnc.world.stonecrest;

/** Actual clipped write volume, with a two-block body/fall safety margin. */
final class LandmarkWorkArea {
    static boolean near(double x,double y,double z,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        return x>=minX-2 && x<maxX+2 && z>=minZ-2 && z<maxZ+2 && y>=minY-2 && y<maxY+2;
    }
}
