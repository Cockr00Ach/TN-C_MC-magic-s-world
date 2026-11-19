package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tiered charge geometry for the lightning school and the corresponding release trace. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class LightningCastFx {

    /** 和别的雷系几何共用加色层（白芯会被 alpha 压住，不要走 DARK ✗）。 */
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;

    /** 屏幕白闪（0..1，按 tick 衰减）。 */
    private static float whiteFlash;
    /** 淡蓝黑暗角（雷系不该压暗，所以很轻）。 */
    private static float blueVignette;

    private LightningCastFx() {
    }

    public static float localCharge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != owner) return 0;
        CastState state = CASTS.get(mc.player.getUUID());
        return state == null ? 0 : state.ratio;
    }

    // ==================================================================
    //  Tick
    // ==================================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != owner) {
            CASTS.clear();
            BURSTS.clear();
            owner = level;
            whiteFlash = 0;
            blueVignette = 0;
        }
        if (level == null || mc.player == null) {
            whiteFlash = 0;
            blueVignette = 0;
            return;
        }
        long now = level.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        Set<UUID> seen = new HashSet<>();
        float vignette = 0;

        for (Player player : level.players()) {
            double distance = player.distanceTo(mc.player);
            if (distance > 64) continue;
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            seen.add(player.getUUID());
            CastState state = CASTS.computeIfAbsent(player.getUUID(), id -> new CastState(now));
            state.update(level, player, info, now, minimal || distance > 24);
            if (player == mc.player && !firstPerson) {
                vignette = Math.max(vignette, blueVignette(info.tier(), info.ratio()));
            }
        }

        // 读条结束 → 释放：雷环外扩 ＋ 白闪 ＋ 震屏 ✓
        Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            CastState state = entry.getValue();
            if (now - state.lastSeen <= 3 && state.maxRatio > .3F) {
                boolean local = entry.getKey().equals(mc.player.getUUID());
                spawnBurst(level, state, Math.min(1, state.maxRatio), minimal, local && !firstPerson);
            }
            it.remove();
        }
        CASTS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 40);

        blueVignette += (vignette - blueVignette) * (vignette > blueVignette ? .30F : .10F);
        if (blueVignette < .004F) {
            blueVignette = 0;
        }
        whiteFlash = Math.max(0, whiteFlash - .055F);
        if (whiteFlash < .01F) {
            whiteFlash = 0;
        }
        BURSTS.removeIf(b -> now - b.start > 14L);
    }

    private static void spawnBurst(ClientLevel level, CastState state, float strength, boolean minimal, boolean screenCue) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, strength, level.getGameTime()));
        if (screenCue && state.tier >= 4) {
            whiteFlash = Math.max(whiteFlash, .17F * strength);
            TNSpellClientVisuals.pulseShake(.1F * strength);
        }
        int puffs = (int) ((4 + state.tier * 3) * strength);
        if (minimal) {
            puffs /= 2;
        }
        for (int i = 0; i < puffs; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .12 + RANDOM.nextDouble() * .40;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    state.x, state.y + .10 + RANDOM.nextDouble() * .9, state.z,
                    Math.cos(a) * speed, .08 + RANDOM.nextDouble() * .35, Math.sin(a) * speed);
        }
    }

    // ==================================================================
    //  世界渲染
    // ==================================================================

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != owner || CASTS.isEmpty() && BURSTS.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        double time = now + partial;

        VertexConsumer vc = BUFFERS.getBuffer(LightningRenderTypes.BOLT);
        boolean any = false;

        for (var entry : CASTS.entrySet()) {
            CastState state = entry.getValue();
            Vec3 base = new Vec3(state.x, state.y, state.z);
            if (base.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            boolean near = mc.options.getCameraType().isFirstPerson()
                    && mc.player != null && entry.getKey().equals(mc.player.getUUID());
            ritual(vc, state, base, camera, time, near);
            any = true;
        }
        for (Burst burst : BURSTS) {
            double age = now - burst.start + partial;
            if (age < 0 || age > 14) {
                continue;
            }
            Vec3 at = new Vec3(burst.x, burst.y, burst.z);
            if (at.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            var stage = LightningCastGlyphs.Stage.of(burst.tier);
            LightningCastGlyphs.release(vc, at, camera, stage, age / 14D, burst.strength, burst.start);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(LightningRenderTypes.BOLT);
        }
    }

    private static void ritual(VertexConsumer out, CastState state, Vec3 base, Vec3 camera,
                               double time, boolean firstPerson) {
        var stage = LightningCastGlyphs.Stage.of(state.tier);
        float charge = .18F + .82F * smooth(state.ratio);
        LightningCastGlyphs.ground(out, base, camera, stage, charge, time, firstPerson);
        if (state.tier >= 4 && !firstPerson) {
            LightningCastGlyphs.halo(out, base.add(0, 3.34 + .37 * (state.tier - 4), 0),
                    camera, stage, time, charge);
        }

        for (Circle circle : state.circles) {
            double age = time - circle.born;
            if (age < 0 || age > circle.life
                    || firstPerson && circle.center.distanceToSqr(camera) < 9) continue;
            float alpha = Math.min(1F, (float) age / 5F)
                    * (1 - smooth(Mth.clamp((float) ((age / circle.life - .78) / .22), 0, 1)));
            if (alpha < .01F) continue;
            double radius = circle.radius * (.68 + .32 * Mth.clamp(age / 7, 0, 1));
            LightningCastGlyphs.seal(out, circle.center, circle.normal, camera, stage, radius,
                    time * .065 + circle.seed % 200 * .025, alpha * charge);
            if (state.tier >= 3 && !firstPerson && age > 4) {
                arc(out, circle.center, base.add(0, 1.32, 0),
                        circle.seed + (long) (time / 4) * 61, .028F, alpha * charge * .68F, stage.accent, camera);
            }
            flash(out, circle.center, .1F + .12F * charge, alpha * charge * .57F, stage.main, camera);
        }

        for (Dragon dragon : state.dragons) {
            double age = time - dragon.born;
            if (age < 0 || age > dragon.life) continue;
            float progress = (float) (age / dragon.life);
            Vec3 at = bezier(dragon.from, dragon.ctrl, dragon.to, progress);
            if (firstPerson && at.distanceToSqr(camera) < 10) continue;
            Vec3 ahead = bezier(dragon.from, dragon.ctrl, dragon.to, Math.min(1, progress + .06F));
            Vec3 direction = ahead.subtract(at);
            if (direction.lengthSqr() < 1e-6) direction = dragon.to.subtract(dragon.from);
            float alpha = Math.min(1, progress * 6) * (1 - smooth(Mth.clamp((progress - .82F) / .18F, 0, 1)));
            if (alpha > .01F) {
                smallDragon(out, at, direction, dragon.scale, alpha, (float) age, dragon.seed, stage.main, camera);
            }
        }

        if (!firstPerson) {
            for (Impact impact : state.impacts) {
                float alpha = (float) (1 - (time - impact.born) / 8);
                if (alpha > 0 && alpha <= 1) {
                    flash(out, impact.at, .10F + .24F * alpha, alpha * .62F, stage.main, camera);
                }
            }
        }

        int sparks = firstPerson ? 2 : state.tier + 1;
        long snap = (long) (time / 3);
        for (int i = 0; i < sparks; i++) {
            double angle = i * Math.PI * 2 / sparks + snap * 1.23;
            double radius = stage.radius * (.78 + .12 * Math.sin(snap * 2.41 + i));
            Vec3 from = base.add(Math.cos(angle) * radius, .09, Math.sin(angle) * radius);
            Vec3 to = from.add(Math.cos(angle + .4) * .30, .16 + charge * .35, Math.sin(angle + .4) * .30);
            arc(out, from, to, state.seed + snap * 31 + i, .023F, .69F * charge, stage.main, camera);
        }
    }

    /**
     * 一条<b>小雷龙</b>（局部坐标：+Z = 前进方向、+Y = 上，体长约 0.75 格 × scale）。
     *
     * <p>用线勾：龙头（白闪 ＋ 双角 ＋ 吻部）＋ 6 节脊线（带摆动）＋ 两对翼线 ＋ 四条腿 ＋
     * 分叉尾 ＋ 3 条绕身的电弧套。体型小（scale ≈ 0.30）⇒ 远看就是"一条电光串成的龙"✓，
     * 和 `TNLightningDragonRenderer` 里那条 45 格长的大龙是同一套语言、不同尺寸。
     */
    private static void smallDragon(VertexConsumer vc, Vec3 at, Vec3 dir, float scale,
                                    float alpha, float age, long seed, int theme, Vec3 cam) {
        Vector3f z = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (z.lengthSquared() < 1e-8F) {
            z.set(0, 0, 1);
        }
        z.normalize();
        Vector3f upGuess = Math.abs(z.y) > .9F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f y = new Vector3f(upGuess).sub(new Vector3f(z).mul(z.dot(upGuess))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();

        float tx = (float) (at.x - cam.x);
        float ty = (float) (at.y - cam.y);
        float tz = (float) (at.z - cam.z);
        Matrix4f m = new Matrix4f(
                x.x, x.y, x.z, 0,
                y.x, y.y, y.z, 0,
                z.x, z.y, z.z, 0,
                tx, ty, tz, 1);
        double camX = -(x.x * tx + x.y * ty + x.z * tz);
        double camY = -(y.x * tx + y.y * ty + y.z * tz);
        double camZ = -(z.x * tx + z.y * ty + z.z * tz);

        float s = scale;
        float w = .016F * s / .3F;

        // ---- 脊线：从吻部到尾巴，6 节，越往后越细、带一点游动 ✓ ----
        double sway = Math.sin(age * .55D) * .035D;
        double[][] spine = {
                {0, .02, .34},
                {.035 + sway * .4, .045, .22},
                {-.040 + sway, .055, .09},
                {.045 + sway * .6, .05, -.04},
                {-.030 - sway * .5, .035, -.17},
                {.020, .020, -.28},
                {0, .006, -.38}
        };
        for (int i = 0; i < spine.length - 1; i++) {
            double[] p = spine[i];
            double[] q = spine[i + 1];
            float width = (float) (w * (1.0D - i * .09D));
            LightningBoltGeometry.arc(vc, m, p[0] * s, p[1] * s, p[2] * s,
                    q[0] * s, q[1] * s, q[2] * s,
                    seed + i, width, alpha, theme, camX, camY, camZ);
        }

        // ---- 头：白闪 ＋ 双角 ＋ 上下颚 ＋ 两颗眼 ✓ ----
        LightningBoltGeometry.billboardFlash(vc, m, 0, .04D * s, .35D * s,
                .075F * s, theme, alpha * .85F, camX, camY, camZ);
        LightningBoltGeometry.arc(vc, m, -.035D * s, .05D * s, .34D * s,
                -.085D * s, .17D * s, .24D * s, seed + 20L, w, alpha, theme, camX, camY, camZ);
        LightningBoltGeometry.arc(vc, m, .035D * s, .05D * s, .34D * s,
                .085D * s, .17D * s, .24D * s, seed + 21L, w, alpha, theme, camX, camY, camZ);
        // 上颚 / 下颚：两条从吻部往后收的线 ⇒ 有个"嘴" ✓
        LightningBoltGeometry.arc(vc, m, 0, .045D * s, .40D * s,
                .045D * s, .0, .30D * s, seed + 22L, w, alpha, theme, camX, camY, camZ);
        LightningBoltGeometry.arc(vc, m, 0, .045D * s, .40D * s,
                -.045D * s, .0, .30D * s, seed + 23L, w, alpha, theme, camX, camY, camZ);
        LightningBoltGeometry.point(vc, m, -.045D * s, .075D * s, .355D * s,
                .020F * s, alpha, theme, camX, camY, camZ);
        LightningBoltGeometry.point(vc, m, .045D * s, .075D * s, .355D * s,
                .020F * s, alpha, theme, camX, camY, camZ);

        // ---- 翼：两对扫向后方的弧（小体型不用真膜，线就够）✓ ----
        for (int side = -1; side <= 1; side += 2) {
            double sx = side;
            LightningBoltGeometry.arc(vc, m, sx * .03D * s, .06D * s, .12D * s,
                    sx * .30D * s, .20D * s, -.02D * s, seed + 30L + side, w, alpha * .9F,
                    theme, camX, camY, camZ);
            LightningBoltGeometry.arc(vc, m, sx * .30D * s, .20D * s, -.02D * s,
                    sx * .20D * s, .05D * s, -.14D * s, seed + 32L + side, w, alpha * .8F,
                    theme, camX, camY, camZ);
            // 后翼（小一号）
            LightningBoltGeometry.arc(vc, m, sx * .03D * s, .05D * s, -.03D * s,
                    sx * .22D * s, .13D * s, -.16D * s, seed + 34L + side, w * .85F, alpha * .75F,
                    theme, camX, camY, camZ);
        }

        // ---- 四条腿：往肚子下面收的小折线（飞行时收起来 ✓）----
        double[][] legs = {{.05, .12}, {.05, -.02}, {-.05, .12}, {-.05, -.02}};
        for (int i = 0; i < legs.length; i++) {
            double lx = legs[i][0] * s;
            double lz = legs[i][1] * s;
            LightningBoltGeometry.arc(vc, m, lx, .02D * s, lz,
                    lx * 1.6D, -.07D * s, lz - .03D * s, seed + 40L + i, w * .8F, alpha * .8F,
                    theme, camX, camY, camZ);
        }

        // ---- 尾：分叉 ✓ ----
        LightningBoltGeometry.arc(vc, m, 0, .006D * s, -.38D * s,
                -.06D * s, -.02D * s, -.47D * s, seed + 50L, w * .8F, alpha * .85F,
                theme, camX, camY, camZ);
        LightningBoltGeometry.arc(vc, m, 0, .006D * s, -.38D * s,
                .06D * s, .02D * s, -.47D * s, seed + 51L, w * .8F, alpha * .85F,
                theme, camX, camY, camZ);

        // ---- 绕身电弧套：3 条，换种子 ⇒ 电光在龙身上爬 ✓ ----
        for (int i = 0; i < 3; i++) {
            double a0 = age * .5D + i * 2.1D;
            double a1 = a0 + 2.4D;
            LightningBoltGeometry.arc(vc, m,
                    Math.cos(a0) * .10D * s, .05D * s + Math.sin(a0) * .05D * s, .05D * s,
                    Math.cos(a1) * .10D * s, .05D * s + Math.sin(a1) * .05D * s, -.10D * s,
                    seed + 60L + (long) (age / 3) * 7L + i, w * .7F, alpha * .8F,
                    theme, camX, camY, camZ);
        }
    }

    /** 世界坐标 → 相机相对的一小段电弧 ✓。 */
    private static void arc(VertexConsumer out, Vec3 from, Vec3 to, long seed, float width, float alpha,
                            int theme, Vec3 camera) {
        LightningBoltGeometry.arc(out, IDENTITY,
                from.x - camera.x, from.y - camera.y, from.z - camera.z,
                to.x - camera.x, to.y - camera.y, to.z - camera.z,
                seed, width, alpha, theme, 0, 0, 0);
    }

    private static void flash(VertexConsumer out, Vec3 at, float radius, float alpha, int theme, Vec3 camera) {
        LightningBoltGeometry.billboardFlash(out, IDENTITY,
                at.x - camera.x, at.y - camera.y, at.z - camera.z, radius, theme, alpha, 0, 0, 0);
    }

    private static Vec3 bezier(Vec3 from, Vec3 ctrl, Vec3 to, float f) {
        float g = 1 - f;
        return new Vec3(
                g * g * from.x + 2 * g * f * ctrl.x + f * f * to.x,
                g * g * from.y + 2 * g * f * ctrl.y + f * f * to.y,
                g * g * from.z + 2 * g * f * ctrl.z + f * f * to.z);
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float blueVignette(int tier, float p) {
        float rise = Mth.clamp(p * 1.2F, 0, 1);
        return (tier <= 2 ? .10F : tier == 3 ? .18F : tier == 4 ? .26F : .34F) * rise;
    }

    // ==================================================================
    //  屏幕：短白闪 ＋ 淡蓝黑暗角（不压暗，雷不是暗系 ✓）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (whiteFlash <= .01F && blueVignette <= .004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (blueVignette > .004F) {
            int alpha = (int) (blueVignette * 44);
            if (alpha > 1) {
                int top = (int) (height * .30F);
                int bottom = (int) (height * .70F);
                event.getGuiGraphics().fillGradient(0, 0, width, top,
                        (alpha << 24) | 0x07182E, 0);
                event.getGuiGraphics().fillGradient(0, bottom, width, height, 0,
                        (alpha << 24) | 0x07182E);
                int side = (int) (width * .12F);
                for (int s = 0; s < 4; s++) {
                    int x0 = side * s / 4;
                    int x1 = side * (s + 1) / 4;
                    int aa = alpha * (4 - s) / 4;
                    if (aa <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, (aa << 24) | 0x0A2440);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height,
                            (aa << 24) | 0x0A2440);
                }
            }
        }
        if (whiteFlash > .01F) {
            int alpha = (int) (whiteFlash * 58);
            if (alpha > 1) {
                event.getGuiGraphics().fill(0, 0, width, height, (alpha << 24) | 0xEAF6FF);
            }
        }
    }

    // ==================================================================
    //  状态
    // ==================================================================

    private record CastInfo(int tier, float ratio) {
    }

    private record Burst(double x, double y, double z, int tier, float strength, long start) {
    }

    private static final class Circle {
        final Vec3 center;
        final Vec3 normal;
        final double radius;
        final long born;
        final long life;
        final long seed;
        boolean emitted;

        Circle(Vec3 center, Vec3 normal, double radius, long born, long life, long seed) {
            this.center = center;
            this.normal = normal;
            this.radius = radius;
            this.born = born;
            this.life = life;
            this.seed = seed;
        }
    }

    private static final class Dragon {
        final Vec3 from;
        final Vec3 ctrl;
        final Vec3 to;
        final long born;
        final long life;
        final long seed;
        final float scale;

        Dragon(Vec3 from, Vec3 ctrl, Vec3 to, long born, long life, long seed, float scale) {
            this.from = from;
            this.ctrl = ctrl;
            this.to = to;
            this.born = born;
            this.life = life;
            this.seed = seed;
            this.scale = scale;
        }
    }

    private record Impact(Vec3 at, long born) {}

    private static final class CastState {
        int tier = 1;
        float ratio;
        float maxRatio;
        double x, y, z;
        long lastSeen;
        long startTick;
        final long seed;
        int spawned;
        int dragonSeq;
        final List<Circle> circles = new ArrayList<>();
        final List<Dragon> dragons = new ArrayList<>();
        final List<Impact> impacts = new ArrayList<>();

        CastState(long now) {
            this.startTick = now;
            this.seed = RANDOM.nextLong() & 0x7FFFFFFFL;
        }

        void update(ClientLevel level, Entity caster, CastInfo info, long now, boolean minimal) {
            if (now - this.lastSeen > 2L || this.ratio - info.ratio() > .35F || this.tier != info.tier()) {
                this.spawned = 0;
                this.dragonSeq = 0;
                this.circles.clear();
                this.dragons.clear();
                this.impacts.clear();
                this.maxRatio = 0;
                this.startTick = now;
            }
            this.tier = info.tier();
            this.ratio = info.ratio();
            this.maxRatio = Math.max(this.maxRatio, info.ratio());
            this.x = caster.getX();
            this.y = caster.getY();
            this.z = caster.getZ();
            this.lastSeen = now;

            var stage = LightningCastGlyphs.Stage.of(this.tier);
            for (int i = this.spawned; i < stage.seals; i++) {
                float at = .13F + i * .13F;
                if (this.ratio < at) {
                    break;
                }
                spawnCircle(level, caster, i, minimal);
                this.spawned = i + 1;
            }

            int dragonLimit = this.tier == 5 ? 2 : this.tier >= 3 ? 1 : 0;
            for (Circle circle : this.circles) {
                if (this.dragonSeq >= dragonLimit) break;
                if (!circle.emitted && (now >= circle.born + circle.life * 32L / 100L || this.ratio > .64F)) {
                    circle.emitted = true;
                    spawnDragon(level, caster, circle, minimal);
                }
            }
            this.dragons.removeIf(dragon -> {
                if (now < dragon.born + dragon.life) return false;
                this.impacts.add(new Impact(dragon.to, now));
                return true;
            });
            if (!minimal) {
                for (Dragon dragon : this.dragons) {
                    if (now - dragon.born < 3L) {
                        Vec3 at = dragon.from;
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, .02D, 0);
                    }
                }
            }
            this.impacts.removeIf(i -> now > i.born + 10L);
        }

        private long estimateLife(long now) {
            double elapsed = Math.max(1.0D, now - this.startTick + 1.0D);
            double total = Math.max(16.0D, elapsed / Math.max(.06F, this.ratio));
            double remain = Math.max(6.0D, total * (1.0D - this.ratio));
            return (long) Mth.clamp(remain * 1.3D + 8.0D, 14.0D, 48.0D);
        }

        private void spawnCircle(ClientLevel level, Entity caster, int index, boolean minimal) {
            var stage = LightningCastGlyphs.Stage.of(this.tier);
            double radius = 2.8 + stage.radius * .72 + index * .24;
            double angle = Math.toRadians(caster.getYRot()) + this.seed % 90 * .02
                    + index * Math.PI * 2 / stage.seals;
            Vec3 center = caster.position().add(Math.cos(angle) * radius, .12 + index * 1.27,
                    Math.sin(angle) * radius);
            Vec3 normal = switch (index) {
                case 0 -> new Vec3(.24 * Math.cos(angle), 1, .24 * Math.sin(angle)).normalize();
                case 1 -> new Vec3(Math.cos(angle), .25, Math.sin(angle)).normalize();
                default -> new Vec3(Math.cos(angle) * .65, .74, Math.sin(angle) * .65).normalize();
            };
            long now = level.getGameTime();
            // 阵本身也放大：0.46→0.85（t5 能到 1.3 格半径）—— 远看才有"法阵"的量感 ✓
            // 再乘一个随机大小（0.85~1.18）：三张阵不一般大，读起来才不是"模具刻的"✓
            double sizeJitter = .85D + RANDOM.nextDouble() * .33D;
            this.circles.add(new Circle(center, normal, (.85D + .09D * this.tier) * sizeJitter,
                    now, estimateLife(now), this.seed + 400L + index * 37L));

            if (!minimal) {
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI * 2 / 8;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            center.x + Math.cos(ang) * .25D, center.y,
                            center.z + Math.sin(ang) * .25D,
                            Math.cos(ang) * .06D, .03D, Math.sin(ang) * .06D);
                }
            }
        }

        private void spawnDragon(ClientLevel level, Entity caster, Circle circle, boolean minimal) {
            Vec3 chest = new Vec3(caster.getX(), caster.getY() + caster.getBbHeight() * .62D, caster.getZ());
            // 起点：阵心往外挪一点（"窜出来"而不是"原地出现"✓）
            Vec3 from = circle.center.add(circle.normal.scale(.18D));
            Vec3 mid = from.add(chest).scale(.5D);
            double side = RANDOM.nextDouble() * Math.PI * 2;
            // 弧线抬得更高：远距离要"绕一道"才好看，直线过去像子弹 ✗
            Vec3 ctrl = mid.add(Math.cos(side) * (.95D + RANDOM.nextDouble() * .85D),
                    .85D + RANDOM.nextDouble() * .95D,
                    Math.sin(side) * (.95D + RANDOM.nextDouble() * .85D));
            // ★ 龙也放大：0.28 → 0.52（t5 约 0.77，体长约 1.9 格），飞 4~6 格远也看得清 ✓
            this.dragons.add(new Dragon(from, ctrl, chest, level.getGameTime(),
                    12L + this.tier, this.seed + 900L + (this.dragonSeq++) * 61L,
                    .52F + .09F * this.tier));

            if (!minimal) {
                for (int i = 0; i < 5; i++) {
                    double ang = i * Math.PI * 2 / 5;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z,
                            Math.cos(ang) * .12D, (RANDOM.nextDouble() - .2D) * .12D,
                            Math.sin(ang) * .12D);
                }
            }
        }
    }

    // ==================================================================
    //  读条扫描：按学派判雷系
    // ==================================================================

    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/lightningfx")
                        .info("TN-C/lightningfx: spell_engine absent -> lightning cast FX idle");
            }
            return null;
        }
        if (scanBroken) {
            return null;
        }
        try {
            return scanUnsafe(player);
        } catch (Throwable t) {
            scanBroken = true;
            if (!scanErrorLogged) {
                scanErrorLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/lightningfx")
                        .warn("TN-C/lightningfx: cast scan failed, lightning cast FX disabled ({})",
                                t.toString());
            }
            return null;
        }
    }

    private static CastInfo scanUnsafe(Player player) {
        if (!(player instanceof net.spell_engine.internals.casting.SpellCasterEntity caster)) {
            return null;
        }
        net.spell_engine.internals.casting.SpellCast.Process process = caster.getSpellCastProcess();
        if (process == null || process.spell() == null) {
            return null;
        }
        net.spell_engine.api.spell.Spell spell = process.spell();
        if (!isLightning(spell)) {
            return null;
        }
        int tier = Mth.clamp(spell.learn == null ? 1 : spell.learn.tier, 1, 5);
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0, 1);
        return new CastInfo(tier, ratio);
    }

    /** 雷系判定：学派 id（{@code spell_power:lightning}）为主，组名兜底 ✓。 */
    private static boolean isLightning(net.spell_engine.api.spell.Spell spell) {
        if (spell.school != null && spell.school.id != null
                && spell.school.id.getPath().toLowerCase(Locale.ROOT).contains("lightning")) {
            return true;
        }
        return spell.group != null && spell.group.toLowerCase(Locale.ROOT).contains("lightning");
    }
}
