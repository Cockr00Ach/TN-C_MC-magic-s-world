package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseDeath;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Thin cooling smoke followed by many rock/ash motes sampled from the actual body surface. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TortoiseDeathFx {
    private static final Map<VolcanicTortoiseEntity, Long> FRAMES = new WeakHashMap<>();
    private static ClientLevel budgetLevel;
    private static long budgetTick = Long.MIN_VALUE;
    private static int total;
    private TortoiseDeathFx() {}

    private static boolean visible(VolcanicTortoiseEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == boss.level() && mc.player != null && !mc.isPaused()
                && mc.player.distanceToSqr(boss) <= 4096;
    }

    public static boolean beginSurfaceFrame(VolcanicTortoiseEntity boss, float age) {
        if (age < TortoiseDeath.SCATTER_START || age >= TortoiseDeath.SCATTER_END || !visible(boss)) return false;
        long now = boss.tickCount;
        Long last = FRAMES.put(boss, now);
        return last == null || last != now;
    }

    private static boolean available(VolcanicTortoiseEntity boss) {
        if (!visible(boss)) return false;
        ClientLevel level = Minecraft.getInstance().level;
        long now = Minecraft.getInstance().player.tickCount;
        if (budgetLevel != level || budgetTick != now) {
            budgetLevel = level;
            budgetTick = now;
            total = 0;
        }
        if (total >= 96) return false;
        total++;
        return true;
    }

    public static void scatter(VolcanicTortoiseEntity boss, float age, Vec3 at) {
        if (age < TortoiseDeath.SCATTER_START || age >= TortoiseDeath.SCATTER_END || !available(boss)) return;
        var random = boss.level().random;
        Vec3 radial = at.subtract(boss.position()).multiply(1, 0, 1).normalize();
        Vec3 velocity = radial.scale(.055 + random.nextDouble() * .08)
                .add((random.nextDouble() - .5) * .035, .025 + random.nextDouble() * .065,
                     (random.nextDouble() - .5) * .035);
        var type = random.nextInt(4) == 0 ? ParticleTypes.ASH
                : new BlockParticleOption(ParticleTypes.BLOCK,
                    (random.nextBoolean() ? Blocks.BASALT : Blocks.BLACKSTONE).defaultBlockState());
        var particle = Minecraft.getInstance().particleEngine.createParticle(type, at.x, at.y, at.z,
                velocity.x, velocity.y, velocity.z);
        if (particle != null) {
            particle.scale(.65F + random.nextFloat() * .45F);
            particle.setLifetime(18 + random.nextInt(TortoiseDeath.MAX_PARTICLE_LIFE - 17));
            particle.setColor(.52F, .5F, .48F);
            particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || !boss.isDyingAnimation() || !visible(boss)) continue;
            int age = (int) boss.deathAge(0);
            if (age < 0 || age >= TortoiseDeath.COOL_END || age % (age < 30 ? 4 : 9) != 0 || !available(boss)) continue;
            var at = boss.craterPoint();
            var particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, .02, 0);
            if (particle != null) {
                particle.scale(.35F);
                particle.setLifetime(16);
                particle.setColor(.4F, .4F, .4F);
            }
        }
    }
}
