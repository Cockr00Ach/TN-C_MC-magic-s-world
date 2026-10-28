package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.TortoiseTransition;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Cosmetic transition cues. No packets, damage, explosions, block edits or falling blocks. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TortoiseTransitionFx {
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(32768));
    private static final int SEGMENTS = 80;
    private static final int GLOBAL_PARTICLE_BUDGET = 120;
    private static final Map<VolcanicTortoiseEntity, Long> LAST_TICK = new WeakHashMap<>();

    private static final class Budget {
        int left;
        Budget(int count) { this.left = count; }
    }

    private TortoiseTransitionFx() {}

    private static float proximity(Minecraft mc, VolcanicTortoiseEntity boss) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceTo(boss.position().add(0, 2.0, 0));
        return (float) Math.max(0.0, Math.min(1.0, (64.0 - distance) / 48.0));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        int handled = 0, available = GLOBAL_PARTICLE_BUDGET;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || !boss.isTransitioning()
                    || proximity(mc, boss) <= 0.0F) continue;
            if (++handled > 3 || available <= 0) break;
            long now = level.getGameTime();
            Long last = LAST_TICK.put(boss, now);
            if (last != null && last == now) continue;
            Budget budget = new Budget(Math.min(available, TortoiseTransition.particleBudget(boss.transitionAge(0))));
            int before = budget.left;
            emitBeat(mc, boss, boss.transitionAge(0), budget);
            available -= before - budget.left;
        }
    }

    private static Vec3 local(VolcanicTortoiseEntity boss, double side, double height, double forward) {
        double yaw = Math.toRadians(boss.getYRot());
        return boss.position().add(side * Math.cos(yaw) - forward * Math.sin(yaw), height,
                side * Math.sin(yaw) + forward * Math.cos(yaw));
    }

    private static void mote(Minecraft mc, Budget budget, ParticleOptions type, Vec3 at, Vec3 velocity,
                             float scale, int life, float red, float green, float blue) {
        if (budget.left <= 0) return;
        budget.left--;
        Particle particle = mc.particleEngine.createParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
        if (particle == null) return;
        particle.scale(scale);
        particle.setLifetime(life);
        particle.setColor(red, green, blue);
        particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
    }

    private static void fire(Minecraft mc, Budget budget, Vec3 at, Vec3 velocity, float size, int life) {
        mote(mc, budget, ParticleTypes.FLAME, at, velocity, size, life, 1.0F, 0.78F, 0.38F);
    }

    private static void emitBeat(Minecraft mc, VolcanicTortoiseEntity boss, float age, Budget budget) {
        var random = mc.level.random;
        if (budget.left <= 0) return; // Includes the deliberate dead-silence interval.
        if (age < TortoiseTransition.SILENCE_START) {
            double pressure = age / TortoiseTransition.SILENCE_START;
            // Two spirals pulled into the shell; increasingly dense fissure sparks.
            for (int i = 0; i < 6; i++) {
                double angle = age * 0.18 + i * Math.PI / 3.0;
                double radius = 4.8 - pressure * 1.9;
                Vec3 at = local(boss, Math.cos(angle) * radius, 0.6 + pressure * 1.5, Math.sin(angle) * radius);
                Vec3 target = boss.position().add(0, 2.0, 0);
                fire(mc, budget, at, target.subtract(at).scale(0.12), 0.7F, 6);
            }
            for (int i = 0; i < 2 + (int) (pressure * 6); i++) {
                double side = (i % 2 == 0 ? 1 : -1) * (2.5 - random.nextDouble() * 0.3);
                fire(mc, budget, local(boss, side, 2.0 + random.nextDouble(), random.nextDouble() * 3.6 - 1.8),
                        new Vec3(0, 0.035, 0), 0.8F, 6);
            }
            if ((int) age % 4 == 0) footDust(mc, boss, budget);
            return;
        }
        if (age < TortoiseTransition.FRACTURE_END) {
            Vec3 vent = boss.craterPoint();
            float force = 1.0F - (age - TortoiseTransition.MODEL_SWAP) / 32.0F;
            // A narrow eruptive jet, with no black smoke over the silhouette.
            for (int i = 0; i < 12; i++) {
                Vec3 at = vent.add((random.nextDouble() - 0.5) * 0.65, random.nextDouble() * 2.8, (random.nextDouble() - 0.5) * 0.65);
                fire(mc, budget, at, new Vec3((random.nextDouble() - 0.5) * 0.08, 0.18 + force * 0.2, (random.nextDouble() - 0.5) * 0.08), 1.4F, 12);
            }
            for (int i = 0; i < (age < 61 ? 8 : 5); i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 at = boss.position().add(radial.scale(2.8)).add(0, 2.2 + random.nextDouble() * 2, 0);
                fire(mc, budget, at, radial.scale(0.22).add(0, 0.12 + random.nextDouble() * 0.16, 0), 1.1F, 16);
                if (age < 61) mote(mc, budget, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                        at, radial.scale(0.16 + random.nextDouble() * 0.16).add(0, 0.12 + random.nextDouble() * 0.18, 0),
                        1.4F, 18, 1, 1, 1);
            }
            if ((int) age % 2 == 0) groundFire(mc, boss, budget, TortoiseTransition.shockRadius(age), 16, 1.0F);
            return;
        }
        if (age < TortoiseTransition.REBUILD_END) {
            // Residual pressure escapes along the opened side plates, then falls as ash.
            double phase = (age - TortoiseTransition.FRACTURE_END) / 48.0;
            for (int i = 0; i < 8; i++) {
                double side = (i % 2 == 0 ? 1 : -1) * 3.0;
                Vec3 at = local(boss, side, 3.5 - phase, random.nextDouble() * 4 - 2);
                fire(mc, budget, at, local(boss, side * 0.025, 0, 0).subtract(boss.position()).add(0, -0.02, 0), 0.85F, 12);
                if (age < TortoiseTransition.ASH_END) mote(mc, budget, ParticleTypes.ASH, at,
                        new Vec3(0, -0.045, 0), 1.1F, 18, 0.6F, 0.53F, 0.45F);
            }
            if ((int) age == 90 || (int) age == 112) footDust(mc, boss, budget);
            return;
        }
        // A second fire wave is tied to the roar, clearly separated from the shell blast.
        if (age < TortoiseTransition.IDLE_BLEND_START && (int) age % 2 == 0) {
            groundFire(mc, boss, budget, TortoiseTransition.scorchRadius(age), 28, 1.5F);
            for (int i = 0; i < 8; i++) fire(mc, budget,
                    boss.craterPoint().add((random.nextDouble() - 0.5) * 0.7, random.nextDouble() * 1.8, (random.nextDouble() - 0.5) * 0.7),
                    new Vec3(0, 0.16 + random.nextDouble() * 0.1, 0), 1.2F, 10);
        }
    }

    private static void footDust(Minecraft mc, VolcanicTortoiseEntity boss, Budget budget) {
        for (double side : new double[]{-2.4, 2.4}) for (double forward : new double[]{-2.2, 2.2}) {
            Vec3 at = local(boss, side, 0.08, forward);
            for (int i = 0; i < 2; i++) mote(mc, budget, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                    at, new Vec3(side * 0.025, 0.04, forward * 0.025), 1.1F, 10, 0.7F, 0.65F, 0.6F);
        }
    }

    private static void groundFire(Minecraft mc, VolcanicTortoiseEntity boss, Budget budget, float radius, int count, float size) {
        if (radius < 2.5F) return;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            double x = boss.getX() + Math.cos(angle) * radius, z = boss.getZ() + Math.sin(angle) * radius;
            BlockPos pos = BlockPos.containing(x, boss.getY(), z);
            if (!mc.level.hasChunkAt(pos)) continue;
            double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            if (Math.abs(y - boss.getY()) > 3) continue;
            fire(mc, budget, new Vec3(x, y + 0.08, z), new Vec3(Math.cos(angle) * 0.035, 0.06, Math.sin(angle) * 0.035), size, 8);
        }
    }

    @SubscribeEvent
    public static void flash(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float strength = 0.0F;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && boss.isTransitioning()) {
                strength = Math.max(strength, proximity(mc, boss)
                        * TortoiseTransition.flash(boss.transitionAge(mc.getFrameTime())));
            }
        }
        if (strength > 0.0F) {
            var gui = event.getGuiGraphics();
            gui.fill(0, 0, gui.guiWidth(), gui.guiHeight(), Math.round(strength * 255.0F) << 24 | 0xFFFFF5);
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        float strength = 0.0F;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && boss.isTransitioning()) {
                strength = Math.max(strength, proximity(mc, boss)
                        * TortoiseTransition.shake(boss.transitionAge((float) event.getPartialTick())));
            }
        }
        float time = mc.level.getGameTime() + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(time * 2.13) * strength);
        event.setYaw(event.getYaw() + (float) Math.sin(time * 1.71 + 0.6) * strength * 0.45F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 2.57) * strength * 0.7F);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);
        // Independent buffers avoid the pack's Oculus no-op shared BufferSource.
        VertexConsumer buffer = BUFFERS.getBuffer(RenderType.debugQuads());
        int drawn = 0;
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof VolcanicTortoiseEntity boss) || !boss.isTransitioning()
                        || proximity(mc, boss) <= 0.0F) continue;
                if (++drawn > 3) break;
                float age = boss.transitionAge(event.getPartialTick());
                float shock = TortoiseTransition.shockRadius(age);
                if (shock > 0.0F) {
                    float alpha = Math.max(0, (TortoiseTransition.SHOCK_END - age) / (TortoiseTransition.SHOCK_END - TortoiseTransition.MODEL_SWAP)) * 0.8F;
                    ring(level, boss.position(), shock, 0.20F, matrix, buffer, 1.0F, 0.93F, 0.75F, alpha);
                }
                float scorch = TortoiseTransition.scorchRadius(age);
                if (scorch > 0.0F) {
                    float alpha = Math.max(0, (TortoiseTransition.DURATION - age) / (TortoiseTransition.DURATION - TortoiseTransition.REBUILD_END));
                    ring(level, boss.position(), scorch, 0.32F, matrix, buffer, 0.08F, 0.055F, 0.04F, alpha * 0.75F);
                    ring(level, boss.position(), scorch + 0.17F, 0.06F, matrix, buffer,
                            0.95F, 0.28F, 0.06F, alpha * 0.38F);
                }
            }
        } finally {
            BUFFERS.endBatch();
        }
    }

    private static void ring(ClientLevel level, Vec3 center, float radius, float width,
                             Matrix4f matrix, VertexConsumer buffer, float r, float g, float b, float alpha) {
        double inner = Math.max(0.0, radius - width * 0.5);
        double outer = radius + width * 0.5;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = i * Math.PI * 2.0 / SEGMENTS;
            double next = (i + 1) * Math.PI * 2.0 / SEGMENTS;
            double[] xs = {center.x + Math.cos(a) * inner, center.x + Math.cos(a) * outer,
                    center.x + Math.cos(next) * outer, center.x + Math.cos(next) * inner};
            double[] zs = {center.z + Math.sin(a) * inner, center.z + Math.sin(a) * outer,
                    center.z + Math.sin(next) * outer, center.z + Math.sin(next) * inner};
            double[] ys = new double[4];
            boolean valid = true;
            for (int v = 0; v < 4; v++) {
                int x = (int) Math.floor(xs[v]), z = (int) Math.floor(zs[v]);
                if (!level.hasChunkAt(new BlockPos(x, (int) center.y, z))) {
                    valid = false;
                    break;
                }
                ys[v] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 0.045;
                if (Math.abs(ys[v] - center.y) > 4.0) valid = false;
            }
            if (valid) {
                for (int v = 0; v < 4; v++) {
                    buffer.vertex(matrix, (float) xs[v], (float) ys[v], (float) zs[v]).color(r, g, b, alpha).endVertex();
                }
            }
        }
    }
}
