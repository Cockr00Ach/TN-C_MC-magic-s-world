package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.WarriorBladeGeometry;
import com.tnc.tnc.warrior.WarriorTrailCurve;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import com.tnc.tnc.warrior.WarriorWeapons;
import com.tnc.tnc.warrior.WindInfusion;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 刀光剑影：把剑尖最近十几帧的世界坐标连成一条<b>加色混合的光带</b>。
 *
 * <p>这是"影之刃零 / 黑神话"那种剑跟着走的斩气，不是粒子。做法是在玩家渲染的最后
 * 一层（{@link WarriorTrailLayer}）读出<b>真实骨骼</b>的位置：调用
 * {@link ModelPart#translateAndRotate} 得到手臂当前的世界变换，再把手心那一点沿剑的
 * 方向推出去当剑尖 —— 于是不管动作怎么摆、武器多长，光带永远贴着剑尖走。
 *
 * <p><b>2026-11-03：按"顺滑"重做了一遍。</b>用户的原话是"人家的刀光剑影怎么那么顺滑"，
 * 顺着这句话把几处硬伤挖出来了：
 * <ol>
 *   <li><b>固定插 3 个点</b>：慢挥够用，快到一秒两圈时一圈只剩十几个点、成了多边形。
 *       现在按<b>转角</b>自适应：每段最多 4°，转得多就多插点（{@link WarriorTrailCurve}）；</li>
 *   <li><b>端点没对齐</b>：每段自己的透明度，和下一段共用的那条截线颜色对不上，快挥时能看出
 *       一截一截的台阶。现在相邻段共享的截线用<b>完全相同的坐标与颜色</b>，插值连续；</li>
 *   <li><b>收招瞬间整条尾巴凭空消失</b>：以前不在挥就 {@code TRAILS.remove}。现在停止采样但
 *       继续按寿命淡出，尾巴"收"进去，而不是"啪"一下没了；</li>
 *   <li>起手 90 ms 淡入、挥速亮度指数低通 —— 头和亮度都不再"跳"。</li>
 * </ol>
 *
 * <p>（试过自造 {@code TRIANGLE_STRIP} 的 {@code RenderType}：那套 {@code RenderStateShard}
 * 常量是 protected，唯一办法是把类塞进 {@code net.minecraft.client.renderer}，而 Forge 用 JPMS
 * 装 mod，运行时会 {@code ResolutionException} 起不来 —— 详见 {@link #trailType()} 的注释。）
 *
 * <p><b>刀光以虚影为主</b>：外圈的大片斩气走在<b>虚影剑尖</b>上（磨砺越高虚影越大，斩气就越大），
 * 贴着真剑刃的那条亮芯才走在真剑尖上。这样"放大了的虚影"是画面主体，真剑只是里面那道刃。
 */
final class WarriorTrail {

    /**
     * 一帧的采样：握把、真剑尖、虚影剑尖的世界坐标 + 时间戳（毫秒）。
     *
     * <p>虚影剑尖 = 握把往剑尖方向按虚影倍率推出去 —— 虚影围绕握把放大，所以世界空间里
     * 就是同一个握把 + 更长的剑。
     */
    private record Sample(Vec3 hilt, Vec3 tip, Vec3 ghostTip, long at) {
    }

    /**
     * 光带用的渲染类型：原版 {@code lightning()} —— 顶点色 + {@code LIGHTNING_TRANSPARENCY}
     * （{@code SRC_ALPHA, ONE}，加色混合）。
     *
     * <p>2026-11-03 试过自己造一个"三角带 + 不剔除 + 不写深度"的 {@code RenderType}，
     * 结论是<b>在 Forge 上做不到</b>：那套 {@code RenderStateShard} 常量是 protected、
     * {@code RenderType.create} 是包内可见，唯一能读它们的办法是把工具类放进
     * {@code net.minecraft.client.renderer} 包 —— 而 Forge 用 JPMS 装 mod，运行时会直接
     * {@code ResolutionException: Module minecraft contains package net.minecraft.client.renderer,
     * module tnc exports package ...} 起不来（GameTest 逮到的）。所以退回原版类型：
     * 每段一个四边形、顶点色连续插值、正反各发一遍，平滑度靠采样密度（{@link WarriorTrailCurve}）
     * 而不是靠拓扑。
     */
    private static RenderType trailType() {
        return RenderType.lightning();
    }

    private static final Map<Integer, Deque<Sample>> TRAILS = new HashMap<>();
    /** 每个玩家**这一帧**的剑身（握把→剑尖）：常驻辉光用它，不依赖"有没有在挥"。 */
    private static final Map<Integer, Sample> BLADES = new HashMap<>();
    /** 这一轮挥砍开始的时刻：光带头部淡入用（不然起手第一帧直接满亮度，"跳"出来）。 */
    private static final Map<Integer, Long> SWING_START = new HashMap<>();
    /** 挥速系数的低通值：逐帧平滑，避免亮度跟着两帧之间的抖动一起抖。 */
    private static final Map<Integer, Float> POWER = new HashMap<>();

    /** 常驻刃口辉光：细、冷、很淡；挥起来按速度亮一点。 */
    private static final float GLOW_IDLE = 0.06f;
    private static final float GLOW_SWING = 0.14f;
    /** 辉光半宽（格）：剑尖比护手略宽一点点，看起来刃口在"发胀"。 */
    private static final double GLOW_WIDTH = 0.021;

    /** 起手淡入时长（毫秒）与收招后尾巴自己淡出的推后量。 */
    private static final long HEAD_FADE_IN = 90L;

    /**
     * 一条光带最多插值出多少个点（每层）。
     *
     * <p>自适应细分是按转角算的，极端快挥时单段就能吃满 12 个点；几十个样本叠起来能到三四百，
     * 再乘"三层 × 正反两面"就是几千个四边形 —— 一个人还好，十几个人同屏纯属浪费。
     * 那么快的挥砍本来也看不清细节，所以超了就整体降密（见 {@link WarriorTrailCurve#limit}）。
     */
    private static final int MAX_SAMPLES = 320;

    /** 外圈斩气的外沿再往外羽化多少（占剑长的比例）：让"大片的边"不是一刀切的硬边。 */
    private static final double GHOST_FEATHER = 0.14;

    /** 这一帧是不是在演终结技/架势（分量 1）：决定尾巴长短。 */
    static boolean finisher(int id) {
        WarriorAnimationClient.Active active = WarriorAnimationClient.active(id);
        return active != null && active.animation().priority() >= 1;
    }

    /**
     * 一把武器"长得什么样"的刀光参数：不同武器必须是不同的东西，不能只是换个颜色。
     *
     * <ul>
     *   <li><b>外圈起点/亮度</b>：从剑身的百分之几开始铺、多亮 —— 决定"斩气"有多厚；</li>
     *   <li><b>内芯起点/亮度</b>：亮线从哪儿开始 —— 决定"刃口"有多长；</li>
     *   <li><b>剑影</b>：剑尖那条细亮线要不要画（钝器不该有）；</li>
     *   <li><b>寿命/采样数</b>：尾巴留多久 —— 重武器更久更"重"，快剑更短更"脆"。</li>
     * </ul>
     */
    record Profile(double outerStart, float outerAlpha, double coreStart, float coreAlpha,
                           boolean tipStreak, float tipStreakAlpha, long life, long finisherLife,
                           int samples, int finisherSamples, float headBoost, boolean edgeLine) {
    }

    /** 长剑：细、快、脆，尾巴短但亮，剑尖一条锐利的剑影。 */
    private static final Profile LONGSWORD_PROFILE =
            new Profile(0.18, 0.50f, 0.54, 1.00f, true, 0.60f, 200, 320, 16, 26, 1.24f, true);
    /** 重剑：宽、厚、慢，斩气从几乎整把剑开始铺，尾巴留得久，像一个"风压面"。 */
    private static final Profile GREATSWORD_PROFILE =
            new Profile(0.02, 0.66f, 0.24, 0.82f, true, 0.66f, 280, 440, 20, 30, 1.12f, true);
    /** 战锤：钝器，没有刃口、没有剑影，只有一大片暖色的"闷"光，短促。 */
    private static final Profile HAMMER_PROFILE =
            new Profile(0.00, 0.78f, 0.62, 0.62f, false, 0f, 165, 250, 14, 20, 1.32f, false);
    /** 长枪：又长又细，亮芯收得紧、尾巴掉得快 —— 突刺要"一道白光穿过去"的观感。 */
    private static final Profile SPEAR_PROFILE =
            new Profile(0.30, 0.46f, 0.64, 1.00f, true, 0.54f, 185, 290, 18, 26, 1.28f, true);

    static Profile profile(WarriorWeaponFamily family) {
        if (family == null) {
            return HAMMER_PROFILE;
        }
        return switch (family) {
            case LONGSWORD -> LONGSWORD_PROFILE;
            case GREATSWORD -> GREATSWORD_PROFILE;
            case HAMMER -> HAMMER_PROFILE;
            case SPEAR -> SPEAR_PROFILE;
            default -> LONGSWORD_PROFILE;
        };
    }

    /** 手心相对手臂骨骼原点的偏移：和原版 {@code ItemInHandLayer} 手臂→物品的偏移同一套数。 */
    static final double HAND_FORWARD = -0.625;
    static final double HAND_DOWN = 0.125;

    /** 剑在手里的朝向（手臂坐标系）：向前、略向上，归一化后就是光的走向。 */
    private static final Vec3 BLADE_DIR = new Vec3(0.0, 0.45, -0.89).normalize();

    /** 握把一侧相对剑尖的亮度比例（渐变用）。 */
    private static final float HILT_RATIO = 0.10f;

    private WarriorTrail() {
    }

    /** 每帧在模型渲染时采样一次（由 {@link WarriorTrailLayer} 调用）。 */
    static void sample(AbstractClientPlayer player, PoseStack poseStack, PlayerModel<?> model) {
        int id = player.getId();
        WarriorWeaponFamily family = WarriorWeapons.family(player.getMainHandItem());
        // 只给本 mod 的武器画刀光：空手/木棍/法杖一律没有
        if (family == null) {
            TRAILS.remove(id);
            BLADES.remove(id);
            SWING_START.remove(id);
            POWER.remove(id);
            return;
        }

        HumanoidArm arm = player.getMainArm();
        ModelPart part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        // 手心（手臂坐标系）：左右手对称
        Vec3 hand = new Vec3(arm == HumanoidArm.RIGHT ? -1.0 / 16.0 : 1.0 / 16.0, HAND_DOWN, HAND_FORWARD);
        double reach = Math.max(0.0, WarriorBladeGeometry.length(family));
        Vec3 tip = hand.add(BLADE_DIR.scale(reach));
        Matrix4f matrix = poseStack.last().pose();
        Vec3 hiltWorld = toWorld(matrix, hand, cam);
        Vec3 tipWorld = toWorld(matrix, tip, cam);
        poseStack.popPose();

        long now = System.currentTimeMillis();
        // 虚影剑尖：虚影围绕握把放大，世界空间里就是"同一个握把 + 更长的剑"
        double draw = ghostScale(player);
        Vec3 ghostTip = hiltWorld.add(tipWorld.subtract(hiltWorld).scale(draw));
        // 不论挥不挥，都记住这一帧的剑身：常驻的刃口辉光要用它（就是"拿在手上也有光"）
        BLADES.put(id, new Sample(hiltWorld, tipWorld, ghostTip, now));

        // 长按停住的架势不算挥：否则一直按着振刀，刀光会永远拖在身后
        boolean swinging = player.swinging || WarriorAnimationClient.swinging(id);
        Deque<Sample> samples = TRAILS.get(id);
        if (reach <= 0 || !swinging) {
            // 收招不再"啪"一下清空：停止采样，让已有的尾巴自己按寿命淡出去。
            // 整条都过期了才把这一份数据丢掉（不然停手会留一个空 deque 在表里）。
            if (samples != null) {
                Profile shape = profile(family);
                long life = finisher(id) ? shape.finisherLife() : shape.life();
                if (samples.isEmpty() || now - samples.peekLast().at() > life) {
                    TRAILS.remove(id);
                    SWING_START.remove(id);
                }
            }
            return;
        }
        if (samples == null) {
            samples = new ArrayDeque<>();
            TRAILS.put(id, samples);
        }
        // 起手淡入的计时：这一次挥砍是新的一轮（队列空，或者上一次已经停了一段时间）
        Sample newest = samples.peekLast();
        if (newest == null || now - newest.at() > 250L) {
            SWING_START.put(id, now);
        }
        Sample last = samples.peekLast();
        // 剑尖没怎么动就不采样：否则站着也会堆一摞重合的片，白白变亮
        if (last != null && last.tip().distanceToSqr(tipWorld) < 4.0E-4) {
            return;
        }
        samples.addLast(new Sample(hiltWorld, tipWorld, ghostTip, now));
        boolean big = finisher(id);
        Profile shape = profile(family);
        int cap = big ? shape.finisherSamples() : shape.samples();
        long life = big ? shape.finisherLife() : shape.life();
        while (samples.size() > cap) {
            samples.removeFirst();
        }
        while (!samples.isEmpty() && now - samples.peekFirst().at() > life) {
            samples.removeFirst();
        }
    }

    /** 这个玩家虚影的视觉倍率（≥1.02：完全重合会和真模型 z-fighting）。 */
    private static double ghostScale(AbstractClientPlayer player) {
        if (WarriorPhantomRenderer.disabled()) {
            return 1.0;
        }
        try {
            return Math.max(1.02, WarriorPhantomRenderer.scaleFor(player));
        } catch (RuntimeException | LinkageError error) {
            return 1.02;
        }
    }

    /**
     * 画刀光：外圈斩气（走虚影剑尖）+ 内芯亮线（走真剑尖）+ 剑尖剑影，三层叠出来。
     *
     * <p>三层各自按武器家族（{@link Profile}）取不同的起点、亮度、寿命和采样数，
     * 所以长剑是"细快脆"的一条锐光，重剑是"宽厚慢"的一大片风压，战锤是"暖而钝"的一团闷光 ——
     * 换武器换的不只是颜色，剪影本身就不一样。
     *
     * <p>所有层都乘一个**挥速系数**：甩得快时更亮，慢慢比划基本不出光。
     */
    static void render(AbstractClientPlayer player, MultiBufferSource buffer) {
        int id = player.getId();
        ItemStack stack = player.getMainHandItem();
        WarriorWeaponFamily family = WarriorWeapons.family(stack);
        Profile shape = profile(family);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();
        VertexConsumer vc = buffer.getBuffer(trailType());
        Tint tint = Tint.of(family, stack);

        Deque<Sample> samples = TRAILS.get(id);
        Sample[] list = samples == null ? null : samples.toArray(new Sample[0]);
        float power = power(id, list);

        // 先画常驻的刃口辉光：不挥的时候手里那把剑也有一层光（不再是粒子）
        Sample blade = BLADES.get(id);
        if (blade != null) {
            bladeGlow(vc, blade, cam, tint, power, shape);
        }
        if (list == null || list.length < 2) {
            return;
        }
        long life = finisher(id) ? shape.finisherLife() : shape.life();
        // 关键帧之间补点：快速回旋时弧线才不是一段段折线
        Sample[] curve = smooth(list);
        // 起手淡入：头部亮度 0→1，避免第一帧"跳"出来
        float ramp = headRamp(id, now);
        // 外圈：宽、暗、衰减快，负责"斩气的体积"；边缘走在**虚影**剑尖上
        ribbon(vc, curve, cam, now, tint, power * shape.outerAlpha() * ramp, life,
                shape.outerStart(), 1.5f, 0.58f, shape.headBoost(), true, GHOST_FEATHER);
        // 内芯：窄、亮、衰减慢，负责"刃口"；贴着**真剑**尖
        ribbon(vc, curve, cam, now, tint, power * shape.coreAlpha() * ramp, life,
                shape.coreStart(), 0.80f, 1.0f, shape.headBoost(), false, 0.0);
        // 剑影：贴着剑尖的一条细亮线（钝器没有）
        if (shape.tipStreak()) {
            tipStreak(vc, curve, cam, now, tint, power * shape.tipStreakAlpha() * ramp, life);
        }
    }

    /** 起手淡入系数：这一轮挥砍开始后 {@link #HEAD_FADE_IN} 毫秒内从 0 升到 1。 */
    private static float headRamp(int id, long now) {
        Long start = SWING_START.get(id);
        if (start == null) {
            return 1f;
        }
        float t = Mth.clamp((now - start) / (float) HEAD_FADE_IN, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Catmull-Rom 细分，<b>按转角自适应</b>。
     *
     * <p>固定插 3 个点有两个毛病：慢慢比划时浪费顶点，快到一秒两圈时又不够 ——
     * 一圈只剩十几个点，看着就是个多边形。这里按两帧之间剑尖的转角决定插几个：
     * 每段不超过 {@link WarriorTrailCurve#MAX_SEGMENT_DEGREES} 度，
     * 最多 {@link WarriorTrailCurve#MAX_SUBDIVISIONS} 个。
     */
    private static Sample[] smooth(Sample[] in) {
        if (in.length < 2) {
            return in;
        }
        // 先算出每一段要插几个点，再一次性把结果铺开；总数超预算就整体降一档
        int[] steps = new int[in.length - 1];
        int total = 1;
        for (int i = 0; i < in.length - 1; i++) {
            steps[i] = subdivisions(in[i], in[i + 1]);
            total += steps[i];
        }
        if (total > MAX_SAMPLES) {
            steps = WarriorTrailCurve.limit(steps, MAX_SAMPLES - in.length);
            total = 1;
            for (int n : steps) {
                total += n;
            }
        }
        Sample[] out = new Sample[total];
        int k = 0;
        out[k++] = in[0];
        for (int i = 0; i < in.length - 1; i++) {
            Sample p0 = in[Math.max(0, i - 1)];
            Sample p1 = in[i];
            Sample p2 = in[i + 1];
            Sample p3 = in[Math.min(in.length - 1, i + 2)];
            int n = steps[i];
            for (int s = 1; s <= n; s++) {
                if (s == n) {
                    // 末点直接用真样本，避免插值把关键帧抹掉
                    out[k++] = p2;
                    continue;
                }
                float t = s / (float) n;
                Vec3 hilt = WarriorTrailCurve.catmull(p0.hilt(), p1.hilt(), p2.hilt(), p3.hilt(), t);
                Vec3 tip = WarriorTrailCurve.catmull(p0.tip(), p1.tip(), p2.tip(), p3.tip(), t);
                Vec3 ghost = WarriorTrailCurve.catmull(p0.ghostTip(), p1.ghostTip(), p2.ghostTip(), p3.ghostTip(), t);
                long at = p1.at() + Math.round((p2.at() - p1.at()) * t);
                out[k++] = new Sample(hilt, tip, ghost, at);
            }
        }
        return out;
    }

    /** 两帧之间的转角 → 需要插几段（数学在 {@link WarriorTrailCurve} 里，单测覆盖）。 */
    private static int subdivisions(Sample a, Sample b) {
        return WarriorTrailCurve.subdivisions(a.tip().subtract(a.hilt()), b.tip().subtract(b.hilt()));
    }

    /**
     * 剑尖的"剑影"：只跟剑尖那条线，画一条面向镜头的细亮光（逐段四边形）。
     *
     * <p>这是"刀光剑影"里"剑影"那半 —— 斩气是面，剑影是线。钝器/锤子没有刃，所以不画。
     */
    private static void tipStreak(VertexConsumer vc, Sample[] list, Vec3 cam, long now, Tint tint,
                                  float alpha, long life) {
        int last = list.length - 1;
        Vec3 prevPoint = null;
        Vec3 prevSide = null;
        float prevLine = 0f;
        for (int i = 0; i < list.length; i++) {
            Sample sample = list[i];
            float fade = 1f - Mth.clamp((now - sample.at()) / (float) life, 0f, 1f);
            Vec3 point = sample.tip().subtract(cam);
            Vec3 prev = i == 0 ? point : list[i - 1].tip().subtract(cam);
            Vec3 dir = point.subtract(prev);
            if (i > 0 && dir.lengthSqr() < 1.0E-8) {
                dir = null;
            }
            Vec3 view = point.lengthSqr() < 1.0E-6 ? null : point.normalize();
            Vec3 side = (dir == null || view == null) ? null : dir.normalize().cross(view);
            if (side == null || side.lengthSqr() < 1.0E-8) {
                continue;
            }
            float head = (float) Math.pow(i / (double) last, 3.0);
            double width = (0.010 + 0.020 * head) * (0.45 + 0.55 * fade);
            side = side.normalize().scale(width);
            float line = Mth.clamp(alpha * fade * (0.85f + 0.45f * head), 0f, 1f);
            if (prevPoint != null) {
                quad(vc,
                        prevPoint.subtract(prevSide), prevPoint.add(prevSide),
                        point.add(side), point.subtract(side),
                        tint.coreR(), tint.coreG(), tint.coreB(), prevLine,
                        tint.coreR(), tint.coreG(), tint.coreB(), prevLine,
                        tint.coreR(), tint.coreG(), tint.coreB(), line,
                        tint.coreR(), tint.coreG(), tint.coreB(), line);
            }
            prevPoint = point;
            prevSide = side;
            prevLine = line;
        }
    }

    /**
     * 常驻刃口辉光：沿剑身画<b>面向镜头</b>的光带。
     *
     * <p>面向镜头是关键：如果直接沿剑身铺一个平面，从侧面看就变成一条线、会闪。
     * 这里用「剑的方向 × 视线方向」当横向，任何角度看都是一条有宽度的光带。
     *
     * <p><b>有没有刃决定画法</b>（{@link Profile#edgeLine()}）：刀剑画"宽光晕 + 细刃口"两层，
     * 中间那条细亮线就是"刃"；锤子没有刃，所以只画一层更宽更软的闷光 —— 不然锤子也会
     * 拖出一条剑影，看着就像把大剑。
     */
    private static void bladeGlow(VertexConsumer vc, Sample blade, Vec3 cam, Tint tint, float power,
                                  Profile shape) {
        Vec3 a = blade.hilt().subtract(cam);
        Vec3 b = blade.tip().subtract(cam);
        Vec3 dir = b.subtract(a);
        double length = dir.length();
        if (length < 0.05) {
            return;
        }
        dir = dir.scale(1.0 / length);
        // 相机已在原点（坐标都减过 cam），中心点方向就是视线
        Vec3 view = a.add(b).scale(0.5);
        if (view.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 side = dir.cross(view.normalize());
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize();
        float glow = GLOW_IDLE + (GLOW_SWING - GLOW_IDLE)
                * Mth.clamp((power - 0.35f) / 1.0f, 0f, 1f);
        if (!shape.edgeLine()) {
            // 钝器：一层又宽又软的暖光，没有刃口那条线
            glowStrip(vc, a, b, side, GLOW_WIDTH * 3.6, glow * 0.42f, tint, 0.55f);
            return;
        }
        // 外层光晕：更宽、更淡
        glowStrip(vc, a, b, side, GLOW_WIDTH * 2.6, glow * 0.45f, tint, 0.72f);
        // 内层刃口：细、亮
        glowStrip(vc, a, b, side, GLOW_WIDTH, glow, tint, 1.0f);
    }

    /** 一条沿剑身的光带：护手端收窄压暗，剑尖端放宽提亮（一个四边形，正反各一遍）。 */
    private static void glowStrip(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 side, double width,
                                  float alpha, Tint tint, float whiteScale) {
        Vec3 wideA = side.scale(width * 0.32);
        Vec3 wideB = side.scale(width);
        float er = tint.edgeR(), eg = tint.edgeG(), eb = tint.edgeB();
        float cr = Mth.lerp(whiteScale, er, tint.coreR());
        float cg = Mth.lerp(whiteScale, eg, tint.coreG());
        float cb = Mth.lerp(whiteScale, eb, tint.coreB());
        float aHilt = alpha * 0.30f;
        quad(vc,
                a.subtract(wideA), a.add(wideA), b.add(wideB), b.subtract(wideB),
                er, eg, eb, aHilt,
                er, eg, eb, aHilt,
                cr, cg, cb, alpha,
                cr, cg, cb, alpha);
    }

    /**
     * 一个四边形，<b>正反各发一遍</b>。
     *
     * <p>{@code RenderType.lightning()} 用的是默认剔除状态（背面会被剔掉），而挥砍时带子的
     * 朝向随动作翻转 —— 只发一遍的话，一半角度上整条光带会消失。两遍的代价是顶点数翻倍，
     * 但每一面都不会重复覆盖同一像素，所以亮度不会叠加。
     */
    private static void quad(VertexConsumer vc,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float ar, float ag, float ab, float aa,
                             float br, float bg, float bb, float ba,
                             float cr, float cg, float cb, float ca,
                             float dr, float dg, float db, float da) {
        put(vc, a, ar, ag, ab, aa);
        put(vc, b, br, bg, bb, ba);
        put(vc, c, cr, cg, cb, ca);
        put(vc, d, dr, dg, db, da);
        // 背面：绕序反过来，同一个四边形从另一侧也看得见
        put(vc, d, dr, dg, db, da);
        put(vc, c, cr, cg, cb, ca);
        put(vc, b, br, bg, bb, ba);
        put(vc, a, ar, ag, ab, aa);
    }

    /**
     * 把历史连成一条带子：每个采样点是一条"起点边→终点边"的截线，相邻两段之间补一个四边形。
     *
     * @param alpha         这一层的峰值透明度（已经乘过挥速与淡入）
     * @param life          寿命（毫秒），终结技用更长的那一档
     * @param startFraction 握把一侧从剑身的百分之几开始（越老越往剑尖收 = 斩气在挥发）
     * @param falloff       衰减曲线的指数：越大越快淡掉（外圈比内芯散得快）
     * @param whiteScale    越接近 1 越白（内芯用 1，外圈用 0.58 让边缘偏冷色）
     * @param headBoost     最新那一段的额外提亮（贴着刃口的地方最亮）
     * @param ghost         终点走虚影剑尖（外圈斩气）还是真剑尖（内芯刃口）
     * @param feather       外沿再往外羽化多少（占剑长的比例，0 = 不羽化）
     */
    private static void ribbon(VertexConsumer vc, Sample[] list, Vec3 cam, long now, Tint tint,
                               float alpha, long life, double startFraction, float falloff,
                               float whiteScale, float headBoost, boolean ghost, double feather) {
        int last = list.length - 1;
        // 上一段的两个端点：与这一段的起点**坐标和颜色完全一样**，所以接缝处插值连续，
        // 加色混合也不会叠出横纹（光栅化对共边的像素只判给一个三角形）
        Vec3 prevStart = null;
        Vec3 prevEnd = null;
        Vec3 prevOuter = null;
        float prevSr = 0f, prevSg = 0f, prevSb = 0f, prevSa = 0f;
        float prevEr = 0f, prevEg = 0f, prevEb = 0f, prevEa = 0f;
        float prevOr = 0f, prevOg = 0f, prevOb = 0f;
        for (int i = 0; i < list.length; i++) {
            Sample sample = list[i];
            float fade = fadeOf(sample, now, life, falloff);
            if (fade <= 0.02f) {
                // 老到看不见的点直接跳过；但只允许在**开头**跳，中间跳会留缺口
                if (prevStart == null) {
                    continue;
                }
                break;
            }
            // 越靠近当前刃口，"前端"权重越大（pow 4 让它集中在最后两三段）
            float head = (float) Math.pow(i / (double) last, 4.0);
            // 越老，起点越往剑尖方向收：尾巴是"收进去"而不是整条一起变淡
            double start = Mth.lerp(1f - fade, (float) startFraction, 0.94f);
            // 前端再往剑尖外鼓出去一点：顶点处的斩气更厚，读起来"这一下很重"
            double bulge = ghost ? 1.0 : 1.0 + 0.13 * head;
            Vec3 tipPoint = ghost ? sample.ghostTip() : sample.tip();
            Vec3 startPoint = sample.hilt().lerp(sample.tip(), start).subtract(cam);
            Vec3 endPoint = sample.hilt().lerp(tipPoint, bulge).subtract(cam);
            Vec3 outerPoint = feather <= 0 ? null
                    : sample.hilt().lerp(tipPoint, bulge + feather).subtract(cam);
            // 前端颜色更"深"：少掺白，让刃色/元素色压过白光，厚而不飘
            float ws = whiteScale * (1f - 0.30f * head);
            float boost = (1f + 0.40f * head) * Mth.lerp(head, 1f, headBoost);
            float weight = Mth.clamp(alpha * boost * fade, 0f, 1f);
            // 起点边压暗（HILT_RATIO），终点边满亮度：光带从刃口往外渐隐
            float sr = tint.edgeR(), sg = tint.edgeG(), sb = tint.edgeB();
            float sa = weight * HILT_RATIO;
            float er = Mth.lerp(ws, tint.edgeR(), tint.coreR());
            float eg = Mth.lerp(ws, tint.edgeG(), tint.coreG());
            float eb = Mth.lerp(ws, tint.edgeB(), tint.coreB());
            float ea = weight;
            if (prevStart != null) {
                quad(vc,
                        prevStart, prevEnd, endPoint, startPoint,
                        prevSr, prevSg, prevSb, prevSa,
                        prevEr, prevEg, prevEb, prevEa,
                        er, eg, eb, ea,
                        sr, sg, sb, sa);
                if (outerPoint != null && prevOuter != null) {
                    // 羽化带：内沿接主光带（同样亮度、同样颜色），外沿透明 —— 边就不再是"切"出来的
                    quad(vc,
                            prevEnd, prevOuter, outerPoint, endPoint,
                            prevEr, prevEg, prevEb, prevEa,
                            prevOr, prevOg, prevOb, 0f,
                            er, eg, eb, ea,
                            tint.coreR(), tint.coreG(), tint.coreB(), 0f);
                }
            }
            prevStart = startPoint;
            prevEnd = endPoint;
            prevOuter = outerPoint;
            prevOr = er;
            prevOg = eg;
            prevOb = eb;
            prevSr = sr;
            prevSg = sg;
            prevSb = sb;
            prevSa = sa;
            prevEr = er;
            prevEg = eg;
            prevEb = eb;
            prevEa = ea;
        }
    }

    /** 某一段样本的衰减值（0 = 全透明，1 = 刚生成）。 */
    private static float fadeOf(Sample sample, long now, long life, float falloff) {
        float age = Mth.clamp((now - sample.at()) / (float) life, 0f, 1f);
        return (float) Math.pow(1f - age, falloff);
    }

    /**
     * 挥速系数：剑尖每秒走多少格 → 亮度倍数（逐帧低通，避免抖）。
     *
     * <p>慢挥基本不发光（0.35 倍），快速斩击能到 1.35 倍；这样"随手一晃"和"全力一斩"
     * 在画面上分得开，也是动作游戏里刀光的常见处理。
     */
    private static float power(int id, Sample[] list) {
        float target;
        long gap = 16L;
        if (list == null || list.length < 2) {
            target = 0.35f;
        } else {
            Sample newest = list[list.length - 1];
            Sample previous = list[list.length - 2];
            long dt = Math.max(1L, newest.at() - previous.at());
            double speed = newest.tip().distanceTo(previous.tip()) / (dt / 1000.0);
            target = Mth.clamp((float) (speed / 9.0), 0.35f, 1.35f);
            gap = dt;
        }
        Float previousPower = POWER.get(id);
        // 指数平滑：亮度跟着挥速慢慢变，不会因为两帧之间的抖动闪一下。
        // 混合系数按**帧间隔**算（时间常数 45 ms），不是每帧写死一个数 ——
        // 写死的话 120 Hz 的高刷屏每帧也混合那么多，亮度就被抹得比 60 Hz 钝一倍。
        // 45 ms 在 60 fps 下等效 0.31，和原来写死的 0.35 基本一个手感。
        float blend = (float) (1.0 - Math.exp(-Mth.clamp(gap, 4L, 120L) / 45.0));
        float smoothed = previousPower == null ? target : previousPower + (target - previousPower) * blend;
        POWER.put(id, smoothed);
        return smoothed;
    }

    /**
     * 配色：武器带元素灌注（`TncWeaponElement`）时用元素色，否则按家族给默认色。
     *
     * <p>灌注标记在物品 NBT 上，客户端拿着同一份 NBT，所以不用加网络包就能让刀光跟着元素走
     * （风刃剑就是青绿色，火附魔的重剑就是橙红）。
     */
    private record Tint(float coreR, float coreG, float coreB, float edgeR, float edgeG, float edgeB) {

        static Tint of(WarriorWeaponFamily family, ItemStack stack) {
            Tint element = byElement(stack);
            if (element != null) {
                return element;
            }
            if (family == null) {
                return COOL;
            }
            return switch (family) {
                case GREATSWORD -> STEEL;
                case HAMMER -> AMBER;
                case SPEAR -> JADE;
                default -> COOL;
            };
        }

        private static Tint byElement(ItemStack stack) {
            if (stack == null || !stack.hasTag()) {
                return null;
            }
            String id = stack.getTag().getString(WindInfusion.ELEMENT_KEY);
            if (id == null || id.isEmpty()) {
                return null;
            }
            return switch (id) {
                case "wind" -> new Tint(0.88f, 1.0f, 0.98f, 0.28f, 0.96f, 0.84f);
                case "fire" -> new Tint(1.0f, 0.92f, 0.70f, 1.0f, 0.42f, 0.12f);
                case "water" -> new Tint(0.88f, 0.97f, 1.0f, 0.22f, 0.60f, 0.98f);
                case "ice" -> new Tint(0.93f, 0.99f, 1.0f, 0.55f, 0.87f, 1.0f);
                case "lightning" -> new Tint(1.0f, 0.98f, 1.0f, 0.72f, 0.55f, 1.0f);
                case "earth" -> new Tint(1.0f, 0.94f, 0.80f, 0.86f, 0.62f, 0.26f);
                case "light" -> new Tint(1.0f, 1.0f, 0.94f, 1.0f, 0.90f, 0.52f);
                case "dark" -> new Tint(0.94f, 0.88f, 1.0f, 0.55f, 0.24f, 0.86f);
                default -> null;
            };
        }

        private static final Tint COOL = new Tint(0.90f, 0.97f, 1.0f, 0.40f, 0.72f, 1.0f);
        private static final Tint STEEL = new Tint(1.0f, 0.98f, 0.92f, 0.55f, 0.72f, 0.95f);
        private static final Tint AMBER = new Tint(1.0f, 0.95f, 0.82f, 0.95f, 0.62f, 0.30f);
        /** 长枪的默认色：风遗物的青玉色，和风系 boss 那把枪一套。 */
        private static final Tint JADE = new Tint(0.86f, 1.0f, 0.94f, 0.30f, 0.92f, 0.72f);
    }

    private static void put(VertexConsumer vc, Vec3 at, float r, float g, float b, float a) {
        vc.vertex(at.x, at.y, at.z).color(r, g, b, a).endVertex();
    }

    private static void put(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(x, y, z).color(r, g, b, a).endVertex();
    }

    private static Vec3 toWorld(Matrix4f matrix, Vec3 local, Vec3 camera) {
        Vector3f out = matrix.transformPosition(
                new Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(out.x, out.y, out.z).add(camera);
    }

    /** 换维度/重生：旧的坐标全部作废。 */
    static void clear() {
        TRAILS.clear();
        BLADES.clear();
        SWING_START.clear();
        POWER.clear();
    }
}
