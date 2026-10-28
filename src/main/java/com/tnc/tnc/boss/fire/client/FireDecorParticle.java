package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 火柱的装饰粒子：细碎亮火星与暗橙红余烬。
 *
 * <p>与主体火苗分开管理，尺寸更小、寿命更短、数量只占主体的 10–15%。
 * 亮火星上升快、侧移很小；余烬上升慢、亮度渐衰。两者都不形成光斑或拖尾光柱。</p>
 */
public class FireDecorParticle extends TextureSheetParticle {

    public enum Kind {
        /** 黄色小亮点，从根部与柱体边缘向上窜出。 */
        SPARK(1.00F, 0.86F, 0.42F, 0.040F, 0.075F, 10, 18, 0.30D, 0.55D),
        /** 暗橙红余烬，外围与顶部点缀，亮度渐弱。 */
        EMBER(0.86F, 0.32F, 0.12F, 0.035F, 0.070F, 16, 26, 0.14D, 0.26D);

        final float r;
        final float g;
        final float b;
        final float minSize;
        final float maxSize;
        final int minLife;
        final int maxLife;
        final double minSpeed;
        final double maxSpeed;

        Kind(float r, float g, float b, float minSize, float maxSize,
             int minLife, int maxLife, double minSpeed, double maxSpeed) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
        }
    }

    private final SpriteSet sprites;
    private final int ownerId = FirePillarFx.spawningOwner();
    private final Kind kind;

    protected FireDecorParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.kind = kind;
        this.lifetime = kind.minLife + this.random.nextInt(kind.maxLife - kind.minLife + 1);
        this.quadSize = kind.minSize + this.random.nextFloat() * (kind.maxSize - kind.minSize);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 0.985F;
        this.roll = (this.random.nextFloat() - 0.5F) * 36.0F;
        this.oRoll = this.roll;
        this.setColor(kind.r, kind.g, kind.b);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        if (this.ownerId >= 0 && this.level.getEntity(this.ownerId) instanceof VolcanicTortoiseEntity boss
                && (boss.isTransitioning() || boss.isDeadOrDying())) {
            this.remove();
            return;
        }
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float t = this.age / (float) this.lifetime;
        if (this.kind == Kind.EMBER) {
            // 余烬逐渐变暗，最后消失，不做大范围飘散
            float k = 1.0F - t;
            this.setColor(this.kind.r * k, this.kind.g * k, this.kind.b * k);
            this.alpha = Mth.clamp(1.0F - (t - 0.35F) / 0.65F, 0.0F, 1.0F);
        } else {
            this.alpha = Mth.clamp(1.0F - (t - 0.55F) / 0.45F, 0.0F, 1.0F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Kind kind) {
        return (type, level, x, y, z, vx, vy, vz) ->
                new FireDecorParticle(level, x, y, z, vx, vy, vz, sprites, kind);
    }
}
