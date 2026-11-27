package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.free.ElementalIntent.Ability;
import com.tnc.tnc.magic.free.ElementalIntent.Intent;
import com.tnc.tnc.magic.free.ElementalIntent.Target;
import com.tnc.tnc.magic.free.ElementalNetwork;
import com.tnc.tnc.magic.free.WaterCastGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import static com.tnc.tnc.magic.free.ElementalIntent.*;

/** Fire has a solid silhouette with moving hot seams. Small flames share the fire hand's
 * untextured, shader-visible lightning pass. Positions and contacts come from the server. */
final class FireCastVisuals {
    static final int VIOLET_OUT = 7; // Local residual pose after early status removal.
    private static final double TAU = Math.PI * 2;
    private static final int CINDER = 0xA62912, RED = 0xFF4510, AMBER = 0xFF9929,
            GOLD = 0xFFE478, VIOLET = 0x9135FF, LILAC = 0xF6AEFF;
    private static final RenderType MINERAL = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/eclipse/white.png"));
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final double now;

    FireCastVisuals(PoseStack pose, MultiBufferSource buffers, double now) {
        this.pose = pose;
        this.buffers = buffers;
        this.now = now;
    }

    void gather(Vec3 camera, Vec3 eye, Vec3 look) {
        Vec3 at = eye.add(look.scale(1.35)).add(WaterCastGeometry.right(look).scale(.52))
                .add(0, -.38, 0).subtract(camera);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        flame(0, 0, 0, .07, .21, 0, .9, false);
        for (int n = 0; n < 6; n++) {
            double q = fraction(now * .045 + n / 6.0);
            double angle = n * 2.4 + q * 3;
            Vec3 spark = ground(angle, .32 * (1 - q), .24 * (1 - q));
            stroke(spark, spark.scale(.78), .012, AMBER, .75);
        }
        pose.popPose();
    }

    /** A violet bud opens by the wand while its petals travel to the marked creature. */
    void violetCalling(Vec3 camera, Vec3 eye, Vec3 look, Vec3 target, double age, int duration) {
        double q = unit(age / Math.max(1, duration));
        Vec3 side = WaterCastGeometry.right(look);
        Vec3 start = eye.add(look.scale(1.55)).add(side.scale(.7)).add(0, -.39, 0);
        pose.pushPose();
        Vec3 local = start.subtract(camera);
        pose.translate(local.x, local.y, local.z);
        violetPetals(.45, .64, (.25 + .75 * smooth(q * 4)) * (1 - smooth((q - .68) / .32)));
        pose.popPose();
        for (int i = 0; i < 3; i++) {
            double t = unit((q - .24 - i * .075) * 1.95);
            if (t <= 0 || t >= 1) continue;
            Vec3 to = target.add(side.scale((i - 1) * .49)).add(0, .65 + .18 * i, 0);
            Vec3 at = start.lerp(to, t).add(0, 1.15 * Math.sin(t * Math.PI), 0).subtract(camera);
            Vec3 tail = start.lerp(to, Math.max(0, t - .045)).add(0, 1.15 * Math.sin(Math.max(0, t - .045) * Math.PI), 0).subtract(camera);
            taper(tail, at, .04, .09, LILAC, .9);
            crystal(at, .075, .16, .075, VIOLET, LILAC);
        }
    }

    void draw(ElementalNetwork.Visual packet, Intent spell, double age, double since) {
        double size = spell.scale();
        double fade = unit((packet.duration() - age) / 10);
        switch (packet.phase()) {
            case ElementalNetwork.FORM -> form(packet, spell, unit(age / Math.max(1, packet.duration())));
            case ElementalNetwork.ACTIVE -> active(packet, spell, age, fade);
            case VIOLET_OUT -> {
                pose.translate(0, -.21, 0);
                violetPetals(.75, 1.75, 1 - smooth(age / packet.duration()));
            }
            case ElementalNetwork.BURN -> burn(spell.ability(), unit(packet.velocity().x), fade);
            case ElementalNetwork.IMPACT -> impact(packet, spell, since);
            case ElementalNetwork.ARC -> {
                Vec3 end = packet.velocity();
                Vec3 bend = end.scale(.5).add(0, Math.min(1.3, end.length() * .2), 0);
                double alpha = unit(1 - since / 10);
                stroke(Vec3.ZERO, bend, .09 * size, RED, alpha);
                stroke(bend, end, .06 * size, AMBER, alpha);
            }
        }
    }

    private void form(ElementalNetwork.Visual packet, Intent spell, double q) {
        Ability a = spell.ability();
        double size = spell.scale();
        double opened = smooth(q * 4);
        boolean muzzle = a.target == Target.PROJECTILE || a.target == Target.CHANNEL;
        boolean violet = a == Ability.VIOLET_FIRE;
        int tint = violet ? VIOLET : RED;
        pose.pushPose();
        if (muzzle) orient(packet.velocity());
        else pose.mulPose(Axis.XP.rotationDegrees(-90));
        double radius = (muzzle ? .96 : 1.35) * size * (.28 + .72 * opened);
        double alpha = .35 + .65 * opened;
        for (int n = 0; n < 3; n++) {
            double a0 = n * TAU / 3 + now * .025;
            arcXY(radius, -.17, a0, a0 + TAU * .285, .025 * size, tint, alpha);
            Vec3 heel = polar(a0, radius, -.17);
            stroke(heel, polar(a0 + .18, radius * .69, .08), .038 * size, AMBER, alpha);
            stroke(heel, polar(a0 - .13, radius * 1.1, -.17), .024 * size, tint, alpha);
        }
        // Fuel travels from outside the seal towards its throat, then falls silent at release.
        for (int n = 0; n < 7; n++) {
            double t = fraction(now * .042 + n / 7.0);
            double r = radius * (1.5 - 1.4 * t);
            double angle = n * 2.4 + t * 2.3;
            stroke(polar(angle, r, -.5 * size * (1 - t)), polar(angle + .32, r * .82, -.3 * size * (1 - t)),
                    .023 * size, violet ? LILAC : AMBER, alpha);
        }
        modifiers(spell, radius, q, alpha);
        pose.popPose();

        double build = smooth((q - .08) / .76);
        if (build == 0) return;
        pose.pushPose();
        if (muzzle) orient(packet.velocity());
        switch (a) {
            case FIREBALL -> {
                pose.mulPose(Axis.ZP.rotation((float)(q * 1.2)));
                model("fireball", build, 1.45 * size);
            }
            case FIREBLADE -> {
                pose.mulPose(Axis.ZP.rotationDegrees((float)(-55 + 36 * q)));
                pose.mulPose(Axis.XP.rotationDegrees((float)(32 - 16 * q)));
                model("fireblade", build, 1.55 * size);
            }
            case FIRE_SERPENT -> {
                // Reveal a coiled side silhouette then turn the head down the cast direction.
                snakePoised(build, size, 0);
            }
            case FIRE_BREATH, MAGMA_SPRAY -> {
                arcXY(.43 * size, .06, now * .09, now * .09 + TAU * build, .06, AMBER, alpha);
                if (a == Ability.MAGMA_SPRAY) nozzle(size, q, alpha);
                else pilot(size, build);
            }
            case HEAT_WAVE -> waveFront(size * build, 1, q);
            case FIRE_WALL -> {
                yaw(packet.velocity());
                double w = 4 * size * build;
                stroke(new Vec3(-w, .06, 0), new Vec3(w, .06, 0), .065 * size, AMBER, alpha);
                flame(-w, .06, 0, .22 * size, .9 * size, 1, alpha, false);
                flame(w, .06, 0, .22 * size, .9 * size, 4, alpha, false);
            }
            case FIRE_COVER, LAVA_VENT, FLAME_COLUMN, FLASH_FIRE -> fissures(
                    switch (a) { case FIRE_COVER -> 3; case FLASH_FIRE -> 2.4; default -> 1.8; } * size,
                    build, a == Ability.FLAME_COLUMN ? GOLD : RED, alpha);
            case FIRE_RAIN -> {
                pose.translate(0, 8 * build, 0);
                overheadSeal(3.5 * size * build, alpha);
            }
            case VIOLET_FIRE -> {
                pose.translate(0, .14, 0);
                violetPetals(.80 * size, 1.45 * size, build);
            }
            case IGNITE, SPREAD_FIRE, EMBER_MARK, DETONATE_BURN -> {
                double r = (1.6 - .85 * q) * size;
                brand(r, build, a == Ability.DETONATE_BURN ? GOLD : AMBER, alpha);
                if (a == Ability.IGNITE || a == Ability.SPREAD_FIRE) flame(0, -.22, 0, .18, .75 * build, 2, alpha, false);
            }
            case FIRE_RING, FLAME_CLOAK -> crown(1.1 * size, .65 * build, alpha, 8, false);
            case KINDLE -> seed(build, size, alpha);
        }
        pose.popPose();
    }

    private void active(ElementalNetwork.Visual p, Intent spell, double age, double fade) {
        Ability a = spell.ability();
        double s = spell.scale();
        if (p.target() == -4) {
            yaw(p.velocity());
            fissures(.8 * s, 1, RED, fade);
            flame(0, .01, 0, .18 * s, .55 * s, p.id() % 11, fade, false);
            return;
        }
        if (p.target() == -3) {
            lavaPool(1.5 * s, fade);
            return;
        }
        if (p.target() == -2) {
            if (a == Ability.FIRE_RAIN) {
                orient(p.velocity());
                pose.mulPose(Axis.ZP.rotation((float)(age * .16)));
                model("fireball", 1, s);
                streaks(s * .55, spell.has(FAST) ? 3.4 : 1.5, fade, false);
            } else {
                moltenDrop(p, age, s, fade);
            }
            return;
        }
        switch (a) {
            case FIREBALL, FIREBLADE, FIRE_SERPENT, HEAT_WAVE -> {
                orient(p.velocity());
                if (a == Ability.HEAT_WAVE) waveFront(s, fade, age);
                else {
                    if (a == Ability.FIREBLADE) pose.mulPose(Axis.ZP.rotationDegrees(-19));
                    if (a == Ability.FIREBALL) pose.mulPose(Axis.ZP.rotation((float)(age * .13)));
                    if (a == Ability.FIRE_SERPENT) {
                        snakePoised(1, s, smooth(age / 9));
                        if (age > 8) {
                            pose.translate(0, .25 * s, -2.95 * s);
                            streaks(s * .32, spell.has(FAST) ? 3.8 : 1.2, fade, false);
                        }
                    } else {
                        model(a == Ability.FIREBALL ? "fireball" : "fireblade", 1, 1.5 * s);
                        streaks(s, spell.has(FAST) ? 5.4 : 2, fade, a == Ability.FIREBLADE);
                    }
                }
            }
            case MAGMA_SPRAY, FIRE_BREATH -> {
                orient(p.velocity());
                pilot(.8 * s, fade);
                if (a == Ability.FIRE_BREATH) breath(spell, fade, age);
                else {
                    double pulse = fraction(age / (spell.has(FAST) ? 3 : 6));
                    nozzle(s, pulse, fade);
                }
            }
            case FIRE_COVER -> {
                double progress = Math.min(1, .30 + age * .70 / 12);
                fissures(3 * s, progress, RED, fade);
                for (int n = 0; n < 16; n++) {
                    Vec3 pos = ground(n * 2.4, 3 * s * Math.sqrt((n + .5) / 16) * progress, .04);
                    flame(pos.x, pos.y, pos.z, .26 * s, (.7 + .35 * Math.sin(n)) * s, n, fade, false);
                }
            }
            case FIRE_WALL -> {
                yaw(p.velocity());
                double grown = smooth((age + 2) / 7);
                for (int n = -7; n <= 7; n++) {
                    double h = 3 * s * (.83 + .17 * Math.cos(n * .85 + now * .13)) * grown;
                    flame(n * .54 * s, .03, 0, .47 * s, h, n * 1.9, fade, false);
                }
            }
            case FLAME_COLUMN -> {
                double height = (4.4 + 1.6 * Math.cos((age - 1) * TAU / 10)) * s;
                fissures(1.8 * s, 1, GOLD, fade);
                crown(1.15 * s, height, fade, 9, false);
                flame(0, 0, 0, .68 * s, height * .9, age * .16, fade, false);
            }
            case FIRE_RAIN -> {
                pose.translate(0, 8, 0);
                overheadSeal(3.5 * s, fade);
            }
            case FIRE_RING -> crown(Math.min(7 * s, age * .25 * spell.speed()), 1.05 * s, fade, 21, false);
            case LAVA_VENT -> {
                lavaPool(1.8 * s, fade);
                double beat = fraction(age / (spell.has(FAST) ? 5 : 10));
                flame(0, 0, 0, .42 * s, (.5 + 1.4 * (1 - beat)) * s, 0, fade, false);
            }
            case KINDLE -> {
                seed(1, s, fade);
                brand(4 * s, .38, CINDER, fade * .65);
            }
            case FLASH_FIRE -> {
                if (age < 6) fissures(2.4 * s, 1 - age / 8, GOLD, fade);
            }
            case DETONATE_BURN, EMBER_MARK -> {
                double remaining = unit(1 - age / p.duration());
                brand((.35 + remaining * .75) * s, 1, remaining < .22 ? GOLD : RED, fade);
            }
            case FLAME_CLOAK -> crown(.63 * s, 1.25 * s, fade * .75, 6, false);
            case VIOLET_FIRE, IGNITE, SPREAD_FIRE -> brand(.65 * s, Math.min(1, age / 3), AMBER, fade);
        }
        if (spell.has(EMPOWER) || spell.has(DURABLE) || spell.has(SHOCK)) {
            pose.pushPose();
            if (a.target != Target.PROJECTILE && a.target != Target.CHANNEL) pose.mulPose(Axis.XP.rotationDegrees(-90));
            modifiers(spell, .93 * s, 1, fade * .8);
            pose.popPose();
        }
    }

    private void breath(Intent spell, double fade, double age) {
        double spread = Math.tan(Math.toRadians(spell.has(GIANT) ? 42 : 22));
        double cycle = spell.has(FAST) ? 5 : 10;
        // The advancing bright ridge coincides with each server damage pulse.
        for (int strand = 0; strand < 7; strand++) {
            double angle = strand * TAU / 7;
            Vec3 previous = polar(angle, .12, 0);
            for (int step = 1; step <= 8; step++) {
                double q = step / 8.0;
                double z = q * 9;
                double a = angle + q * 1.8 + .13 * Math.sin(age * .24 + strand + q);
                double r = (.10 + z * spread) * (.73 + .13 * Math.sin(age * .21 - q * 6 + strand));
                Vec3 tip = polar(a, r, z);
                double crest = .5 + .5 * Math.cos(TAU * (age / cycle - q));
                double width = (.09 + .21 * q) * (spell.has(GIANT) ? 1.4 : 1);
                taper(previous, tip, width, width * .67, crest > .70 ? AMBER : RED, fade * (.74 - .4 * q));
                previous = tip;
            }
        }
    }

    private void burn(Ability ability, double heat, double fade) {
        boolean purple = ability == Ability.VIOLET_FIRE;
        if (purple) {
            pose.pushPose();
            pose.translate(0, -.21, 0);
            violetPetals(.75 + .18 * heat, 1.75 + .55 * heat, fade);
            pose.popPose();
            return;
        }
        crown(.34 + .18 * heat, .65 + .7 * heat, fade, 3 + (int)(heat * 4), purple);
    }

    private void impact(ElementalNetwork.Visual packet, Intent spell, double ticks) {
        double q = unit(ticks / 16);
        if (q >= 1) return;
        double spread = 1 - Math.pow(1 - q, 2);
        double alpha = 1 - q;
        double size = spell.scale();
        Ability a = spell.ability();
        double radius = switch (a) {
            case DETONATE_BURN -> Math.max(.1, packet.velocity().length());
            case FLASH_FIRE -> 2.4 * size;
            case FIREBALL -> 1.4 * size;
            case EMBER_MARK -> 2.2 * size;
            case FIRE_SERPENT -> .95 * size;
            default -> .85 * size;
        };
        double r = radius * (.12 + .88 * spread);
        boolean directional = a == Ability.FIRE_SERPENT || a == Ability.FIREBLADE || a == Ability.FIRE_BREATH;
        if (directional) orient(packet.velocity());
        int count = directional ? 7 : 13;
        for (int n = 0; n < count; n++) {
            double angle = n * 2.4;
            double h = (radius * (.35 + .65 * fraction(n * .618 + .12)) * Math.sin(q * Math.PI)) + .08;
            Vec3 tip = directional ? polar(angle, r * .6, q * size + h * .45) : ground(angle, r, h);
            Vec3 tail = tip.scale(.5 + .27 * q);
            taper(tail, tip, (.15 - q * .09) * size, .015 * size, n % 3 == 0 ? GOLD : RED, alpha);
        }
        if (!directional || spell.has(SHOCK)) {
            pose.pushPose();
            if (!directional) pose.mulPose(Axis.XP.rotationDegrees(-90));
            arcXY(r, .05, 0, TAU, .05 * size, AMBER, alpha);
            if (spell.has(SHOCK)) arcXY(r * 1.55, .2, 0, TAU, .09 * size, GOLD, alpha);
            pose.popPose();
        }
    }

    private void modifiers(Intent spell, double r, double progress, double alpha) {
        if (spell.has(EMPOWER)) {
            for (int layer = 0; layer < 2; layer++) {
                for (int i = 0; i < 3; i++) {
                    double a = (layer == 0 ? 1 : -1) * now * .07 + i * TAU / 3;
                    arcXY(r * (1.12 + .15 * layer), -.16 - layer * .30, a, a + 1.6 * progress, .047, GOLD, alpha);
                }
            }
        }
        if (spell.has(FAST)) {
            for (int i = 0; i < 4; i++) {
                double a = i * TAU / 4 + now * .12;
                stroke(polar(a, r * .74, -.75), polar(a, r * .53, .22), .028, AMBER, alpha);
            }
        }
        if (spell.has(DURABLE)) {
            for (int i = 0; i < 5; i++) {
                double a = i * TAU / 5 - now * .016;
                stroke(polar(a, r * 1.17, -.06), polar(a + .12, r * 1.36, .12), .035, RED, alpha);
                arcXY(r * 1.36, .12, a + .12, a + 1.12, .023, AMBER, alpha * .8);
            }
        }
        if (spell.has(SHOCK)) {
            double pulse = fraction(now * .08);
            arcXY(r * (.6 + pulse * .65), -.4 + pulse * .75, 0, TAU, .052, GOLD, alpha * (1 - pulse));
        }
    }

    private void pilot(double size, double brightness) {
        for (int i = 0; i < 3; i++) {
            double angle = i * TAU / 3 + now * .11;
            taper(polar(angle, .30 * size, -.02), polar(angle + .9, .08 * size, .82 * size),
                    .13 * size, .018, AMBER, brightness);
        }
    }

    private void streaks(double s, double length, double alpha, boolean blade) {
        for (int i = 0; i < 5; i++) {
            double a = i * TAU / 5 + (blade ? 0 : now * .09);
            double y = blade ? .15 : 1;
            Vec3 root = new Vec3(Math.cos(a) * s * .55, Math.sin(a) * s * .37 * y, -.25 * s);
            Vec3 tail = new Vec3(root.x * .42, root.y * .42, -length * (.68 + i * .08));
            taper(root, tail, s * .11, .008, i == 0 ? GOLD : RED, alpha * .8);
        }
    }

    private void waveFront(double size, double alpha, double phase) {
        for (int i = 0; i < 3; i++) {
            double radius = size * (1.15 + i * .48);
            for (int side = 0; side < 2; side++) {
                double start = side * Math.PI - 1.22 + .06 * Math.sin(phase * .15);
                arcXY(radius, -i * .34, start, start + 2.44, .06 * size, i == 0 ? AMBER : RED, alpha);
            }
        }
    }

    private void overheadSeal(double radius, double alpha) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        for (int i = 0; i < 5; i++) {
            double a = i * TAU / 5 + now * .022;
            arcXY(radius, 0, a, a + .77, .05, RED, alpha);
            stroke(polar(a, radius, 0), polar(a + .52, radius * .6, -.3), .04, AMBER, alpha);
        }
        pose.popPose();
    }

    private void brand(double r, double open, int color, double alpha) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float)(now * .05)));
        for (int n = 0; n < 4; n++) {
            double a = n * Math.PI / 2;
            Vec3 top = ground(a, .38 * r, r * open);
            Vec3 mid = ground(a + .40, r, 0);
            Vec3 foot = ground(a + .70, .38 * r, -r * open);
            stroke(top, mid, .037 * r, color, alpha);
            stroke(mid, foot, .037 * r, color, alpha);
        }
        pose.popPose();
    }

    private void moltenDrop(ElementalNetwork.Visual p, double age, double size, double fade) {
        // Follow the same gravitational arc backwards from the latest server position.
        Vec3 v = p.velocity();
        for (int i = 1; i <= 4; i++) {
            double back = Math.min(age, i * .88);
            if (back <= 0) break;
            Vec3 trail = v.scale(-back).add(0, -.0175 * back * back, 0);
            double width = (.19 - .028 * i) * size;
            crystal(trail, width, width * .81, width, i % 2 == 0 ? 0xA23F15 : RED, AMBER);
        }
        orient(v);
        pose.mulPose(Axis.ZP.rotation((float)(age * .23 + p.id() % 7)));
        double bulge = 1.28 + .33 * Math.sin(age * .36);
        pose.scale((float)(size * bulge), (float)(size * bulge), (float)(size * 1.55));
        model("magma-drop", 1, 1);
        crystal(new Vec3(.18, .09, .06), .14, .10, .15, 0xF87513, GOLD);
        taper(new Vec3(.11, -.04, -.14), new Vec3(-.12, .12, -1.0), .13, .025, AMBER, fade);
    }

    /** Two refractory cheeks frame an open throat. Molten seams travel forward with each pulse. */
    private void nozzle(double size, double pulse, double alpha) {
        for (int side : new int[]{-1, 1}) {
            Vec3 heel = new Vec3(side * .63 * size, -.21 * size, -.22 * size);
            Vec3 lip = new Vec3(side * (.41 + .09 * pulse) * size, .08 * size, .52 * size);
            crystal(heel, .23 * size, .20 * size, .34 * size, 0x5A2921, 0xDB5C22);
            crystal(lip, .17 * size, .16 * size, .24 * size, 0x863820, AMBER);
            taper(heel, lip, .12 * size, .045 * size, AMBER, alpha);
        }
        double pressure = .16 + .14 * Math.sin(Math.PI * pulse);
        crystal(new Vec3(0, -.13 * size, .37 * size), pressure * size, .18 * size,
                .33 * size, 0xF69A18, GOLD);
    }

    /** Five free-standing violet petals grow out past the silhouette of the creature. */
    private void violetPetals(double radius, double height, double growth) {
        if (growth <= .01) return;
        for (int i = 0; i < 5; i++) {
            double angle = i * TAU / 5 + now * .026;
            double beat = .90 + .10 * Math.sin(now * .18 + i);
            Vec3 root = ground(angle - .48, radius * .54 * growth, .06);
            Vec3 shoulder = ground(angle + .16, radius * growth * beat, height * .46 * growth);
            Vec3 tip = ground(angle + .75, radius * .72 * growth, height * growth * (i % 2 == 0 ? beat : .69));
            double width = radius * .24 * growth;
            mineralStem(root, shoulder, width * .63, width, 0x522181, 0xBD57FF);
            mineralStem(shoulder, tip, width, .006, 0x742DA5, 0xE09FFF);
            taper(shoulder, tip, width * .35, .008, LILAC, .92 * growth);
            double lift = fraction(now * .055 + i * .20);
            Vec3 ash = tip.add(Math.sin(angle + lift * 3) * lift * .24, lift * height * .40, 0);
            double fleck = (.085 - .050 * lift) * growth;
            crystal(ash, fleck, fleck * 2.2, fleck, VIOLET, LILAC);
        }
        pose.pushPose();
        pose.translate(0, .34 * growth, 0);
        model("violet-fire", growth, radius * 1.22);
        pose.popPose();
    }

    private void crystal(Vec3 at, double rx, double ry, double rz, int shell, int seam) {
        Vec3 top = at.add(0, ry, 0), foot = at.add(0, -ry, 0);
        for (int i = 0; i < 5; i++) {
            double a = i * TAU / 5;
            double b = (i + 1) * TAU / 5;
            Vec3 v = at.add(Math.cos(a) * rx, 0, Math.sin(a) * rz);
            Vec3 w = at.add(Math.cos(b) * rx, 0, Math.sin(b) * rz);
            mineralQuad(top, w, v, top, i % 3 == 0 ? seam : shell);
            mineralQuad(foot, v, w, foot, shell);
        }
    }

    private void mineralStem(Vec3 from, Vec3 to, double start, double end, int shell, int seam) {
        Vec3 v = to.subtract(from).normalize();
        Vec3 side = v.cross(new Vec3(0, 0, 1)).normalize();
        Vec3 other = v.cross(side);
        Vec3 b = side.scale(-.5).add(other.scale(.866));
        Vec3 c = side.scale(-.5).subtract(other.scale(.866));
        mineralSide(from, to, side, b, start, end, shell);
        mineralSide(from, to, b, c, start, end, seam);
        mineralSide(from, to, c, side, start, end, shell);
    }

    private void mineralSide(Vec3 from, Vec3 to, Vec3 a, Vec3 b, double start, double end, int color) {
        mineralQuad(from.add(a.scale(start)), from.add(b.scale(start)), to.add(b.scale(end)), to.add(a.scale(end)), color);
    }

    private void mineralQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        VertexConsumer out = buffers.getBuffer(MINERAL);
        Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
        mineralVertex(out, a, color, n);
        mineralVertex(out, b, color, n);
        mineralVertex(out, c, color, n);
        mineralVertex(out, d, color, n);
    }

    private void mineralVertex(VertexConsumer out, Vec3 v, int color, Vec3 n) {
        out.vertex(pose.last().pose(), (float)v.x, (float)v.y, (float)v.z)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                .uv(.5f, .5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.last().normal(), (float)n.x, (float)n.y, (float)n.z).endVertex();
    }

    private void seed(double growth, double size, double alpha) {
        Vec3 center = new Vec3(0, .43 * size, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 foot = ground(i * TAU / 4, .5 * size, .02);
            stroke(foot, center, .08 * size, CINDER, alpha);
        }
        flame(center.x, center.y, 0, .27 * size, size * (.68 + .15 * Math.sin(now * .11)) * growth, 1, alpha, false);
    }

    private void lavaPool(double radius, double alpha) {
        for (int i = 0; i < 8; i++) {
            Vec3 crust = ground(i * 2.4, radius * Math.sqrt((i + .25) / 8), .075);
            crystal(crust, .31 * radius, (.06 + .05 * Math.cos(i)) * radius,
                    .27 * radius, 0x603029, 0xDF5B13);
            Vec3 seam = crust.add(0, .035, 0);
            stroke(seam, seam.add(.24 * radius, .018, -.11 * radius), .045 * radius, AMBER, alpha);
        }
        fissures(radius, 1, AMBER, alpha);
        for (int i = 0; i < 3; i++) {
            Vec3 at = ground(i * 2.4, radius * .5, .03);
            double bubble = fraction(now * .04 + i * .37);
            flame(at.x, at.y, at.z, (.12 + bubble * .1) * radius, (.12 + bubble * .25) * radius, i, alpha, false);
        }
    }

    private void fissures(double radius, double growth, int color, double alpha) {
        for (int i = 0; i < 7; i++) {
            double angle = i * TAU / 7;
            Vec3 from = ground(angle, .15 * radius, .04);
            for (int k = 1; k <= 3; k++) {
                double t = k / 3.0 * growth;
                Vec3 to = ground(angle + .18 * Math.sin(k * 3.7 + i), radius * t, .045);
                taper(from, to, radius * .036 * (1.3 - .65 * t), .025 * radius, color, alpha);
                if (k == 2) stroke(to, ground(angle + .35, radius * t * 1.24, .05), .018 * radius, AMBER, alpha);
                from = to;
            }
        }
    }

    private void crown(double radius, double height, double alpha, int count, boolean purple) {
        for (int i = 0; i < count; i++) {
            Vec3 pos = ground(i * TAU / count, radius, 0);
            flame(pos.x, pos.y, pos.z, Math.max(.06, Math.min(height * .20, radius * .45)), height, i * 1.9, alpha, purple);
        }
    }

    private void flame(double x, double y, double z, double width, double height, double seed,
                       double alpha, boolean purple) {
        double phase = now * .19 + seed;
        Vec3 base = new Vec3(x, y, z);
        Vec3 belly = base.add(width * .28 * Math.sin(phase), height * .34, width * .24 * Math.cos(phase));
        Vec3 tip = base.add(width * .95 * Math.sin(phase + 1.2), height * (.92 + .12 * Math.sin(phase)),
                width * .65 * Math.cos(phase + .8));
        taper(base, belly, width * .85, width * .7, purple ? VIOLET : CINDER, alpha);
        taper(belly, tip, width * .7, .006, purple ? VIOLET : RED, alpha);
        taper(base, base.lerp(tip, .61), width * .37, .01, purple ? LILAC : AMBER, alpha);
    }

    private void snakePoised(double growth, double size, double release) {
        // Show the fang and nearer eye in three-quarter view. The tail coils away from the
        // caster, into the space behind the head, then unrolls as the head actually travels.
        double rest = 1 - release;
        pose.pushPose();
        pose.translate(-.57 * Math.sqrt(size) * rest, -.14 * rest, .34 * size * rest);
        pose.mulPose(Axis.YP.rotationDegrees((float)(-116 * rest)));
        pose.mulPose(Axis.ZP.rotationDegrees((float)(-8 * rest)));
        snake(growth, Math.pow(size, .79 + .21 * release), rest * 1.5);
        pose.popPose();
    }

    private void snake(double growth, double size, double coil) {
        pose.pushPose();
        float s = (float)(size * 1.14);
        pose.scale(s, s, s);
        ElementalMeshes.draw(pose, buffers, "fire-snake", growth, now, coil);
        if (growth > .45) {
            double blink = .032 * smooth((growth - .45) * 4);
            for (int side : new int[]{-1,1}) {
                crystal(new Vec3(side * .32, .395, .34), .016, blink, .052, 0xD86C18, GOLD);
            }
        }
        pose.popPose();
    }

    private void model(String name, double progress, double scale) {
        pose.pushPose();
        pose.scale((float)scale, (float)scale, (float)scale);
        ElementalMeshes.draw(pose, buffers, name, progress, now);
        pose.popPose();
    }

    private void yaw(Vec3 dir) {
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(dir.x, dir.z)));
    }

    private void orient(Vec3 dir) {
        if (dir.lengthSqr() < 1e-7) return;
        Vec3 n = dir.normalize();
        pose.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float)n.x, (float)n.y, (float)n.z));
    }

    private static Vec3 polar(double angle, double r, double z) {
        return new Vec3(Math.cos(angle) * r, Math.sin(angle) * r, z);
    }

    private static Vec3 ground(double angle, double r, double y) {
        return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    private void arcXY(double radius, double z, double start, double end, double width, int color, double alpha) {
        int segments = Math.max(2, (int)Math.ceil(Math.abs(end - start) * 3.8));
        Vec3 from = polar(start, radius, z);
        for (int i = 1; i <= segments; i++) {
            Vec3 to = polar(start + (end - start) * i / segments, radius, z);
            stroke(from, to, width, color, alpha);
            from = to;
        }
    }

    private void stroke(Vec3 from, Vec3 to, double width, int color, double alpha) {
        taper(from, to, width, width, color, alpha);
    }

    /** Three tapered sides give sparks thickness even when viewed along the ground. */
    private void taper(Vec3 from, Vec3 to, double start, double end, int color, double alpha) {
        if (alpha <= .01 || from.distanceToSqr(to) < 1e-9) return;
        Vec3 axis = to.subtract(from).normalize();
        Vec3 right = axis.cross(Math.abs(axis.y) > .85 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = axis.cross(right);
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        Vec3 second = right.scale(-.5).add(up.scale(.8660254));
        Vec3 third = right.scale(-.5).subtract(up.scale(.8660254));
        facet(out, from, to, right, second, start, end, color, alpha);
        facet(out, from, to, second, third, start, end, color, alpha * .72);
        facet(out, from, to, third, right, start, end, color, alpha * .88);
    }

    private void facet(VertexConsumer out, Vec3 from, Vec3 to, Vec3 left, Vec3 right,
                       double start, double end, int color, double alpha) {
        vertex(out, from.x + left.x * start, from.y + left.y * start, from.z + left.z * start, color, alpha);
        vertex(out, from.x + right.x * start, from.y + right.y * start, from.z + right.z * start, color, alpha);
        vertex(out, to.x + right.x * end, to.y + right.y * end, to.z + right.z * end, color, alpha);
        vertex(out, to.x + left.x * end, to.y + left.y * end, to.z + left.z * end, color, alpha);
    }

    private void vertex(VertexConsumer out, double x, double y, double z, int color, double alpha) {
        out.vertex(pose.last().pose(), (float)x, (float)y, (float)z)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (int)(255 * unit(alpha))).endVertex();
    }

    private static double unit(double v) { return Math.max(0, Math.min(1, v)); }
    private static double fraction(double v) { return v - Math.floor(v); }
    private static double smooth(double v) { double t = unit(v); return t * t * (3 - 2 * t); }
}
