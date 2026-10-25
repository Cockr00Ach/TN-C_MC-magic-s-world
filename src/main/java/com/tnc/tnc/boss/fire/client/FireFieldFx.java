package com.tnc.tnc.boss.fire.client;

import java.util.List;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.VolcanicFireFieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 火球落地后的余火表现：范围内持续冒小火苗 + 黑烟，中心偶尔爆一点火星。
 * 只做视觉，伤害由 {@link VolcanicFireFieldEntity} 在服务端结算。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class FireFieldFx {

    private static final double VIEW_DISTANCE = 64.0D;

    private FireFieldFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        AABB area = mc.player.getBoundingBox().inflate(VIEW_DISTANCE);
        List<VolcanicFireFieldEntity> fields = level.getEntitiesOfClass(VolcanicFireFieldEntity.class, area);
        if (fields.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (VolcanicFireFieldEntity field : fields) {
            Vec3 center = field.position();
            double radius = field.radius();
            for (int i = 0; i < 2; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0D;
                double d = radius * Math.sqrt(level.random.nextDouble());
                double x = center.x + Math.cos(a) * d;
                double z = center.z + Math.sin(a) * d;
                double y = center.y + 0.05D + level.random.nextDouble() * 0.35D;
                level.addAlwaysVisibleParticle(TortoiseParticles.FIRE_MID.get(), true, x, y, z,
                        (level.random.nextDouble() - 0.5D) * 0.01D, 0.02D, (level.random.nextDouble() - 0.5D) * 0.01D);
            }
            if ((now & 1L) == 0L) {
                double a = level.random.nextDouble() * Math.PI * 2.0D;
                double d = radius * 0.75D * Math.sqrt(level.random.nextDouble());
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        center.x + Math.cos(a) * d, center.y + 0.4D, center.z + Math.sin(a) * d,
                        0.0D, 0.03D, 0.0D);
            }
            if ((now % 6L) == 0L) {
                level.addParticle(ParticleTypes.FLAME,
                        center.x + (level.random.nextDouble() - 0.5D) * radius,
                        center.y + 0.1D,
                        center.z + (level.random.nextDouble() - 0.5D) * radius,
                        0.0D, 0.03D, 0.0D);
            }
        }
    }
}
