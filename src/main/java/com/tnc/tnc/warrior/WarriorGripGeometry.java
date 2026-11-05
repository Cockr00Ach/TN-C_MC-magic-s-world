package com.tnc.tnc.warrior;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Compensation for uniform phantom growth about a displayed grip, after item transforms. */
public final class WarriorGripGeometry {
    private WarriorGripGeometry() {}
    public static Vector3f anchor(Matrix4f itemTransform, float x, float y, float z) {
        return itemTransform.transformPosition(new Vector3f(x - .5f, y - .5f, z - .5f));
    }
    public static Vector3f compensation(Vector3f anchor, double scale) {
        return new Vector3f(anchor).mul((float)(1 - scale));
    }
}
