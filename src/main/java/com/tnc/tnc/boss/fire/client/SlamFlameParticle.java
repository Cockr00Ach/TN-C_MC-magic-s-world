package com.tnc.tnc.boss.fire.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 重踏火环上的一颗粒子。
 *
 * <p>它不按速度自由飞行：每 tick 用火环实例共享的 {@code R(t)} 重算自己的位置，
 * 只在径向上加一个固定的小偏移形成火带宽度。因此所有粒子严格待在同一条环上，
 * 不会像普通 FLAME 那样留在旧半径往上飘。
 */
public final class SlamFlameParticle extends TextureSheetParticle {

    /** 贴图帧数：assets/tnc/particles/slam_flame.json 里列出的张数。 */
    private static final int FRAMES = 4;

    private final SpriteSet sprites;
    private final SlamRingFx.Ring ring;
    private final double angle;
    private final double radialOffset;
    private final double yOffset;
    private final float phase;
    private double groundY;

    SlamFlameParticle(SimpleParticleType type, ClientLevel level,
                      double x, double y, double z, double vx, double vy, double vz,
                      SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.ring = SlamRingFx.pending;
        this.groundY = y;
        if (this.ring == null) {
            // 只可能发生在别处手动 spawn 的时候：立刻消失，不留下无主火焰。
            this.angle = 0.0D;
            this.radialOffset = 0.0D;
            this.yOffset = 0.0D;
            this.phase = 0.0F;
            this.lifetime = 1;
            this.setSprite(sprites.get(0, FRAMES));
            this.remove();
            return;
        }
        int index = Math.max(0, Math.min(SlamRingFx.COUNT - 1, SlamRingFx.pendingIndex++));
        this.angle = ring.angle[index];
        this.radialOffset = ring.offset[index];
        this.yOffset = ring.height[index];
        this.phase = ring.phase[index];
        this.quadSize = ring.size[index];
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = SlamRingFx.TICKS + 8;
        this.setSprite(sprites.get(frame(), FRAMES));
        this.setColor(1.0F, 0.94F, 0.86F);
        this.setAlpha(0.0F);
        this.moveToRing(1.0F);
    }

    private int frame() {
        return Math.floorMod(this.age / 2 + Mth.floor(this.phase * FRAMES), FRAMES);
    }

    @Override
    public void tick() {
        super.tick();
        if (ring == null || ring.expired || this.age >= SlamRingFx.TICKS + 8) {
            this.remove();
            return;
        }
        // 贴图逐帧翻卷；只改贴图，不改整体高度，避免变成一堵越来越高的火墙。
        this.setSprite(sprites.get(frame(), FRAMES));
        this.moveToRing((float) this.age);
    }

    /** 把粒子放到 {@code R(age) + radialOffset} 的圆周上，并贴当前地表。 */
    private void moveToRing(float age) {
        double radius = ring.radius(age) + this.radialOffset;
        double px = ring.centerX + Math.cos(this.angle) * radius;
        double pz = ring.centerZ + Math.sin(this.angle) * radius;
        if ((int) age % 2 == 0) {
            this.groundY = groundAt(px, pz);
        }
        this.setPos(px, this.groundY + this.yOffset, pz);

        if (age < 2.0F) {
            this.setAlpha(age * 0.5F);
        } else {
            float fade = Mth.clamp((SlamRingFx.TICKS + 6.0F - age) / 8.0F, 0.0F, 1.0F);
            this.setAlpha(fade);
        }
    }

    private double groundAt(double x, double z) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        double y = top + 0.02D;
        // 悬崖或深坑：退回环心高度，避免把火带拉到天上或地下。
        if (Math.abs(y - ring.centerY) > 6.0D) {
            y = ring.centerY + 0.02D;
        }
        return y;
    }

    /** 火环不受光照影响，关光影/夜晚同样清晰。 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * 粒子 provider。角度/偏移在创建时从 {@link SlamRingFx#pending} 一次性分配，
     * 生成点就是服务端同步的环心。
     */
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
                new SlamFlameParticle(type, level, x, y, z, vx, vy, vz, sprites);
    }
}
