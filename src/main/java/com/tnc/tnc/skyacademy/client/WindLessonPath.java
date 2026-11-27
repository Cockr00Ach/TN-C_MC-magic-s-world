package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.skyacademy.AcademyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The camera and the luminous current sample the same passage through the three bell crowns. */
final class WindLessonPath {
    static final int LENGTH = AcademyRules.WIND_DEMO_TICKS;
    static final int[] STRIKES = {22, 69, 119};
    private static final int[] RELEASES = {41, 89, 139};
    private final Vec3[] bells = new Vec3[3];
    private final Vec3[] ordered = new Vec3[3];

    WindLessonPath(BlockPos[] bases) {
        for (int index = 0; index < bells.length; index++) {
            bells[index] = Vec3.atBottomCenterOf(bases[index]).add(0, 2.46, .23);
        }
        for (int step = 0; step < ordered.length; step++) ordered[step] = bells[AcademyRules.runeAtStep(step)];
    }

    Vec3 bell(int index) { return bells[index]; }

    Vec3 wind(double time) {
        if (time < STRIKES[0]) {
            double u = smooth(time / STRIKES[0]);
            return ordered[0].add(-2.8 * (1-u), -.85 * (1-u) + .32 * Math.sin(u*Math.PI), 1.65 * (1-u));
        }
        for (int i = 0; i < ordered.length; i++) {
            if (time < RELEASES[i]) {
                double angle = 2*Math.PI * smooth((time - STRIKES[i]) / (RELEASES[i] - STRIKES[i]));
                return ordered[i].add(.43*Math.sin(angle), .22*Math.sin(2*angle), .30*(1-Math.cos(angle)));
            }
            if (i + 1 < ordered.length && time < STRIKES[i+1]) {
                double u = smooth((time - RELEASES[i]) / (STRIKES[i+1] - RELEASES[i]));
                double arc = Math.sin(u*Math.PI);
                return ordered[i].lerp(ordered[i+1], u).add(0, arc*(.58+.24*Math.cos(u*Math.PI)), arc*1.28);
            }
        }
        double u = smooth((time - RELEASES[2]) / (LENGTH - RELEASES[2]));
        return ordered[2].add(1.55*u, 1.38*u, .56*Math.sin(u*Math.PI));
    }

    record Shot(Vec3 eye, Vec3 subject, Vec3 clipStart) {}

    Shot shot(double time) {
        double t = Mth.clamp(time, 0, LENGTH);
        Vec3 head = wind(t);
        // Keep the bell in frame through the little eddy; lead a travelling gust by a fraction of a second.
        Vec3 subject = head.lerp(wind(t+3), .42).add(0,-.13,0);
        double travelling = smooth((t-36)/11) * (1-smooth((t-61)/12))
                + smooth((t-84)/11) * (1-smooth((t-111)/12));
        double pullback = smooth((t-132)/27);
        Vec3 last = ordered[2];
        subject = subject.lerp(last.add(-.7,-.25,0), .52*pullback);
        Vec3 eye = subject.add(.49*Math.cos(t*.029), .28+.76*travelling+1.1*pullback,
                2.65 + 3.15*travelling + 2.30*pullback);
        // Start in front of the bell housing so its own collision shape cannot collapse the shot.
        return new Shot(eye, subject, subject.add(0,0,1.05));
    }

    static double smooth(double x) {
        double u = Mth.clamp(x, 0, 1);
        return u*u*(3-2*u);
    }
}
