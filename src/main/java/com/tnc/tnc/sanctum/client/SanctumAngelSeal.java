package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The gilded keystone at the front edge of the first form's plinth. */
final class SanctumAngelSeal {
    private static final int SIDES = 8;
    private static final Vec3[] BASE = ring(.69, .08);
    private static final Vec3[] SETTING = ring(.46, .29);
    private static final Vec3[] STONE = ring(.35, .70);
    private static final Vec3 CROWN = new Vec3(0, 1.46, 0);
    private static final Vec3 SEAM = new Vec3(.04, .57, -.39);

    static void draw(MultiBufferSource buffers, Matrix4f matrix, float age) {
        VertexConsumer out = buffers.getBuffer(SanctumProjectedBlade.EDGE);
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            int metal = (i & 1) == 0 ? 0xAD783C : 0x82552D;
            face(out, matrix, BASE[i], SETTING[i], BASE[next], metal);
            face(out, matrix, BASE[next], SETTING[i], SETTING[next], metal);
            face(out, matrix, SETTING[i], STONE[i], SETTING[next], 0x765E49);
            face(out, matrix, SETTING[next], STONE[i], STONE[next], 0x765E49);
            face(out, matrix, STONE[i], CROWN, STONE[next], (i & 1) == 0 ? 0xB79F78 : 0x897158);
        }
        int glow = 185 + (int)(32 * Math.sin(age * .09));
        for (int i = 0; i < SIDES; i += 2) {
            float angle = (float)(i * Math.PI * 2 / SIDES);
            Matrix4f side = new Matrix4f(matrix).rotateY(angle);
            SanctumProjectedBlade.face(out, side, new Vec3(0, 1.38, -.05),
                    new Vec3(-.065, .72, -.353), SEAM, 0xFFF1A3, glow);
            SanctumProjectedBlade.face(out, side, SEAM, new Vec3(-.065, .72, -.353),
                    new Vec3(-.13, .28, -.455), 0xFFD46B, glow);
        }
    }

    private static Vec3[] ring(double radius, double height) {
        Vec3[] rim = new Vec3[SIDES];
        for (int i = 0; i < SIDES; i++) {
            double angle = i * Math.PI * 2 / SIDES;
            rim[i] = new Vec3(radius * Math.sin(angle), height, radius * Math.cos(angle));
        }
        return rim;
    }

    private static void face(VertexConsumer out, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, int color) {
        SanctumProjectedBlade.face(out, matrix, a, b, c, color, 255);
    }

    private SanctumAngelSeal() {}
}
