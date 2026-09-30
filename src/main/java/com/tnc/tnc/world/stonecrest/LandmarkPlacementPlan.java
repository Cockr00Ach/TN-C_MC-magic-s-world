package com.tnc.tnc.world.stonecrest;

/** Pure height and overlap policy, independent of Minecraft bootstrap. */
final class LandmarkPlacementPlan {
    static boolean terrainFits(int minimum,int maximum,boolean landscape) {
        // A whole district needs gentle rolling land, not a village-sized level plateau.
        // Landscape sites have a measured ground profile and a 96-block transition belt.
        return maximum-minimum<=(landscape?48:30);
    }
    static int originY(int median,int maximum,int groundOffset,int height,int maxBuild,boolean floating,boolean sunken) {
        int y=floating?Math.max(132,maximum+24):median-groundOffset;
        return sunken?Math.min(y,maxBuild-4-height):y;
    }
    static boolean fits(int origin,int height,int minBuild,int maxBuild) {
        return origin>=minBuild+8 && origin+height<=maxBuild-4;
    }
}
