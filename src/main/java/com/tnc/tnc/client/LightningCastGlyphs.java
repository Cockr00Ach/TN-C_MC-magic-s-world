package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningTheme;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Geometry shared by the charging seals and the ground trace after release. */
final class LightningCastGlyphs {
    private static final double TURN = Math.PI * 2;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    enum Stage {
        SPARK(.88, 3, 0, LightningTheme.BLUE, LightningTheme.GOLD),
        SILVER(1.38, 4, 1, LightningTheme.BLUE, LightningTheme.BLUE),
        STORM(1.95, 3, 2, LightningTheme.VIOLET, LightningTheme.BLUE),
        ARMILLARY(2.56, 6, 3, LightningTheme.BLUE, LightningTheme.VIOLET),
        CROWN(3.38, 6, 3, LightningTheme.GOLD, LightningTheme.VIOLET);

        private static final Stage[] STAGES = values();
        final double radius;
        final int divisions;
        final int seals;
        final int main;
        final int accent;

        Stage(double radius, int divisions, int seals, int main, int accent) {
            this.radius = radius;
            this.divisions = divisions;
            this.seals = seals;
            this.main = main;
            this.accent = accent;
        }

        static Stage of(int tier) {
            return STAGES[Mth.clamp(tier, 1, STAGES.length) - 1];
        }
    }

    private LightningCastGlyphs() {}

    static void ground(VertexConsumer out, Vec3 feet, Vec3 camera, Stage stage,
                       float charge, double time, boolean nearCamera) {
        var frame = new Plane(feet.subtract(camera).add(0, .07, 0), EAST, SOUTH);
        double radius = stage.radius * (.79 + .21 * charge);
        double turn = time * .024;
        float alpha = charge * (nearCamera ? .54F : .85F);
        gates(out, frame, radius, turn, stage.divisions, .70, .031F, alpha, stage.main);

        if (stage == Stage.SPARK) return;
        gates(out, frame, radius * .83, -turn * .65, stage.divisions, .56, .015F, alpha * .58F, stage.accent);
        marks(out, frame, radius, turn * .47, stage.divisions * 2, alpha, stage.main);
        if (stage == Stage.STORM) {
            polygon(out, frame, radius * .76, turn + Math.PI / 2, 3, .018F, alpha * .70F, stage.main);
        }
        if (stage.ordinal() >= Stage.ARMILLARY.ordinal()) {
            polygon(out, frame, radius * .73, -turn * .55, 6, .017F, alpha * .64F, stage.accent);
            for (int i = 0; i < stage.divisions; i++) {
                double angle = i * TURN / stage.divisions + turn;
                Vec3 start = frame.at(angle, radius);
                Vec3 tip = frame.at(angle, radius * 1.20);
                stroke(out, .021F, alpha * .72F, stage.main,
                        frame.at(angle - .145, radius * 1.12), tip, frame.at(angle + .145, radius * 1.12));
                if (stage == Stage.CROWN && !nearCamera) {
                    lightning(out, start, tip.add(0, .36 + .36 * charge, 0),
                            (long) (time / 4) * 53 + i * 19L, .041F, alpha * .62F, stage.main);
                }
            }
        }
        Vec3 origin = frame.origin;
        LightningBoltGeometry.groundFlash(out, IDENTITY, origin.x, origin.y, origin.z,
                (float) (radius * .78), stage.main, alpha * .16F);
    }

    static void seal(VertexConsumer out, Vec3 center, Vec3 normal, Vec3 camera, Stage stage,
                     double radius, double spin, float alpha) {
        var frame = Plane.facing(center.subtract(camera), normal);
        gates(out, frame, radius, spin, stage.divisions, .71, .021F, alpha, stage.main);
        marks(out, frame, radius, -spin * .54, stage.divisions * 2, alpha * .72F, stage.accent);
        int sides = stage == Stage.SILVER ? 4 : stage == Stage.STORM ? 3 : 6;
        polygon(out, frame, radius * .65, -spin, sides, .018F, alpha * .85F, stage.accent);
        if (stage == Stage.CROWN) {
            polygon(out, frame, radius * .62, spin + Math.PI / 6, 3, .014F, alpha * .6F, stage.main);
        }
    }

    static void halo(VertexConsumer out, Vec3 center, Vec3 camera, Stage stage, double time, float charge) {
        double angle = time * .057;
        Vec3 normal = new Vec3(.68 * Math.cos(angle), 1, .68 * Math.sin(angle)).normalize();
        var orbit = Plane.facing(center.subtract(camera), normal);
        float alpha = .68F * charge;
        double radius = stage == Stage.CROWN ? 1.74 : 1.31;
        gates(out, orbit, radius, angle, 3, .72, .025F, alpha, stage.main);
        if (stage == Stage.CROWN) {
            var crest = Plane.facing(center.subtract(camera).add(0, .32, 0), new Vec3(-normal.x, normal.y, -normal.z));
            gates(out, crest, radius * 1.18, -angle * .76, 6, .62, .021F, alpha * .8F, stage.accent);
            for (int i = 0; i < 6; i++) {
                double spoke = i * TURN / 6 - angle * .76;
                Vec3 tip = crest.at(spoke, radius * 1.32).add(0, .27, 0);
                stroke(out, .024F, alpha, stage.main,
                        crest.at(spoke - .16, radius * 1.16), tip, crest.at(spoke + .16, radius * 1.16));
            }
        }
    }

    static void release(VertexConsumer out, Vec3 feet, Vec3 camera, Stage stage, double progress,
                        float strength, long seed) {
        var frame = new Plane(feet.subtract(camera).add(0, .10, 0), EAST, SOUTH);
        double advance = 1 - (1 - progress) * (1 - progress);
        double radius = stage.radius * (.74 + advance * 1.93);
        double turn = seed % 360 * Math.PI / 180 + progress * .30;
        float alpha = (float) ((1 - progress) * strength);
        gates(out, frame, radius, turn, stage.divisions, .81, .055F, alpha, stage.main);
        if (stage != Stage.SPARK) {
            gates(out, frame, radius * .72, -turn, stage.divisions, .62, .030F, alpha * .80F, stage.accent);
            marks(out, frame, radius, turn, stage.divisions * 2, alpha, stage.main);
        }
        for (int i = 0; i < stage.divisions; i++) {
            double angle = turn + i * TURN / stage.divisions;
            Vec3 start = frame.at(angle - .17, radius * .65);
            Vec3 end = frame.at(angle + .10, radius * 1.10).add(0, progress * .56, 0);
            lightning(out, start, end, seed + 41L * i, .047F, alpha * .84F, stage.main);
        }
        if (stage == Stage.CROWN) {
            var rising = new Plane(frame.origin.add(0, .12 + advance * 1.2, 0), EAST, SOUTH);
            gates(out, rising, radius * .53, -turn * .78, 6, .57, .036F, alpha * .74F, stage.main);
        }
    }

    private static void gates(VertexConsumer out, Plane frame, double radius, double turn, int divisions,
                              double span, float width, float alpha, int theme) {
        int samples = divisions < 5 ? 12 : 9;
        double step = TURN / divisions;
        for (int i = 0; i < divisions; i++) {
            double start = turn + i * step - step * span / 2;
            Vec3[] points = new Vec3[samples + 1];
            for (int j = 0; j <= samples; j++) points[j] = frame.at(start + step * span * j / samples, radius);
            float sweep = (float) (.84 + .16 * Math.sin(turn * 4 - i * .88));
            stroke(out, width, alpha * sweep, theme, points);
        }
    }

    private static void marks(VertexConsumer out, Plane frame, double radius, double turn, int count,
                              float alpha, int theme) {
        for (int i = 0; i < count; i++) {
            double angle = turn + i * TURN / count;
            if ((i & 1) == 0) {
                stroke(out, .017F, alpha, theme, frame.at(angle - .08, radius),
                        frame.at(angle, radius * 1.10), frame.at(angle + .08, radius),
                        frame.at(angle, radius * .90), frame.at(angle - .08, radius));
            } else {
                stroke(out, .014F, alpha * .64F, theme, frame.at(angle, radius * .91), frame.at(angle, radius * 1.04));
            }
        }
    }

    private static void polygon(VertexConsumer out, Plane frame, double radius, double turn, int sides,
                                float width, float alpha, int theme) {
        Vec3[] points = new Vec3[sides + 1];
        for (int i = 0; i < sides; i++) points[i] = frame.at(turn + i * TURN / sides, radius);
        points[sides] = points[0];
        stroke(out, width, alpha, theme, points);
    }

    private static void stroke(VertexConsumer out, float width, float alpha, int theme, Vec3... points) {
        double[][] line = new double[points.length][3];
        for (int i = 0; i < points.length; i++) {
            var point = points[i];
            line[i][0] = point.x;
            line[i][1] = point.y;
            line[i][2] = point.z;
        }
        LightningBoltGeometry.polyline(out, IDENTITY, line, width, alpha,
                LightningTheme.edge(theme), LightningTheme.mid(theme), LightningTheme.glow(theme), 0, 0, 0);
    }

    private static void lightning(VertexConsumer out, Vec3 from, Vec3 to, long seed, float width,
                                  float alpha, int theme) {
        LightningBoltGeometry.arc(out, IDENTITY, from.x, from.y, from.z, to.x, to.y, to.z,
                seed, width, alpha, theme, 0, 0, 0);
    }

    private record Plane(Vec3 origin, Vec3 u, Vec3 v) {
        static Plane facing(Vec3 center, Vec3 normal) {
            Vec3 reference = Math.abs(normal.dot(UP)) > .92 ? EAST : UP;
            Vec3 across = reference.cross(normal).normalize();
            return new Plane(center, across, normal.cross(across));
        }

        Vec3 at(double angle, double radius) {
            return origin.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
        }
    }
}
