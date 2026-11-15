package com.tnc.tnc.world.yan;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.tnc.tnc.boss.YanDarkBossEntity;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Distant ambient lightning in Yan's worlds, drawn as a single local effect. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class YanSanctuaryLightning {
    private static final int FALL_TICKS = 3;
    private static final int LIFE_TICKS = 13;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(32768));
    private static final MethodHandle SHADOW_PASS = findShadowPass();
    private static ClientLevel world;
    private static Strike strike;
    private static int delay;

    private record Strike(Vec3 ground, Vec3 cloud, long seed, long started) {}

    private YanSanctuaryLightning() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level != world) {
            strike = null;
            delay = 26;
            world = level;
        }
        if (level == null || client.player == null || client.isPaused() || !YanDarkBossEntity.isYanArena(level)) return;

        long now = level.getGameTime();
        if (strike != null && now - strike.started() >= LIFE_TICKS) strike = null;
        if (client.screen != null || client.getCameraEntity() != client.player || --delay > 0) return;

        // Pick an already loaded column in front of the player. No entities, chunk tickets or particles.
        double yaw = Math.toRadians(client.player.getYRot() + (RANDOM.nextDouble() - .5) * 72);
        int distance = 38 + RANDOM.nextInt(36);
        int x = Mth.floor(client.player.getX() - Math.sin(yaw) * distance);
        int z = Mth.floor(client.player.getZ() + Math.cos(yaw) * distance);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            delay = 17;
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (y <= level.getMinBuildHeight()) {
            delay = 17;
            return;
        }
        var ground = Vec3.atBottomCenterOf(new BlockPos(x, y, z));
        var cloud = ground.add((RANDOM.nextDouble() - .5) * 17,
                Math.min(54 + RANDOM.nextInt(24), level.getMaxBuildHeight() + 12 - y), (RANDOM.nextDouble() - .5) * 17);
        strike = new Strike(ground, cloud, RANDOM.nextLong(), now);
        delay = 85 + RANDOM.nextInt(85);

        Vec3 audible = ground.subtract(client.player.position()).normalize().scale(12).add(client.player.position());
        level.playLocalSound(audible.x, audible.y, audible.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, .78F, .55F + RANDOM.nextFloat() * .25F, true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (strike == null || world != Minecraft.getInstance().level
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || inShaderShadowPass()) return;
        float age = world.getGameTime() - strike.started() + event.getPartialTick();
        if (age < 0 || age >= LIFE_TICKS) return;
        Vec3 camera = event.getCamera().getPosition();
        Vec3 base = strike.ground();
        if (base.distanceToSqr(camera) > 120 * 120) return;
        Vec3 high = strike.cloud().subtract(base);
        Vec3 eye = camera.subtract(base);
        float fall = Mth.clamp(age / FALL_TICKS, 0, 1);
        float fade = 1 - Mth.clamp((age - FALL_TICKS) / (LIFE_TICKS - FALL_TICKS), 0, 1);
        float alpha = (.64F + .25F * Mth.cos(age * 1.34F)) * fade;
        // At AFTER_WEATHER the view rotation is already in the render system's model-view matrix.
        var matrix = new Matrix4f().translation((float) -eye.x, (float) -eye.y, (float) -eye.z);
        var vertices = BUFFERS.getBuffer(LightningRenderTypes.BOLT);
        LightningBoltGeometry.bolt(vertices, matrix, high.x, high.y, high.z,
                high.x * (1 - fall), high.y * (1 - fall), high.z * (1 - fall),
                strike.seed(), .29F, alpha, LightningTheme.VIOLET, 2, eye.x, eye.y, eye.z);
        if (age >= FALL_TICKS) {
            LightningBoltGeometry.groundFlash(vertices, matrix, 0, .07, 0,
                    3.8F, LightningTheme.VIOLET, fade * fade * .69F);
            LightningBoltGeometry.billboardFlash(vertices, matrix, 0, .2, 0,
                    2.1F, LightningTheme.VIOLET, fade * fade * .77F, eye.x, eye.y, eye.z);
        }
        BUFFERS.endBatch(LightningRenderTypes.BOLT);
    }

    private static MethodHandle findShadowPass() {
        try {
            return MethodHandles.publicLookup().findStatic(
                    Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState"),
                    "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static boolean inShaderShadowPass() {
        try {
            return SHADOW_PASS != null && (boolean) SHADOW_PASS.invokeExact();
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
