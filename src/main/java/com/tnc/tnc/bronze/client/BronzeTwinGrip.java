package com.tnc.tnc.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Grip-local math, independent of renderer creation and game registry bootstrap. */
final class BronzeTwinGrip {
    private BronzeTwinGrip(){}
    static void apply(boolean partner,PoseStack pose){
        if(!partner)return;
        // Reverse the long blade and pommel spike while the hand stays on the handle.
        pose.translate(.5,.5,.5);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.translate(-.5,-.5,-.5);
    }
}
