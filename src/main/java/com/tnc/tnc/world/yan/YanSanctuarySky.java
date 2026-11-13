package com.tnc.tnc.world.yan;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YanSanctuarySky {
    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(YanSanctuary.ID, new DimensionSpecialEffects.OverworldEffects() {
            @Override public float getCloudHeight() { return Float.NaN; }
            @Override
            public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                        double camX, double camY, double camZ, Matrix4f projectionMatrix) {
                return true;
            }
        });
    }

    private YanSanctuarySky() {}
}
