package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 重踏火环上的一颗粒子。
 *
 * <p>它不按速度自由飞行：每 tick 用火环实例共享的 {@code R(t)} 重算自己的位置，
 * 只在径向上加一个固定的小偏移形成火带宽度。因此所有粒子严格待在同一条环上，
 * 不会像普通 FLAME 那样留在旧半径往上飘。
 *
 * <p>渲染时不使用朝向相机的 billboard，而是画一张「沿半径法线」的竖直贴片：
 * 贴片平面由切线方向和竖直方向组成，顶部再沿半径向外倾倒 {@code lean} 格。
 * 这样从环外看过去是一道向外翻卷的火浪，而不是一圈立着的方片。
 */
public final class SlamFlameParticle extends TextureSheetParticle {

    /** 贴图帧数：assets/tnc/particles/slam_flame.json 里列出的张数。 */
    private static final int FRAMES = 4;
    /** 只打印一次“粒子确实被创建”，用于排查 provider 是否注册成功。 */
    private static boolean CREATED_LOGGED = false;

    private final SpriteSet sprites;
    private final SlamRingFx.Ring ring;
    private final double angle;
    private final double radialOffset;
    private final double height;
    private final double width;
    private final double lean;
    private final float phase;
    private final boolean mirror;
    private final int layer;
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
            this.height = 0.0D;
            this.width = 0.0D;
            this.lean = 0.0D;
            this.phase = 0.0F;
            this.mirror = false;
            this.layer = 0;
            this.lifetime = 1;
            this.setSprite(sprites.get(0, FRAMES));
            this.remove();
            return;
        }
        int index = Math.max(0, Math.min(SlamRingFx.TOTAL - 1, SlamRingFx.pendingIndex++));
        this.angle = ring.angle[index];
        this.radialOffset = ring.offset[index];
        this.height = ring.height[index];
        this.width = ring.width[index];
        this.lean = ring.lean[index];
        this.phase = ring.phase[index];
        this.mirror = ring.mirror[index];
        this.layer = ring.layer[index];
        this.quadSize = 1.0F;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = SlamRingFx.TICKS + 8;
        this.setSprite(sprites.get(frame(), FRAMES));
        // 内侧火尾偏橙红，中层偏亮，外层稍暗，三层叠在一起是一道有层次的波峰。
        switch (this.layer) {
            case 0 -> this.setColor(1.0F, 0.80F, 0.64F);
            case 1 -> this.setColor(1.0F, 0.96F, 0.90F);
            default -> this.setColor(1.0F, 0.90F, 0.78F);
        }
        this.setAlpha(0.0F);
        this.moveToRing(1.0F);
        if (!CREATED_LOGGED) {
            CREATED_LOGGED = true;
            org.slf4j.LoggerFactory.getLogger("TN-C/SlamRing")
                    .info("[TN-C SlamRing] particle created: ring={} h={} y={}",
                            this.ring != null, this.height, this.y);
        }
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
        this.setPos(px, this.groundY + 0.04D, pz);

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
        double y = top + 0.05D;
        // 不同版本/不同 mod 对 getHeight 的返回值约定不完全一致：把它夹在环心附近，
        // 既不会整圈埋进地面，也不会在悬崖边把火带拉到天上。
        if (y < ring.centerY - 0.60D || y > ring.centerY + 0.35D) {
            y = ring.centerY + 0.05D;
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

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (this.ring == null || this.ring.expired) {
            return;
        }
        float age = this.age + partialTick;
        double radius = this.ring.radius(age) + this.radialOffset;
        double px = this.ring.centerX + Math.cos(this.angle) * radius;
        double pz = this.ring.centerZ + Math.sin(this.angle) * radius;
        double py = this.groundY + 0.04D;

        // 贴片所在平面 = 切线方向 x 竖直方向；顶部沿半径向外倒。
        double rx = Math.cos(this.angle);
        double rz = Math.sin(this.angle);
        double tx = -rz;
        double tz = rx;
        double half = this.width * 0.5D;
        double h = this.height * (1.0D + 0.07D * Math.sin(age * 0.35D + this.phase * 6.2832D));
        double leanDist = this.lean;

        Vec3 cam = camera.getPosition();
        float ox = (float) (px - cam.x);
        float oy = (float) (py - cam.y);
        float oz = (float) (pz - cam.z);

        float tX = (float) (tx * half);
        float tZ = (float) (tz * half);
        float lX = (float) (rx * leanDist);
        float lZ = (float) (rz * leanDist);
        float topY = oy + (float) h;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        if (this.mirror) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        int light = this.getLightColor(partialTick);
        float r = this.rCol;
        float g = this.gCol;
        float b = this.bCol;
        float a = this.alpha;

        // 两个绕序各写一遍，避免不同渲染状态下的背面剔除把火带裁掉。
        this.quad(buffer, ox, oy, oz, tX, tZ, lX, lZ, topY, u0, u1, v0, v1,
                light, r, g, b, a * 0.86F, false);
        this.quad(buffer, ox, oy, oz, tX, tZ, lX, lZ, topY, u0, u1, v0, v1,
                light, r, g, b, a * 0.86F, true);

        // 贴地的一条亮带：竖直贴片从高处俯视会变成一条线，这块水平面负责
        // 让玩家在高处也能看出完整的圆环。三层各自贡献一点，叠出约 2 格宽的火带。
        float bandTop = v0 + (v1 - v0) * 0.66F;
        float outX = (float) (rx * 0.58D);
        float outZ = (float) (rz * 0.58D);
        float gy = oy + 0.005F;
        float groundAlpha = switch (this.layer) {
            case 0 -> 0.30F;
            case 1 -> 0.52F;
            default -> 0.26F;
        };
        this.groundQuad(buffer, ox, oz, gy, tX, tZ, outX, outZ,
                u0, u1, bandTop, v1, light, r, g, b, a * groundAlpha, false);
        this.groundQuad(buffer, ox, oz, gy, tX, tZ, outX, outZ,
                u0, u1, bandTop, v1, light, r, g, b, a * groundAlpha, true);
    }

    private void groundQuad(VertexConsumer buffer,
                            float ox, float oz, float y,
                            float tX, float tZ, float outX, float outZ,
                            float u0, float u1, float vTop, float vBottom,
                            int light, float r, float g, float b, float a,
                            boolean reverse) {
        float ax = ox - tX - outX;
        float az = oz - tZ - outZ;
        float bx = ox + tX - outX;
        float bz = oz + tZ - outZ;
        float cx = ox + tX + outX;
        float cz = oz + tZ + outZ;
        float dx = ox - tX + outX;
        float dz = oz - tZ + outZ;
        if (!reverse) {
            vertex(buffer, ax, y, az, u0, vBottom, light, r, g, b, a);
            vertex(buffer, bx, y, bz, u1, vBottom, light, r, g, b, a);
            vertex(buffer, cx, y, cz, u1, vTop, light, r, g, b, a);
            vertex(buffer, dx, y, dz, u0, vTop, light, r, g, b, a);
        } else {
            vertex(buffer, dx, y, dz, u0, vTop, light, r, g, b, a);
            vertex(buffer, cx, y, cz, u1, vTop, light, r, g, b, a);
            vertex(buffer, bx, y, bz, u1, vBottom, light, r, g, b, a);
            vertex(buffer, ax, y, az, u0, vBottom, light, r, g, b, a);
        }
    }

    private void quad(VertexConsumer buffer,
                      float ox, float oy, float oz,
                      float tX, float tZ, float lX, float lZ,
                      float topY,
                      float u0, float u1, float v0, float v1,
                      int light, float r, float g, float b, float a,
                      boolean reverse) {
        // 底部两个角
        float bx0 = ox - tX;
        float bz0 = oz - tZ;
        float bx1 = ox + tX;
        float bz1 = oz + tZ;
        // 顶部两个角（向外倾倒）
        float tx0 = ox - tX + lX;
        float tz0 = oz - tZ + lZ;
        float tx1 = ox + tX + lX;
        float tz1 = oz + tZ + lZ;

        if (!reverse) {
            vertex(buffer, bx0, oy, bz0, u0, v1, light, r, g, b, a);
            vertex(buffer, bx1, oy, bz1, u1, v1, light, r, g, b, a);
            vertex(buffer, tx1, topY, tz1, u1, v0, light, r, g, b, a);
            vertex(buffer, tx0, topY, tz0, u0, v0, light, r, g, b, a);
        } else {
            vertex(buffer, tx0, topY, tz0, u0, v0, light, r, g, b, a);
            vertex(buffer, tx1, topY, tz1, u1, v0, light, r, g, b, a);
            vertex(buffer, bx1, oy, bz1, u1, v1, light, r, g, b, a);
            vertex(buffer, bx0, oy, bz0, u0, v1, light, r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer buffer, float x, float y, float z,
                               float u, float v, int light,
                               float r, float g, float b, float a) {
        buffer.vertex(x, y, z).uv(u, v).color(r, g, b, a).uv2(light).endVertex();
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
