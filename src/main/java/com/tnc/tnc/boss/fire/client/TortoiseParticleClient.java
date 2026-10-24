package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端粒子 provider 注册：重踏火环使用专用贴图序列，不复用原版 FLAME。 */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TortoiseParticleClient {

    private TortoiseParticleClient() {
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TortoiseParticles.SLAM_FLAME.get(), SlamFlameParticle::provider);
    }
}
