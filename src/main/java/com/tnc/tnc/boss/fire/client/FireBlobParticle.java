package com.tnc.tnc.boss.fire.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 火球/蓄力用的球面火团粒子（billboard，始终朝向相机）。
 *
 * <p>三层的区别只在调色板、尺寸与寿命：核心小且亮、外缘大且深，叠起来形成球体轮廓。
 * 渲染走 {@link ParticleRenderType#PARTICLE_SHEET_TRANSLUCENT}，整亮不受环境光影响，
 * 关闭光影时同样清楚。
 */
public class FireBlobParticle extends TextureSheetParticle {

    public enum Layer {
        CORE(1.00F, 0.97F, 0.82F, 0.30F, 0.52F, 7, 11),
        MID(1.00F, 0.72F, 0.30F, 0.42F, 0.72F, 6, 9),
        EDGE(0.95F, 0.42F, 0.14F, 0.55F, 0.85F, 4, 7),
        CHARGE(1.00F, 0.86F, 0.52F, 0.30F, 0.55F, 3, 5);

        final float r;
        final float g;
        final float b;
        final float minSize;
        final float maxSize;
        final int minLife;
        final int maxLife;

        Layer(float r, float g, float b, float minSize, float maxSize, int minLife, int maxLife) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.minLife = minLife;
            this.maxLife = maxLife;
        }
    }

    private final SpriteSet sprites;

    protected FireBlobParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites, Layer layer) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = layer.minLife + this.random.nextInt(layer.maxLife - layer.minLife + 1);
        this.quadSize = Mth.lerp(this.random.nextFloat(), layer.minSize, layer.maxSize);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 0.92F;
        this.setColor(layer.r, layer.g, layer.b);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float left = 1.0F - (float) this.age / (float) this.lifetime;
        this.alpha = Mth.clamp(left * 1.6F, 0.0F, 1.0F);
        // 轻微收缩，避免整团越飘越散
        this.quadSize *= 0.985F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Layer layer) {
        return (type, level, x, y, z, vx, vy, vz) -> new FireBlobParticle(level, x, y, z, vx, vy, vz, sprites, layer);
    }
}
