package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 火柱用的单颗火舌粒子。
 *
 * <p>只负责“这一颗怎么升、怎么翻卷、什么颜色”，柱状轮廓由 {@link FirePillarFx}
 * 用受控的生成位置和速度场堆出来；粒子本身不决定柱子的高度和宽度。</p>
 *
 * <p>颜色随年龄从根部亮黄过渡到顶部深橙红，越往上越暗、越透，
 * 这样顶部会自然消散，而不是整根柱子一起消失。</p>
 */
public class FirePillarParticle extends TextureSheetParticle {

    private static final float MIN_SIZE = 0.055F;
    private static final float MAX_SIZE = 0.130F;
    private static final float TONGUE_CHANCE = 0.10F;
    private static final float TONGUE_MULTIPLIER = 1.35F;
    private static final float MAX_QUAD = 0.180F;
    private static final int MIN_LIFE = 28;
    private static final int MAX_LIFE = 38;

    private final SpriteSet sprites;
    private final float swirlPhase;
    private final float swirlAmount;

    protected FirePillarParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = MIN_LIFE + this.random.nextInt(MAX_LIFE - MIN_LIFE + 1);
        this.quadSize = MIN_SIZE + this.random.nextFloat() * (MAX_SIZE - MIN_SIZE);
        if (this.random.nextFloat() < TONGUE_CHANCE) {
            this.quadSize = Math.min(this.quadSize * TONGUE_MULTIPLIER, MAX_QUAD);
        }
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 0.975F;
        this.swirlPhase = this.random.nextFloat() * 6.2832F;
        this.swirlAmount = 0.0006F + this.random.nextFloat() * 0.0005F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);

        float t = this.age / (float) this.lifetime;
        // 径向小幅摆动 + 竖向翻卷，避免整根柱子是直线上升的均匀光筒
        double angle = this.swirlPhase + this.age * 0.16F;
        this.xd += Math.cos(angle) * this.swirlAmount * (t < 0.5F ? 0.55D : 0.20D);
        this.zd += Math.sin(angle) * this.swirlAmount * (t < 0.5F ? 0.55D : 0.20D);

        // 颜色：根部近白黄 → 金黄 → 橙 → 深橙红；顶部更透
        float[] c = colorAt(t);
        this.setColor(c[0], c[1], c[2]);
        this.alpha = t < 0.55F ? 1.0F : Mth.clamp(1.0F - (t - 0.55F) / 0.45F, 0.0F, 1.0F);
    }

    private static float[] colorAt(float t) {
        if (t < 0.35F) {
            float k = t / 0.35F;
            return new float[]{1.0F, Mth.lerp(k, 0.96F, 0.78F), Mth.lerp(k, 0.80F, 0.30F)};
        }
        if (t < 0.70F) {
            float k = (t - 0.35F) / 0.35F;
            return new float[]{Mth.lerp(k, 1.0F, 0.98F), Mth.lerp(k, 0.78F, 0.48F), Mth.lerp(k, 0.30F, 0.14F)};
        }
        float k = (t - 0.70F) / 0.30F;
        return new float[]{Mth.lerp(k, 0.98F, 0.78F), Mth.lerp(k, 0.48F, 0.24F), Mth.lerp(k, 0.14F, 0.09F)};
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
                new FirePillarParticle(level, x, y, z, vx, vy, vz, sprites);
    }
}
