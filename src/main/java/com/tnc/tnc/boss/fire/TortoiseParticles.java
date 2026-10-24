package com.tnc.tnc.boss.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 火山巨龟专用粒子类型。
 *
 * <p>重踏火环用 {@link #SLAM_FLAME}：粒子自带圆周角度与径向偏移，全部共享同一个传播半径，
 * 因此整道环只有一个轮廓、一条时间线，不会在旧半径上留下残留。
 */
public final class TortoiseParticles {

    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TNMod.MODID);

    /** overrideLimiter=true：即使玩家把粒子设为「最少」，火环也完整保留。 */
    public static final RegistryObject<SimpleParticleType> SLAM_FLAME =
            TYPES.register("slam_flame", () -> new SimpleParticleType(true));

    private TortoiseParticles() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
