package com.tnc.tnc.boss.fire.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 火球/蓄力/命中用的小火苗粒子（billboard，始终朝向相机）。
 *
 * <p>每颗粒子只是一小簇火舌，靠数量与三维分布堆出火团，不再用一张大圆贴图。
 * 三层只差调色板、尺寸与寿命：核心小而亮、中层橙黄、外缘深橙红。
 * 渲染走 {@link ParticleRenderType#PARTICLE_SHEET_TRANSLUCENT}，整亮不受环境光影响。
 */
public class FireBlobParticle extends TextureSheetParticle {

    public enum Layer {
        CORE(1.00F, 0.96F, 0.80F, 0.055F, 0.090F, 4, 7),
        MID(1.00F, 0.68F, 0.26F, 0.075F, 0.130F, 5, 9),
        EDGE(0.94F, 0.38F, 0.12F, 0.100F, 0.165F, 4, 8),
        CHARGE(1.00F, 0.86F, 0.50F, 0.055F, 0.120F, 3, 6);

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

    /** 少数粒子放大成火舌，避免整团全是同样的小点。 */
    private static final float TONGUE_CHANCE = 0.12F;
    private static final float TONGUE_MULTIPLIER = 1.35F;
    private static final float MAX_QUAD = 0.22F;

    private final SpriteSet sprites;
    private final float phase;

    protected FireBlobParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites, Layer layer) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = layer.minLife + this.random.nextInt(layer.maxLife - layer.minLife + 1);
        float size = Mth.lerp(this.random.nextFloat(), layer.minSize, layer.maxSize);
        if (this.random.nextFloat() < TONGUE_CHANCE) {
            size *= TONGUE_MULTIPLIER;
        }
        this.quadSize = Math.min(size, MAX_QUAD);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 0.90F;
        this.phase = this.random.nextFloat() * 6.2832F;
        // 每颗歪一点，堆起来才不像整齐的圆盘
        this.roll = (this.random.nextFloat() - 0.5F) * 46.0F;
        this.oRoll = this.roll;
        this.setColor(layer.r, layer.g, layer.b);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float left = 1.0F - (float) this.age / (float) this.lifetime;
        // 轻微闪动 + 淡出，避免整团糊成一块纯色
        float flicker = 0.86F + 0.14F * Mth.sin((this.age + this.phase) * 1.7F);
        this.alpha = Mth.clamp(left * 1.8F, 0.0F, 1.0F) * flicker;
        // 小火苗自己往上窜一点，速度很小，整体仍聚在一起
        this.yd += 0.0009D;
        this.xd *= 0.97D;
        this.zd *= 0.97D;
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
