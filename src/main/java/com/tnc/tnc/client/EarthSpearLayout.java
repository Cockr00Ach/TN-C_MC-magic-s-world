package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** The authored spear is centered at X/Y=8 and its point is at Z=32. */
final class EarthSpearLayout {
    private EarthSpearLayout() {}
    static void apply(PoseStack pose,float width,float length) {
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(width,width,length/2);
        // Two model blocks long: anchor Z=2 to the impact point, not the model center.
        pose.translate(-.5,-.5,-2);
    }
}
