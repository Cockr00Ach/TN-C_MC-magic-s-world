package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorWavePacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Solid, tapered crescent ribbons: no dust, smoke, sprites or particle settings dependency. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorWaveRenderer {
    private static final List<WarriorWavePacket> WAVES = new ArrayList<>();
    private static ClientLevel world;
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource BUFFERS =
            net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(65536));
    private WarriorWaveRenderer() {}
    public static void accept(WarriorWavePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (world != level) { WAVES.clear(); world = level; }
        if (WAVES.size() >= 128) WAVES.remove(0);
        WAVES.add(packet);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (world != level) { WAVES.clear(); world = level; }
        if (level != null) WAVES.removeIf(w -> level.getGameTime() - w.startedAt() > w.range() / w.speed() + 1);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || WAVES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != world) return;
        Vec3 camera = event.getCamera().getPosition();
        // View rotation is already in the model-view stack at AFTER_WEATHER.
        // Own immediate buffer also works with Oculus segmented buffers (shaders on or off).
        var buffers = BUFFERS;
        var vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = new Matrix4f().translation((float)-camera.x, (float)-camera.y, (float)-camera.z);
        for (var wave : WAVES) {
            double age = Math.max(0, mc.level.getGameTime() - wave.startedAt() + event.getPartialTick());
            double distance = age * wave.speed();
            if (distance > wave.range()) continue;
            float fade = (float) Math.min(1, (wave.range() - distance) / Math.max(.5, wave.speed()));
            Vec3 center = wave.origin().add(wave.direction().scale(distance));
            Vec3 side = new Vec3(-wave.direction().z, 0, wave.direction().x);
            if (side.lengthSqr() < 1e-6) side = new Vec3(1,0,0);
            side = side.normalize();
            Vec3 up = side.cross(wave.direction()).normalize();
            // Ground waves lie across the ground; air blades are upright, with sharp pointed ends.
            Vec3 span = wave.ground() ? side.scale(wave.halfWidth()) : up.scale(1.15);
            if (!wave.ground()) center = center.add(up.scale(.65));
            for (int echo = 2; echo >= 0; echo--) {
                Vec3 at = center.subtract(wave.direction().scale(echo * .32));
                float alpha = fade * (echo == 0 ? 1 : .10f / echo);
                strip(vertices, matrix, wave, at, span, .34, wave.edge(), alpha * .55f);
                strip(vertices, matrix, wave, at, span, .19, wave.core(), alpha * .85f);
                strip(vertices, matrix, wave, at, span, .045, 0xF4FDFF, alpha * .95f);
            }
            // A perpendicular narrow spine gives the blade volume when viewed edge-on.
            strip(vertices, matrix, wave, center, wave.ground() ? up.scale(.13) : side.scale(.10),
                    .28, wave.core(), fade * .45f);
        }
        buffers.endBatch(RenderType.lightning());
    }
    private static void strip(VertexConsumer out, Matrix4f matrix, WarriorWavePacket wave,
            Vec3 center, Vec3 span, double width, int color, float alpha) {
        for (int i = 0; i < 48; i++) {
            double a = -1 + i / 24.0, b = -1 + (i + 1) / 24.0;
            Vec3 outerA = point(wave, center, span, a, 0);
            Vec3 outerB = point(wave, center, span, b, 0);
            Vec3 innerA = point(wave, center, span, a, width);
            Vec3 innerB = point(wave, center, span, b, width);
            float aa = alpha * (float) Math.pow(Math.max(0, 1 - a * a), .35);
            float ab = alpha * (float) Math.pow(Math.max(0, 1 - b * b), .35);
            vertex(out, matrix, outerA, color, aa); vertex(out, matrix, outerB, color, ab);
            vertex(out, matrix, innerB, color, ab); vertex(out, matrix, innerA, color, aa);
        }
    }
    private static Vec3 point(WarriorWavePacket wave, Vec3 center, Vec3 span, double t, double width) {
        double taper = Math.max(0, 1 - t * t);
        return center.add(span.scale(t)).add(wave.direction().scale(.55 * taper - width * taper));
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, int color, float alpha) {
        out.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
                        (color & 255) / 255f, Mth.clamp(alpha, 0, 1)).endVertex();
    }
}
