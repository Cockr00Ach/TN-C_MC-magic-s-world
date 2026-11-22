package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Spray rises from the open water immediately downstream of each rock step. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class WaterfallSpray {
    // Each point is the receiving pool block, with a falling sheet to its north.
    private static final BlockPos[] IMPACTS = {
        new BlockPos(-162, 261, -162), new BlockPos(-178, 261, -157),
        new BlockPos(-182, 245, -150), new BlockPos(-162, 245, -150),
        new BlockPos(-194, 233, -134), new BlockPos(-154, 233, -133),
        new BlockPos(-187, 212, -129), new BlockPos(-164, 212, -122),
        new BlockPos(-189, 201, -117), new BlockPos(-168, 201, -109)
    };

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var world = mc.level;
        if (mc.player == null || world == null || mc.isPaused() || !world.dimension().equals(SkyhavenContent.KEY)) return;
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(-176, 239, -142) > 80 * 80) return;

        // Stagger the landings across ticks. Vanilla's addParticle applies the
        // client's normal particle density and distance settings.
        var random = world.random;
        int lane = (int)(world.getGameTime() & 3);
        for (int i = lane; i < IMPACTS.length; i += 4) {
            var pool = IMPACTS[i];
            if (!pool.closerToCenterThan(camera, 32)) continue;
            var sheet = pool.north().above();
            if (world.getChunkSource().getChunk(pool.getX() >> 4, pool.getZ() >> 4, ChunkStatus.FULL, false) == null
                    || world.getChunkSource().getChunk(sheet.getX() >> 4, sheet.getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
            var water = world.getFluidState(sheet);
            if (!water.is(FluidTags.WATER) || !water.hasProperty(FlowingFluid.FALLING) || !water.getValue(FlowingFluid.FALLING)
                    || !world.getFluidState(pool).is(FluidTags.WATER) || !world.isEmptyBlock(pool.above())) continue;

            double x = pool.getX() + .12 + random.nextDouble() * .76;
            double y = pool.getY() + 1.03;
            double z = pool.getZ() + .04 + random.nextDouble() * .75;
            world.addParticle(ParticleTypes.SPLASH, x, y, z, 0, .10, .05);
            if (random.nextInt(4) == 0) {
                world.addParticle(ParticleTypes.CLOUD, x, y + .15, z,
                        .007 + random.nextDouble() * .014, .018 + random.nextDouble() * .02, .023);
            }
        }
    }

    private WaterfallSpray() {}
}
