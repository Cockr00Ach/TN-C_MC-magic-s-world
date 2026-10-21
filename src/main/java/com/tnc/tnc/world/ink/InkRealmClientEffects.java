package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gray sky/fog palette; the post effect handles entities and foreign blocks too. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InkRealmClientEffects {
    private InkRealmClientEffects() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(InkRealmTravel.ID, new InkEffects());
    }

    private static final class InkEffects extends DimensionSpecialEffects {
        private InkEffects() {
            super(148.0F, true, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 ignored, float brightness) {
            double gray = 0.43D + brightness * 0.34D;
            return new Vec3(gray, gray, gray);
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }
    }
}
