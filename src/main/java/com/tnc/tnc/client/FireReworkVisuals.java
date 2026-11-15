package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.FireReworkEffects;
import com.tnc.tnc.magic.fire.TNFireBoltEntity;
import com.tnc.tnc.magic.fire.FireSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** White-hot cores, broken amber edges and curved ion filaments; no retained particle entities. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class FireReworkVisuals {
    private record Visible(FireReworkEffects.Cue cue, long tick, long started) {}
    private static final List<Visible> VISIBLE = new ArrayList<>();
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/beam_white.png");
    private static final RenderType MATERIAL = RenderType.entityTranslucentEmissive(WHITE);
    // A private source avoids Oculus replacing the global source with a no-op wrapper.
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static final int MAX_CUES = 48, MAX_QUADS = 7200;
    private static final java.lang.invoke.MethodHandle SHADOW = shadowHook();
    private static ClientLevel world;
    private static int quads;
    private static final int WHITE_HOT = 0xffefc9, GOLD = 0xffb955, AMBER = 0xff6320, RED = 0xc3240d;

    private FireReworkVisuals() {}

    private static java.lang.invoke.MethodHandle shadowHook() {
        try {
            return java.lang.invoke.MethodHandles.publicLookup().findStatic(
                    Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState"),
                    "areShadowsCurrentlyBeingRendered", java.lang.invoke.MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
    }
    private static boolean shadowPass() {
        try { return SHADOW != null && (boolean) SHADOW.invokeExact(); }
        catch (Throwable unavailable) { return false; }
    }

    public static void accept(FireReworkEffects.Cue cue) {
        var level = Minecraft.getInstance().level;
        if (level != world) { VISIBLE.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(cue.dimension())) return;
        long now = level.getGameTime();
        long started = now;
        prune(now);
        if (cue.kind() == FireReworkEffects.Kind.SCORCHED) {
            for (var it = VISIBLE.iterator(); it.hasNext();) {
                Visible old = it.next();
                if (old.cue.kind() == cue.kind() && old.cue.targetId() == cue.targetId()) {
                    started = old.started;
                    it.remove();
                    break;
                }
            }
        } else if (cue.kind() == FireReworkEffects.Kind.FORM) {
            VISIBLE.removeIf(v -> v.cue.kind() == cue.kind() && java.util.Objects.equals(v.cue.caster(), cue.caster()));
        }
        if (VISIBLE.size() >= MAX_CUES) {
            int remove = 0;
            // Preserve delayed fire-seed / molten-core telegraphs before decorative sparks.
            for (int i = 1; i < VISIBLE.size(); i++)
                if (priority(VISIBLE.get(i).cue) < priority(VISIBLE.get(remove).cue)) remove = i;
            if (priority(cue) < priority(VISIBLE.get(remove).cue)) return;
            VISIBLE.remove(remove);
        }
        VISIBLE.add(new Visible(cue, now, started));
    }

    private static int priority(FireReworkEffects.Cue cue) {
        return switch (cue.kind()) { case SEED, MOLTEN, INFERNO, SKYFIRE, MARTYR_DRAW -> 3; case SPARK, SCAR, FORM, FOOTPRINT -> 0; case SCORCHED -> 2; default -> 1; };
    }
    private static void prune(long tick) { VISIBLE.removeIf(v -> tick - v.tick > v.cue.lifetime()); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { VISIBLE.clear(); world = null; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (level != world) { VISIBLE.clear(); world = level; }
        if (level != null) prune(level.getGameTime());
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || shadowPass()) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world) { VISIBLE.clear(); world = mc.level; }
        if (world == null || VISIBLE.isEmpty()) return;
        prune(world.getGameTime()); quads = 0;
        VertexConsumer out = BUFFER.getBuffer(MATERIAL);
        Vec3 camera = event.getCamera().getPosition();
        for (int i = VISIBLE.size() - 1; i >= 0 && quads < MAX_QUADS; i--) {
            Visible visible = VISIBLE.get(i); var cue = visible.cue;
            Vec3 origin = cue.at();
            float bodyHeight = 1.8F;
            if (cue.kind() == FireReworkEffects.Kind.FORM && cue.caster() != null) {
                var wearer = world.getPlayerByUUID(cue.caster());
                if (wearer == null || !wearer.isAlive()) continue;
                origin = wearer.getPosition(event.getPartialTick());
            } else if (cue.kind() == FireReworkEffects.Kind.SCORCHED) {
                if (!(world.getEntity(cue.targetId()) instanceof LivingEntity target)
                        || !target.isAlive() || target.isInWaterOrBubble()) continue;
                origin = target.getPosition(event.getPartialTick());
                bodyHeight = target.getBbHeight();
            }
            if (camera.distanceToSqr(origin) > Math.pow(100 + cue.radius(), 2)) continue;
            double age = world.getGameTime() - visible.tick + event.getPartialTick();
            if (age < 0 || age >= cue.lifetime()) continue;
            float fade = cue.kind() == FireReworkEffects.Kind.SCORCHED || cue.kind() == FireReworkEffects.Kind.FORM
                    ? (float) Math.min(1, (cue.lifetime() - age) / 5) : envelope(age, cue.lifetime());
            Matrix4f pose = new Matrix4f().translation((float) (origin.x - camera.x),
                    (float) (origin.y - camera.y), (float) (origin.z - camera.z));
            var palette = FireFlamePalette.forEffect(cue.kind().name(), cue.intensity());
            Draw d = new Draw(out, pose, cue, camera.subtract(origin), age, fade, origin, palette, true);
            double clock = world.getGameTime() + event.getPartialTick();
            draw(d, bodyHeight, clock, clock - visible.started);
            if (hot(cue.kind())) FireHeatHaze.offer(origin, Math.min(10, cue.radius() * 1.2), cue.intensity() * fade * .75F);
        }
        BUFFER.endBatch(MATERIAL);
    }

    private static boolean hot(FireReworkEffects.Kind kind) {
        return switch (kind) { case WALL, DETONATE, HEAT_BURST, SEED, SKY_IMPACT, MOLTEN_IMPACT, MARTYR -> true; default -> false; };
    }
    private static float envelope(double age, int life) {
        return (float) Math.max(0, Math.min(1, Math.min((age + .5) / 2, (life - age) / Math.min(12D, life * .4))));
    }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static Vec3 radial(double angle, double radius, double y) { return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius); }

    private static void draw(Draw d, double bodyHeight, double clock, double sinceImpact) {
        double r = d.cue.radius(), age = d.age, intensity = d.cue.intensity();
        Vec3 axis = d.cue.direction().lengthSqr() < .001 ? new Vec3(0, 0, 1) : d.cue.direction().normalize();
        Vec3 right = axis.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < .001) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(axis).normalize();
        switch (d.cue.kind()) {
            case SPARK -> {
                double length = d.cue.direction().length();
                double travel = smooth(age / 7) * length;
                for (int i = 0; i < 9; i++) {
                    double angle = i * 2.399963;
                    Vec3 end = axis.scale(travel).add(right.scale(Math.cos(angle) * .65)).add(up.scale(Math.sin(angle) * .65));
                    d.filament(axis.scale(Math.max(.1, travel - 2)), end, .055 + i % 3 * .012, .95F);
                }
                d.arc(Vec3.ZERO, right, up, .55 + smooth(age / 8) * .65, age * .04, Math.PI * 1.4, .028, .5F);
            }
            case SCAR, FOOTPRINT -> {
                boolean footprint = d.cue.kind() == FireReworkEffects.Kind.FOOTPRINT;
                for (int i = 0; i < (footprint ? 4 : 8); i++) {
                    double t = i / (footprint ? 3D : 7D);
                    double drift = Math.sin(i * 2.2) * (footprint ? .28 : .5);
                    Vec3 root = axis.scale((t - .5) * r * 2).add(right.scale(drift)).add(0, .045, 0);
                    d.plume(root, (.48 + Math.sin(i * 1.7 + age * .18) * .19) * (1 - age / d.cue.lifetime()),
                            .17, i * .8, .83F);
                    d.curve(root, root.add(axis.scale(.45)).add(right.scale(drift * .45)), .045, i, .8F);
                }
            }
            case WALL -> {
                double growth = smooth(age / 6), drift = Math.max(0, age - 6) * .075;
                for (int i = 0; i < 14; i++) {
                    double a = (i - 7) * r / 7, b = a + r / 7;
                    Vec3 root = right.scale(a).add(axis.scale(.065 * a * a + drift)).add(0, .08, 0);
                    Vec3 next = right.scale(b).add(axis.scale(.065 * b * b + drift)).add(0, .08, 0);
                    double height = growth * (2.15 + .75 * Math.sin(i * .77 - age * .22));
                    Vec3 crest = root.add(axis.scale(.66 + .38 * Math.sin(age * .23 + i))).add(0, height, 0);
                    Vec3 crestNext = next.add(axis.scale(.9)).add(0, height * (.72 + .24 * Math.cos(i)), 0);
                    if (i % 4 != 1) d.patch(root, next, crestNext, crest, AMBER, .2F);
                    d.curve(root, crest, .18, i * .7, .84F);
                    d.curve(next, crestNext.add(axis.scale(.42)).add(0, .26, 0), .077, i + 2, .6F);
                }
            }
            case INFERNO -> {
                for (int i = 0; i < 4; i++) {
                    double size = 3.4 + i * 1.6, lead = 8 + i * 11 - age;
                    d.ring(size, .05 + i * .012, .028, .27F);
                    if (lead > 0 && lead < 7) {
                        d.ring(size * (.22 + .78 * smooth(1 - lead / 7)), .13, .069, .78F);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Vec3 p = radial(a, r, .10);
                    d.curve(p, p.add(radial(a + 2.6, .9, .55)), .044, i, .55F);
                }
            }
            case INFERNO_PULSE -> {
                double expansion = .96 + .12 * smooth(age / 12);
                double front = r * expansion;
                d.ring(front, .12, .095, .94F);
                for (int i = 0; i < 14; i++) {
                    double a = i * 2.399963;
                    Vec3 foot = radial(a, front * (.88 + .09 * Math.sin(i)), .07);
                    Vec3 top = radial(a + .12, front + .38, .7 + .36 * Math.sin(i + age * .3));
                    d.curve(foot, top, .16, i, .78F);
                }
            }
            case SKYFIRE -> {
                double descent = smooth((age - 5) / Math.max(1, d.cue.lifetime() - 5));
                double height = d.cue.direction().y;
                Vec3 head = new Vec3(0, Math.max(.13, height * (1 - descent)), 0);
                Vec3 sky = new Vec3(0, height, 0);
                d.ring(r, .075, .07, .63F);
                d.arc(sky, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        .85 + (1 - descent) * .65, age * .14, Math.PI * 1.65, .12, .86F);
                for (int i = 0; i < 7; i++) {
                    double a = i * 2.399963 + age * .14;
                    Vec3 root = head.add(radial(a, .22, 0));
                    Vec3 tail = root.add(radial(a + .65, .48 + descent * .48, Math.min(height * descent, 4.5)));
                    d.curve(root, tail, .12 + descent * .05, i, .86F);
                }
                d.core(head, .32 + .3 * descent, .8F);
            }
            case DETONATE, HEAT_BURST, HAND_CRUSH -> burst(d, r, intensity);
            case GHOST_BURST, FIRE_WAVE -> fireWave(d, r, d.cue.kind() == FireReworkEffects.Kind.GHOST_BURST);
            case SKY_IMPACT, MOLTEN_IMPACT -> {
                burst(d, r, intensity);
                double height = (1 - smooth(age / 23)) * (d.cue.kind() == FireReworkEffects.Kind.SKY_IMPACT ? 4.5 : 2.8);
                for (int i = 0; i < 8; i++) {
                    double a = i * 2.399963;
                    d.plume(radial(a, r * (.18 + i * .055), .12), height * (.58 + .4 * Math.sin(i + 1)),
                            .24, a, .83F);
                }
            }
            case CHAIN -> {
                Vec3 end = d.cue.direction();
                d.curve(Vec3.ZERO, end, .09 + intensity * .04, 3.5, .85F);
                for (int i = 0; i < 3; i++) {
                    double t = (age * .13 + i / 3D) % 1;
                    Vec3 center = end.scale(t);
                    d.arc(center, right, up, .18 + .12 * intensity, age * .15, Math.PI * 1.7, .035, .9F);
                }
            }
            case SEED -> {
                double pulse = .85 + .15 * Math.sin(age * .34), urgency = smooth(age / d.cue.lifetime());
                Vec3 core = new Vec3(0, .9, 0);
                d.core(core, (.3 + .12 * urgency) * pulse, .85F);
                for (int i = 0; i < 3; i++) {
                    double a = age * (.04 + urgency * .025) * (i % 2 == 0 ? 1 : -1) + i * 2;
                    Vec3 normal = new Vec3(Math.cos(a), .35 + i * .25, Math.sin(a)).normalize();
                    Vec3 tangent = normal.cross(new Vec3(0, 1, 0)).normalize();
                    d.arc(core, tangent, tangent.cross(normal).normalize(), .7 + i * .17, a, Math.PI * 1.5, .035, .75F);
                }
                d.ring(r, .065, .045 + urgency * .025, .65F);
                for (int i = 0; i < 6; i++) {
                    double phase = (age * .045 + i / 6D) % 1;
                    d.curve(radial(i * 2.399963 + phase * .4, r * (1 - phase), .1 + phase * .6), core,
                            .023 + urgency * .018, i, .4F);
                }
            }
            case MOLTEN -> {
                double t = Math.pow(age / d.cue.lifetime(), 1.45);
                Vec3 core = new Vec3(0, d.cue.direction().y * (1 - t) + .28, 0);
                d.core(core, .68, .96F);
                d.core(core, .38, .72F);
                for (int i = 0; i < 8; i++) {
                    double a = i * 2.399963 + age * .12;
                    Vec3 edge = core.add(radial(a, .5, 0));
                    Vec3 tail = edge.add(radial(a + .45, .57, 2.4 + t * 1.45));
                    d.curve(edge, tail, .12 + .035 * Math.sin(i), i, .85F);
                }
                for (int i = 0; i < 3; i++) {
                    d.arc(new Vec3(0, .07, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                            r * (1 - t * .56), i * 2.094 - age * .08, 1.55, .065, .86F);
                }
            }
            case MARTYR_DRAW -> {
                for (int i = 0; i < 10; i++) {
                    double a = i * 2.399963 + age * .09;
                    Vec3 outer = radial(a, r * (1 - smooth(age / 12) * .74), .25 + i * .16);
                    Vec3 inward = radial(a + .7, outer.horizontalDistance() * .27, .18);
                    d.curve(outer, inward, .093, i, .87F);
                }
                d.core(Vec3.ZERO, .18 + smooth(age / 12) * .54, .96F);
            }
            case MARTYR -> {
                burst(d, r, intensity);
                for (int i = 0; i < 5; i++) d.arc(new Vec3(0, r * .35, 0), right, up,
                        r * (.3 + i * .12), i * 1.1 + age * .02, Math.PI * 1.25, .035, .45F);
            }
            case HEAT_IGNITE -> {
                for (int i = 0; i < 5; i++) d.plume(radial(i * 2.399963, r * .5, -.4), r * 1.6, .16, i, .7F);
                d.ring(r, -.3, .035, .5F);
            }
            case FORM -> form(d, r, FireFlamePalette.formTier((float) intensity), clock);
            case FORM_STRIKE -> {
                for (int i = 0; i < 3; i++) {
                    Vec3 root = right.scale((i - 1) * .32).add(0, -.4, 0).add(axis.scale(age * .06));
                    d.flame(root, new Vec3(axis.x * .64, .7 + i * .16, axis.z * .64),
                            .23, age, i * 1.9, .8F);
                }
            }
            case SCORCHED -> scorch(d, r, bodyHeight, clock, sinceImpact);
        }
    }

    private static void scorch(Draw d, double radius, double bodyHeight, double clock, double sinceImpact) {
        int tier = FireFlamePalette.formTier(d.cue.intensity());
        int count = tier >= 4 ? 5 : 3;
        double split = smooth(sinceImpact / 9);
        for (int i = 0; i < count; i++) {
            double phase = i * 2.399963 + d.cue.targetId();
            double a = clock * .072 + phase;
            double bob = .28 + .18 * Math.sin(clock * .118 + phase);
            Vec3 root = radial(a, radius * (.35 + .69 * split + .14 * Math.sin(clock * .21 + phase)),
                    bodyHeight * (.48 * (1 - split) + bob * split) + .08);
            Vec3 rise = radial(a + 1.15, radius * (.66 - .24 * split), .46 + tier * .065);
            d.flame(root, rise, Math.min(.47, radius * .36 + .12), clock, phase, .79F);
        }
    }

    private static void fireWave(Draw d, double r, boolean ghost) {
        double reach = .25 + .75 * smooth(d.age / (ghost ? 19 : 12));
        double waveRadius = r * reach;
        for (int i = 0; i < 13; i++) {
            double a = i * Math.PI * 2 / 13;
            double ragged = .88 + .13 * Math.sin(i * 2.33);
            Vec3 root = radial(a, waveRadius * ragged, -.06);
            Vec3 crest = radial(a + .25, r * (.11 + .07 * Math.sin(i)),
                    (ghost ? 1.28 : .68) * ragged * (1 - .65 * reach));
            if (i % 4 != 2) {
                double b = a + Math.PI * 2 / 13;
                Vec3 edge = radial(b, waveRadius * (.88 + .13 * Math.sin((i + 1) % 13 * 2.33)), -.06);
                Vec3 crestEdge = edge.add(radial(b + .18, r * .10, crest.y * .78));
                d.gradient(root, edge, crestEdge, root.add(crest), AMBER, .30F, .26F, 0, 0);
            }
            d.flame(root, crest, r * .145, d.age, i * 1.57, .83F);
        }
    }

    private static void form(Draw d, double r, int tier, double clock) {
        var wearer = d.cue.caster() == null ? null : world.getPlayerByUUID(d.cue.caster());
        double heading = wearer == null ? 0 : Math.toRadians(-wearer.getYRot());
        double fuel = wearer == null ? .35 : Math.max(0, 1 - wearer.getHealth() / wearer.getMaxHealth());
        for (int i = 0; i < (tier == 2 ? 4 : tier == 5 ? 10 : 6); i++) {
            double phase = i * 2.399963;
            double flutter = Math.sin(clock * .17 + phase);
            switch (tier) {
                case 1 -> {
                    double a = phase + clock * .037;
                    d.flame(radial(a, .53, .05), radial(a + 1.5, .14, .31 + flutter * .12),
                            .19, clock, phase, .77F);
                }
                case 2 -> {
                    double a = heading + (i < 2 ? 0 : Math.PI);
                    Vec3 wrist = radial(a, .58 + .04 * (i % 2), .79 + .15 * (i % 2));
                    d.flame(wrist, radial(a + .4, .19, .51 + .14 * flutter), .17,
                            clock, phase, .82F);
                }
                case 3 -> {
                    double a = phase + clock * .039;
                    d.flame(radial(a, .84 + .1 * flutter, .12),
                            radial(a + 2.15, .43, .98 + flutter * .23), .24, clock, phase, .78F);
                }
                case 4 -> {
                    double a = phase - clock * .035;
                    double y = .48 + .51 * (.5 + .5 * Math.sin(clock * .079 + phase));
                    d.flame(radial(a, r * .63, y), radial(a + 1.2, .21, .43 + flutter * .11),
                            .25, clock, phase, .8F);
                }
                case 5 -> {
                    double a = phase + clock * .048;
                    double rootRadius = .64 + .13 * flutter;
                    d.flame(radial(a, rootRadius, .16 + .11 * (i % 3)),
                            radial(a + .66, .22 + fuel * .39, .87 + fuel * .57 + flutter * .21),
                            .22 + fuel * .08, clock, phase, .85F);
                }
                default -> {}
            }
        }
    }

    static void renderIgnition(TNFireBoltEntity bolt, int tier, float partial, Matrix4f pose, MultiBufferSource buffers) {
        if (shadowPass()) return;
        Vec3 origin = bolt.getPosition(partial);
        var mc = Minecraft.getInstance();
        var owner = bolt.getOwner();
        var cue = new FireReworkEffects.Cue(bolt.level().dimension().location(), FireReworkEffects.Kind.SPARK,
                origin, Vec3.ZERO, bolt.radius(), tier / 5F, 40, owner == null ? null : owner.getUUID());
        double age = bolt.tickCount + partial;
        double radius = bolt.radius() * (.8 + .2 * smooth(age / 3));
        var palette = FireFlamePalette.forIgnition(tier);
        var draw = new Draw(buffers.getBuffer(MATERIAL), pose, cue,
                mc.gameRenderer.getMainCamera().getPosition().subtract(origin), age, 1, origin, palette, false);
        Vec3 axis = bolt.safeDirection(), side = FireSpellRules.right(axis), up = side.cross(axis).normalize();
        draw.orb(axis, side, up, radius);
        for (int i = 0; i < 7; i++) {
            double a = i * 2.399963 + age * .17;
            Vec3 rim = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 root = axis.scale(.48 * radius).add(rim.scale(radius * (.47 + .12 * Math.sin(i))));
            Vec3 tail = axis.scale(-radius * (2.7 + .45 * (i % 3)))
                    .add(rim.scale(.38 * radius)).add(0, radius * .4, 0);
            draw.flame(root, tail, radius * .66, age, i * 1.27, .9F);
        }
    }

    private static void burst(Draw d, double radius, double intensity) {
        double expand = 1 - Math.pow(1 - smooth(d.age / 13), 3);
        double r = Math.max(.1, radius * expand);
        // A broken pressure skin occupies actual 3D space. It is deliberately amber,
        // thin and full of gaps: the white core never becomes a screen-sized solid ball.
        if (d.age < 16) {
            float membrane = (float) (.2 * (1 - smooth(d.age / 16)));
            for (int row = 0; row < 3; row++) for (int i = 0; i < 14; i++) {
                if ((i + row) % 4 == 0) continue;
                double a = i * Math.PI * 2 / 14, b = (i + .82) * Math.PI * 2 / 14;
                double lower = row * Math.PI / 7, upper = (row + .8) * Math.PI / 7;
                double ripple = 1 + .07 * Math.sin(i * 2.1 + row + d.age * .31);
                Vec3 p = radial(a, r * Math.cos(lower) * ripple, .1 + r * Math.sin(lower) * .8);
                Vec3 q = radial(b, r * Math.cos(lower), .1 + r * Math.sin(lower) * .8);
                Vec3 u = radial(b, r * Math.cos(upper), .1 + r * Math.sin(upper) * .8);
                Vec3 v = radial(a, r * Math.cos(upper) * ripple, .1 + r * Math.sin(upper) * .8);
                d.patch(p, q, u, v, AMBER, membrane);
            }
        }
        d.ring(r, .07, .1 + (1 - expand) * .18, .9F);
        d.ring(r * .86, .12, .045, .55F);
        if (d.age < 7) d.core(new Vec3(0, .6, 0), .25 + (1 - d.age / 7) * radius * .2, .85F);
        for (int i = 0; i < 12; i++) {
            double a = i * 2.399963;
            Vec3 base = radial(a, r * (.45 + .4 * Math.sin(i * 3.1) * Math.sin(i * 3.1)), .08);
            d.plume(base, (.6 + intensity * 1.6) * (1 - expand * .45), .18, a, .72F);
            if (i % 2 == 0) d.filament(radial(a, r * .18, .2), base.add(0, .4, 0), .035, .8F);
            if (d.age > 13) {
                double recall = smooth((d.age - 13) / 16);
                Vec3 ember = radial(a + recall * .9, r * (1 - recall * .72), .4 + recall * .9);
                d.curve(ember, ember.add(radial(a + 1.2, -.45, .65)), .018, i, .55F);
            }
        }
    }

    private static final class Draw {
        final VertexConsumer out; final Matrix4f pose; final FireReworkEffects.Cue cue;
        final Vec3 camera; final Vec3 origin; final double age; final float fade;
        final FireFlamePalette.Palette palette;
        final boolean budgeted;
        Draw(VertexConsumer out, Matrix4f pose, FireReworkEffects.Cue cue, Vec3 camera, double age, float fade,
             Vec3 origin, FireFlamePalette.Palette palette, boolean budgeted) {
            this.out = out; this.pose = pose; this.cue = cue; this.camera = camera; this.origin = origin;
            this.age = age; this.fade = fade; this.palette = palette; this.budgeted = budgeted;
        }

        void orb(Vec3 axis, Vec3 side, Vec3 up, double radius) {
            for (int row = 0; row < 4; row++) {
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * .2, b = (i + 1) * Math.PI * .2;
                    double low = Math.PI * row / 4, high = Math.PI * (row + 1) / 4;
                    int rgb = row <= 1 ? GOLD : AMBER;
                    patch(orbPoint(axis, side, up, low, a, radius), orbPoint(axis, side, up, low, b, radius),
                            orbPoint(axis, side, up, high, b, radius), orbPoint(axis, side, up, high, a, radius), rgb, .68F);
                }
            }
            Vec3 face = camera.normalize();
            Vec3 across = FireSpellRules.right(face);
            Vec3 vertical = across.cross(face).normalize();
            Vec3 front = face.scale(radius * 1.01);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6, b = (i + 1) * Math.PI / 6;
                Vec3 edgeA = front.add(across.scale(Math.cos(a) * radius * .58)).add(vertical.scale(Math.sin(a) * radius * .58));
                Vec3 edgeB = front.add(across.scale(Math.cos(b) * radius * .58)).add(vertical.scale(Math.sin(b) * radius * .58));
                gradient(edgeA, edgeB, front, front, WHITE_HOT, 0, 0, .93F, .93F);
            }
        }

        Vec3 orbPoint(Vec3 axis, Vec3 side, Vec3 up, double theta, double angle, double radius) {
            double swirl = 1 + .10 * Math.sin(angle * 3 + theta * 2 - age * .29);
            return axis.scale(radius * Math.cos(theta)).add(
                    side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(radius * Math.sin(theta) * swirl));
        }

        /** Each tongue is a single folded ribbon with smoothly fading edges. */
        void flame(Vec3 root, Vec3 rise, double width, double clock, double phase, float alpha) {
            Vec3 view = camera.subtract(root);
            Vec3 side = rise.cross(view).normalize();
            if (side.lengthSqr() < .001) side = FireSpellRules.right(rise.normalize());
            Vec3 bend = rise.cross(side).normalize();
            Vec3 before = root, beforeEdge = side.scale(width * .54);
            float previous = alpha * .82F;
            for (int i = 1; i <= 7; i++) {
                double t = i / 7D;
                double flicker = Math.sin(clock * .18 * palette.motion() + phase - t * 3.5);
                Vec3 point = root.add(rise.scale(t)).add(side.scale(width * flicker * t))
                        .add(bend.scale(width * .44 * Math.sin(t * Math.PI)));
                double span = width * (1 - t) * (.54 + .9 * Math.sin(Math.PI * t));
                Vec3 edge = side.scale(span);
                float strength = alpha * (float) Math.pow(1 - t, .6);
                gradient(before.subtract(beforeEdge), point.subtract(edge), point, before,
                        AMBER, 0, 0, strength, previous);
                gradient(before, point, point.add(edge), before.add(beforeEdge),
                        AMBER, previous, strength, 0, 0);
                if (i <= 4) gradient(before.subtract(beforeEdge.scale(.28)), point.subtract(edge.scale(.28)),
                        point.add(edge.scale(.28)), before.add(beforeEdge.scale(.28)),
                        i == 1 ? WHITE_HOT : GOLD, previous * .54F, strength * .46F, strength * .46F, previous * .54F);
                before = point;
                beforeEdge = edge;
                previous = strength;
            }
        }
        void ring(double radius, double y, double width, float alpha) {
            arc(new Vec3(0, y, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, 0, Math.PI * 2, width, alpha);
        }
        void arc(Vec3 center, Vec3 right, Vec3 up, double radius, double start, double sweep, double width, float alpha) {
            int segments = 28;
            for (int i = 0; i < segments; i++) {
                double a = start + sweep * i / segments, b = start + sweep * (i + 1) / segments;
                // Broken orange edges leave small gaps instead of opaque neon hoops.
                double edge = 1 + .025 * Math.sin(i * 1.7 + age * .14);
                filament(center.add(right.scale(Math.cos(a) * radius * edge)).add(up.scale(Math.sin(a) * radius * edge)),
                        center.add(right.scale(Math.cos(b) * radius)).add(up.scale(Math.sin(b) * radius)), width, alpha);
            }
        }
        void plume(Vec3 root, double height, double width, double phase, float alpha) {
            Vec3 previous = root;
            for (int i = 1; i <= 6; i++) {
                double t = i / 6D;
                double curl = Math.sin(t * 5.4 - age * .13 * palette.motion() + phase) * height * .16 * t;
                Vec3 next = root.add(Math.cos(phase) * curl, height * t, Math.sin(phase) * curl);
                filament(previous, next, width * Math.pow(1 - t * .88, .65), alpha * (float) (1 - t * .45));
                if (i == 3) filament(next, next.add(Math.cos(phase + 1) * height * .22, height * .23,
                        Math.sin(phase + 1) * height * .22), width * .28, alpha * .55F);
                previous = next;
            }
        }
        void curve(Vec3 from, Vec3 to, double width, double phase, float alpha) {
            Vec3 direction = to.subtract(from), side = direction.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < .01) side = new Vec3(Math.cos(phase), 0, Math.sin(phase));
            Vec3 previous = from;
            for (int i = 1; i <= 6; i++) {
                double t = i / 6D, bend = Math.sin(Math.PI * t) * Math.sin(t * 8 - age * .12 * palette.motion() + phase) * .38;
                Vec3 next = from.lerp(to, t).add(side.scale(bend));
                filament(previous, next, width * (.55 + .45 * Math.sin(Math.PI * t)), alpha);
                previous = next;
            }
        }
        void core(Vec3 center, double r, float alpha) {
            // Faceted hollow lattice: the background stays visible between glowing hot seams.
            Vec3 top = center.add(0, r, 0), bottom = center.add(0, -r, 0);
            for (int i = 0; i < 6; i++) {
                Vec3 a = center.add(radial(i * Math.PI / 3 + age * .025, r, 0));
                Vec3 b = center.add(radial((i + 1) * Math.PI / 3 + age * .025, r, 0));
                filament(a, b, r * .07, alpha); filament(a, top, r * .06, alpha); filament(a, bottom, r * .06, alpha);
            }
        }
        void filament(Vec3 a, Vec3 b, double width, float alpha) {
            if (width <= .0001 || alpha <= .002 || quads >= MAX_QUADS) return;
            Vec3 midpoint = a.add(b).scale(.5), tangent = b.subtract(a);
            Vec3 side = tangent.cross(camera.subtract(midpoint)).normalize();
            if (side.lengthSqr() < .001) side = tangent.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < .001) side = new Vec3(1, 0, 0);
            // Broad dim fringe + narrow amber body + a small incandescent core.
            strip(a, b, side.scale(width * 1.8), RED, alpha * .18F);
            strip(a, b, side.scale(width), AMBER, alpha * .6F);
            strip(a, b, side.scale(width * .48), GOLD, alpha * .8F);
            strip(a, b, side.scale(width * .16), WHITE_HOT, alpha);
        }
        void strip(Vec3 a, Vec3 b, Vec3 side, int rgb, float alpha) {
            patch(a.subtract(side), b.subtract(side), b.add(side), a.add(side), rgb, alpha);
        }
        void patch(Vec3 p, Vec3 q, Vec3 r, Vec3 s, int rgb, float alpha) {
            gradient(p, q, r, s, rgb, alpha, alpha, alpha, alpha);
        }

        void gradient(Vec3 p, Vec3 q, Vec3 r, Vec3 s, int rgb, float ap, float aq, float ar, float as) {
            if (budgeted && quads >= MAX_QUADS) return;
            Vec3 mid = p.add(q).add(r).add(s).scale(.25).add(origin);
            float opacity = fade * FirstPersonSpellView.opacity(cue.caster(), mid, .65);
            if (opacity * Math.max(Math.max(ap, aq), Math.max(ar, as)) <= .003F) return;
            rgb = rgb == RED ? palette.fringe() : rgb == AMBER ? palette.body()
                    : rgb == GOLD ? palette.glow() : rgb == WHITE_HOT ? palette.core() : rgb;
            vertex(p, 0, 0, rgb, ap * opacity); vertex(q, 0, 1, rgb, aq * opacity);
            vertex(r, 1, 1, rgb, ar * opacity); vertex(s, 1, 0, rgb, as * opacity);
            vertex(s, 1, 0, rgb, as * opacity); vertex(r, 1, 1, rgb, ar * opacity);
            vertex(q, 0, 1, rgb, aq * opacity); vertex(p, 0, 0, rgb, ap * opacity);
            if (budgeted) quads++;
        }
        void vertex(Vec3 p, float u, float v, int rgb, float alpha) {
            out.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                    .color((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha)
                    .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
        }
    }
}
