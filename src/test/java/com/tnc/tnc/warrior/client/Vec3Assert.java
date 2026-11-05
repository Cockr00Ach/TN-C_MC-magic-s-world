package com.tnc.tnc.warrior.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;

/** 断言 Vec3 相等（double 精确比较，Catmull-Rom 的端点系数是精确的 0/1）。 */
final class Vec3Assert {
    private Vec3Assert() {
    }

    static void assertVec(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, 1.0E-9, message + " (x)");
        assertEquals(expected.y, actual.y, 1.0E-9, message + " (y)");
        assertEquals(expected.z, actual.z, 1.0E-9, message + " (z)");
    }
}
