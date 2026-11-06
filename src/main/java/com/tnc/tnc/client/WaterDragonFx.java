package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>水法的水龙前戏（2026-11-06）</b> —— 作者：「弄完你去抄一下雷法新做的雷龙，抄一下，
 * 做一个水龙出来，要比较大只的水龙」。
 *
 * <p>抄的是 {@code LightningCastFx.smallDragon} 那一套：读条过程中，在施法者周围的地面上
 * 先亮起一个小小的<b>水涡</b>，一条龙从涡心窜出来，沿二次贝塞尔弧线飞进玩家胸口，入体时炸一圈水。
 * 区别只有两点，都是作者点名要的：
 *
 * <ul>
 *   <li><b>水</b> —— 雷龙是"线勾"的（电弧 + 白闪），水龙是"水管"的：脊干每一节都是一根
 *       深蓝→青→白芯的<b>圆管</b>（有厚度，不是线），背鳍/尾鳍是半透明水膜，头上有角、
 *       两根很长的龙须在飘，四条小腿收在肚子下面，身上套三圈水环、几颗水珠；</li>
 *   <li><b>比较大只</b> —— 雷龙那条小的是 {@code scale = .28 + .025×档}（全长约 0.75 格），
 *       这里 {@code scale = 1.0 + .08×档}、全长 {@code 3.4 × scale} 格 ⇒
 *       <b>t1 就有 3.4 格、t5 到 3.8 格</b>，是雷龙小版的 4.5~5 倍。放大方式是整体缩放
 *       （{@link #lengthFor(double)} 是唯一自由度），不会像之前那把枪一样被拉长。</li>
 * </ul>
 *
 * <p>技术路线和 {@code WaterCastFx} 完全一致：客户端 tick 里只看
 * {@code SpellCast.Process.progress()}（不生成实体、不改读条时长、不改伤害），
 * 几何在 {@code AFTER_WEATHER} 阶段用 {@code RenderType.lightning()} 的加色缓冲现画
 * （这个阶段天气缓冲还没合成 ⇒ 画得出来；实体渲染阶段用这层是看不见的）。
 * 不读任何贴图/模型，全部是即时生成的四边形。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WaterDragonFx {

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** 水法的四条链（和 {@code WaterCastFx} 同一组）。 */
    private static final Set<String> WATER_GROUPS =
            Set.of("water_ball", "water_wave", "water_bind", "water_rain");

    /** 深蓝（外辉光）、青（主体）、浪白（芯）、水沫。 */
    private static final float DEEP_R = .04F, DEEP_G = .22F, DEEP_B = .55F;
    private static final float C_R = .26F, C_G = .80F, C_B = 1.0F;
    private static final float FOAM_R = .86F, FOAM_G = .98F, FOAM_B = 1.0F;
    private static final float LIGHT_R = .55F, LIGHT_G = .92F, LIGHT_B = 1.0F;

    /** 龙身分段数（每段画 4 层管）。 */
    private static final int SPINE = 12;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Dragon> DRAGONS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;
    private static boolean renderBroken;

    private WaterDragonFx() {
    }

    // ==================================================================
    //  Tick：读条状态、放龙、拖尾、入体
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
            DRAGONS.clear();
            BURSTS.clear();
            owner = level;
        }
        if (level == null || mc.player == null) {
            return;
        }
        long now = level.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;

        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            seen.add(player.getUUID());
            Vec3 pos = player.position();
            CastState state = CASTS.computeIfAbsent(player.getUUID(), id -> new CastState());
            float previous = state.ratio;
            state.tier = info.tier();
            state.ratio = info.ratio();
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.height = player.getBbHeight();
            state.lastSeen = now;

            // 读条跨过阈值就"开一口水涡、放一条龙"——和雷法小雷龙同一个触发思路，
            // 只是水龙更少、更大（一口气三条会像鱼群，不像龙 ✗）。
            int count = spawnCountFor(info.tier());
            for (int i = 0; i < count; i++) {
                float at = spawnRatioFor(info.tier(), i);
                if (previous < at && info.ratio() >= at) {
                    spawnDragon(level, player, state, info, now);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator(); it.hasNext(); ) {
            if (!seen.contains(it.next().getKey())) {
                it.remove();
            }
        }

        // 龙在飞：拖尾粒子、到点入体。
        for (Iterator<Dragon> it = DRAGONS.iterator(); it.hasNext(); ) {
            Dragon dragon = it.next();
            double age = now - dragon.born;
            if (age < 0) {
                continue;
            }
            if (age >= dragon.life) {
                it.remove();
                burstAtChest(level, dragon, minimal);
                continue;
            }
            if (!minimal) {
                tickTrail(level, dragon, age);
            }
        }
        BURSTS.removeIf(b -> now - b.start > 14L);
    }

    private static void tickTrail(ClientLevel level, Dragon dragon, double age) {
        float f = (float) Mth.clamp(age / (double) dragon.life, 0, 1);
        Vec3 at = bezierAt(dragon.from, dragon.ctrl, dragon.to, f);
        Vec3 dir = bezierAt(dragon.from, dragon.ctrl, dragon.to, Math.min(1F, f + .1F)).subtract(at);
        if (dir.lengthSqr() < 1e-6) {
            return;
        }
        dir = dir.normalize();
        Vec3 right = rightOf(dir);
        // ⚠️ 必须是 right × dir（= +Y）。写成 dir × right 得到的是 (0,-1,0) —— 整条龙的背鳍、
        // 龙角、龙须全会朝下翻（2026-11-07 出预览图时才发现）。
        Vec3 up = right.cross(dir).normalize();
        double s = dragon.scale;
        for (int i = 0; i < 2; i++) {
            Vec3 offset = right.scale((RANDOM.nextDouble() - .5) * .5 * s)
                    .add(up.scale((RANDOM.nextDouble() - .5) * .45 * s));
            Vec3 tail = at.subtract(dir.scale(.25 * s)).add(offset);
            level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                    tail.x, tail.y, tail.z,
                    -dir.x * .02 + offset.x * .10,
                    .015 + RANDOM.nextDouble() * .035,
                    -dir.z * .02 + offset.z * .10);
        }
    }

    private static void burstAtChest(ClientLevel level, Dragon dragon, boolean minimal) {
        BURSTS.add(new Burst(dragon.to, level.getGameTime(), dragon.scale, dragon.tier));
        int count = minimal ? 6 : 14;
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .05 + RANDOM.nextDouble() * .16;
            level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                    dragon.to.x, dragon.to.y, dragon.to.z,
                    Math.cos(a) * speed, -.02 + RANDOM.nextDouble() * .10, Math.sin(a) * speed);
        }
        if (dragon.tier >= 4) {
            TNSpellClientVisuals.pulseShake(.10F + .02F * dragon.tier);
        }
    }

    private static void spawnDragon(ClientLevel level, Player player, CastState state,
                                    CastInfo info, long now) {
        double s = scaleFor(info.tier());
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double radius = 3.2 + RANDOM.nextDouble() * 2.2;
        Vec3 base = new Vec3(state.x, state.y, state.z);
        Vec3 from = base.add(Math.cos(angle) * radius, .14, Math.sin(angle) * radius);
        Vec3 chest = base.add(0, state.height * .62, 0);
        Vec3 flat = chest.subtract(from).scale(.5);
        Vec3 ctrl = from.add(flat).add(0, .85 + .18 * info.tier(), 0);
        DRAGONS.add(new Dragon(from, ctrl, chest, now, lifeFor(info.tier()),
                RANDOM.nextLong(), (float) s, info.tier()));
    }

    // ==================================================================
    //  世界渲染（和 WaterCastFx 同一个阶段、同一层缓冲）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (renderBroken) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != owner) {
            return;
        }
        try {
            renderWorld(event, level);
        } catch (Throwable t) {
            renderBroken = true;
            org.apache.logging.log4j.LogManager.getLogger("TN-C/waterdragon")
                    .warn("TN-C/waterdragon: render failed, water dragon disabled ({})", t.toString());
        }
    }

    private static void renderWorld(RenderLevelStageEvent event, ClientLevel level) {
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        boolean any = false;

        VertexConsumer out = BUFFERS.getBuffer(RenderType.lightning());
        for (Dragon dragon : DRAGONS) {
            double age = now - dragon.born + partial;
            if (age < 0 || age > dragon.life) {
                continue;
            }
            Vec3 origin = dragon.from;
            if (origin.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            if (age < 9) {
                gate(out, dragon.from.subtract(camera), age, dragon.scale);
            }
            float f = (float) Mth.clamp(age / (double) dragon.life, 0, 1);
            Vec3 at = bezierAt(dragon.from, dragon.ctrl, dragon.to, f);
            Vec3 ahead = bezierAt(dragon.from, dragon.ctrl, dragon.to, Math.min(1F, f + .08F));
            Vec3 dir = ahead.subtract(at);
            if (dir.lengthSqr() < 1e-7) {
                dir = dragon.to.subtract(dragon.from);
            }
            float alpha = fade(f);
            dragonGeometry(out, at.subtract(camera), dir, dragon.scale, alpha,
                    (float) (age * 1.0D), dragon.seed);
            any = true;
        }
        for (Burst burst : BURSTS) {
            double age = now - burst.start + partial;
            if (age < 0 || age > 14) {
                continue;
            }
            if (burst.at.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            burstGeometry(out, burst.at.subtract(camera), age, burst.strength, burst.tier);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(RenderType.lightning());
        }
    }

    private static float fade(float f) {
        // 出生 3 tick 淡入、最后 30% 淡出，中间全亮。
        float in = Mth.clamp(f / .08F, 0, 1);
        float out = Mth.clamp((1 - f) / .30F, 0, 1);
        return Math.min(in, out);
    }

    // ==================================================================
    //  水涡门（龙出来的地方）
    // ==================================================================

    private static void gate(VertexConsumer out, Vec3 at, double age, float scale) {
        float fade = (float) Mth.clamp(1 - age / 9.0, 0, 1);
        double open = 1 - Math.pow(1 - Mth.clamp(age / 5.0, 0, 1), 2);
        double radius = .55 * scale * (.7 + .5 * open);
        WaterGeometry.ringColor(out, IDENTITY, at.add(0, .02, 0), UP, radius, .022 * scale,
                C_R, C_G, C_B, .55F * fade, 26);
        WaterGeometry.ringColor(out, IDENTITY, at.add(0, .035, 0), UP, radius * .78, .012 * scale,
                FOAM_R, FOAM_G, FOAM_B, .40F * fade, 20);
        // 六条旋转的吸水臂，把"水是在转的"讲清楚。
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6 + age * .16;
            Vec3 inner = at.add(Math.cos(a) * radius * .30, .03, Math.sin(a) * radius * .30);
            Vec3 outer = at.add(Math.cos(a + .55) * radius * .95, .03, Math.sin(a + .55) * radius * .95);
            WaterGeometry.stroke(out, IDENTITY, UP, inner, outer, .012 * scale,
                    C_R, C_G, C_B, .38F * fade);
        }
        // 从涡心溅起来的水柱（还没成形那几 tick）。
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 2 / 4 + age * .22;
            Vec3 base = at.add(Math.cos(a) * radius * .42, .04, Math.sin(a) * radius * .42);
            Vec3 tip = base.add(0, .30 * scale * open, 0);
            WaterGeometry.tube(out, IDENTITY, base, tip, .022 * scale,
                    FOAM_R, FOAM_G, FOAM_B, .22F * fade, 5);
        }
    }

    // ==================================================================
    //  水龙本体
    // ==================================================================

    /**
     * 一条水龙：脊干 12 节（每节 4 层圆管：深蓝辉光 → 青 → 浅 → 白芯）、背鳍、尾鳍、
     * 头（颅/吻/下颚/眉/眼/四角/两口龙须/牙/鳃鳍）、四腿、三圈水环、水珠。
     *
     * <p>局部坐标：{@code +Z} 前进、{@code +Y} 上、原点在龙的中段；所有长度都乘 {@code scale}。
     */
    private static void dragonGeometry(VertexConsumer out, Vec3 at, Vec3 dirIn, float scale,
                                       float alpha, float age, long seed) {
        Vec3 dir = dirIn.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : dirIn.normalize();
        Vec3 right = rightOf(dir);
        // right × dir = +Y（见 tickTrail 的注释：反过来会把背鳍/龙角/龙须全翻到肚子底下）。
        Vec3 up = right.cross(dir).normalize();
        double s = scale;
        double len = lengthFor(s);   // 以格为单位的全长

        // ---- 1. 脊线：从吻后到尾巴，带游动摆动（每节位置都确定性）----
        Vec3[] spine = new Vec3[SPINE];
        double[] radii = new double[SPINE];
        for (int i = 0; i < SPINE; i++) {
            double t = i / (SPINE - 1.0);                       // 0 = 头，1 = 尾
            double z = (.5 - t) * len;
            double sway = Math.sin(age * .55D - t * 4.6D) * .055 * len * (.25 + .75 * t);
            double rise = Math.sin(age * .42D - t * 5.4D) * .028 * len * t;
            spine[i] = at.add(dir.scale(z)).add(right.scale(sway)).add(up.scale(rise));
            // 头要比脖子粗一点，否则整条龙读成「一根会发光的管子、前面鼓了个包」。
            radii[i] = s * (.108 - .052 * t);
        }

        // ---- 2. 四层管：外层越宽越暗，芯越窄越白 ----
        // 一条连续的扫掠管（不是 11 段圆柱）。分段圆柱在接缝处半径一跳、截面还各转一个角度，
        // 远看就是"一叠藕片"；平行移动坐标架扫过去才是水。
        final int bodyPts = 26;
        Vec3[] bodyPath = new Vec3[bodyPts];
        double[] bodyRad = new double[bodyPts];
        for (int i = 0; i < bodyPts; i++) {
            double t = i / (double) (bodyPts - 1);
            bodyPath[i] = spineAt(spine, t);
            bodyRad[i] = s * (.108 - .052 * t);
        }
        // 四层叠加（加色）：中心累计 0.05+0.13+0.20+0.40 ≈ 0.78，过 1 就烧成纯白塑料管了。
        sweep(out, bodyPath, scaled(bodyRad, 2.00), DEEP_R, DEEP_G, DEEP_B, .05F * alpha, 8);
        sweep(out, bodyPath, scaled(bodyRad, 1.55), C_R * .75F, C_G * .85F, C_B, .13F * alpha, 6);
        sweep(out, bodyPath, scaled(bodyRad, 1.00), LIGHT_R, LIGHT_G, LIGHT_B, .20F * alpha, 5);
        sweep(out, bodyPath, scaled(bodyRad, .42), FOAM_R, FOAM_G, FOAM_B, .40F * alpha, 4);
        // 尾尖一点更亮的珠子。
        WaterGeometry.droplet(out, IDENTITY, spine[SPINE - 1], .10 * s, age * 1.3,
                FOAM_R, FOAM_G, FOAM_B, .55F * alpha);

        // ---- 3. 背鳍：7 片半透明水膜 + 一条亮脊线 ----
        Vec3 previousTip = null;
        for (int k = 0; k <= 6; k++) {
            double t = .12 + k * .10;
            Vec3 base = spineAt(spine, t);
            Vec3 next = spineAt(spine, Math.min(1, t + .09));
            double height = s * (.30 - .022 * k);
            Vec3 tip = base.add(up.scale(height));
            Vec3 tipNext = next.add(up.scale(height * .88));
            WaterGeometry.quad(out, IDENTITY, base, next, tipNext, tip,
                    C_R, C_G, C_B, .30F * alpha);
            WaterGeometry.tube(out, IDENTITY, base, tip, .012 * s, LIGHT_R, LIGHT_G, LIGHT_B,
                    .28F * alpha, 4);
            if (previousTip != null) {
                WaterGeometry.tube(out, IDENTITY, previousTip, tip, .008 * s,
                        FOAM_R, FOAM_G, FOAM_B, .34F * alpha, 4);
            }
            previousTip = tip;
        }

        // ---- 4. 头 ----
        Vec3 head = spine[0];
        double jawOpen = .10 + .07 * Math.sin(age * .5D);
        head(out, head, dir, right, up, s, age, jawOpen, alpha);

        // ---- 5. 四腿（收在肚子下面：两段 ＋ 三爪）----
        for (int side = -1; side <= 1; side += 2) {
            for (int pair = 0; pair < 2; pair++) {
                double t = pair == 0 ? .26 : .58;
                Vec3 root = spineAt(spine, t).add(right.scale(side * radii[Math.min(SPINE - 1,
                        (int) Math.round(t * (SPINE - 1)))] * .55));
                // 腿往后收：膝在外侧下方，脚再往下、略往前，爪子朝前——像在"游泳蹬水"。
                Vec3 knee = root.add(right.scale(side * .13 * s)).add(0, -.19 * s, 0)
                        .add(dir.scale(-.06 * s));
                Vec3 foot = knee.add(right.scale(side * .04 * s)).add(dir.scale(.07 * s))
                        .add(0, -.09 * s, 0);
                WaterGeometry.tube(out, IDENTITY, root, knee, .045 * s, C_R, C_G, C_B, .30F * alpha, 5);
                WaterGeometry.tube(out, IDENTITY, knee, foot, .034 * s, C_R, C_G, C_B, .26F * alpha, 5);
                for (int claw = -1; claw <= 1; claw++) {
                    Vec3 tip = foot.add(dir.scale(.10 * s))
                            .add(right.scale(claw * .045 * s)).add(0, -.02 * s, 0);
                    WaterGeometry.tube(out, IDENTITY, foot, tip, .010 * s,
                            FOAM_R, FOAM_G, FOAM_B, .40F * alpha, 4);
                }
            }
        }

        // ---- 6. 尾鳍：五根扇骨（短一点，太长会像一圈太阳芒）+ 骨间水膜 ----
        Vec3 tail = spine[SPINE - 1];
        Vec3[] tips = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double a = (i - 2) * .34;
            tips[i] = tail.add(right.scale(Math.sin(a) * .24 * s))
                    .add(up.scale(Math.cos(a) * .17 * s))
                    .add(dir.scale(-.12 * s));
            WaterGeometry.tube(out, IDENTITY, tail, tips[i], .012 * s, LIGHT_R, LIGHT_G, LIGHT_B,
                    .30F * alpha, 4);
        }
        // 骨与骨之间拉一片扇形水膜（比一整块三角板更像鳍）
        for (int i = 0; i < 4; i++) {
            WaterGeometry.quad(out, IDENTITY, tail, tips[i], tips[i + 1], tail,
                    C_R, C_G, C_B, .10F * alpha);
        }

        // ---- 7. 三圈绕身水环 + 沿身水珠 ----
        for (int k = 1; k <= 3; k++) {
            Vec3 center = spineAt(spine, k * .24);
            Vec3 axis = spineAt(spine, k * .24 - .03).subtract(spineAt(spine, k * .24 + .03));
            if (axis.lengthSqr() < 1e-6) {
                axis = dir;
            }
            WaterGeometry.ringColor(out, IDENTITY, center, axis.normalize(),
                    radii[(int) Math.min(SPINE - 1, Math.round(k * .24 * (SPINE - 1)))] * 1.8,
                    .012 * s, LIGHT_R, LIGHT_G, LIGHT_B, .16F * alpha, 18);
        }
        for (int k = 0; k < 4; k++) {
            double t = .18 + k * .20;
            Vec3 base = spineAt(spine, t).add(up.scale(.10 * s));
            double bob = Math.sin(age * .8 + k * 1.7) * .05 * s;
            WaterGeometry.droplet(out, IDENTITY, base.add(up.scale(bob)), .045 * s, age + k,
                    FOAM_R, FOAM_G, FOAM_B, .48F * alpha);
        }
    }

    /**
     * 头：脖、颅、吻、下颚、眉骨、眼、四角、两口长龙须、牙、鳃鳍。
     *
     * <p>全部用<b>锥形圆管</b>拼（不是方块）。方块头在本项目的加色渲染里就是一个亮盒子，
     * 一眼看出是"贴了个箱子"；管子才有水的那种圆润过渡。
     */
    private static void head(VertexConsumer out, Vec3 head, Vec3 dir, Vec3 right, Vec3 up,
                             double s, float age, double jawOpen, float alpha) {
        // 脖子 + 颅 + 吻：一条连续的扫掠管，半径从脖子到吻尖一路收细
        sweep(out, new Vec3[]{head.add(dir.scale(-.34 * s)), head.add(dir.scale(-.16 * s)),
                        head.add(dir.scale(-.03 * s)), head.add(dir.scale(.09 * s)),
                        head.add(dir.scale(.19 * s)), head.add(dir.scale(.30 * s)),
                        head.add(dir.scale(.345 * s))},
                new double[]{.092 * s, .118 * s, .130 * s, .126 * s, .104 * s, .075 * s, .048 * s},
                C_R, C_G, C_B, .24F * alpha, 12);
        // 下颚：以颅后为铰链往下张（角度由 age 推）
        Vec3 hinge = head.add(dir.scale(.02 * s)).add(up.scale(-.055 * s - jawOpen * .05 * s));
        Vec3 jawMid = head.add(dir.scale(.17 * s)).add(up.scale(-.075 * s - jawOpen * .10 * s));
        Vec3 jawTip = head.add(dir.scale(.30 * s)).add(up.scale(-.070 * s - jawOpen * .13 * s));
        sweep(out, new Vec3[]{hinge, jawMid, jawTip},
                new double[]{.070 * s, .058 * s, .040 * s}, C_R * .85F, C_G, C_B, .22F * alpha, 8);
        // 眉骨：两道短短的凸脊，压在眼上方
        for (int side = -1; side <= 1; side += 2) {
            Vec3 a = head.add(dir.scale(.05 * s)).add(right.scale(side * .055 * s))
                    .add(up.scale(.095 * s));
            Vec3 b = head.add(dir.scale(.21 * s)).add(right.scale(side * .070 * s))
                    .add(up.scale(.072 * s));
            WaterGeometry.tube(out, IDENTITY, a, b, .024 * s, C_R, C_G, C_B, .26F * alpha, 5);
        }
        // 眼：一颗亮芯 + 一圈光晕，朝外
        for (int side = -1; side <= 1; side += 2) {
            Vec3 eye = head.add(right.scale(side * .105 * s)).add(up.scale(.055 * s))
                    .add(dir.scale(.135 * s));
            Vec3 normal = right.scale(side);
            WaterGeometry.disk(out, IDENTITY, eye, normal, .058 * s, C_R, C_G, C_B, .30F * alpha, 12);
            WaterGeometry.disk(out, IDENTITY, eye.add(normal.scale(.002)), normal,
                    .027 * s, FOAM_R, FOAM_G, FOAM_B, .85F * alpha, 10);
        }
        // 四角：两对，向后上扫，逐节摆
        for (int side = -1; side <= 1; side += 2) {
            for (int pair = 0; pair < 2; pair++) {
                Vec3 start = head.add(right.scale(side * (.080 + pair * .035) * s))
                        .add(up.scale((.100 - pair * .030) * s))
                        .add(dir.scale((-.02 - pair * .05) * s));
                Vec3 tip = start.add(right.scale(side * (.14 + pair * .05) * s))
                        .add(up.scale((.20 - pair * .06) * s))
                        .add(dir.scale(-(.24 + pair * .10) * s));
                horn(out, start, tip, s * (pair == 0 ? 1 : .8), age + side * .7, alpha);
            }
        }
        // 两口龙须：14 节，贴着脖子往后飘（张开太多会像两根天线）
        for (int side = -1; side <= 1; side += 2) {
            Vec3 root = head.add(dir.scale(.27 * s)).add(right.scale(side * .050 * s))
                    .add(up.scale(-.010 * s));
            whisker(out, root, dir, right, up, side, s * 1.05, age + side * 1.3, alpha);
        }
        // 牙：上下各 4 颗小白锥
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                double z = (.265 - i * .052) * s;
                Vec3 at = head.add(dir.scale(z)).add(right.scale(side * (.016 + i * .014) * s))
                        .add(up.scale(-.050 * s));
                WaterGeometry.tube(out, IDENTITY, at, at.add(0, -.028 * s, 0), .009 * s,
                        FOAM_R, FOAM_G, FOAM_B, .45F * alpha, 4);
                Vec3 low = head.add(dir.scale(z - .01 * s))
                        .add(right.scale(side * (.014 + i * .013) * s))
                        .add(up.scale(-.060 * s - jawOpen * .10 * s));
                WaterGeometry.tube(out, IDENTITY, low, low.add(0, .024 * s, 0), .008 * s,
                        FOAM_R, FOAM_G, FOAM_B, .40F * alpha, 4);
            }
        }
        // 鳃鳍：三片向后扫的水膜
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                Vec3 root = head.add(right.scale(side * (.115 + i * .012) * s))
                        .add(up.scale((.020 - i * .035) * s)).add(dir.scale(-.07 * s));
                Vec3 tip = root.add(right.scale(side * .20 * s)).add(up.scale(.04 * s))
                        .add(dir.scale(-.20 * s));
                WaterGeometry.quad(out, IDENTITY, root, root.add(dir.scale(-.09 * s)), tip, tip,
                        C_R, C_G, C_B, .12F * alpha);
                WaterGeometry.tube(out, IDENTITY, root, tip, .010 * s, LIGHT_R, LIGHT_G, LIGHT_B,
                        .24F * alpha, 4);
            }
        }
    }

    /**
     * 连续扫掠管：沿一条折线把截面扫过去，半径逐点插值，截面用<b>平行移动坐标架</b>。
     *
     * <p>这是这条龙身上最重要的一处：用 {@code WaterGeometry.tube} 一段段接出来的管子，
     * 每个接缝半径一跳、截面还各转一个角度，加色叠起来就是"一叠藕片"或者"竹节"。
     * 扫掠出来的是一整条光滑的水柱，只有首尾是开口的。
     */
    private static void sweep(VertexConsumer out, Vec3[] pts, double[] rad,
                              float r, float g, float b, float alpha, int sides) {
        int n = pts.length;
        Vec3 t0 = pts[1].subtract(pts[0]).normalize();
        Vec3 ref = Math.abs(t0.y) > .9 ? new Vec3(1, 0, 0) : UP;
        Vec3 nrm = t0.cross(ref).normalize();
        Vec3[][] ring = new Vec3[n][sides];
        for (int i = 0; i < n; i++) {
            Vec3 t = i == 0 ? t0
                    : i == n - 1 ? pts[n - 1].subtract(pts[n - 2]).normalize()
                    : pts[i + 1].subtract(pts[i - 1]).normalize();
            // 上一环的法向投影到当前截面 = 平行移动，整条管子不会自己拧麻花
            Vec3 flat = nrm.subtract(t.scale(nrm.dot(t)));
            nrm = flat.lengthSqr() < 1e-9 ? t0.cross(ref).normalize() : flat.normalize();
            Vec3 bin = t.cross(nrm).normalize();
            for (int k = 0; k < sides; k++) {
                double a = k * Math.PI * 2 / sides;
                ring[i][k] = pts[i].add(nrm.scale(Math.cos(a) * rad[i]))
                        .add(bin.scale(Math.sin(a) * rad[i]));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                WaterGeometry.quad(out, IDENTITY, ring[i][k], ring[i + 1][k], ring[i + 1][k2],
                        ring[i][k2], r, g, b, alpha);
            }
        }
    }

    /** 半径数组整体乘一个系数（四层管共用同一条脊线，只是粗细不同）。 */
    private static double[] scaled(double[] in, double k) {
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] * k;
        }
        return out;
    }
    /** 一根角：5 节锥形管，逐节往回收，带轻微摆动。 */
    private static void horn(VertexConsumer out, Vec3 start, Vec3 tip, double s, double phase,
                             float alpha) {
        Vec3 previous = start;
        int segments = 5;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 p = start.lerp(tip, t)
                    .add(0, Math.sin(phase + t * 2.2) * .012 * s, 0);
            double r = s * (.030 - .022 * t);
            WaterGeometry.tube(out, IDENTITY, previous, p, r, LIGHT_R, LIGHT_G, LIGHT_B,
                    .30F * alpha, 5);
            previous = p;
        }
    }

    /** 一根龙须：14 节，向后外方飘；先粗后细。 */
    private static void whisker(VertexConsumer out, Vec3 root, Vec3 dir, Vec3 right, Vec3 up,
                                int side, double s, double phase, float alpha) {
        Vec3 previous = root;
        int segments = 14;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double wave = Math.sin(phase + t * 4.4D) * .10 * s * t;
            Vec3 p = root.add(right.scale(side * (.10 + .34 * t) * s + wave))
                    .add(dir.scale(-1.15 * t * s))
                    .add(up.scale(-.16 * t * s + Math.cos(phase + t * 3.6) * .05 * s * t));
            WaterGeometry.tube(out, IDENTITY, previous, p, s * (.012 - .007 * t),
                    FOAM_R, FOAM_G, FOAM_B, .40F * alpha, 4);
            previous = p;
        }
    }

    // ==================================================================
    //  入体水爆（胸口一圈环 + 几颗水珠）
    // ==================================================================

    private static void burstGeometry(VertexConsumer out, Vec3 at, double age, float s, int tier) {
        double f = Mth.clamp(age / 14.0, 0, 1);
        float fade = (float) ((1 - f) * (1 - f));
        double radius = .28 + f * (.55 + .10 * tier) * s;
        WaterGeometry.ringColor(out, IDENTITY, at, UP, radius, .030 + .02 * (1 - f),
                C_R, C_G, C_B, .55F * fade, 26);
        WaterGeometry.ringColor(out, IDENTITY, at, UP, radius * .72, .018,
                FOAM_R, FOAM_G, FOAM_B, .45F * fade, 20);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6 + f * 1.6;
            Vec3 dir = new Vec3(Math.cos(a), .18 + (i % 3) * .10, Math.sin(a));
            WaterGeometry.tube(out, IDENTITY, at, at.add(dir.scale(radius * .85)),
                    .016, FOAM_R, FOAM_G, FOAM_B, .30F * fade, 4);
        }
    }

    // ==================================================================
    //  小工具
    // ==================================================================

    /** 世界坐标下的"右"：水平、和 forward 垂直。 */
    private static Vec3 rightOf(Vec3 forward) {
        Vec3 flat = new Vec3(forward.x, 0, forward.z);
        if (flat.lengthSqr() < 1e-6) {
            return new Vec3(1, 0, 0);
        }
        flat = flat.normalize();
        return new Vec3(-flat.z, 0, flat.x);
    }

    private static Vec3 spineAt(Vec3[] spine, double t) {
        double x = Mth.clamp(t, 0, 1) * (spine.length - 1);
        int i = (int) Math.floor(x);
        int j = Math.min(spine.length - 1, i + 1);
        double u = x - i;
        return spine[i].scale(1 - u).add(spine[j].scale(u));
    }

    /** 一个按三个轴摆的盒子（六个面都写；加色层不需要背面剔除，索性画全）。 */
    private static void box(VertexConsumer out, Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
                            double hx, double hy, double hz, float r, float g, float b, float a) {
        Vec3 x = right.scale(hx);
        Vec3 y = up.scale(hy);
        Vec3 z = forward.scale(hz);
        // +Z / -Z
        WaterGeometry.quad(out, IDENTITY, center.add(x).add(y).add(z),
                center.subtract(x).add(y).add(z),
                center.subtract(x).subtract(y).add(z),
                center.add(x).subtract(y).add(z), r, g, b, a);
        WaterGeometry.quad(out, IDENTITY, center.add(x).subtract(y).subtract(z),
                center.subtract(x).subtract(y).subtract(z),
                center.subtract(x).add(y).subtract(z),
                center.add(x).add(y).subtract(z), r * .80F, g * .85F, b * .92F, a);
        // ±X
        WaterGeometry.quad(out, IDENTITY, center.add(z).add(y).subtract(x),
                center.add(z).subtract(y).subtract(x),
                center.subtract(z).subtract(y).subtract(x),
                center.subtract(z).add(y).subtract(x), r * .90F, g * .94F, b, a);
        WaterGeometry.quad(out, IDENTITY, center.subtract(z).add(y).add(x),
                center.subtract(z).subtract(y).add(x),
                center.add(z).subtract(y).add(x),
                center.add(z).add(y).add(x), r * .90F, g * .94F, b, a);
        // ±Y
        WaterGeometry.quad(out, IDENTITY, center.add(x).add(z).add(y),
                center.subtract(x).add(z).add(y),
                center.subtract(x).subtract(z).add(y),
                center.add(x).subtract(z).add(y), r * 1.06F, g * 1.04F, b, a);
        WaterGeometry.quad(out, IDENTITY, center.subtract(x).add(z).subtract(y),
                center.add(x).add(z).subtract(y),
                center.add(x).subtract(z).subtract(y),
                center.subtract(x).subtract(z).subtract(y), r * .72F, g * .78F, b * .88F, a);
    }

    private static Vec3 bezierAt(Vec3 from, Vec3 ctrl, Vec3 to, float f) {
        float g = 1 - f;
        return new Vec3(
                g * g * from.x + 2 * g * f * ctrl.x + f * f * to.x,
                g * g * from.y + 2 * g * f * ctrl.y + f * f * to.y,
                g * g * from.z + 2 * g * f * ctrl.z + f * f * to.z);
    }

    // ==================================================================
    //  参数（测试用 static 钩子）
    // ==================================================================

    /** 体型：t1 就 1.08，t5 到 1.40 —— 越大越"大只"，但都是同一套比例的缩放。 */
    static double scaleFor(int tier) {
        return 1.08 + .08 * (Mth.clamp(tier, 1, 5) - 1);
    }

    /** 全长（格）= 2.8 × scale ⇒ t1 ≈ 3.0 格、t5 ≈ 3.9 格（雷龙小版只有 0.75 格）。 */
    static double lengthFor(double scale) {
        return 2.8 * scale;
    }

    /** 每档放几条龙：低阶一条，t4 两条，t5 三条。 */
    static int spawnCountFor(int tier) {
        return tier >= 5 ? 3 : tier >= 4 ? 2 : 1;
    }

    /** 第 i 条龙在读条进度多少时放出来（必须递增、且都在 (0,1)）。 */
    static float spawnRatioFor(int tier, int index) {
        float base = tier >= 5 ? .22F : .34F;
        float step = tier >= 5 ? .24F : .30F;
        return Math.min(.94F, base + step * index);
    }

    /** 一条龙的寿命（tick）：越大越久，但都不超过 26 tick。 */
    static int lifeFor(int tier) {
        return 17 + Mth.clamp(tier, 1, 5);
    }

    /** 测试用：贝塞尔的端点必须正好是出生点/胸口。 */
    static Vec3 bezierFor(Vec3 from, Vec3 ctrl, Vec3 to, float f) {
        return bezierAt(from, ctrl, to, f);
    }

    // ==================================================================
    //  读条状态
    // ==================================================================

    private record CastInfo(int tier, float ratio) {
    }

    private record Dragon(Vec3 from, Vec3 ctrl, Vec3 to, long born, int life, long seed,
                          float scale, int tier) {
    }

    private record Burst(Vec3 at, long start, float strength, int tier) {
    }

    private static final class CastState {
        int tier;
        float ratio;
        double x, y, z;
        double height = 1.8;
        long lastSeen;
        boolean gateLogged;
    }

    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/waterdragon")
                        .info("TN-C/waterdragon: spell_engine absent -> water dragon idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/waterdragon")
                        .warn("TN-C/waterdragon: cast scan failed, water dragon disabled ({})", t.toString());
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
        if (spell.group == null || !WATER_GROUPS.contains(spell.group)) {
            return null;
        }
        int tier = Mth.clamp(spell.learn == null ? 1 : spell.learn.tier, 1, 5);
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0, 1);
        return new CastInfo(tier, ratio);
    }
}
