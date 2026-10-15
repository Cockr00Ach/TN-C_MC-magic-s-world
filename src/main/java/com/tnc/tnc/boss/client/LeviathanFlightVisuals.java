package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.LeviathanMovementRules;
import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-local blue cloud sheet beneath the belly; no server packets, registry or static world cache. */
@Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class LeviathanFlightVisuals {
    private LeviathanFlightVisuals() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()
                || mc.level.getGameTime()%LeviathanMovementRules.MIST_INTERVAL != 0) return;
        int remaining = mc.options.particles().get() == ParticleStatus.MINIMAL
                ? 2 : LeviathanMovementRules.MIST_TOTAL_BUDGET;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TNWaterLeviathanEntity boss) || !boss.isAlive()
                    || boss.isInWater() || boss.distanceToSqr(mc.player)>160.0D*160.0D) continue;
            Vec3 forward = Vec3.directionFromRotation(boss.castingPitch(1), boss.yBodyRot);
            Vec3 up = Vec3.directionFromRotation(boss.castingPitch(1)-90F, boss.yBodyRot);
            Vec3 side = Vec3.directionFromRotation(0,boss.yBodyRot+90);
            for (int i=0; i<LeviathanMovementRules.MIST_PER_BOSS && remaining>0; i++, remaining--) {
                double along = (-7.5D+mc.level.random.nextDouble()*10.0D)*boss.SIZE_SCALE;
                double across = (mc.level.random.nextDouble()-0.5D)*4.0D*boss.SIZE_SCALE;
                Vec3 at = boss.position().add(forward.scale(along)).add(side.scale(across))
                        .add(up.scale((0.12D+mc.level.random.nextDouble()*0.35D)*boss.SIZE_SCALE));
                Vec3 drift = boss.getDeltaMovement().scale(0.14D).add(0, -0.018D, 0);
                Particle cloud = mc.particleEngine.createParticle(ParticleTypes.CLOUD,
                        at.x, at.y, at.z, drift.x, drift.y, drift.z);
                if (cloud != null) {
                    cloud.setColor(0.07F, 0.32F+mc.level.random.nextFloat()*0.12F, 0.88F);
                    cloud.scale(3.5F*boss.SIZE_SCALE);
                    cloud.setLifetime(LeviathanMovementRules.MIST_LIFETIME);
                    cloud.setParticleSpeed(drift.x, drift.y, drift.z);
                }
            }
            if (remaining<=0) break;
        }
    }
}
