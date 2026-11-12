package com.tnc.tnc.travel.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.client.DarkCastFx;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.travel.TravelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 元素附身的第三人称形态外观（七系）。
 *
 * <p>服务端只负责玩法状态；这里做两件事：
 * <ol>
 *   <li>在 {@link RenderPlayerEvent.Pre} 取消原版人形（含盔甲／手持物／名牌）；</li>
 *   <li>在 {@link RenderLevelStageEvent} 的 {@code AFTER_WEATHER} 里，用和法术同一套
 *       自发光几何把玩家的位置画成元素本体。</li>
 * </ol>
 *
 * <h2>七系形态（2026-11-08 作者定稿）</h2>
 * <table>
 *   <tr><td>雷</td><td>横雷 ＋ <b>往旁边放电</b>（射线打到墙/怪就拉一道弧）</td></tr>
 *   <tr><td>火</td><td>燃烧的<b>火球</b>（脉动核心 ＋ 往上舔的火舌）＋ 燃烧拖尾</td></tr>
 *   <tr><td>水</td><td><b>激流</b>（绕身螺旋水线 ＋ 前方水舌）</td></tr>
 *   <tr><td>风</td><td><b>风球</b>（三圈旋转风环）＋ 服务端引力牵引敌人</td></tr>
 *   <tr><td>土</td><td><b>裂纹</b>（脚下地裂 ＋ 身上的琥珀色裂线）</td></tr>
 *   <tr><td>光</td><td><b>翅膀</b>（两扇光羽，随呼吸扇动）</td></tr>
 *   <tr><td>暗</td><td><b>黑夜之手</b>（黑手/黑雾手同一只线勾手）＋ 黑雾拖尾</td></tr>
 * </table>
 *
 * <p>颜色全走顶点色（{@code boltTinted}/{@code flashTinted}），不新增任何贴图；
 * 画法统一是"白芯 ＋ 元素色辉光"，七系看起来是同一套语言的不同元素。
 *
 * <h2>为什么在 AFTER_WEATHER 单独开 BufferSource</h2>
 * 和 {@code DarkCastFx} 一致：这个阶段深度缓冲已经画完（能被墙挡住 ✓），
 * 自己 {@code endBatch} 保证一定提交；{@code RenderType.lightning()} 走的是天气帧缓冲，
 * 画进实体渲染根本不显示 ✗（见 {@code LightningRenderTypes} 的类注释）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TravelFormRenderer {

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));

    /**
     * 单位矩阵：几何全部用"相机相对坐标 + 单位矩阵"，所以一帧画几百段也不用
     * 每段 new 一个 {@link Matrix4f}（7 个形态 × 每帧几十段 = 每秒上千次分配 ✗）。
     * 几何只读这个矩阵，不会改它。
     */
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static final float[] FIRE_MID = {1.00F, 0.42F, 0.10F};
    private static final float[] FIRE_GLOW = {1.00F, 0.24F, 0.04F};
    private static final float[] WATER_MID = {0.22F, 0.62F, 1.00F};
    private static final float[] WATER_GLOW = {0.08F, 0.40F, 0.95F};
    private static final float[] WIND_MID = {0.72F, 1.00F, 0.84F};
    private static final float[] WIND_GLOW = {0.40F, 0.92F, 0.68F};
    private static final float[] LIGHT_MID = {1.00F, 0.95F, 0.62F};
    private static final float[] LIGHT_GLOW = {1.00F, 0.84F, 0.30F};
    private static final float[] EARTH_MID = {0.98F, 0.55F, 0.20F};
    private static final float[] EARTH_GLOW = {0.70F, 0.32F, 0.08F};

    private TravelFormRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        // 原版人形整体不画；元素本体在 onRenderWorld 里画。
        if (TravelAppearance.elementOf(event.getEntity()) >= 0) {
            event.setCanceled(true);
        }
    }

    /** 变成形态时第一人称也不该还举着法杖/手。 */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (TravelClientState.active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();

        VertexConsumer glow = null;
        VertexConsumer dark = null;
        boolean anyGlow = false;
        boolean anyDark = false;

        for (Player player : level.players()) {
            int element = TravelAppearance.elementOf(player);
            TravelTrail.Tail tail = TravelTrail.tail(player.getId());
            boolean active = element >= 0 && element < Element.values().length;
            if (!active && tail == null) {
                continue;
            }
            if (player.position().distanceToSqr(camera) > 96.0D * 96.0D) {
                continue;
            }
            // 正在淡出的残影用"它原来那个元素"的颜色，壳和爆发只用当前元素。
            int skin = active ? element : tail.element;
            boolean darkLayer = skin == Element.DARK.ordinal();
            // 第一人称时自己的形态壳正好糊在镜头上 ⇒ 不画（原版第一人称也不画自己）。
            // 但"变身那一瞬间"的光环要画：那是进入附身唯一的即时反馈 ✓
            // （拖尾在身后，第一人称本来就看不见，一起跳过省事。）
            boolean firstPersonSelf = firstPerson && player == minecraft.player;
            if (firstPersonSelf && !active) {
                continue;
            }

            VertexConsumer vc;
            if (darkLayer) {
                if (dark == null) {
                    dark = BUFFERS.getBuffer(LightningRenderTypes.DARK);
                }
                vc = dark;
                anyDark = true;
            } else {
                if (glow == null) {
                    glow = BUFFERS.getBuffer(LightningRenderTypes.BOLT);
                }
                vc = glow;
                anyGlow = true;
            }

            if (tail != null && !firstPersonSelf) {
                renderTrail(vc, player, camera, partial, tail);
            }
            if (!active) {
                continue;   // 淡出中的残影只有尾巴，没有壳
            }
            renderBurst(vc, player, camera, partial, element);
            if (firstPersonSelf) {
                continue;
            }
            extraLayer(vc, player, camera, partial, element);
            if (darkLayer) {
                renderDark(vc, player, camera, partial);
            } else {
                renderGlow(vc, player, camera, partial, element);
            }
        }
        if (anyGlow) {
            BUFFERS.endBatch(LightningRenderTypes.BOLT);
        }
        if (anyDark) {
            BUFFERS.endBatch(LightningRenderTypes.DARK);
        }
    }

    /** 除暗系之外六种，都用加色自发光层。 */
    private static void renderGlow(VertexConsumer vc, Player player, Vec3 camera,
                                   float partial, int element) {
        if (element == Element.LIGHTNING.ordinal()) {
            renderLightning(vc, player, camera, partial);
        } else if (element == Element.FIRE.ordinal()) {
            renderFire(vc, player, camera, partial);
        } else if (element == Element.WATER.ordinal()) {
            renderWater(vc, player, camera, partial);
        } else if (element == Element.WIND.ordinal()) {
            renderWind(vc, player, camera, partial);
        } else if (element == Element.EARTH.ordinal()) {
            renderEarth(vc, player, camera, partial);
        } else if (element == Element.LIGHT.ordinal()) {
            renderLight(vc, player, camera, partial);
        }
    }

    // ------------------------------------------------------------------
    //  雷：横雷 + 往旁边放电
    // ------------------------------------------------------------------

    private static void renderLightning(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length() * 20.0D;

        Vec3 dir = horizontal(motion);
        if (dir == null) {
            dir = horizontal(player.getLookAngle());
        }
        if (dir == null) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }

        double forward = Mth.clamp(1.2D + speed * 0.17D, 1.4D, 4.6D);
        double back = Mth.clamp(0.55D + speed * 0.025D, 0.6D, 1.25D);
        Vec3 center = feet.add(0.0D, 1.02D, 0.0D);
        Vec3 head = center.add(dir.scale(forward));
        Vec3 tail = center.subtract(dir.scale(back));

        float flicker = 0.74F + 0.26F * Mth.sin((float) (time * 1.7D));
        flicker *= 0.86F + 0.14F * Mth.sin((float) (time * 5.3D) + player.getId());
        long seed = player.getId() * 0x9E3779B97F4A7C15L + (long) (time * 1.5D);

        // ① 主横雷（穿过身体）＋ ② 白芯
        bolt(vc, camera, tail, head, seed, 0.185F, flicker, LightningTheme.BLUE, 5);
        bolt(vc, camera, tail, head, seed + 11L, 0.070F,
                Math.min(1.0F, flicker * 1.25F), LightningTheme.BLUE, 2);
        // ③ 上下两条平行细雷，给横雷一点厚度。
        bolt(vc, camera, tail.add(0.0D, 0.16D, 0.0D), head.add(0.0D, 0.11D, 0.0D),
                seed + 30L, 0.075F, flicker * 0.55F, LightningTheme.BLUE, 2);
        bolt(vc, camera, tail.add(0.0D, -0.17D, 0.0D), head.add(0.0D, -0.12D, 0.0D),
                seed + 31L, 0.075F, flicker * 0.55F, LightningTheme.BLUE, 2);

        // ④ 雷尖爆闪
        LightningBoltGeometry.billboardFlash(vc, IDENTITY,
                head.x - camera.x, head.y - camera.y, head.z - camera.z,
                0.26F + 0.07F * Mth.sin((float) (time * 3.1D)),
                LightningTheme.BLUE, flicker * 0.6F, 0.0D, 0.0D, 0.0D);

        // ⑤ 「电一下旁边」：八个方向打到哪就放电到哪，每 tick 换种子 ⇒ 噼啪乱跳。
        ClientLevel level = (ClientLevel) player.level();
        long zapSeed = player.getId() * 7919L + (long) (time * 2.0D);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D + time * 0.11D;
            double lift = Math.sin(time * 0.7D + i * 1.3D) * 0.45D;
            Vec3 ray = new Vec3(Math.cos(angle), lift, Math.sin(angle)).normalize();
            Vec3 from = center.add(ray.scale(0.35D));
            Vec3 to = center.add(ray.scale(3.4D));
            BlockHitResult hit = level.clip(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            LightningBoltGeometry.arc(vc, IDENTITY,
                    from.x - camera.x, from.y - camera.y, from.z - camera.z,
                    hit.getLocation().x - camera.x, hit.getLocation().y - camera.y,
                    hit.getLocation().z - camera.z,
                    zapSeed + i, 0.034F, flicker * 0.8F, LightningTheme.BLUE,
                    0.0D, 0.0D, 0.0D);
        }
        // ⑥ 旁边的活物也电一下（只画弧，不造成伤害：附身是赶路，不是战斗）。
        //    用 AABB 查询而不是遍历全世界的实体：后者每帧都要扫一遍已加载实体 ✗。
        for (Entity other : level.getEntities(player, player.getBoundingBox().inflate(3.2D))) {
            if (!(other instanceof LivingEntity) || other.getBoundingBox().getCenter()
                    .distanceToSqr(center) > 3.2D * 3.2D) {
                continue;
            }
            Vec3 at = other.getBoundingBox().getCenter();
            LightningBoltGeometry.arc(vc, IDENTITY,
                    center.x - camera.x, center.y - camera.y, center.z - camera.z,
                    at.x - camera.x, at.y - camera.y, at.z - camera.z,
                    zapSeed + 100L + other.getId(), 0.030F, flicker * 0.7F,
                    LightningTheme.BLUE, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    //  火：燃烧的火球
    // ------------------------------------------------------------------

    private static void renderFire(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 center = feet.add(0.0D, 0.95D, 0.0D);
        float pulse = 0.82F + 0.18F * Mth.sin((float) (time * 2.3D));
        long seed = player.getId() * 0x2545F4914F6CDD1DL + (long) (time * 2.0D);

        // ① 火球本体：三层同心光斑，越大越暗 ⇒ 白热核心 + 橙色火边。
        flash(vc, camera, center, 0.66F * pulse, FIRE_MID, 0.55F);
        flash(vc, camera, center, 0.42F * pulse, FIRE_MID, 0.75F);
        flash(vc, camera, center.add(0.0D, 0.05D, 0.0D), 0.20F * pulse, FIRE_MID, 0.9F);

        // ② 火舌：从球心往上、往四周舔，每根都短、都抖。
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0D + time * 0.9D;
            double radius = 0.34D + 0.10D * Math.sin(time * 3.1D + i);
            Vec3 from = center.add(Math.cos(angle) * radius * 0.5D,
                    -0.10D + 0.05D * Math.sin(time * 2.0D + i), Math.sin(angle) * radius * 0.5D);
            Vec3 to = center.add(Math.cos(angle) * radius,
                    0.28D + 0.22D * Math.abs(Math.sin(time * 2.6D + i * 1.7D)),
                    Math.sin(angle) * radius);
            strand(vc, camera, from, to, seed + 40L + i, 0.085F, 0.72F * pulse,
                    FIRE_MID, FIRE_GLOW, 1);
        }

        // ③ 球壳：一圈横向火弧，让"球"有边。
        for (int i = 0; i < 8; i++) {
            double a0 = i * Math.PI / 4.0D + time * 0.5D;
            double a1 = a0 + Math.PI / 8.0D;
            Vec3 p0 = center.add(Math.cos(a0) * 0.52D, Math.sin(a0) * 0.18D, Math.sin(a0) * 0.52D);
            Vec3 p1 = center.add(Math.cos(a1) * 0.52D, Math.sin(a1) * 0.18D, Math.sin(a1) * 0.52D);
            strand(vc, camera, p0, p1, seed + 70L + i, 0.045F, 0.55F * pulse,
                    FIRE_MID, FIRE_GLOW, 0);
        }
    }

    // ------------------------------------------------------------------
    //  水：激流
    // ------------------------------------------------------------------

    private static void renderWater(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length() * 20.0D;
        Vec3 dir = horizontal(motion);
        if (dir == null) {
            dir = horizontal(player.getLookAngle());
        }
        if (dir == null) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 center = feet.add(0.0D, 0.95D, 0.0D);
        long seed = player.getId() * 0x9E3779B97F4A7C15L + (long) (time * 2.0D);
        float pulse = 0.80F + 0.20F * Mth.sin((float) (time * 3.4D));

        // ① 螺旋水线：三条绕身体转，整体顺着运动方向拉长 ⇒ "激流"。
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        for (int i = 0; i < 3; i++) {
            double phase = i * Math.PI * 2.0D / 3.0D;
            Vec3 prev = null;
            for (int k = 0; k <= 8; k++) {
                double t = k / 8.0D;
                double angle = phase + t * Math.PI * 2.6D + time * 0.55D;
                double radius = 0.40D - 0.10D * t;
                Vec3 point = center
                        .add(dir.scale((t - 0.35D) * (1.5D + speed * 0.05D)))
                        .add(side.scale(Math.cos(angle) * radius))
                        .add(0.0D, Math.sin(angle) * radius, 0.0D);
                if (prev != null) {
                    strand(vc, camera, prev, point, seed + i * 31L + k, 0.055F,
                            0.62F * pulse, WATER_MID, WATER_GLOW, 0);
                }
                prev = point;
            }
        }
        // ② 前冲水舌：速度越快越长，像被激流顶着走。
        Vec3 jetFrom = center.add(dir.scale(0.25D));
        Vec3 jetTo = center.add(dir.scale(1.1D + speed * 0.12D));
        bolt(vc, camera, jetFrom, jetTo, seed + 300L, 0.10F, 0.70F * pulse,
                new float[]{WATER_MID[0], WATER_MID[1], WATER_MID[2]}, WATER_GLOW, 2);

        // ③ 身体一圈水光环，读起来是"一团水"而不是几根线。
        for (int i = 0; i < 10; i++) {
            double a0 = i * Math.PI / 5.0D + time * 0.4D;
            double a1 = a0 + Math.PI / 10.0D;
            strand(vc, camera,
                    center.add(Math.cos(a0) * 0.46D, Math.sin(a0 * 2.0D) * 0.10D,
                            Math.sin(a0) * 0.46D),
                    center.add(Math.cos(a1) * 0.46D, Math.sin(a1 * 2.0D) * 0.10D,
                            Math.sin(a1) * 0.46D),
                    seed + 400L + i, 0.040F, 0.48F * pulse, WATER_MID, WATER_GLOW, 0);
        }
    }

    // ------------------------------------------------------------------
    //  风：风球（牵引敌人是服务端做的）
    // ------------------------------------------------------------------

    private static void renderWind(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 center = feet.add(0.0D, 1.0D, 0.0D);
        long seed = player.getId() * 0x2545F4914F6CDD1DL + (long) (time * 2.0D);
        float pulse = 0.78F + 0.22F * Mth.sin((float) (time * 2.6D));

        // ① 三圈风环：倾角不同、方向相反，转起来就是一个球在滚。
        for (int ring = 0; ring < 3; ring++) {
            double tilt = ring * Math.PI / 3.0D + time * 0.18D;
            double spin = time * (0.9D + ring * 0.25D) * (ring % 2 == 0 ? 1.0D : -1.0D);
            double radius = 0.62D + ring * 0.06D;
            for (int i = 0; i < 12; i++) {
                double a0 = spin + i * Math.PI / 6.0D;
                double a1 = a0 + Math.PI / 12.0D;
                Vec3 p0 = ringPoint(center, tilt, a0, radius);
                Vec3 p1 = ringPoint(center, tilt, a1, radius);
                strand(vc, camera, p0, p1, seed + ring * 100L + i, 0.048F,
                        0.60F * pulse, WIND_MID, WIND_GLOW, 0);
            }
        }
        // ② 球心一层淡淡的亮 ⇒ 风球有"芯"。
        flash(vc, camera, center, 0.30F * pulse, WIND_MID, 0.30F);

        // ③ 被吸进来的气流：几条从外往内收的短线。
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2.0D / 5.0D - time * 1.1D;
            double outer = 1.25D - (time * 0.6D + i * 0.4D) % 0.9D;
            Vec3 from = center.add(Math.cos(angle) * outer, 0.22D * Math.sin(time + i),
                    Math.sin(angle) * outer);
            Vec3 to = center.add(Math.cos(angle) * (outer - 0.32D),
                    0.16D * Math.sin(time + i), Math.sin(angle) * (outer - 0.32D));
            strand(vc, camera, from, to, seed + 500L + i, 0.038F, 0.45F * pulse,
                    WIND_MID, WIND_GLOW, 0);
        }
    }

    /** 倾斜圆环上的一点（风球用）。 */
    private static Vec3 ringPoint(Vec3 center, double tilt, double angle, double radius) {
        double x = Math.cos(angle) * radius;
        double y = Math.sin(angle) * radius;
        double cz = Math.sin(angle) * radius * 0.35D;
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return center.add(x, y * cos - cz * sin, y * sin + cz * cos);
    }

    // ------------------------------------------------------------------
    //  土：裂纹
    // ------------------------------------------------------------------

    private static void renderEarth(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 center = feet.add(0.0D, 0.95D, 0.0D);
        long seed = player.getId() * 0x9E3779B97F4A7C15L + (long) (time * 1.2D);
        float pulse = 0.75F + 0.25F * Mth.sin((float) (time * 1.4D));

        // ① 脚下地裂：贴着地面画（飞高了就不画，避免一圈裂纹悬在空中）。
        ClientLevel level = (ClientLevel) player.level();
        BlockHitResult ground = level.clip(new ClipContext(
                feet.add(0.0D, 0.2D, 0.0D), feet.subtract(0.0D, 1.6D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (ground.getType() != HitResult.Type.MISS) {
            Vec3 at = ground.getLocation();
            LightningBoltGeometry.groundWeb(vc, IDENTITY,
                    at.x - camera.x, at.y + 0.06D - camera.y, at.z - camera.z,
                    1.25F, seed, LightningTheme.GOLD, 0.65F * pulse, (float) (time * 0.25D),
                    0.0D, 0.0D, 0.0D, true);
        }

        // ② 身上的琥珀裂线：绕身体一圈的折线，像石头壳裂开露出的熔线。
        for (int i = 0; i < 5; i++) {
            double base = i * Math.PI * 2.0D / 5.0D + time * 0.12D;
            Vec3 prev = null;
            for (int k = 0; k <= 4; k++) {
                double t = k / 4.0D;
                double angle = base + Math.sin(time * 0.8D + k * 1.7D + i) * 0.28D;
                double radius = 0.30D + 0.10D * Math.sin(t * Math.PI);
                Vec3 point = center
                        .add(Math.cos(angle) * radius, (t - 0.5D) * 1.5D,
                                Math.sin(angle) * radius);
                if (prev != null) {
                    strand(vc, camera, prev, point, seed + i * 47L + k, 0.040F,
                            0.62F * pulse, EARTH_MID, EARTH_GLOW, 0);
                }
                prev = point;
            }
        }

        // ③ 躯干轮廓：几条竖直裂纹，读起来还是"人形的一块石头"。
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0D + 0.4D;
            double radius = 0.38D;
            Vec3 from = center.add(Math.cos(angle) * radius, -0.75D, Math.sin(angle) * radius);
            Vec3 to = center.add(Math.cos(angle + 0.5D) * radius * 0.7D, 0.66D,
                    Math.sin(angle + 0.5D) * radius * 0.7D);
            strand(vc, camera, from, to, seed + 200L + i, 0.045F, 0.40F * pulse,
                    EARTH_MID, EARTH_GLOW, 1);
        }
    }

    // ------------------------------------------------------------------
    //  光：翅膀
    // ------------------------------------------------------------------

    private static void renderLight(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length() * 20.0D;
        Vec3 dir = horizontal(motion);
        if (dir == null) {
            dir = horizontal(player.getLookAngle());
        }
        if (dir == null) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 back = dir.scale(-1.0D);
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        Vec3 center = feet.add(0.0D, 1.05D, 0.0D);
        long seed = player.getId() * 0x2545F4914F6CDD1DL + (long) (time * 1.6D);
        float pulse = 0.80F + 0.20F * Mth.sin((float) (time * 2.2D));
        double flap = Math.sin(time * 0.28D) * 0.22D;

        // 两扇翅膀：每扇 6 根光羽，从肩根扇形张开，翼尖略往身后收。
        for (int s = 0; s < 2; s++) {
            double sgn = s == 0 ? 1.0D : -1.0D;
            Vec3 root = center.add(side.scale(0.16D * sgn)).add(0.0D, 0.28D, 0.0D);
            for (int f = 0; f < 6; f++) {
                double spread = 0.18D + f * 0.20D;            // 0.18 .. 1.18
                double length = (1.05D + f * 0.10D) * (0.92D + speed * 0.004D);
                double lift = 0.55D + Math.sin(spread) * 0.85D + flap;
                Vec3 tip = root
                        .add(side.scale(sgn * Math.sin(spread) * length))
                        .add(0.0D, Math.sin(spread) * length * 0.55D + lift * 0.15D, 0.0D)
                        .add(back.scale(0.22D + f * 0.06D));
                strand(vc, camera, root, tip, seed + s * 100L + f, 0.055F,
                        0.78F * pulse, LIGHT_MID, LIGHT_GLOW, 0);
                // 次级羽枝：让"翅膀"有羽毛感，而不是几根直线。
                Vec3 mid = root.lerp(tip, 0.55D);
                Vec3 branch = tip.add(0.0D, -0.22D, 0.0D).add(side.scale(sgn * 0.12D));
                strand(vc, camera, mid, branch, seed + s * 100L + f + 40L, 0.032F,
                        0.45F * pulse, LIGHT_MID, LIGHT_GLOW, 0);
            }
            // 翼根亮斑。
            flash(vc, camera, root, 0.22F * pulse, LIGHT_MID, 0.45F);
        }

        // 头顶一枚小光点 + 身体一圈淡光环：远看是一团"发光的人"。
        flash(vc, camera, center.add(0.0D, 0.62D, 0.0D), 0.16F * pulse, LIGHT_MID, 0.60F);
        for (int i = 0; i < 8; i++) {
            double a0 = i * Math.PI / 4.0D + time * 0.3D;
            double a1 = a0 + Math.PI / 8.0D;
            strand(vc, camera,
                    center.add(Math.cos(a0) * 0.44D, -0.45D, Math.sin(a0) * 0.44D),
                    center.add(Math.cos(a1) * 0.44D, -0.45D, Math.sin(a1) * 0.44D),
                    seed + 400L + i, 0.035F, 0.35F * pulse, LIGHT_MID, LIGHT_GLOW, 0);
        }
    }

    // ------------------------------------------------------------------
    //  暗：黑夜之手 + 黑雾拖尾
    // ------------------------------------------------------------------

    private static void renderDark(VertexConsumer vc, Player player, Vec3 camera, float partial) {
        double time = player.tickCount + partial;
        Vec3 feet = player.getPosition(partial);
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length() * 20.0D;

        Vec3 palm = horizontal(motion);
        if (palm == null) {
            palm = horizontal(player.getLookAngle());
        }
        if (palm == null) {
            palm = new Vec3(0.0D, 0.0D, 1.0D);
        }

        // DarkHandLines 的局部高度约 1.52 格；×1.28 ≈ 1.95，正好一个玩家高。
        float scale = 1.28F;
        double bob = Math.sin(time * 0.11D) * 0.035D + Math.min(0.10D, speed * 0.005D);
        Vec3 wrist = feet.add(0.0D, 0.03D + bob, 0.0D).subtract(palm.scale(0.08D));
        float clench = 0.10F + 0.06F * Mth.sin((float) (time * 0.17D));
        long seed = player.getId() * 0x2545F4914F6CDD1DL + (long) (time * 0.5D);
        DarkCastFx.renderHand(vc, wrist, palm, scale, 1.0F, clench, 0.95F, seed, camera);

        // 黑雾：手周围一团缓慢翻滚的雾线，往身后拖。
        Vec3 back = palm.scale(-1.0D);
        Vec3 side = new Vec3(-palm.z, 0.0D, palm.x);
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2.0D / 7.0D + time * 0.30D;
            double radius = 0.55D + 0.12D * Mth.sin((float) (time * 0.9D) + i);
            Vec3 from = feet.add(0.0D, 0.85D + 0.35D * Math.sin(time * 0.6D + i), 0.0D)
                    .add(side.scale(Math.cos(angle) * radius))
                    .add(palm.scale(Math.sin(angle) * radius));
            Vec3 to = from.add(back.scale(0.55D + 0.35D * ((i + time * 0.25D) % 1.0D)))
                    .add(0.0D, -0.10D + 0.16D * Math.sin(time * 1.1D + i * 2.0D), 0.0D);
            LightningBoltGeometry.darkBolt(vc, IDENTITY,
                    from.x - camera.x, from.y - camera.y, from.z - camera.z,
                    to.x - camera.x, to.y - camera.y, to.z - camera.z,
                    seed + i * 37L, 0.042F, 0.42F, 1.0D, 0.0D, 0.0D, 0.0D);
        }
        // 手腕后两段更实的黑线：高速移动时不会像一只手在原地平移。
        if (speed > 1.0D) {
            for (int i = 0; i < 2; i++) {
                Vec3 from = wrist.add(0.0D, 0.10D + i * 0.22D, 0.0D);
                Vec3 to = from.subtract(palm.scale(0.30D + i * 0.25D))
                        .add(0.0D, -0.10D - i * 0.05D, 0.0D);
                LightningBoltGeometry.darkBolt(vc, IDENTITY,
                        from.x - camera.x, from.y - camera.y, from.z - camera.z,
                        to.x - camera.x, to.y - camera.y, to.z - camera.z,
                        seed + 60L + i, 0.032F, 0.55F, 1.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------
    //  元素附加层（各系的环境级细节）
    // ------------------------------------------------------------------

    /**
     * 形态壳之外的"环境层"：让每个元素在自己脚下/四周留下痕迹。
     *
     * <p>形态壳负责"我变成了什么"，这一层负责"我经过的地方有什么反应"——
     * 只有壳的话，飞过草地和水面完全一样，缺了元素该有的环境感。
     */
    private static void extraLayer(VertexConsumer vc, Player player, Vec3 camera,
                                   float partial, int element) {
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        Vec3 feet = player.position().add(0.0D, 0.10D, 0.0D);
        double time = player.tickCount + partial;
        float pulse = 0.75F + 0.25F * Mth.sin((float) (time * 0.35D));
        long seed = player.getId() * 613L;

        if (element == Element.LIGHTNING.ordinal()) {
            // 雷笼：每 20 tick 亮 6 tick，像电在四周找地方跳。
            if (player.tickCount % 20 < 6) {
                LightningBoltGeometry.cage(vc, IDENTITY,
                        center.x - camera.x, center.y - camera.y, center.z - camera.z,
                        1.55F, 2.10F, seed + player.tickCount / 20,
                        LightningTheme.BLUE, 0.50F * pulse, (float) (time * 0.05D),
                        0.0D, 0.0D, 0.0D);
            }
            return;
        }
        if (element == Element.LIGHT.ordinal()) {
            // 八道放射光线 + 脚下光池。
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D + time * 0.02D;
                double reach = 1.45D + 0.35D * Math.sin(time * 0.4D + i);
                Vec3 tip = center.add(Math.cos(angle) * reach,
                        0.55D * Math.sin(time * 0.3D + i), Math.sin(angle) * reach);
                strand(vc, camera, center, tip, seed + i, 0.020F, 0.32F * pulse,
                        LIGHT_MID, LIGHT_GLOW, 4);
            }
            flash(vc, camera, feet, 1.10F, LIGHT_MID, 0.22F * pulse);
            return;
        }
        if (element == Element.FIRE.ordinal()) {
            // 脚边一圈热浪往上卷 + 少量火星。
            for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI / 5.0D + time * 0.03D;
                Vec3 base = player.position().add(Math.cos(angle) * 0.55D, 0.10D,
                        Math.sin(angle) * 0.55D);
                Vec3 up = base.add(0.0D, 0.55D + 0.20D * Math.sin(time * 0.5D + i), 0.0D);
                strand(vc, camera, base, up, seed + i, 0.014F, 0.38F * pulse,
                        FIRE_MID, FIRE_GLOW, 3);
            }
            if (player.tickCount % 4 == 0) {
                flash(vc, camera, center.add(0.0D, 0.4D, 0.0D), 0.35F, FIRE_MID, 0.30F);
            }
            return;
        }
        if (element == Element.WATER.ordinal()) {
            // 尾流：脚下两圈水花，越靠外越淡。
            for (int ring = 0; ring < 2; ring++) {
                double radius = 0.55D + ring * 0.35D + 0.10D * Math.sin(time * 0.4D + ring);
                float alpha = (0.34F - ring * 0.12F) * pulse;
                for (int i = 0; i < 12; i++) {
                    double a0 = i * Math.PI / 6.0D + time * 0.05D * (ring == 0 ? 1 : -1);
                    Vec3 p0 = ringPoint(feet, 0.0D, a0, radius);
                    Vec3 p1 = ringPoint(feet, 0.0D, a0 + Math.PI / 6.0D, radius);
                    trailArc(vc, camera, p0, p1, seed + ring * 40L + i, 0.028F, alpha,
                            new float[]{1.0F, 1.0F, 1.0F}, WATER_MID, WATER_GLOW);
                }
            }
            return;
        }
        if (element == Element.WIND.ordinal()) {
            // 地面尘环：风把人托起来时地上的灰被吹开。
            for (int i = 0; i < 14; i++) {
                double a0 = i * Math.PI / 7.0D - time * 0.06D;
                Vec3 p0 = ringPoint(feet, 0.0D, a0, 1.05D + 0.15D * Math.sin(time * 0.5D + i));
                Vec3 p1 = ringPoint(feet, 0.0D, a0 + Math.PI / 7.0D,
                        1.05D + 0.15D * Math.sin(time * 0.5D + i + 1));
                trailArc(vc, camera, p0, p1, seed + i, 0.026F, 0.26F * pulse,
                        new float[]{1.0F, 1.0F, 1.0F}, WIND_MID, WIND_GLOW);
            }
            return;
        }
        if (element == Element.EARTH.ordinal()) {
            // 碎土往下掉：几颗小光点 + 短竖线，读起来像被带起来的土块。
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3.0D + time * 0.02D;
                Vec3 top = player.position().add(Math.cos(angle) * 0.65D, 0.35D,
                        Math.sin(angle) * 0.65D);
                Vec3 down = top.subtract(0.0D, 0.45D + 0.25D * Math.sin(time * 0.6D + i), 0.0D);
                strand(vc, camera, top, down, seed + i, 0.016F, 0.30F * pulse,
                        EARTH_MID, EARTH_GLOW, 2);
            }
            return;
        }
        if (element == Element.DARK.ordinal()) {
            // 墨滴：暗雾往下滴，落到半空就散。
            for (int i = 0; i < 5; i++) {
                double angle = i * Math.PI * 2.0D / 5.0D + time * 0.04D;
                Vec3 top = center.add(Math.cos(angle) * 0.42D, 0.30D, Math.sin(angle) * 0.42D);
                Vec3 down = top.subtract(0.0D, 0.60D + 0.30D * Math.sin(time * 0.5D + i), 0.0D);
                LightningBoltGeometry.darkBolt(vc, IDENTITY,
                        top.x - camera.x, top.y - camera.y, top.z - camera.z,
                        down.x - camera.x, down.y - camera.y, down.z - camera.z,
                        seed + i, 0.030F, 0.55F * pulse, 1.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------
    //  运动残影 + 入场爆发（七系共用）
    // ------------------------------------------------------------------

    /**
     * 沿 {@link TravelTrail} 记下来的历史位置画一道尾巴。
     *
     * <p>宽度/亮度都随“越靠近身体越大”递增，末端直接贴到当前位置，
     * 所以急转弯时尾巴会跟着甩过去，而不是留在原地 ✗。
     */
    private static void renderTrail(VertexConsumer vc, Player player, Vec3 camera,
                                    float partial, TravelTrail.Tail tail) {
        List<Vec3> samples = tail.points;
        if (samples.size() < 2 || tail.element < 0) {
            return;
        }
        int element = tail.element;
        // 退出附身后尾巴继续淡出 8 tick，而不是"啪"一下被剪掉。
        float fade = tail.active ? 1.0F
                : Math.max(0.0F, 1.0F - (float) tail.age / (TravelTrail.FADE_TICKS + 1));
        if (fade <= 0.01F) {
            return;
        }
        double time = player.tickCount + partial;
        boolean dark = element == Element.DARK.ordinal();
        float[] core = coreColor(element);
        float[] mid = midColor(element);
        float[] glow = glowColor(element);
        int count = samples.size();
        // 渲染位置是插值过的（partial），历史点却是整数 tick 的位置；
        // 头部必须也用插值位置，否则每一 tick 尾巴都会往回"缩"一格 ✗。
        Vec3 head = player.getPosition(partial).add(0.0D, 1.0D, 0.0D);
        // 站住不动时不该拖一条 20 格的尾巴：按最近两个点的间距缩放到 0.35~1。
        float motion = 1.0F;
        if (count >= 2) {
            double step = Math.sqrt(samples.get(count - 1)
                    .distanceToSqr(samples.get(count - 2)));
            motion = (float) Math.min(1.0D, 0.35D + step * 0.9D);
        }
        float scale = fade * motion;

        for (int i = 1; i < count; i++) {
            float t = (float) i / (count - 1);
            Vec3 from = samples.get(i - 1);
            Vec3 to = i == count - 1 ? head : samples.get(i);
            if (from.distanceToSqr(to) < 1.0E-6D) {
                continue;
            }
            float width = (0.045F + 0.115F * t) * (0.75F + 0.25F * scale);
            float alpha = (0.09F + 0.80F * t * t) * scale;
            long seed = player.getId() * 131L + i * 17L + (long) (time * 0.7D);

            if (dark) {
                LightningBoltGeometry.darkBolt(vc, IDENTITY,
                        from.x - camera.x, from.y - camera.y, from.z - camera.z,
                        to.x - camera.x, to.y - camera.y, to.z - camera.z,
                        seed, width * 1.6F, alpha, 1.0D, 0.0D, 0.0D, 0.0D);
                continue;
            }
            // 两层：外面一层宽的彩色辉光，里面一层细的白芯，尾巴才有"热"的感觉。
            LightningBoltGeometry.arcTinted(vc, IDENTITY,
                    from.x - camera.x, from.y - camera.y, from.z - camera.z,
                    to.x - camera.x, to.y - camera.y, to.z - camera.z,
                    seed, width, alpha, core, mid, glow, 0.0D, 0.0D, 0.0D);
            LightningBoltGeometry.arcTinted(vc, IDENTITY,
                    from.x - camera.x, from.y - camera.y, from.z - camera.z,
                    to.x - camera.x, to.y - camera.y, to.z - camera.z,
                    seed + 401L, width * 0.45F, alpha * 0.85F,
                    new float[]{1.0F, 1.0F, 1.0F}, mid, mid, 0.0D, 0.0D, 0.0D);
            if (t < 0.45F) {
                continue;   // 远端再挂细节也看不见，省掉
            }
            decorateTrail(vc, camera, element, from, to, width, alpha * fade,
                    t, i, seed, core, mid, glow, time);
        }
    }

    /** 各元素挂在拖尾上的小细节（火星 / 泡沫 / 螺旋 / 裂纹 / 光羽 / 分叉）。 */
    private static void decorateTrail(VertexConsumer vc, Vec3 camera, int element,
                                      Vec3 from, Vec3 to, float width, float alpha,
                                      float t, int index, long seed,
                                      float[] core, float[] mid, float[] glow, double time) {
        if (element == Element.LIGHTNING.ordinal()) {
            if (index % 3 == 1) {
                Vec3 tip = to.add(index % 2 == 0 ? 0.18D : -0.18D, 0.12D, 0.10D);
                trailArc(vc, camera, to, tip, seed + 5L, width * 0.5F, alpha * 0.6F,
                        core, mid, glow);
            }
            return;
        }
        if (element == Element.FIRE.ordinal()) {
            if (index % 2 == 0) {
                flash(vc, camera, to.add(0.0D, 0.10D, 0.0D),
                        0.055F + 0.05F * t, mid, alpha * 0.55F);
            }
            return;
        }
        if (element == Element.WATER.ordinal()) {
            Vec3 foam = to.subtract(0.0D, 0.10D, 0.0D);
            trailArc(vc, camera, to, foam, seed + 9L, width * 0.45F, alpha * 0.7F,
                    new float[]{1.0F, 1.0F, 1.0F}, WATER_MID, WATER_GLOW);
            return;
        }
        if (element == Element.WIND.ordinal()) {
            Vec3 side = new Vec3(-(to.z - from.z), 0.0D, to.x - from.x);
            if (side.lengthSqr() > 1.0E-6D) {
                double swing = 0.16D * Math.sin(time * 0.6D + index);
                trailArc(vc, camera, to, to.add(side.normalize().scale(swing)),
                        seed + 3L, width * 0.5F, alpha * 0.55F, core, mid, glow);
            }
            return;
        }
        if (element == Element.EARTH.ordinal()) {
            if (index % 2 == 1) {
                Vec3 tick = to.add(0.0D, index % 4 == 1 ? 0.10D : -0.10D, 0.12D);
                trailArc(vc, camera, to, tick, seed + 7L, width * 0.4F, alpha * 0.5F,
                        core, mid, glow);
            }
            return;
        }
        if (element == Element.LIGHT.ordinal() && index % 2 == 0) {
            flash(vc, camera, to, 0.05F + 0.04F * t, mid, alpha * 0.5F);
        }
    }

    /** 入场爆发：一圈横向 + 一圈纵向的光环往外炸开，0.6 秒收干净。 */
    private static void renderBurst(VertexConsumer vc, Player player, Vec3 camera,
                                    float partial, int element) {
        int age = TravelTrail.burstAge(player.getId(), player.tickCount);
        if (age < 0 || age > TravelTrail.BURST_TICKS) {
            return;
        }
        float t = (age + partial) / TravelTrail.BURST_TICKS;
        float radius = 0.55F + 2.3F * t;
        float alpha = (1.0F - t) * 0.75F;
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        boolean dark = element == Element.DARK.ordinal();
        float[] core = coreColor(element);
        float[] mid = midColor(element);
        float[] glow = glowColor(element);
        long seed = player.getId() * 977L + age;
        int segments = 10;
        for (int ring = 0; ring < 2; ring++) {
            double tilt = ring == 0 ? 0.0D : Math.PI / 2.0D;
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2.0D / segments;
                double a1 = a0 + Math.PI * 2.0D / segments;
                Vec3 p0 = ringPoint(center, tilt, a0, radius);
                Vec3 p1 = ringPoint(center, tilt, a1, radius);
                if (dark) {
                    LightningBoltGeometry.darkBolt(vc, IDENTITY,
                            p0.x - camera.x, p0.y - camera.y, p0.z - camera.z,
                            p1.x - camera.x, p1.y - camera.y, p1.z - camera.z,
                            seed + ring * 50L + i, 0.05F, alpha, 1.0D,
                            0.0D, 0.0D, 0.0D);
                } else {
                    trailArc(vc, camera, p0, p1, seed + ring * 50L + i, 0.055F, alpha,
                            core, mid, glow);
                }
            }
        }
        flash(vc, camera, center, 0.30F + 1.0F * t * (1.0F - t), mid, alpha * 0.8F);
    }

    /** 一条短的加色弧（拖尾/细节/光环共用）。 */
    private static void trailArc(VertexConsumer vc, Vec3 camera, Vec3 from, Vec3 to,
                                 long seed, float width, float alpha,
                                 float[] core, float[] mid, float[] glow) {
        LightningBoltGeometry.arcTinted(vc, IDENTITY,
                from.x - camera.x, from.y - camera.y, from.z - camera.z,
                to.x - camera.x, to.y - camera.y, to.z - camera.z,
                seed, width, alpha, core, mid, glow, 0.0D, 0.0D, 0.0D);
    }

    private static float[] midColor(int element) {
        if (element == Element.FIRE.ordinal()) {
            return FIRE_MID;
        }
        if (element == Element.WATER.ordinal()) {
            return WATER_MID;
        }
        if (element == Element.WIND.ordinal()) {
            return WIND_MID;
        }
        if (element == Element.EARTH.ordinal()) {
            return EARTH_MID;
        }
        if (element == Element.LIGHT.ordinal()) {
            return LIGHT_MID;
        }
        return LightningTheme.mid(LightningTheme.BLUE);
    }

    private static float[] glowColor(int element) {
        if (element == Element.FIRE.ordinal()) {
            return FIRE_GLOW;
        }
        if (element == Element.WATER.ordinal()) {
            return WATER_GLOW;
        }
        if (element == Element.WIND.ordinal()) {
            return WIND_GLOW;
        }
        if (element == Element.EARTH.ordinal()) {
            return EARTH_GLOW;
        }
        if (element == Element.LIGHT.ordinal()) {
            return LIGHT_GLOW;
        }
        return LightningTheme.glow(LightningTheme.BLUE);
    }

    /** 芯色：默认白热；火偏黄、土偏琥珀，免得七系看起来都是同一根白线。 */
    private static float[] coreColor(int element) {
        if (element == Element.FIRE.ordinal()) {
            return new float[]{1.0F, 0.86F, 0.55F};
        }
        if (element == Element.EARTH.ordinal()) {
            return new float[]{1.0F, 0.72F, 0.32F};
        }
        return new float[]{1.0F, 1.0F, 1.0F};
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /** 主题色闪电（雷）。 */
    private static void bolt(VertexConsumer vc, Vec3 camera, Vec3 from, Vec3 to,
                             long seed, float width, float alpha, int theme, int branches) {
        LightningBoltGeometry.bolt(vc, IDENTITY,
                from.x - camera.x, from.y - camera.y, from.z - camera.z,
                to.x - camera.x, to.y - camera.y, to.z - camera.z,
                seed, width, alpha, theme, branches, 0.0D, 0.0D, 0.0D);
    }

    /** 自定义配色闪电（火/水/风/土/光）。 */
    private static void bolt(VertexConsumer vc, Vec3 camera, Vec3 from, Vec3 to,
                             long seed, float width, float alpha,
                             float[] mid, float[] glow, int branches) {
        LightningBoltGeometry.boltTinted(vc, IDENTITY,
                from.x - camera.x, from.y - camera.y, from.z - camera.z,
                to.x - camera.x, to.y - camera.y, to.z - camera.z,
                seed, width, alpha, mid, glow, branches, 0.0D, 0.0D, 0.0D);
    }

    /** 细丝（无分叉）的短线段，形态壳用得最多。 */
    private static void strand(VertexConsumer vc, Vec3 camera, Vec3 from, Vec3 to,
                               long seed, float width, float alpha,
                               float[] mid, float[] glow, int branches) {
        bolt(vc, camera, from, to, seed, width, alpha, mid, glow, branches);
    }

    /** 元素色光斑。 */
    private static void flash(VertexConsumer vc, Vec3 camera, Vec3 at,
                              float radius, float[] mid, float alpha) {
        if (radius <= 0.01F || alpha <= 0.01F) {
            return;
        }
        LightningBoltGeometry.flashTinted(vc, IDENTITY,
                at.x - camera.x, at.y - camera.y, at.z - camera.z,
                radius, mid, alpha, 0.0D, 0.0D, 0.0D);
    }

    /** 只保留水平分量并归一化；接近 0 时返回 null。 */
    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0D, vector.z);
        return flat.lengthSqr() < 1.0E-4D ? null : flat.normalize();
    }
}
