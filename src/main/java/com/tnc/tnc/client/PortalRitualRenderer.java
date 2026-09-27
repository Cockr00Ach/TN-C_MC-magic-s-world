package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.PortalRitualTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

/** Continuous emissive tubes: independent of the particle limiter, no damage or block writes. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class PortalRitualRenderer {
    private record Charge(int elapsed, long received) {}
    private static final Map<BlockPos, Charge> CHARGES = new HashMap<>();
    private static ClientLevel owner;
    // Oculus replaces Minecraft's shared source with segmented buffers whose
    // endBatch(RenderType) is a no-op, even with shaders OFF. Flush our own source here.
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(262144));
    private static boolean loggedReceive,loggedDraw;

    private PortalRitualRenderer() {}

    public static void accept(BlockPos pos, int elapsed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != owner) { CHARGES.clear(); owner = level; }
        if (level == null) return;
        if (!loggedReceive && elapsed>=0) {
            com.mojang.logging.LogUtils.getLogger().info("[TN-C Portal] client received ritual packet at {}",pos);
            loggedReceive=true;
        }
        if (elapsed < 0 || elapsed > PortalRitualTiming.DURATION) CHARGES.remove(pos);
        else CHARGES.put(pos.immutable(), new Charge(elapsed, level.getGameTime()));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != owner) { CHARGES.clear(); owner = level; }
        if (level != null) CHARGES.values().removeIf(c -> level.getGameTime() - c.received() > 6);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || CHARGES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != owner) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffers = BUFFERS;
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        for (var entry : CHARGES.entrySet()) {
            Vec3 center = Vec3.atBottomCenterOf(entry.getKey());
            if (center.distanceToSqr(camera) > 80 * 80) continue;
            Charge charge = entry.getValue();
            double elapsed = Math.min(80, charge.elapsed() + mc.level.getGameTime()
                    - charge.received() + event.getPartialTick());
            // AFTER_WEATHER already has the view rotation in RenderSystem's model-view stack.
            // Applying event.getPoseStack() here would rotate the world-space beams twice.
            Matrix4f translation = new Matrix4f().translation((float)(center.x-camera.x),
                    (float)(center.y-camera.y), (float)(center.z-camera.z));
            ritual(vertices, translation, Vec3.ZERO, elapsed);
        }
        buffers.endBatch(RenderType.lightning());
        if (!loggedDraw) {
            com.mojang.logging.LogUtils.getLogger().info("[TN-C Portal] ritual geometry submitted with independent immediate buffer");
            loggedDraw=true;
        }
    }

    private static void ritual(VertexConsumer out, Matrix4f pose, Vec3 center, double elapsed) {
        double phase = elapsed * .025;
        double ring = PortalRitualTiming.clamp(elapsed / 20);
        for (int i = 0; i < 64 * ring; i++) {
            double a = Math.PI * 2 * i / 64;
            double b = Math.PI * 2 * (i + 1) / 64;
            tube(out, pose, center.add(Math.cos(a) * 10.2, .07, Math.sin(a) * 10.2),
                    center.add(Math.cos(b) * 10.2, .07, Math.sin(b) * 10.2), .09, 255, 22, 48, 185);
        }
        // The reference seal has five points; draw a precise luminous star above the block mosaic.
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / 5;
            double b = -Math.PI / 2 + (i + 2) * Math.PI * 2 / 5;
            Vec3 start = center.add(Math.cos(a) * 8.8, .08, Math.sin(a) * 8.8);
            Vec3 end = center.add(Math.cos(b) * 8.8, .08, Math.sin(b) * 8.8);
            tube(out, pose, start, start.lerp(end, ring), .065, 255, 40, 60, 180);
        }
        Vec3 core = center.add(0, 20.5, 0);
        double converge = PortalRitualTiming.convergence(elapsed);
        if (converge > 0) {
            for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
                // Template (11/23,10,11/23), relative to landing (17,2,17).
                Vec3 start = center.add(dx * 6, 8.5, dz * 6);
                laser(out, pose, start, start.lerp(core, converge), .16 + converge * .12);
            }
            // Slow orbit around the suspended core, no strobing.
            for (int i = 0; i < 32; i++) {
                double a = phase + i * Math.PI / 16;
                double b = phase + (i + 1) * Math.PI / 16;
                tube(out, pose, core.add(Math.cos(a) * 2.2, -.7, Math.sin(a) * 2.2),
                        core.add(Math.cos(b) * 2.2, -.7, Math.sin(b) * 2.2), .045, 255, 55, 65, 150);
            }
        }
        // Rising arcs around the traveller are visible at eye level without looking straight up.
        for (int i=0;i<36;i++) {
            double a=phase*2+i*Math.PI/9,b=phase*2+(i+1)*Math.PI/9;
            double y=i*.13;
            tube(out,pose,center.add(Math.cos(a)*2.8,y,Math.sin(a)*2.8),
                    center.add(Math.cos(b)*2.8,y+.13,Math.sin(b)*2.8),.035,255,42,65,(int)(100*ring));
        }
        double release = PortalRitualTiming.release(elapsed);
        if (release > 0) {
            laser(out, pose, core, core.lerp(center.add(0, .12, 0), release), .24 + .38 * release);
            laser(out, pose, core, core.add(0, 20 * release, 0), .12 + .16 * release);
        }
    }

    private static void laser(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, double width) {
        tube(out, pose, a, b, width * .28, 255, 220, 215, 230);
        tube(out, pose, a, b, width, 255, 36, 64, 155);
        tube(out, pose, a, b, width * 2.1, 220, 8, 32, 38);
    }

    private static void tube(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b,
                             double radius, int r, int g, int blue, int alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < .000001) return;
        axis = axis.normalize();
        Vec3 u = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = axis.cross(u).normalize();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double next = (i + 1) * Math.PI / 4;
            Vec3 p = u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius));
            Vec3 q = u.scale(Math.cos(next) * radius).add(v.scale(Math.sin(next) * radius));
            vertex(out, pose, a.add(p), r, g, blue, alpha);
            vertex(out, pose, a.add(q), r, g, blue, alpha);
            vertex(out, pose, b.add(q), r, g, blue, alpha);
            vertex(out, pose, b.add(p), r, g, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 p, int r, int g, int b, int a) {
        out.vertex(pose, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, a).endVertex();
    }
}
