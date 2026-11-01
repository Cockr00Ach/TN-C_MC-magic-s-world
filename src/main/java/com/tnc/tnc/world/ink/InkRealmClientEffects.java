package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Monochrome sky with no dimension clouds or atmospheric fog. No screen filter. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InkRealmClientEffects {
    private static final ResourceLocation EFFECTS_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ink_realm");
    static final float CLEAR_FOG_START = 1_000_000.0F;
    static final float CLEAR_FOG_END = 2_000_000.0F;
    private InkRealmClientEffects() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS_ID, new InkEffects());
    }

    static final class InkEffects extends DimensionSpecialEffects {
        InkEffects() {
            super(Float.NaN, true, SkyType.NORMAL, false, false);
        }

        @Override
        public boolean renderClouds(net.minecraft.client.multiplayer.ClientLevel level, int ticks,
                float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                double camX, double camY, double camZ, org.joml.Matrix4f projectionMatrix) {
            // Also consume the Forge hook; do not rely only on the vanilla NaN height guard.
            return true;
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

    static boolean clearsAtmosphere(ResourceLocation dimension) {
        return EFFECTS_ID.equals(dimension);
    }

    @Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
    public static final class FogEvents {
        private FogEvents() {}

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public static void fog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null || !clearsAtmosphere(level.dimension().location())) return;
            // Finite, ordered planes avoid Infinity/NaN shader uniforms. Covers both sky and
            // terrain passes, including camera-fluid fog, but never changes other dimensions.
            event.setNearPlaneDistance(CLEAR_FOG_START);
            event.setFarPlaneDistance(CLEAR_FOG_END);
            event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
            event.setCanceled(true);
        }
    }
}
