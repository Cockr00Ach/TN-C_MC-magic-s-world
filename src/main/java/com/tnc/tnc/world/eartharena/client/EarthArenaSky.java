package com.tnc.tnc.world.eartharena.client;

import com.tnc.tnc.world.eartharena.EarthArena;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keep the ochre sky clear of vanilla low clouds intersecting the tall ridges. */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EarthArenaSky {
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EarthArena.id("earth_arena"), new Effects());
    }
    public static final class Effects extends DimensionSpecialEffects.OverworldEffects {
        @Override public float getCloudHeight() { return Float.NaN; }
    }
}
