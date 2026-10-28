package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseDeath;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Death-only cosmetic emissions. Fragment positions come from actual clipped model faces. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TortoiseDeathFx {
    private static final Map<VolcanicTortoiseEntity, State> STATES = new WeakHashMap<>();
    private static ClientLevel budgetLevel;
    private static long budgetTick = Long.MIN_VALUE;
    private static int total;

    private static final class State {
        long tick = Long.MIN_VALUE;
        long renderTick = Long.MIN_VALUE;
        int count;
    }

    private TortoiseDeathFx() {}

    private static boolean available(VolcanicTortoiseEntity boss, float age) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.player.distanceToSqr(boss) > 4096) return false;
        long now = mc.level.getGameTime();
        if (budgetLevel != mc.level || budgetTick != now) {
            budgetLevel = mc.level;
            budgetTick = now;
            total = 0;
        }
        State state = STATES.computeIfAbsent(boss, ignored -> new State());
        if (state.tick != now) { state.tick = now; state.count = 0; }
        if (total >= 96 || state.count >= TortoiseDeath.particleBudget(age)) return false;
        total++;
        state.count++;
        return true;
    }

    public static boolean beginFragmentFrame(VolcanicTortoiseEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return false;
        State state = STATES.computeIfAbsent(boss, ignored -> new State());
        if (state.renderTick == mc.level.getGameTime()) return false;
        state.renderTick = mc.level.getGameTime();
        return true;
    }

    private static void particle(VolcanicTortoiseEntity boss, float age, ParticleOptions type, Vec3 at, Vec3 velocity,
                                 float size, int life, float r, float g, float b) {
        if (!available(boss, age)) return;
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, at.x, at.y, at.z,
                velocity.x, velocity.y, velocity.z);
        if (particle == null) return;
        particle.scale(size);
        particle.setLifetime(life);
        particle.setColor(r, g, b);
        particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
    }

    public static void fragment(VolcanicTortoiseEntity boss, float age, Vec3 at) {
        if (age < TortoiseDeath.DISSOLVE_START || age >= TortoiseDeath.DURATION) return;
        var random = boss.level().random;
        Vec3 radial = at.subtract(boss.position()).multiply(1, 0, 1).normalize();
        Vec3 speed = radial.scale(0.06 + random.nextDouble() * 0.07)
                .add((random.nextDouble() - 0.5) * 0.05, 0.045 + random.nextDouble() * 0.08,
                        (random.nextDouble() - 0.5) * 0.05);
        particle(boss, age, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), at,
                speed, 0.9F + random.nextFloat() * 0.4F, 18 + random.nextInt(10), 0.65F, 0.62F, 0.58F);
        if (random.nextInt(4) == 0) particle(boss, age, ParticleTypes.ASH, at, speed.scale(0.4),
                0.7F, 20, 0.5F, 0.48F, 0.45F);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || !boss.isDyingAnimation()
                    || mc.player.distanceToSqr(boss) > 4096) continue;
            int age = (int) boss.deathAge(0);
            var random = mc.level.random;
            if (age >= TortoiseDeath.IMPACT && age < TortoiseDeath.IMPACT + 3) {
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 8 + age * 0.2;
                    Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Vec3 at = boss.position().add(radial.scale(3.3 + (age - TortoiseDeath.IMPACT) * 0.5)).add(0, 0.10, 0);
                    particle(boss, age, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), at,
                            radial.scale(0.09).add(0, 0.04, 0), 1.0F, 14, 0.6F, 0.56F, 0.5F);
                }
                for (int i = 0; i < 2; i++) particle(boss, age, ParticleTypes.SMALL_FLAME,
                        boss.position().add((random.nextDouble() - 0.5) * 4, 0.3, (random.nextDouble() - 0.5) * 4),
                        new Vec3(0, 0.06, 0), 0.8F, 8, 1, 0.4F, 0.1F);
            }
            if (age >= TortoiseDeath.COOL_START && age < TortoiseDeath.COOL_END && age % 3 == 0) {
                int count = 1 + (int) (3 * (1 - TortoiseDeath.cooling(age)));
                for (int i = 0; i < count; i++) particle(boss, age, ParticleTypes.SMOKE,
                        boss.craterPoint().add((random.nextDouble() - 0.5) * 0.4,
                                boss.usesPhaseTwoModel() ? -0.26 : -0.48, (random.nextDouble() - 0.5) * 0.4),
                        new Vec3(0.005, 0.025, 0), 0.5F, 16, 0.48F, 0.46F, 0.43F);
            }
            if (age >= TortoiseDeath.COOL_END && age < TortoiseDeath.EMBER_END
                    && age % (age < 106 ? 3 : 6) == 0) {
                particle(boss, age, ParticleTypes.SMALL_FLAME,
                        boss.position().add((random.nextDouble() - 0.5) * 5, 2.0, (random.nextDouble() - 0.5) * 4),
                        new Vec3((random.nextDouble() - 0.5) * 0.04, 0.025, (random.nextDouble() - 0.5) * 0.04),
                        0.6F, 10, 0.8F, 0.18F, 0.03F);
            }
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        float strength = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && boss.isDyingAnimation()) {
                float distance = (float) Math.sqrt(mc.player.distanceToSqr(boss));
                strength = Math.max(strength, Math.max(0, 1 - distance / 32) * TortoiseDeath.shake(boss.deathAge((float) event.getPartialTick())));
            }
        }
        float time = mc.level.getGameTime() + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(time * 2.4) * strength);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 1.8) * strength * 0.5F);
    }
}
