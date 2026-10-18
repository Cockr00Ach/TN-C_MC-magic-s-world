package com.tnc.tnc.mana.sight;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Data registration stays independent of all client rendering classes. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ManaSightParticleContent {
    public static SimpleParticleType TYPE;
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.PARTICLE_TYPE, helper -> {
            TYPE=new SimpleParticleType(true) {};
            helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","mana_sight"),TYPE);
        });
    }
}
