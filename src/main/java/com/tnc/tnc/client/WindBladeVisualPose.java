package com.tnc.tnc.client;

import org.joml.Quaternionf;

/** The local Z normal stays aligned with launch direction throughout the in-plane spin. */
final class WindBladeVisualPose {
    private WindBladeVisualPose() {}
    static Quaternionf rotation(float yaw,float pitch,float spin) {
        float radians=(float)(Math.PI/180);
        return new Quaternionf().rotateY(yaw*radians).rotateX(pitch*radians).rotateZ(spin*radians);
    }
}
