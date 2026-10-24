package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端粒子 provider 注册：火环与火球都使用专用贴图序列，不复用原版 FLAME。 */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TortoiseParticleClient {

    private TortoiseParticleClient() {
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TortoiseParticles.SLAM_FLAME.get(), SlamFlameParticle::provider);
        event.registerSpriteSet(TortoiseParticles.FIRE_CORE.get(),
                sprites -> FireBlobParticle.provider(sprites, FireBlobParticle.Layer.CORE));
        event.registerSpriteSet(TortoiseParticles.FIRE_MID.get(),
                sprites -> FireBlobParticle.provider(sprites, FireBlobParticle.Layer.MID));
        event.registerSpriteSet(TortoiseParticles.FIRE_EDGE.get(),
                sprites -> FireBlobParticle.provider(sprites, FireBlobParticle.Layer.EDGE));
        event.registerSpriteSet(TortoiseParticles.FIRE_CHARGE.get(),
                sprites -> FireBlobParticle.provider(sprites, FireBlobParticle.Layer.CHARGE));
        event.registerSpriteSet(TortoiseParticles.FIRE_PILLAR.get(), FirePillarParticle::provider);
        event.registerSpriteSet(TortoiseParticles.FIRE_SPARK.get(),
                sprites -> FireDecorParticle.provider(sprites, FireDecorParticle.Kind.SPARK));
        event.registerSpriteSet(TortoiseParticles.FIRE_EMBER.get(),
                sprites -> FireDecorParticle.provider(sprites, FireDecorParticle.Kind.EMBER));
    }
}
