package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterPrisonShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Paired streams rise from each footing, meet over an open bay, then shed a moving foam crest. */
final class WaterPrisonArcade {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int SECTIONS = 18;

    private WaterPrisonArcade() {}

    static void render(PoseStack stack, MultiBufferSource buffers, double radius, double height,
                       double age, float opacity, int tier) {
        if (opacity <= 0 || radius <= 0 || height <= 0) return;
        var pose = stack.last().pose();
        double crest = height * WaterPrisonShape.rise(age);
        int bays = WaterPrisonShape.bays(radius);
        var water = buffers.getBuffer(WaterRenderTypes.geometry());
        WaterGeometry.ringColor(water, pose, new Vec3(0, .12, 0), UP, radius, .22, .02F, .40F, .55F, opacity * .70F);
        for (int bay = 0; bay < bays; bay++) drawBay(water, pose, radius, crest, age, opacity, bay, bays, false);
        var foam = buffers.getBuffer(WaterRenderTypes.glow());
        for (int bay = 0; bay < bays; bay++) drawBay(foam, pose, radius, crest, age, opacity, bay, bays, true);
        WaterGeometry.richCircle(buffers, stack, new Vec3(0, .06, 0), UP, radius, age, opacity * .49F, 2, tier);
    }

    private static void drawBay(VertexConsumer out, Matrix4f pose, double radius, double height, double age,
                                float opacity, int bay, int count, boolean foam) {
        double span = Math.PI * 2 / count;
        // The inward-facing undersides hold a deeper blue. Only the narrow outside lip catches white light.
        Vec3 lower = Vec3.ZERO, upper = Vec3.ZERO, hem = Vec3.ZERO;
        for (int i = 0; i <= SECTIONS; i++) {
            double t = i / (double)SECTIONS;
            double s = Math.sin(Math.PI * t);
            double angle = span * (bay + t - .11 * Math.sin(2 * Math.PI * t));
            double flow = age * .075 - t * Math.PI * 3 + bay * 1.71;
            double arch = Math.pow(s, .58);
            double y = WaterPrisonShape.height(height, arch, flow);
            double thickness = Math.min(1.12, height * .085) * (.42 + .58 * s);
            double r = radius + Math.min(.57, radius * .04) * Math.sin(2 * Math.PI * t) + .10 * s * Math.sin(flow);
            double c = Math.cos(angle), z = Math.sin(angle);
            Vec3 nextUpper = new Vec3(r * c, y, r * z);
            Vec3 nextLower = foam ? new Vec3((r + .025) * c, y - thickness * .14, (r + .025) * z)
                    : new Vec3((r - thickness * .67) * c, Math.max(.13, y - thickness), (r - thickness * .67) * z);
            // A sheet gathers near each spring and breaks into moving scallops along its lower edge.
            // Keep the middle of each bay open at eye height so targets remain readable through it.
            Vec3 nextHem = Vec3.ZERO;
            if (!foam) {
                double edge = Math.abs(2 * t - 1);
                double scallop = .5 + .5 * Math.sin(6 * Math.PI * t - age * .09 + bay * 1.72);
                double depth = height * s * (.13 + .56 * edge * edge + .115 * scallop * scallop);
                double fold = r - thickness * .67 + s * (.24 + .30 * Math.sin(flow + scallop));
                nextHem = new Vec3(fold * c, Math.max(.12, nextLower.y - depth), fold * z);
            }
            if (i > 0) {
                float gleam = (float)Math.pow(.5 + .5 * Math.cos(flow), 3);
                if (foam) {
                    WaterGeometry.quad(out, pose, lower, nextLower, nextUpper, upper,
                            .47F + .37F * gleam, .86F, .95F, opacity * (.17F + .60F * gleam));
                } else {
                    WaterGeometry.vertex(out, pose, hem, .12F, .61F, .69F, opacity * .08F);
                    WaterGeometry.vertex(out, pose, nextHem, .19F, .72F, .78F, opacity * (.08F + .08F * gleam));
                    WaterGeometry.vertex(out, pose, nextLower, .05F, .48F, .63F, opacity * (.28F + .15F * gleam));
                    WaterGeometry.vertex(out, pose, lower, .04F, .40F, .58F, opacity * .32F);
                    WaterGeometry.vertex(out, pose, lower, .02F, .27F, .43F, opacity * .76F);
                    WaterGeometry.vertex(out, pose, nextLower, .025F, .34F, .52F, opacity * .72F);
                    WaterGeometry.vertex(out, pose, nextUpper, .16F, .72F, .77F, opacity * .64F);
                    WaterGeometry.vertex(out, pose, upper, .22F, .80F, .83F, opacity * .61F);
                }
            }
            hem = nextHem;
            lower = nextLower;
            upper = nextUpper;
        }
        if (!foam) {
            double angle = span * bay;
            Vec3 base = new Vec3(Math.cos(angle) * radius, .10, Math.sin(angle) * radius);
            double size = Math.min(1, radius * .09);
            WaterGeometry.ringColor(out, pose, base, UP, size, size * .16, .20F, .67F, .70F, opacity * .72F, 16);
            WaterGeometry.ringColor(out, pose, base.add(0, .13, 0), UP, size * .67, size * .12,
                    .63F, .84F, .78F, opacity * .63F, 12);
        }
    }
}
