package com.tnc.tnc.magic.free.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Authored projectile surfaces in local +Z space, shared by flight and chant previews. */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaterShapeVisuals {
    private static final Profile[] PROFILES = {
            new Profile(1.05f, .015f, .84f, false, false),
            new Profile(0, .023f, .96f, false, false),
            new Profile(1.8f, .034f, .91f, false, false),
            new Profile(2.25f, .025f, .94f, false, false),
            new Profile(0, .022f, .94f, false, false),    // Sword
            new Profile(1.2f, .032f, .94f, false, false), // Arrow
            new Profile(7f, .027f, .93f, true, false),   // Disc
            new Profile(8.3f, .031f, .94f, true, false), // Shuriken
            new Profile(0, .024f, .96f, false, false),   // Axe
            new Profile(0, .019f, .96f, false, false),   // Hammer
            new Profile(0, .032f, .96f, false, true),    // Whip
            new Profile(0, .024f, .94f, false, true),    // Tentacle
            new Profile(8.1f, .032f, .95f, false, false),// Drill
            new Profile(0, .021f, .91f, false, false),   // Wave
            new Profile(0, .017f, .87f, false, false),   // Tsunami
            new Profile(1.4f, -.028f, .90f, true, false),// Geyser
            new Profile(4.3f, -.032f, .89f, true, false),// Vortex
            new Profile(0, .022f, .94f, false, true),    // Dragon
            new Profile(.55f, .015f, .56f, true, false), // Bubble
            new Profile(0, .026f, .94f, false, false)    // Net
    };
    private static final Form[] FORMS = new Form[PROFILES.length];
    private static final RenderType CORE = RenderType.entityCutoutNoCull(texture("core"));
    private static final RenderType SHELL = ShellPass.createPass("shell");
    private static final RenderType FORMING_CORE = ShellPass.createPass("core");

    private WaterShapeVisuals() {}

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources ->
                Minecraft.getInstance().execute(() -> java.util.Arrays.fill(FORMS, null)));
    }

    /** Draws into the caller's buffer; the caller owns its usual endBatch. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int shape, double growth, double timeTicks) {
        if (shape < 0 || shape >= PROFILES.length || !(growth > 0) || !Double.isFinite(timeTicks)) return;
        var form = form(shape);
        var profile = PROFILES[shape];
        float amount = (float) Math.min(1, growth);
        float scale = .68f + .32f * ease(amount, 0, 1);
        if (shape < 4) scale *= .98f + .02f * (float) Math.cos(timeTicks * .30);
        float roll = profile.roll == 0 ? 3 * (float) Math.sin(timeTicks * .16)
                : (float) ((timeTicks * profile.roll) % 360);
        float flow = phase(timeTicks * profile.flow);
        float spiral = phase(timeTicks * profile.flow * .36);
        float flex = profile.flex ? phase(timeTicks * .027) * (float) (2 * Math.PI) : -1;
        boolean mature = amount >= .999f;
        pose.pushPose();
        try {
            pose.scale(scale, scale, scale);
            // Rising forms stay vertical and wave walls stay level.
            if (profile.spinY) pose.mulPose(Axis.YP.rotationDegrees(roll));
            else if (shape < 13 || shape == 17) pose.mulPose(Axis.ZP.rotationDegrees(roll));
            var frame = pose.last();
            float pulse = .87f + .13f * (float) Math.sin(timeTicks * .31);
            if (amount > .19f && !form.core.isEmpty()) {
                var core = buffers.getBuffer(mature ? CORE : FORMING_CORE);
                for (var quad : form.core) {
                    float density = mature ? 1 : ease(amount, .19f + .31f * quad.position, .94f);
                    if (density > .08f) quad.draw(frame, core, spiral, flow, density, pulse, 0, flex);
                }
            }
            var shell = buffers.getBuffer(SHELL);
            for (var quad : form.shell) {
                float reveal = ease(amount, quad.position * .17f, .47f + quad.position * .19f);
                if (reveal > .08f) quad.draw(frame, shell, spiral, flow, profile.opacity * reveal, 1, 0, flex);
            }
            for (int i = 0; i < form.wakes.size(); i++) {
                float oscillation = (float) Math.sin(timeTicks * .28 + i * 1.9);
                float shift = oscillation * .022f;
                float opacity = (.65f + .19f * oscillation) * ease(amount, 0, .48f);
                for (var quad : form.wakes.get(i)) {
                    if (opacity > .08f) quad.draw(frame, shell, spiral, flow, opacity, .94f, shift, flex);
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static float phase(double value) {
        return (float) (value - Math.floor(value));
    }

    private static float ease(float amount, float start, float end) {
        float t = Math.max(0, Math.min(1, (amount - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/effects/free_water/" + name + ".png");
    }

    private static Form form(int index) {
        if (FORMS[index] != null) return FORMS[index];
        var id = ResourceLocation.fromNamespaceAndPath("tnc", "models/effects/free_water/" + index + ".json");
        var resources = Minecraft.getInstance().getResourceManager();
        try (var reader = resources.getResourceOrThrow(id).openAsReader()) {
            var body = new ArrayList<Quad>();
            var skin = new ArrayList<Quad>();
            var wakes = new ArrayList<List<Quad>>();
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            int axis = index >= 13 && index <= 16 ? 1 : 2;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (var entry : json.getAsJsonArray("meshes")) {
                for (var point : entry.getAsJsonObject().getAsJsonArray("points")) {
                    float value = point.getAsJsonArray().get(axis).getAsFloat();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
            float span = Math.max(.001f, max - min);
            for (var entry : json.getAsJsonArray("meshes")) {
                var mesh = entry.getAsJsonObject();
                var points = mesh.getAsJsonArray("points");
                var quads = new ArrayList<Quad>();
                for (var item : mesh.getAsJsonArray("faces")) {
                    var face = item.getAsJsonObject();
                    quads.add(Quad.decode(points, face.getAsJsonArray("v"), face.getAsJsonArray("uv"),
                            axis, min, span));
                }
                switch (mesh.get("material").getAsString()) {
                    case "core" -> body.addAll(quads);
                    case "shell" -> skin.addAll(quads);
                    case "wake" -> wakes.add(List.copyOf(quads));
                    default -> throw new IllegalArgumentException("Unknown water mesh material in " + id);
                }
            }
            return FORMS[index] = new Form(List.copyOf(body), List.copyOf(skin), List.copyOf(wakes));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load water shape " + id, e);
        }
    }

    private record Form(List<Quad> core, List<Quad> shell, List<List<Quad>> wakes) {}
    private record Profile(float roll, float flow, float opacity, boolean spinY, boolean flex) {}

    private record Quad(float[] data, Vector3f normal, float shading, float position) {
        static Quad decode(JsonArray points, JsonArray indices, JsonArray uv, int axis, float min, float span) {
            float[] data = new float[20];
            for (int i = 0; i < 4; i++) {
                int k = Math.min(i, indices.size() - 1);
                var p = points.get(indices.get(k).getAsInt()).getAsJsonArray();
                var t = uv.get(k).getAsJsonArray();
                for (int coordinate = 0; coordinate < 3; coordinate++) {
                    data[i * 5 + coordinate] = p.get(coordinate).getAsFloat();
                }
                data[i * 5 + 3] = t.get(0).getAsFloat();
                data[i * 5 + 4] = t.get(1).getAsFloat();
            }
            var normal = new Vector3f(data[5] - data[0], data[6] - data[1], data[7] - data[2])
                    .cross(data[10] - data[0], data[11] - data[1], data[12] - data[2]).normalize();
            float shade = .74f + .26f * Math.max(0, normal.y * .85f + normal.z * .35f);
            float center = (data[axis] + data[axis + 5] + data[axis + 10] + data[axis + 15]) * .25f;
            return new Quad(data, normal, shade, (center - min) / span);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, float spiral, float flow, float alpha,
                  float intensity, float wakeShift, float flex) {
            float shade = shading * intensity;
            for (int i = 0; i < data.length; i += 5) {
                float sway = flex < 0 ? 0 : .09f * (float) Math.sin(data[i + 2] * 3.6f - flex);
                out.vertex(pose.pose(), data[i] + sway, data[i + 1] + wakeShift * .45f, data[i + 2] + wakeShift)
                        .color(shade, shade, intensity, alpha).uv(data[i + 3] + spiral, data[i + 4] + flow)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
            }
        }
    }

    /** One front-facing transparent surface, composited over the bright core and the scene. */
    private static final class ShellPass extends RenderType {
        private ShellPass() {
            super("tnc_free_water_shell", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    1024, false, true, () -> {}, () -> {});
        }

        static RenderType createPass(String material) {
            return create("tnc_free_water_" + material, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    8192, false, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture(material), true, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                            .setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
        }
    }
}
