package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.WeaponChampion.Strike;
import com.tnc.tnc.warrior.WarriorWeaponRanks.Branch;
import com.tnc.tnc.warrior.WeaponChampionCue;
import com.tnc.tnc.warrior.WeaponChampionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World-space warning and weapon light. Both use the locked server strike footprint. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class WeaponChampionEffects {
    private static final double CELL = .25;
    private static final int GRID = (int) (WeaponChampionCue.FOOTPRINT_HALF * 2 / CELL);
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static final WeakHashMap<WeaponChampionCue, Footprint> FOOTPRINTS = new WeakHashMap<>();

    private record View(Vec3 eye, Vec3 origin, double scale) {}
    private record Edge(Vec3 from, Vec3 to) {}
    private record Strip(double x, double z, double endX) {}
    private record Footprint(List<Strip> strips, List<Edge> edges) {}

    private WeaponChampionEffects() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        double now = (double) level.getGameTime() + event.getPartialTick();
        Vec3 eye = event.getCamera().getPosition();
        VertexConsumer out = WarriorUltimateRadianceRenderType.buffer(BUFFER);
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof WeaponChampionEntity boss) || boss.distanceToSqr(eye) > 4096) continue;
            WeaponChampionCue warning = boss.warning();
            int color = boss.champion().color();
            if (warning != null && now < warning.start() + warning.ticks() + 3) {
                View camera = new View(eye, warning.origin(), warning.scale());
                renderFootprint(out, camera, warning, color, warning.progress(now), false);
                if (boss.champion().branch() == Branch.BOW) {
                    double flight = 1 - (warning.start() + warning.ticks() - now) / 9;
                    if (flight > 0) volley(out, camera, warning, boss, event.getPartialTick(), color, Math.min(1, flight), false);
                }
            }
            WeaponChampionCue impact = boss.impactCue();
            if (impact != null && now < impact.start() + impact.ticks()) {
                View camera = new View(eye, impact.origin(), impact.scale());
                double age = impact.progress(now);
                renderFootprint(out, camera, impact, color, age, true);
                if (boss.champion().branch() == Branch.BOW) {
                    volley(out, camera, impact, boss, event.getPartialTick(), color, age, true);
                }
                if (impact.strike() != Strike.COMBO_FINISH || boss.champion().branch() != Branch.BOW)
                    impact(out, camera, impact, color, age);
            }
        }
        BUFFER.endBatch(WarriorUltimateRadianceRenderType.TYPE);
    }

    private static Footprint sample(WeaponChampionCue cue) {
        boolean[][] cells = new boolean[GRID + 2][GRID + 2];
        double left = cue.home().x - WeaponChampionCue.FOOTPRINT_HALF;
        double top = cue.home().z - WeaponChampionCue.FOOTPRINT_HALF;
        double y = cue.home().y + .035;
        for (int z = 1; z <= GRID; z++) {
            for (int x = 1; x <= GRID; x++) {
                cells[z][x] = cue.templateHits(left + (x - .5) * CELL, top + (z - .5) * CELL);
            }
        }
        List<Strip> strips = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int z = 1; z <= GRID; z++) {
            double tz = top + (z - 1) * CELL;
            int start = -1;
            for (int x = 1; x <= GRID + 1; x++) {
                double lx = left + (x - 1) * CELL;
                if (!cells[z][x]) {
                    if (start >= 0) strips.add(new Strip(left + (start - 1) * CELL, tz, lx));
                    start = -1;
                    continue;
                }
                if (start < 0) start = x;
                Vec3 a = new Vec3(lx, y, tz);
                Vec3 b = a.add(CELL, 0, 0);
                Vec3 c = b.add(0, 0, CELL);
                Vec3 d = a.add(0, 0, CELL);
                if (!cells[z - 1][x]) edges.add(new Edge(a, b));
                if (!cells[z + 1][x]) edges.add(new Edge(d, c));
                if (!cells[z][x - 1]) edges.add(new Edge(a, d));
                if (!cells[z][x + 1]) edges.add(new Edge(b, c));
            }
        }
        return new Footprint(strips, edges);
    }

    private static void renderFootprint(VertexConsumer out, View camera, WeaponChampionCue cue,
                                        int color, double progress, boolean impact) {
        Footprint mesh = FOOTPRINTS.computeIfAbsent(cue, WeaponChampionEffects::sample);
        double y = cue.home().y + (impact ? .06 : .035);
        float fill = (float) (impact ? .20 * (1 - progress) : .055 + .11 * progress * progress);
        if (cue.strike().isUltimate()) fill *= 1.55f;
        float edge = (float) (impact ? .95 * (1 - progress) : .60 + .35 * progress);
        for (Strip strip : mesh.strips()) {
            Vec3 a = new Vec3(strip.x(), y, strip.z());
            Vec3 b = new Vec3(strip.endX(), y, strip.z());
            quad(out, camera, a, b, b.add(0, 0, CELL), a.add(0, 0, CELL), color, fill);
        }
        for (Edge line : mesh.edges()) {
            Vec3 lift = new Vec3(0, impact ? progress * .24 + .035 : 0, 0);
            Vec3 a = line.from().add(lift);
            Vec3 b = line.to().add(lift);
            Vec3 normal = new Vec3(a.z - b.z, 0, b.x - a.x).normalize();
            ribbon(out, camera, a, b, normal, impact ? .072 : .040, color, edge);
        }
    }

    private static void impact(VertexConsumer out, View camera, WeaponChampionCue cue, int color, double age) {
        float fade = (float) Math.pow(1 - age, 2);
        Vec3 at = cue.origin();
        Vec3 f = cue.forward();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        switch (cue.strike()) {
            case COMBO_FINISH -> arc(out, camera, cue, color, age, 4, Math.toRadians(cue.arc() * .5), false);
            case SWORD, SWORD_GUARD -> arc(out, camera, cue, color, age, 3.4, 1.13, false);
            case TWIN_CUT -> arc(out, camera, cue, color, age, 3.8, 1.83, cue.wavesLeft() == 1);
            case CLOUD_EDGES -> {
                arc(out, camera, cue, color, age, 5.25, 1.43, cue.wavesLeft() == 2);
                arc(out, camera, cue, color, Math.min(1, age + .18), 4.8, 1.34, cue.wavesLeft() == 2);
            }
            case BACKSWING, HALBERD_SWEEP -> arc(out, camera, cue, color, age, 4.55, 1.83, true);
            case CLOSING_REAP, BONE_ORBIT -> arc(out, camera, cue, color, age, 5.5, Math.PI, cue.wavesLeft() == 1);
            case REAP, SCYTHE_CORE -> arc(out, camera, cue, color, age,
                cue.strike() == Strike.REAP ? 5.7 : 2.8, Math.PI, true);
            case CLEAVE, CROSS, SPADE_FORK, PIVOT_CROSS, MOON_DIVIDE -> {
                if (cue.strike() == Strike.MOON_DIVIDE && cue.wavesLeft() == 2) {
                    arc(out, camera, cue, color, age, 5.8, 1.8, true);
                    break;
                }
                boolean cross = cue.strike() == Strike.CROSS || cue.strike() == Strike.PIVOT_CROSS;
                for (int axis = 0; axis < (cross ? 4 : 1); axis++) {
                    Vec3 direction = switch (axis) {
                        case 1 -> side;
                        case 2 -> f.scale(-1);
                        case 3 -> side.scale(-1);
                        default -> f;
                    };
                    Vec3 tip = tip(cue, direction, 8.8);
                    Vec3 up = new Vec3(0, 1, 0);
                    Vec3 top = at.add(0, 2.6 * fade + .28, 0).add(direction.scale(.5));
                    ribbon(out, camera, top, tip.add(0, .14, 0), up, .55 * fade, color, fade * .72f);
                    ribbon(out, camera, top, tip.add(0, .14, 0), up, .055, 0xfff1d3, fade);
                }
            }
            case THRUST, RETURN_THRUST, TURNING_THRUST, SILVER_GATE -> {
                if (cue.strike() == Strike.SILVER_GATE && cue.wavesLeft() == 1) {
                    arc(out, camera, cue, color, age, 5.3, 1.7, false);
                    break;
                }
                Vec3 blade = at.add(0, 1.05, 0);
                Vec3 tip = tip(cue, f, cue.strike() == Strike.THRUST ? 8.9 : 7.9)
                    .add(0, 1.02, 0);
                double[] lanes = cue.strike() == Strike.SILVER_GATE ? new double[]{-1.55, 1.55} : new double[]{0};
                for (double lane : lanes) {
                    Vec3 offset = side.scale(lane);
                    Vec3 start = blade.add(offset), end = tip.add(offset);
                    ribbon(out, camera, start, end, side, .23 * fade, color, .82f * fade);
                    ribbon(out, camera, start, end, side, .037, 0xffffff, fade);
                    ribbon(out, camera, start, end, new Vec3(0, 1, 0), .035, color, .88f * fade);
                }
            }
            case SWORD_RAIN, DRAGON_LANCES -> {
                boolean sword = cue.strike() == Strike.SWORD_RAIN;
                double lean = cue.wavesLeft() == 3 ? -1 : cue.wavesLeft() == 2 ? 1 : 0;
                Vec3 axis = sword ? f.add(side.scale(.64 * lean)) : f;
                Vec3 base = sword ? at : at.add(side.scale(2.8 * lean));
                Vec3 start = base.add(axis.scale(sword ? -8 : 0)).add(0, 1.2, 0);
                Vec3 end = base.add(axis.scale(sword ? 8 : 10)).add(0, 1.2, 0);
                ribbon(out, camera, start, end, side, .60 * fade, color, fade * .8f);
                ribbon(out, camera, start, end, new Vec3(0, 1, 0), .08, 0xfffbec, fade);
            }
            case ECLIPSE_WHEEL -> {
                arc(out, camera, cue, color, age, cue.wavesLeft() * 2.1, Math.PI, false);
                arc(out, camera, cue, color, age, cue.wavesLeft() * 2.1, Math.PI, true);
            }
            case QUAKE, INNER_QUAKE, ROLLING_QUAKE, BELL_STEPS, IRON_GRID, FOX_TRAIL, FURNACE_CUT,
                 MOUNTAIN_FALL, THUNDER_TIDE, SKY_LATTICE, METEOR_VOLLEY,
                 SHELL_RESONANCE, DRAGON_WEAVE, STAR_GATE, MANTIS_HOOK -> {
                // Rising hot seams trace the actual live ring; later shockwaves use different radii.
                for (Edge line : FOOTPRINTS.computeIfAbsent(cue, WeaponChampionEffects::sample).edges()) {
                    Vec3 up = new Vec3(0, Math.sin(age * Math.PI) * (cue.strike().isUltimate() ? 2.8 : 1.35), 0);
                    quad(out, camera, line.from(), line.to(), line.to().add(up), line.from().add(up),
                        color, fade * .4f);
                }
            }
            case VOLLEY, QUICK_VOLLEY, ARROW_CURTAIN, HORN_CAGE, CINDER_PLUMES -> {}
        }
    }

    private static void volley(VertexConsumer out, View camera, WeaponChampionCue cue, WeaponChampionEntity boss,
                               float partialTick, int color, double progress, boolean landed) {
        Vec3 forward = cue.forward();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        // The floor mesh uses normalized reach. Convert the archer to that space exactly once.
        Vec3 bow = boss.getPosition(partialTick).add(0, boss.getBbHeight() * .58, 0);
        Vec3 delta = bow.subtract(cue.origin());
        Vec3 launch = cue.origin().add(delta.x / cue.scale(), delta.y, delta.z / cue.scale());
        float glow = (float) (landed ? (1 - progress) * (1 - progress) : Math.min(1, progress * 5));
        for (int i = 0; i < 9; i++) {
            double angle = i * 2.399963;
            double radius = 2 * Math.sqrt(i / 9.0);
            Vec3 hit = switch (cue.strike()) {
                case ARROW_CURTAIN -> cue.origin().add(side.scale((i - 4) * 1.25))
                    .add(forward.scale(Math.sin(angle) * .66));
                case HORN_CAGE -> {
                    double reach = cue.wavesLeft() == 1 ? radius : 4.3 + (i % 3) * .45;
                    yield cue.origin().add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
                }
                case CINDER_PLUMES -> {
                    double lane = (i % 3 - 1) * 3.7;
                    double along = (i / 3 - 1) * 3.25;
                    yield cue.origin().add(side.scale(cue.wavesLeft() == 2 ? lane : along))
                        .add(forward.scale(cue.wavesLeft() == 2 ? along : lane));
                }
                case METEOR_VOLLEY -> switch (cue.wavesLeft()) {
                    case 3 -> cue.origin().add(forward.scale(i % 2 == 0 ? 3.6 : -3.6)).add(side.scale(i - 4));
                    case 2 -> cue.origin().add(side.scale(i % 2 == 0 ? 2.4 : -2.4)).add(forward.scale(i - 4));
                    default -> cue.origin().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                };
                default -> cue.origin().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            };
            if (!cue.templateHits(hit.x, hit.z)) continue;
            hit = hit.add(0, .10, 0);
            if (landed) {
                Vec3 spark = hit.add(side.scale(.27 * Math.cos(angle) * progress))
                    .add(forward.scale(.27 * Math.sin(angle) * progress)).add(0, .24 + .72 * progress, 0);
                ribbon(out, camera, hit, spark, side, .065 * glow, color, glow);
                ribbon(out, camera, hit, spark, forward, .026 * glow, 0xfff6e3, glow);
                continue;
            }
            double loft = 1.7 + .27 * (i % 3) + (cue.strike().isUltimate() ? 2.2 : 0);
            double slice = .20;
            Vec3 last = arrowPoint(launch, hit, Math.max(0, progress - slice), loft);
            for (int segment = 1; segment <= 4; segment++) {
                double t = Math.max(0, progress - slice + slice * segment / 4);
                Vec3 tip = arrowPoint(launch, hit, t, loft);
                ribbon(out, camera, last, tip, up, .018 + .014 * segment, color, glow * segment / 5);
                ribbon(out, camera, last, tip, side, .018, 0xfff4df, glow * segment / 4);
                last = tip;
            }
            Vec3 back = arrowPoint(launch, hit, Math.max(0, progress - .035), loft);
            Vec3 wing = side.scale(.065);
            quad(out, camera, last, back.add(wing), back.subtract(up.scale(.045)), back.subtract(wing), color, glow);
        }
    }

    private static Vec3 arrowPoint(Vec3 launch, Vec3 hit, double t, double loft) {
        return launch.lerp(hit, t).add(0, 4 * loft * t * (1 - t), 0);
    }

    private static void arc(VertexConsumer out, View camera, WeaponChampionCue cue, int color,
                            double age, double radius, double span, boolean reverse) {
        Vec3 forward = cue.forward();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        double reach = radius * (.82 + .18 * age);
        double head = -span + 2 * span * Math.min(1, age * 2.6 + .08);
        double tail = Math.max(-span, head - 1.22 - age * .75);
        float fade = (float) Math.pow(1 - age, 1.4);
        for (int i = 0; i < 24; i++) {
            double a = (tail + (head - tail) * i / 24) * (reverse ? -1 : 1);
            double b = (tail + (head - tail) * (i + 1) / 24) * (reverse ? -1 : 1);
            Vec3 d1 = forward.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            Vec3 d2 = forward.scale(Math.cos(b)).add(side.scale(Math.sin(b)));
            Vec3 start = tip(cue, d1, reach).add(0, 1.07 + .32 * Math.sin(a), 0);
            Vec3 end = tip(cue, d2, reach).add(0, 1.07 + .32 * Math.sin(b), 0);
            Vec3 inner1 = start.subtract(d1.scale(.60 + .62 * fade)).add(0, -.20, 0);
            Vec3 inner2 = end.subtract(d2.scale(.60 + .62 * fade)).add(0, -.20, 0);
            quad(out, camera, start, end, inner2, inner1, color, fade * .65f);
            ribbon(out, camera, start, end, new Vec3(0, 1, 0), .025, 0xfff1ff, fade);
        }
    }

    private static Vec3 tip(WeaponChampionCue cue, Vec3 direction, double radius) {
        Vec3 at = cue.origin();
        Vec3 target = at.add(direction.scale(radius));
        double limit = WeaponChampionCue.FOOTPRINT_HALF;
        return new Vec3(Math.max(cue.home().x - limit, Math.min(cue.home().x + limit, target.x)), at.y,
            Math.max(cue.home().z - limit, Math.min(cue.home().z + limit, target.z)));
    }

    private static void ribbon(VertexConsumer out, View camera, Vec3 a, Vec3 b, Vec3 axis,
                               double thickness, int color, float alpha) {
        Vec3 offset = axis.scale(thickness);
        quad(out, camera, a.add(offset), b.add(offset), b.subtract(offset), a.subtract(offset), color, alpha);
    }

    private static void quad(VertexConsumer out, View camera, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
        vertex(out, camera, a, color, alpha);
        vertex(out, camera, b, color, alpha);
        vertex(out, camera, c, color, alpha);
        vertex(out, camera, d, color, alpha);
    }

    private static void vertex(VertexConsumer out, View camera, Vec3 at, int color, float alpha) {
        out.vertex(camera.origin.x - camera.eye.x + (at.x - camera.origin.x) * camera.scale, at.y - camera.eye.y,
                camera.origin.z - camera.eye.z + (at.z - camera.origin.z) * camera.scale)
            .color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha).endVertex();
    }
}
