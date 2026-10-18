package com.tnc.tnc.magic;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered brown dust also used by Spell Engine's JSON particle batches. */
public final class EarthParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,"tnc");
    public static final RegistryObject<SimpleParticleType> DUST=TYPES.register("earth_dust",()->new SimpleParticleType(false));
    private EarthParticles(){}
    public static void register(IEventBus bus){TYPES.register(bus);}
}
