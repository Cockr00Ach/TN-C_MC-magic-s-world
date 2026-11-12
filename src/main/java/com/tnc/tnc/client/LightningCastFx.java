package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
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

/**
 * <b>雷系读条前戏（2026-11-06）</b> —— 作者：「雷法的前戏你也可以优化做做，我希望是周围
 * 随机出现三个不同角度的小法阵，然后跑出我们的小雷龙飞进玩家的身体，脚边出现小闪电这样」✓
 *
 * <h2>三件事，一一对上</h2>
 * <ol>
 *   <li><b>三个小法阵</b>：固定 <b>3</b> 张（不是按档位加数量），位置绕身随机、<b>朝向随机</b>
 *       —— 平贴 / 立着朝外 / 斜插，正好三种角度 ✓；</li>
 *   <li><b>小雷龙</b>：每张阵亮一会儿就"开"，一条<b>线勾的小雷龙</b>（`scale` ≈ 0.16 格长）
 *       从阵心窜出来、沿弧线飞进施法者胸口；入体那一下胸口爆一圈电，t4+ 一张阵能放两条 ✓；</li>
 *   <li><b>脚边小闪电</b>：读条全程脚下一圈小雷网 ＋ 不断跳的短电弧（每 2~3 tick 换一次位置，
 *       像静电在脚边爬），越接近释放越密 ✓。</li>
 * </ol>
 *
 * <h2>额外加的</h2>
 * <ul>
 *   <li>头顶一颗<b>聚雷球</b>：3 条电弧往球心收，随进度变大（t2+ 才明显）；</li>
 *   <li>身侧<b>爆电</b>：从躯干往斜上方炸出去的短弧，每几 tick 换一批；</li>
 *   <li>释放：脚下雷环外扩 ＋ 白闪 ＋ 震屏；</li>
 *   <li>屏幕：极短的白闪（入体/释放）＋ 很淡的蓝黑暗角，<b>不压暗画面</b>（雷不是暗系）✓。</li>
 * </ul>
 *
 * <h2>为什么用学派判定</h2>
 * 雷系五条链（雷击／雷速／雷场／雷球／登神）在数据文件里不一定有 {@code spell.group}
 * （暗系有、雷系是"零散法术＋机制"），所以这里按 <b>{@code spell.school}</b> 判：
 * {@code spell_power:lightning} 命中即算雷系 —— 以后加新雷法术也自动吃到 ✓。
 *
 * <h2>为什么走 {@code BOLT} 层</h2>
 * 雷是<b>加色</b>的（白芯＋主题色外沿），必须 {@link LightningRenderTypes#BOLT}；
 * 黑线那种走 {@code DARK} 层的做法在这里会把雷画没 ✗（§9/§10 的反面）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class LightningCastFx {

    /** 三个小法阵：固定三张（作者原话"三个"）✓。 */
    private static final int CIRCLES = 3;

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

        Set<UUID> seen = new HashSet<>();
        float vignette = 0;

        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            seen.add(player.getUUID());
            CastState state = CASTS.computeIfAbsent(player.getUUID(), id -> new CastState(now));
            state.update(level, player, info, now, minimal);

            double distance = player == mc.player ? 0 : player.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 32);
            if (player == mc.player) {
                vignette = Math.max(vignette, blueVignette(info.tier(), info.ratio()));
            } else if (falloff > 0) {
                vignette = Math.max(vignette, blueVignette(info.tier(), info.ratio())
                        * (float) falloff * .3F);
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
                spawnBurst(level, state, Math.min(1, state.maxRatio), minimal);
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

    private static void spawnBurst(ClientLevel level, CastState state, float strength, boolean minimal) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, strength, level.getGameTime()));
        whiteFlash = Math.max(whiteFlash, .55F * strength);
        TNSpellClientVisuals.pulseShake((.10F + .04F * state.tier) * strength);
        int puffs = (int) ((12 + state.tier * 8) * strength);
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

        for (CastState state : CASTS.values()) {
            Vec3 base = new Vec3(state.x, state.y, state.z);
            if (base.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            ritual(vc, state, base, camera, time, partial);
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
            burstFx(vc, at, burst, age, camera);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(LightningRenderTypes.BOLT);
        }
    }

    private static void ritual(VertexConsumer vc, CastState state, Vec3 base, Vec3 cam,
                               double time, float partial) {
        int tier = state.tier;
        float p = state.ratio;
        float glow = .25F + .75F * Mth.clamp(p, 0, 1);

        // ① 脚边小闪电：一圈小雷网 ＋ 不断跳的短电弧（作者点名的第三件事）✓
        footLightning(vc, base, cam, time, tier, glow);
        // ② 三个不同角度的小法阵 ✓
        java.util.ArrayList<Vec3> alive = new java.util.ArrayList<>(3);
        for (Circle circle : state.circles) {
            double age = time - circle.born;
            if (age < 0 || age > circle.life) {
                continue;
            }
            float f = (float) Mth.clamp(age / (double) circle.life, 0, 1);
            float a = Math.min(1F, (float) age / 3F) * (1F - smooth(Mth.clamp((f - .84F) / .16F, 0, 1)));
            if (a <= .01F) {
                continue;
            }
            double r = circle.radius * (.70D + .30D * Math.min(1D, age / 5D))
                    * (1D + .06D * Math.sin(time * .3D + circle.seed));
            ringInPlane(vc, circle.center, circle.normal, r, circle.seed,
                    a * .80F, time * .18D + circle.seed * .002D, cam);
            // ★ 2026-11-06 作者：「太小家碧玉了，那个什么唤龙小阵可以离玩家远点」
            //   ⇒ 阵挪远了（4.3~5.9 格）就得让"它和玩家之间有关系"，否则像三个不相干的圈 ✗。
            //   开阵前先拉一道忽明忽暗的引导电弧：阵心 → 胸口，读作"阵在把人叫过去 / 把龙叫出来"✓
            float door = Mth.clamp((f - .45F) / .5F, 0, 1);
            if (door > .04F) {
                Vec3 chest = base.add(0, 1.05D, 0);
                arc(vc, circle.center, chest, circle.seed + 900L + (long) (time / 2) * 7L,
                        .010F + .003F * tier, a * door * .42F, cam);
            }
            // 阵心"充电"：越接近开门，中心那点越亮 ✓
            if (door > .01F) {
                flash(vc, circle.center, .05F + .14F * door, a * door, cam);
            }
            // 阵要散了：边缘甩出一圈短电（"烧完"的样子，不是无声消失）✓
            if (f > .80F) {
                float die = (f - .80F) / .20F;
                for (int d = 0; d < 6; d++) {
                    double ang = d * Math.PI * 2 / 6 + time * .5D + circle.seed;
                    arc(vc, planePoint(circle.center, circle.normal, ang, r),
                            planePoint(circle.center, circle.normal, ang, r * (1.35D + .5D * die)),
                            circle.seed + 700L + d, .012F, a * (1F - die) * .8F, cam);
                }
            }
            alive.add(circle.center);
        }

        // ★ 2026-11-06：三张阵之间拉一圈电 —— 让"三个"读成"一个阵组"，而不是三个孤立的圈 ✓
        if (alive.size() >= 2) {
            int links = alive.size() == 2 ? 1 : alive.size();
            for (int i = 0; i < links; i++) {
                Vec3 a0 = alive.get(i);
                Vec3 b0 = alive.get((i + 1) % alive.size());
                arc(vc, a0, b0, state.seed + 800L + i * 13L + (long) (time / 4) * 7L,
                        .012F, .16F + .26F * glow, cam);
            }
        }

        // ③ 小雷龙：从阵心飞进胸口 ✓
        for (Dragon dragon : state.dragons) {
            double age = time - dragon.born;
            if (age < 0 || age > dragon.life) {
                continue;
            }
            float f = (float) Mth.clamp(age / (double) dragon.life, 0, 1);
            Vec3 at = bezier(dragon.from, dragon.ctrl, dragon.to, f);
            Vec3 ahead = bezier(dragon.from, dragon.ctrl, dragon.to, Math.min(1F, f + .06F));
            Vec3 dir = ahead.subtract(at);
            if (dir.lengthSqr() < 1e-6) {
                dir = dragon.to.subtract(dragon.from);
            }
            float a = Math.min(1F, f * 6F) * (1F - smooth(Mth.clamp((f - .82F) / .18F, 0, 1)));
            if (a <= .01F) {
                continue;
            }
            smallDragon(vc, at, dir, dragon.scale, a, (float) age, dragon.seed, cam);
        }

        // ④ 入体：胸口爆一圈电 ✓
        for (Impact impact : state.impacts) {
            double age = time - impact.born;
            if (age < 0 || age > 8) {
                continue;
            }
            float f = (float) Mth.clamp(age / 8D, 0, 1);
            float a = (1F - f) * .9F;
            flash(vc, impact.at, .30F * (1F - f * .5F), a * .8F, cam);
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI * 2 / 6 + impact.seed * .01D;
                Vec3 to = impact.at.add(Math.cos(ang) * (.35D + .5D * f),
                        Math.sin(ang) * (.35D + .5D * f), Math.cos(ang * 2.3D) * .18D * (1 - f));
                arc(vc, impact.at, to, impact.seed + i, .018F, a * .85F, cam);
            }
        }

        // ⑤ 头顶聚雷球（t2+）：3 条弧往球心收 ✓
        if (tier >= 2) {
            Vec3 orb = base.add(0, 2.70D + .12D * Math.sin(time * .09D), 0);
            float size = (.26F + .06F * tier) * (.55F + .45F * glow);
            flash(vc, orb, size, .30F + .35F * glow, cam);
            for (int i = 0; i < 3 + tier; i++) {
                double ang = time * .12D + i * Math.PI * 2 / (3 + tier);
                Vec3 from = orb.add(Math.cos(ang) * (1.25D + .35D * Math.sin(time * .2D + i)),
                        .45D * Math.sin(ang * 1.7D + time * .13D),
                        Math.sin(ang) * (1.25D + .35D * Math.cos(time * .17D + i)));
                arc(vc, from, orb, state.seed + 300L + (long) (time / 3) * 17L + i,
                        .022F, (.30F + .40F * glow), cam);
            }
        }

        // ⑥ 身侧爆电：从躯干斜着炸出去的短弧，每 3 tick 换一批 ✓
        int sideArcs = 4 + tier;
        long snap = (long) (time / 3);
        for (int i = 0; i < sideArcs; i++) {
            double ang = i * Math.PI * 2 / sideArcs + snap * .7D;
            Vec3 from = base.add(Math.cos(ang) * .35D, 1.0D + Math.sin(ang * 2 + snap) * .35D,
                    Math.sin(ang) * .35D);
            Vec3 to = base.add(Math.cos(ang) * (1.55D + .55D * glow),
                    1.0D + Math.sin(ang * 2 + snap) * .35D + (.30D + .45D * glow) * Math.cos(ang),
                    Math.sin(ang) * (1.55D + .55D * glow));
            arc(vc, from, to, state.seed + 500L + snap * 31L + i, .019F,
                    .18F + .30F * glow, cam);
        }
    }

    /** 平面里的一点：{@code center + (u·cosθ + v·sinθ)·r}，{@code u,v} 张成垂直于 {@code normal} 的平面 ✓。 */
    private static Vec3 planePoint(Vec3 center, Vec3 normal, double angle, double radius) {
        Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        if (n.lengthSquared() < 1e-8F) {
            n.set(0, 1, 0);
        }
        n.normalize();
        Vector3f u = new Vector3f(1, 0, 0);
        if (Math.abs(n.dot(u)) > .95F) {
            u.set(0, 0, 1);
        }
        u.cross(n).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        double c = Math.cos(angle) * radius;
        double s = Math.sin(angle) * radius;
        return center.add(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s);
    }

    /** 脚边：小雷网 ＋ 短电弧（每 2~3 tick 换位置：像静电在脚边爬）✓。 */
    private static void footLightning(VertexConsumer vc, Vec3 base, Vec3 cam,
                                      double time, int tier, float glow) {
        Vec3 feet = base.add(0, .045D, 0);
        long snap = (long) (time / 2);
        // ★ 2026-11-06：脚边雷网从 0.85 放大到 1.30~2.30 格 —— 原来那圈太秀气，
        //   远看只是"脚下一亮"，现在是一整片噼啪作响的地面 ✓
        double radius = 1.30D + .11D * tier + .20D * glow;
        // 大雷网（三圈电）—— 不用 nodes（那会更亮，读条期不需要）✓
        web(vc, feet, (float) radius, 9000L + (long) (time / 6) * 13L, .28F + .34F * glow,
                (float) (time * .10D), cam, false);
        // 外圈 14 根朝外的尖刺：把"雷网"读成"雷阵" ✓
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + time * .05D;
            arc(vc, feet.add(Math.cos(a) * radius * .96D, 0, Math.sin(a) * radius * .96D),
                    feet.add(Math.cos(a) * radius * (1.10D + .06D * Math.sin(time * .3D + i)), 0,
                            Math.sin(a) * radius * (1.10D + .06D * Math.sin(time * .3D + i))),
                    9000L + i, .013F + .003F * tier, .30F + .34F * glow, cam);
        }
        // 贴地的一点底光（很轻，别抢戏）
        LightningBoltGeometry.groundFlash(vc, IDENTITY,
                feet.x - cam.x, feet.y - cam.y, feet.z - cam.z,
                (float) (radius * .75D), LightningTheme.GOLD, .16F + .22F * glow);
        // 跳动的短电弧：4 + 档 条，从地面小点往上跳
        int sparks = 4 + tier;
        for (int i = 0; i < sparks; i++) {
            long seed = 7000L + snap * 53L + i * 11L;
            RandomSource rnd = RandomSource.create(seed);
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = radius * (.25D + rnd.nextDouble() * .75D);
            Vec3 from = feet.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            double h = .20D + rnd.nextDouble() * (.45D + .12D * tier);
            Vec3 to = from.add((rnd.nextDouble() - .5D) * .22D, h,
                    (rnd.nextDouble() - .5D) * .22D);
            arc(vc, from, to, seed, .014F + .004F * tier, (.35F + .45F * glow), cam);
            if (i % 2 == 0) {
                arc(vc, to, to.add((rnd.nextDouble() - .5D) * .3D, -.1D,
                        (rnd.nextDouble() - .5D) * .3D), seed + 77L, .011F,
                        (.25F + .30F * glow), cam);
            }
        }
    }

    private static void burstFx(VertexConsumer vc, Vec3 at, Burst burst, double age, Vec3 cam) {
        double f = Mth.clamp(age / 12D, 0, 1);
        float a = (float) ((1 - f) * (.55 + .10 * burst.tier) * burst.strength);
        double R = 3.40D + burst.tier * .90D;
        web(vc, at.add(0, .05D, 0), (float) (R * (.55D + f * 1.5D)), burst.start + 5L,
                a * .8F, (float) (age * .3D), cam, true);
        LightningBoltGeometry.groundFlash(vc, IDENTITY, at.x - cam.x, at.y - cam.y, at.z - cam.z,
                (float) (R * (.4D + f)), LightningTheme.GOLD, a * .7F);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI * 2 / 8 + burst.start * .01D;
            Vec3 to = at.add(Math.cos(ang) * R * (.35D + f), .10D + f * .5D,
                    Math.sin(ang) * R * (.35D + f));
            arc(vc, at.add(0, .12D, 0), to, burst.start + 40L + i, .022F, a * .8F, cam);
        }
    }

    // ==================================================================
    //  几何
    // ==================================================================

    /** 任意朝向的小法阵：三圈电环 ＋ 刻度（{@code normal} 决定"平 / 立 / 斜"）✓。 */
    private static void ringInPlane(VertexConsumer vc, Vec3 center, Vec3 normal, double radius,
                                    long seed, float alpha, double spin, Vec3 cam) {
        if (radius <= .02D || alpha <= .01F) {
            return;
        }
        Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
        Vector3f u = new Vector3f(1, 0, 0);
        if (Math.abs(n.dot(u)) > .95F) {
            u.set(0, 0, 1);
        }
        u.cross(n).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();

        float tx = (float) (center.x - cam.x);
        float ty = (float) (center.y - cam.y);
        float tz = (float) (center.z - cam.z);
        Matrix4f m = new Matrix4f(
                u.x, u.y, u.z, 0,
                v.x, v.y, v.z, 0,
                n.x, n.y, n.z, 0,
                tx, ty, tz, 1);
        double camX = -(u.x * tx + u.y * ty + u.z * tz);
        double camY = -(v.x * tx + v.y * ty + v.z * tz);
        double camZ = -(n.x * tx + n.y * ty + n.z * tz);

        LightningBoltGeometry.groundWeb(vc, m, 0, 0, 0, (float) radius, seed, LightningTheme.GOLD,
                alpha, (float) spin, camX, camY, camZ, false);
        // 刻度：10 根从 0.88R 到 1.04R 的短弧（"这圈是会咬人的"✓）
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 - spin * 1.4D;
            LightningBoltGeometry.arc(vc, m,
                    Math.cos(a) * radius * .88D, Math.sin(a) * radius * .88D, 0,
                    Math.cos(a) * radius * 1.04D, Math.sin(a) * radius * 1.04D, 0,
                    seed + i, .016F, alpha * .9F, LightningTheme.GOLD, camX, camY, camZ);
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
                                    float alpha, float age, long seed, Vec3 cam) {
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
        int theme = LightningTheme.GOLD;
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
    private static void arc(VertexConsumer vc, Vec3 a, Vec3 b, long seed,
                            float width, float alpha, Vec3 cam) {
        LightningBoltGeometry.arc(vc, IDENTITY,
                a.x - cam.x, a.y - cam.y, a.z - cam.z,
                b.x - cam.x, b.y - cam.y, b.z - cam.z,
                seed, width, alpha, LightningTheme.GOLD, 0, 0, 0);
    }

    private static void flash(VertexConsumer vc, Vec3 at, float radius, float alpha, Vec3 cam) {
        LightningBoltGeometry.billboardFlash(vc, IDENTITY,
                at.x - cam.x, at.y - cam.y, at.z - cam.z,
                radius, LightningTheme.GOLD, alpha, 0, 0, 0);
    }

    private static void web(VertexConsumer vc, Vec3 at, float radius, long seed,
                            float alpha, float spin, Vec3 cam, boolean nodes) {
        LightningBoltGeometry.groundWeb(vc, IDENTITY,
                at.x - cam.x, at.y - cam.y, at.z - cam.z,
                radius, seed, LightningTheme.GOLD, alpha, spin, 0, 0, 0, nodes);
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
        int emitted;

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

    private static final class Impact {
        final Vec3 at;
        final long born;
        final long seed;

        Impact(Vec3 at, long born, long seed) {
            this.at = at;
            this.born = born;
            this.seed = seed;
        }
    }

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
            this.tier = info.tier();
            this.ratio = info.ratio();
            // 连着放第二发：把上一发的三张阵清掉（不然第二发没阵）✗
            if (now - this.lastSeen > 2L || this.ratio - info.ratio() > .35F) {
                this.spawned = 0;
                this.dragonSeq = 0;
                this.circles.clear();
                this.dragons.clear();
                this.impacts.clear();
                this.maxRatio = 0;
                this.startTick = now;
            }
            this.maxRatio = Math.max(this.maxRatio, info.ratio());
            this.x = caster.getX();
            this.y = caster.getY();
            this.z = caster.getZ();
            this.lastSeen = now;

            // ① 三张阵：0.10 / 0.22 / 0.34 各出一张（三张都在读条前半段就位 ✓）
            for (int i = this.spawned; i < CIRCLES; i++) {
                float at = .10F + i * .12F;
                if (this.ratio < at) {
                    break;
                }
                spawnCircle(level, caster, i, minimal);
                this.spawned = i + 1;
            }

            // ② 阵亮够了就开：一条小雷龙窜出来 ✓
            for (Circle circle : this.circles) {
                int max = this.tier >= 4 ? 2 : 1;
                if (circle.emitted >= max) {
                    continue;
                }
                long nextAt = circle.born + circle.life * (circle.emitted == 0 ? 55L : 82L) / 100L;
                if (now >= nextAt || this.ratio > .9F) {
                    circle.emitted++;
                    spawnDragon(level, caster, circle, minimal);
                }
            }
            // ③ 龙飞到了：胸口那一下 ✓
            for (Dragon dragon : this.dragons) {
                if (now >= dragon.born + dragon.life && dragon.born > 0) {
                    this.impacts.add(new Impact(dragon.to, now, dragon.seed + 13L));
                    // 用 born 取反标记"已入体"，避免重复触发 ✓
                    this.dragons.remove(dragon);
                    break;
                }
            }
            if (!minimal) {
                for (Dragon dragon : this.dragons) {
                    if (now - dragon.born < 3L) {
                        Vec3 at = dragon.from;
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, .02D, 0);
                    }
                }
            }
            this.dragons.removeIf(d -> now > d.born + d.life + 1L);
            this.impacts.removeIf(i -> now > i.born + 10L);
        }

        private long estimateLife(long now) {
            double elapsed = Math.max(1.0D, now - this.startTick + 1.0D);
            double total = Math.max(16.0D, elapsed / Math.max(.06F, this.ratio));
            double remain = Math.max(6.0D, total * (1.0D - this.ratio));
            return (long) Mth.clamp(remain * 1.3D + 8.0D, 14.0D, 48.0D);
        }

        private void spawnCircle(ClientLevel level, Entity caster, int index, boolean minimal) {
            // ★ 2026-11-06 作者：「太小家碧玉了，那个什么唤龙小阵可以离玩家远点啊」
            //   原来 2.05~2.55 格（贴着人，读起来像"三个小圈"✗）⇒ 现在 4.30~5.90 格，
            //   龙要真的"飞一段"才进身体 ✓
            double radius = 4.30D + index * .55D + (this.tier >= 4 ? .65D : 0);
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double y = caster.getY() + .75D + RANDOM.nextDouble() * (1.10D + .10D * this.tier);
            Vec3 center = new Vec3(caster.getX() + Math.cos(a) * radius, y,
                    caster.getZ() + Math.sin(a) * radius);
            // ★ 三种角度：平贴 / 立着朝外 / 斜插 —— 作者要的"不同角度"✓
            double kind = RANDOM.nextDouble();
            Vec3 normal;
            if (kind < .34D) {
                normal = new Vec3(0, 1, 0);
                center = new Vec3(center.x, caster.getY() + .05D + RANDOM.nextDouble() * .08D, center.z);
            } else if (kind < .70D) {
                double outward = a + (RANDOM.nextDouble() - .5D) * 1.0D;
                normal = new Vec3(Math.cos(outward), (RANDOM.nextDouble() - .5D) * .5D, Math.sin(outward))
                        .normalize();
            } else {
                double tilt = .5D + RANDOM.nextDouble() * .7D;
                double yaw = RANDOM.nextDouble() * Math.PI * 2;
                normal = new Vec3(Math.cos(yaw) * tilt, 1.0D, Math.sin(yaw) * tilt).normalize();
            }
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
                    18L + this.tier, this.seed + 900L + (this.dragonSeq++) * 61L,
                    .52F + .05F * this.tier));

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
