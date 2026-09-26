package com.tnc.tnc.world.stonecrest;

/** Pure height and overlap policy, independent of Minecraft bootstrap. */
final class LandmarkPlacementPlan {
    static int originY(int median,int maximum,int groundOffset,int height,int maxBuild,boolean floating,boolean sunken) {
        int y=floating?Math.max(132,maximum+24):median-groundOffset;
        return sunken?Math.min(y,maxBuild-4-height):y;
    }
    static boolean fits(int origin,int height,int minBuild,int maxBuild) {
        return origin>=minBuild+8 && origin+height<=maxBuild-4;
    }
}
