package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.EarthParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
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
 * <b>土系读条前戏（2026-11-06）</b> —— 作者：「地面的裂纹逐渐向玩家脚下靠近，然后土块土粒子从裂缝中冒出，
 * 汇聚到玩家身上形成一个漩涡，然后施法」。
 *
 * <p>本轮就是把这条描述做成几何：读条一开始，远处地面先裂开一圈<b>放射状裂缝</b>，缝尖随着进度一格一格
 * 往玩家脚边爬（8~16 条，带分叉和抖动）；裂缝里冒出碎石（真实 {@code BLOCK} 粒子 + 几何碎块），
 * 先向上顶出地面，再被吸向玩家；进度过半后玩家身上出现<b>石旋涡</b>——两三圈倾斜石环 + 螺旋上升的
 * 石屑流 + 贴地扬尘；最后 20% 裂缝收到脚边、旋涡收紧，释放瞬间炸开一圈碎石与尘环。
 *
 * <p>在此之上加了三样作者没提但同一母题的：
 * <ul>
 *   <li><b>跑光的地裂</b>：裂缝边缘是金色岩浆光，缝芯是暗色岩缝，读条越靠后边缘越亮；</li>
 *   <li><b>浮石</b>（t4 起）：几块石头脱离地面悬在身侧，随旋涡一起转；</li>
 *   <li><b>神枪虚影</b>（t5 = 土神的枪）：最后一小段在头顶凝出一把金色巨枪的轮廓，指向准星方向。</li>
 * </ul>
 *
 * <p>全部是客户端几何／粒子，只读 {@code SpellCast.Process.progress()}，不生成服务端实体、
 * 不改伤害，也不写方块；其他玩家看得到同样的前戏（各自本地算）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class EarthCastFx {

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Set<String> EARTH_GROUPS = Set.of("earth_shot");

    /** 土系配色：岩缝暗褐、裂缝金、余烬橙、石屑灰褐。 */
    private static final float GOLD_R = 1.0F, GOLD_G = .74F, GOLD_B = .28F;
    private static final float EMBER_R = .88F, EMBER_G = .38F, EMBER_B = .12F;
    private static final float STONE_R = .52F, STONE_G = .45F, STONE_B = .34F;
    private static final float DEEP_R = .17F, DEEP_G = .12F, DEEP_B = .08F;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;
    private static boolean renderBroken;

    private static float dustWash;
    private static float deepVignette;

    private EarthCastFx() {
    }

    // ==================================================================
    //  Tick：读条状态、粒子、震屏、释放检测
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
            dustWash = 0;
            deepVignette = 0;
        }
        if (level == null || mc.player == null) {
            dustWash = 0;
            deepVignette = 0;
            return;
        }
        long now = level.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;

        Set<UUID> seen = new HashSet<>();
        float wash = 0;
        float vignette = 0;
        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            seen.add(player.getUUID());
            Vec3 pos = player.position();
            CastState state = CASTS.computeIfAbsent(player.getUUID(), id -> new CastState(info.tier()));
            state.tier = info.tier();
            state.ratio = info.ratio();
            state.maxRatio = Math.max(state.maxRatio, info.ratio());
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.lastSeen = now;

            double distance = player == mc.player ? 0 : player.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 32);
            if (distance < 64) {
                tickParticles(level, player, info, state, minimal);
            }
            if (player == mc.player) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()));
                vignette = Math.max(vignette, deepShade(info.tier(), info.ratio()));
                tickRumble(info, state, now);
            } else if (falloff > 0) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()) * (float) falloff * .4F);
                vignette = Math.max(vignette, deepShade(info.tier(), info.ratio()) * (float) falloff * .25F);
            }
        }

        Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            CastState state = entry.getValue();
            if (now - state.lastSeen <= 3 && state.maxRatio > .25F) {
                spawnBurst(level, state, Math.min(1, state.maxRatio));
            }
            it.remove();
        }
        CASTS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 40);

        dustWash += (wash - dustWash) * (wash > dustWash ? .35F : .12F);
        deepVignette += (vignette - deepVignette) * (vignette > deepVignette ? .30F : .10F);
        if (dustWash < .004F) {
            dustWash = 0;
        }
        if (deepVignette < .004F) {
            deepVignette = 0;
        }
        BURSTS.removeIf(b -> now - b.start > 16L);
    }

    /** 土系不是心跳而是低频地鸣：越接近释放越密，t1/t2 几乎没有。 */
    private static void tickRumble(CastInfo info, CastState state, long now) {
        if (info.tier() < 3) {
            return;
        }
        int every = info.ratio() < .4F ? 12 : info.ratio() < .75F ? 8 : 5;
        if (now < state.nextBeat) {
            return;
        }
        state.nextBeat = now + every;
        float strength = (.05F + .045F * info.tier()) * (.4F + .6F * info.ratio());
        TNSpellClientVisuals.pulseShake(strength);
    }

    private static void tickParticles(ClientLevel level, Player player, CastInfo info,
                                      CastState state, boolean minimal) {
        Vec3 pos = player.position();
        double R = info.radius();
        float p = info.ratio();
        float reach = reach(p);
        int cracks = crackCount(info.tier());
        int budget = minimal ? 0 : 1;
        long now = level.getGameTime();

        // 裂缝里冒出的土块：从缝尖附近顶出地面，越到后面越靠近玩家。隔帧发射，避免沙尘暴。
        var dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
        var stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        for (int i = 0; i < cracks; i++) {
            if (((now + i) & 1) == 0) {
                continue;
            }
            double angle = crackAngle(i, cracks);
            double tip = crackTip(R, reach, i);
            for (int k = 0; k < budget; k++) {
                double rr = tip + RANDOM.nextDouble() * (R - tip) * .35;
                double jitter = (RANDOM.nextDouble() - .5) * .5;
                double x = pos.x + Math.cos(angle + jitter) * rr;
                double z = pos.z + Math.sin(angle + jitter) * rr;
                double y = pos.y + .05;
                level.addParticle(RANDOM.nextBoolean() ? dirt : stone,
                        x, y, z,
                        -Math.cos(angle) * (.04 + RANDOM.nextDouble() * .07),
                        .22 + RANDOM.nextDouble() * .28,
                        -Math.sin(angle) * (.04 + RANDOM.nextDouble() * .07));
            }
        }

        // 被旋涡吸过去的尘土：沿切线飞向玩家胸口。
        int motes = minimal ? 1 : 2 + info.tier() / 2;
        for (int i = 0; i < motes; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double rr = 2.0 + RANDOM.nextDouble() * info.radius() * .55;
            double x = pos.x + Math.cos(angle) * rr;
            double z = pos.z + Math.sin(angle) * rr;
            double y = pos.y + .15 + RANDOM.nextDouble() * 1.1;
            double inward = .07 + .05 * p;
            level.addParticle(EarthParticles.DUST.get(), x, y, z,
                    -Math.cos(angle) * inward - Math.sin(angle) * .05 * p,
                    .03 + RANDOM.nextDouble() * .05,
                    -Math.sin(angle) * inward + Math.cos(angle) * .05 * p);
        }

        // 释放前最后几帧：脚下裂缝一起亮一下。
        if (p > .82F && state.flare < .82F) {
            state.flare = 1;
            for (int i = 0; i < cracks; i++) {
                double angle = crackAngle(i, cracks);
                level.addParticle(EarthParticles.DUST.get(),
                        pos.x + Math.cos(angle) * .7, pos.y + .1, pos.z + Math.sin(angle) * .7,
                        Math.cos(angle) * .05, .18, Math.sin(angle) * .05);
            }
        }
    }

    private static void spawnBurst(ClientLevel level, CastState state, float strength) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, strength, level.getGameTime()));
        TNSpellClientVisuals.pulseShake((.14F + .05F * state.tier) * strength);
        boolean minimal = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL;
        var dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
        var stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        int count = (int) ((22 + state.tier * 10) * strength);
        if (minimal) {
            count /= 2;
        }
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .12 + RANDOM.nextDouble() * .45;
            level.addParticle(RANDOM.nextBoolean() ? dirt : stone,
                    state.x, state.y + .15 + RANDOM.nextDouble() * .6, state.z,
                    Math.cos(a) * speed, .25 + RANDOM.nextDouble() * .5, Math.sin(a) * speed);
        }
    }

    // ==================================================================
    //  世界渲染
    // ==================================================================

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || renderBroken) {
            return;
        }
        try {
            renderWorld(event);
        } catch (Throwable t) {
            // 画错几何绝不能把整局游戏带走：关掉自己，把栈留给日志，玩家只损失这一套前戏。
            renderBroken = true;
            org.apache.logging.log4j.LogManager.getLogger("TN-C/earthfx")
                    .error("TN-C/earthfx: render failed, earth cast FX disabled for this session", t);
        }
    }

    private static void renderWorld(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != owner) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();

        // 先把本帧要画的东西收集起来：两遍共用，避免重复扫描与重复矩阵。
        List<Cast> casts = new ArrayList<>();
        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            Vec3 base = interpolated(player, partial);
            if (base.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation((float) (base.x - camera.x),
                    (float) (base.y - camera.y), (float) (base.z - camera.z));
            casts.add(new Cast(player, info, pose));
        }
        List<Bursted> bursts = new ArrayList<>();
        for (Burst burst : BURSTS) {
            double age = now - burst.start + partial;
            if (age < 0 || age > 16) {
                continue;
            }
            Vec3 at = new Vec3(burst.x, burst.y, burst.z);
            if (at.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation((float) (at.x - camera.x),
                    (float) (at.y - camera.y), (float) (at.z - camera.z));
            bursts.add(new Bursted(burst, age, pose));
        }
        if (casts.isEmpty() && bursts.isEmpty()) {
            return;
        }

        // 第一遍：暗色岩缝、碎块、尘影（普通混合）。两遍都必须 endBatch：
        // immediate BufferSource 一次只允许一种渲染类型，空的也要收尾。
        VertexConsumer dark = BUFFERS.getBuffer(LightningRenderTypes.DARK);
        for (Cast cast : casts) {
            ritualDark(dark, cast.pose(), cast.player(), cast.info(), now + partial);
        }
        for (Bursted burst : bursts) {
            burstDark(dark, burst.pose(), burst.burst(), burst.age());
        }
        BUFFERS.endBatch(LightningRenderTypes.DARK);

        // 第二遍：金色裂缝边缘、法阵、旋涡、神枪虚影（加色发光）。
        VertexConsumer glow = BUFFERS.getBuffer(RenderType.lightning());
        for (Cast cast : casts) {
            ritualGlow(glow, cast.pose(), cast.player(), cast.info(), now + partial);
        }
        for (Bursted burst : bursts) {
            burstGlow(glow, burst.pose(), burst.burst(), burst.age());
        }
        BUFFERS.endBatch(RenderType.lightning());
    }

    // ---------------------------------------------------------------- dark pass

    /*
     * 暗色几何不能借 WaterGeometry 的 quad/stroke/disk：那些是给 RenderType.lightning()
     * （POSITION_COLOR）写的，只填位置+颜色；而 LightningRenderTypes.DARK 是 NEW_ENTITY，
     * 还要 uv / overlay / light / normal，少填一个就抛
     * "Not filled all elements of the vertex" —— 真机 2026-11-06 03:41 就是这么崩的。
     * 所以暗色这一遍的每个顶点都在这里写全。
     */

    private static void darkVertex(VertexConsumer out, Matrix4f pose, Vec3 p,
                                   float r, float g, float b, float alpha) {
        out.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, alpha)
                .uv(.5F, .5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0, 1, 0)
                .endVertex();
    }

    static void darkQuad(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                 float r, float g, float bl, float alpha) {
        darkVertex(out, pose, a, r, g, bl, alpha);
        darkVertex(out, pose, b, r, g, bl, alpha);
        darkVertex(out, pose, c, r, g, bl, alpha);
        darkVertex(out, pose, d, r, g, bl, alpha);
    }

    static void darkStroke(VertexConsumer out, Matrix4f pose, Vec3 dir, Vec3 a, Vec3 q,
                                   double halfWidth, float r, float g, float b, float alpha) {
        Vec3 axis = q.subtract(a);
        if (axis.lengthSqr() < 1e-9) {
            return;
        }
        Vec3 across = axis.normalize().cross(dir);
        if (across.lengthSqr() < 1e-9) {
            return;
        }
        across = across.normalize().scale(halfWidth);
        darkQuad(out, pose, a.subtract(across), a.add(across), q.add(across), q.subtract(across),
                r, g, b, alpha);
    }

    private static Vec3[] basis(Vec3 dir) {
        Vec3 axis = dir.lengthSqr() < 1e-9 ? UP : dir.normalize();
        Vec3 seed = Math.abs(axis.x) > .9 ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 right = seed.cross(axis).normalize();
        return new Vec3[]{right, axis.cross(right).normalize()};
    }

    static void darkDisk(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 dir, double radius,
                                 float r, float g, float b, float alpha, int segments) {
        Vec3[] b2 = basis(dir);
        Vec3 right = b2[0], up = b2[1];
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 p0 = center.add(right.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = center.add(right.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            darkQuad(out, pose, center, p0, p1, p1, r, g, b, alpha);
        }
    }

    static void darkRing(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 dir, double radius,
                                 double width, float r, float g, float b, float alpha, int segments) {
        Vec3[] b2 = basis(dir);
        Vec3 right = b2[0], up = b2[1];
        double inner = radius - width;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 o0 = center.add(right.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 o1 = center.add(right.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            Vec3 i0 = center.add(right.scale(Math.cos(a0) * inner)).add(up.scale(Math.sin(a0) * inner));
            Vec3 i1 = center.add(right.scale(Math.cos(a1) * inner)).add(up.scale(Math.sin(a1) * inner));
            darkQuad(out, pose, o0, o1, i1, i0, r, g, b, alpha);
        }
    }

    /** 暗色小立方：3 个交叉面（给 DARK 写的，元素齐全）。 */
    static void darkCube(VertexConsumer out, Matrix4f pose, Vec3 at, double size,
                                 float r, float g, float b, float alpha) {
        Vec3 x = new Vec3(size, 0, 0), y = new Vec3(0, size, 0), z = new Vec3(0, 0, size);
        darkQuad(out, pose, at.subtract(x), at.add(x), at.add(x).add(y), at.subtract(x).add(y),
                r, g, b, alpha);
        darkQuad(out, pose, at.subtract(z), at.add(z), at.add(z).add(y), at.subtract(z).add(y),
                r * .85F, g * .85F, b * .85F, alpha);
        darkQuad(out, pose, at.subtract(x).subtract(z), at.add(x).subtract(z),
                at.add(x).add(z), at.subtract(x).add(z), r * 1.05F, g, b * .95F, alpha);
    }

    private static void ritualDark(VertexConsumer out, Matrix4f pose, Player player, CastInfo info, double time) {
        int tier = info.tier();
        float p = info.ratio();
        float reach = reach(p);
        int cracks = crackCount(tier);
        double R = info.radius();

        // 每条裂缝的暗色缝芯。
        for (int i = 0; i < cracks; i++) {
            crackCore(out, pose, i, cracks, R, reach, tier, time);
        }

        // 脚边被翻起的土层：一圈暗色环 + 中心薄盘。
        double near = 1.35 + .28 * tier;
        darkDisk(out, pose, new Vec3(0, .028, 0), UP, near * (.6 + .4 * reach),
                DEEP_R, DEEP_G, DEEP_B, .26F + .22F * reach, 28);
        darkRing(out, pose, new Vec3(0, .034, 0), UP,
                near * (.85 + .2 * reach), .10 + .02 * tier,
                DEEP_R, DEEP_G, DEEP_B, .30F + .20F * reach, 40);

        // 从裂缝里顶出来、正被吸走的碎块（几何小块，比粒子更“实”）。
        float pull = Mth.clamp((p - .40F) / .45F, 0, 1);
        double chest = player.getBbHeight() * .56D;
        for (int i = 0; i < cracks; i++) {
            for (int k = 0; k < 3; k++) {
                double phase = (time * .035 + i * .137 + k * .31) % 1.0;
                double angle = crackAngle(i, cracks) + Math.sin(time * .03 + i) * .08;
                double start = crackTip(R, reach, i) + (R - crackTip(R, reach, i)) * (.25 + .55 * phase);
                Vec3 ground = new Vec3(Math.cos(angle) * start, .06, Math.sin(angle) * start);
                double up = (.35 + .55 * phase) * (1 - .55 * pull);
                Vec3 air = ground.add(0, up, 0);
                double swirl = angle + time * (.05 + .05 * pull) + k * .8;
                double vr = Math.max(.5, 2.1 - 1.4 * pull) * (1 - .25 * k);
                Vec3 target = new Vec3(Math.cos(swirl) * vr, chest - .35 + .28 * k,
                        Math.sin(swirl) * vr);
                Vec3 at = air.lerp(target, pull * pull);
                double size = .075 + .045 * ((i + k) % 3);
                darkCube(out, pose, at, size, STONE_R, STONE_G, STONE_B, .55F);
            }
        }
    }

    /** 一条放射状裂缝：4 段折线、每段带抖动，缝芯暗、两边稍亮（亮边在 glow 遍）。 */
    private static void crackCore(VertexConsumer out, Matrix4f pose, int i, int cracks,
                                  double R, double reach, int tier, double time) {
        double start = crackStart(R, i);
        double tip = crackTip(R, reach, i);
        if (tip >= start - .05) {
            return;
        }
        Vec3 prev = null;
        for (int s = 0; s <= 4; s++) {
            Vec3 point = crackPoint(i, s, cracks, start, tip, tier);
            if (prev != null) {
                darkStroke(out, pose, UP, prev, point, .030 + .010 * tier,
                        DEEP_R, DEEP_G, DEEP_B, .78F);
            }
            prev = point;
        }
        // 分叉：偶数裂缝在中段再裂一条短的。
        if ((i & 1) == 0) {
            Vec3 mid = crackPoint(i, 2, cracks, start, tip, tier);
            Vec3 other = mid.add(Math.cos(crackAngle(i, cracks) + 1.1) * (1.1 + .2 * tier), 0,
                    Math.sin(crackAngle(i, cracks) + 1.1) * (1.1 + .2 * tier));
            darkStroke(out, pose, UP, mid, other, .022 + .006 * tier,
                    DEEP_R, DEEP_G, DEEP_B, .62F);
        }
    }

    private static Vec3 crackPoint(int i, int s, int cracks, double start, double tip, int tier) {
        double t = s / 4.0;
        double radius = start + (tip - start) * t;
        double wobble = (hash(i * 7 + s, 3) - .5) * (.30 + .05 * tier);
        double angle = crackAngle(i, cracks) + wobble;
        return new Vec3(Math.cos(angle) * radius, .020 + hash(i * 13 + s, 7) * .012,
                Math.sin(angle) * radius);
    }

    // ---------------------------------------------------------------- glow pass

    private static void ritualGlow(VertexConsumer out, Matrix4f pose, Player player, CastInfo info, double time) {
        int tier = info.tier();
        float p = info.ratio();
        float reach = reach(p);
        float glow = chargeGlow(p);
        int cracks = crackCount(tier);
        double R = info.radius();

        // 裂缝的金色边缘：贴着缝芯画，越到后面越亮。
        for (int i = 0; i < cracks; i++) {
            double start = crackStart(R, i);
            double tip = crackTip(R, reach, i);
            if (tip >= start - .05) {
                continue;
            }
            Vec3 prev = null;
            for (int s = 0; s <= 4; s++) {
                Vec3 point = crackPoint(i, s, cracks, start, tip, tier).add(0, .012, 0);
                if (prev != null) {
                    WaterGeometry.stroke(out, pose, UP, prev, point, .012 + .004 * tier,
                            GOLD_R, GOLD_G, GOLD_B, (.22F + .55F * glow) * (.55F + .45F * (1 - s / 5F)));
                }
                prev = point;
            }
            // 缝尖的余烬点。
            Vec3 tipPoint = crackPoint(i, 4, cracks, start, tip, tier).add(0, .02, 0);
            WaterGeometry.disk(out, pose, tipPoint, UP, .10 + .03 * tier,
                    EMBER_R, EMBER_G, EMBER_B, .35F + .40F * glow, 20);
        }

        // 地面法阵：1 圈（t1-2）/ 2 圈 / 3 圈 + 刻度。
        double ring = 2.0 + .55 * tier;
        WaterGeometry.ringColor(out, pose, new Vec3(0, .040, 0), UP, ring,
                .020 + .004 * tier, GOLD_R, GOLD_G, GOLD_B, .18F + .35F * glow, 48);
        if (tier >= 3) {
            WaterGeometry.ringColor(out, pose, new Vec3(0, .046, 0), UP, ring * .72,
                    .014, EMBER_R, EMBER_G, EMBER_B, .14F + .28F * glow, 40);
        }
        if (tier >= 4) {
            WaterGeometry.ringColor(out, pose, new Vec3(0, .052, 0), UP, ring * 1.12,
                    .012, GOLD_R, GOLD_G, GOLD_B, .12F + .22F * glow, 44);
        }
        int ticks = 8 + tier * 3;
        for (int i = 0; i < ticks; i++) {
            double a = i * Math.PI * 2 / ticks + time * .004;
            Vec3 inner = new Vec3(Math.cos(a) * ring * .90, .045, Math.sin(a) * ring * .90);
            Vec3 outer = new Vec3(Math.cos(a) * ring * (1.0 + .05 * (i & 1)), .045,
                    Math.sin(a) * ring * (1.0 + .05 * (i & 1)));
            WaterGeometry.stroke(out, pose, UP, inner, outer, .016,
                    EMBER_R, EMBER_G, EMBER_B, .16F + .22F * glow);
        }

        // 石旋涡：2~3 圈倾斜石环 + 螺旋上升的石屑流。
        int rings = tier <= 2 ? 1 : tier == 3 ? 2 : 3;
        double spin = time * (.06 + .04 * p);
        for (int r = 0; r < rings; r++) {
            double radius = (1.55 + r * .45) * (1.15 - .35 * p);
            double y = .28 + r * .30 + .18 * Math.sin(time * .07 + r);
            Vec3 normal = new Vec3(Math.sin(time * .03 + r * 1.7) * .22, 1, Math.cos(time * .03 + r) * .22)
                    .normalize();
            WaterGeometry.ringColor(out, pose, new Vec3(0, y, 0), normal, radius,
                    .016 + .004 * (rings - r), STONE_R, STONE_G + .08F, STONE_B,
                    (.20F + .30F * glow) * (1 - r * .18F), 40);
            WaterGeometry.ringColor(out, pose, new Vec3(0, y + .01, 0), normal, radius * 1.01,
                    .006, GOLD_R, GOLD_G, GOLD_B, (.12F + .25F * glow) * (1 - r * .15F), 40);
        }
        int arms = 7 + tier * 2;
        double rise = 1.4 + .45 * tier;
        for (int i = 0; i < arms; i++) {
            double a = i * Math.PI * 2 / arms + spin * 2.2;
            double t = ((time * .022 + i * .11) % 1.0);
            double radius = (2.25 - .95 * t) * (1.10 - .30 * p);
            Vec3 from = new Vec3(Math.cos(a) * radius, .18 + rise * t * .9, Math.sin(a) * radius);
            double a2 = a + .28;
            Vec3 to = new Vec3(Math.cos(a2) * radius * .82, from.y + .16, Math.sin(a2) * radius * .82);
            WaterGeometry.stroke(out, pose, UP, from, to, .026,
                    GOLD_R, GOLD_G, GOLD_B, (float) ((.12F + .30F * glow) * (1 - t * .55F)));
        }

        // 浮石（t4+）：身侧悬着几块慢慢的石头。
        if (tier >= 4) {
            int stones = tier == 4 ? 4 : 6;
            for (int i = 0; i < stones; i++) {
                double a = i * Math.PI * 2 / stones + spin * .8;
                double radius = 1.9 + (i & 1) * .5;
                double y = 1.1 + (i % 3) * .45 + .12 * Math.sin(time * .08 + i);
                Vec3 at = new Vec3(Math.cos(a) * radius, y, Math.sin(a) * radius);
                cube(out, pose, at, .13 + .03 * (i % 2), STONE_R + .06F, STONE_G + .04F, STONE_B, .55F + .25F * glow);
                WaterGeometry.ringColor(out, pose, at, UP, .22, .008,
                        GOLD_R, GOLD_G, GOLD_B, .10F + .18F * glow, 16);
            }
        }

        // 土神的枪：最后一小段在头顶凝出巨枪轮廓，指向准星。
        if (tier >= 5 && p > .45F) {
            float form = Mth.clamp((p - .45F) / .45F, 0, 1);
            spearSilhouette(out, pose, player, form, time, glow);
        }
    }

    /** 一把朝准星方向的半透明巨枪：枪杆 + 锥形枪头 + 两道刃光。 */
    private static void spearSilhouette(VertexConsumer out, Matrix4f pose, Player player,
                                        float form, double time, float glow) {
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1e-5) {
            look = new Vec3(0, 0, 1);
        }
        look = look.normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        if (right.lengthSqr() < 1e-5) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        double length = 4.2 * form;
        Vec3 base = new Vec3(0, 2.55 + .35 * form, 0).add(look.scale(-.9 * form));
        Vec3 tip = base.add(look.scale(length));
        double shaft = .075 + .03 * form;
        double head = .34 + .18 * form;
        float alpha = (.18F + .30F * glow) * form;

        // 枪杆（两根交叉面，任意角度看都有厚度）。
        WaterGeometry.stroke(out, pose, up, base, tip.subtract(look.scale(length * .28)), shaft,
                GOLD_R, GOLD_G, GOLD_B, alpha);
        WaterGeometry.stroke(out, pose, right, base, tip.subtract(look.scale(length * .28)), shaft,
                GOLD_R, GOLD_G, GOLD_B, alpha * .8F);
        // 枪头：四个三角面拼出锥体。
        Vec3 neck = tip.subtract(look.scale(length * .30));
        Vec3 edge = tip.subtract(look.scale(length * .02));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + time * .04;
            Vec3 side = right.scale(Math.cos(a) * head).add(up.scale(Math.sin(a) * head));
            WaterGeometry.quad(out, pose, neck.add(side), neck.subtract(side), edge, edge,
                    GOLD_R, GOLD_G, GOLD_B, alpha * .9F);
        }
        WaterGeometry.disk(out, pose, tip, look, head * .35, GOLD_R, GOLD_G, GOLD_B, alpha, 16);
        // 刃光：从枪头往后扫的两道细光。
        WaterGeometry.stroke(out, pose, up, neck, tip, .020, 1.0F, .92F, .70F, alpha * .8F);
    }

    // ---------------------------------------------------------------- burst

    private static void burstDark(VertexConsumer out, Matrix4f pose, Burst burst, double age) {
        double f = Mth.clamp(age / 14.0, 0, 1);
        double R = 2.6 + burst.tier * .9;
        float alpha = (float) ((1 - f) * .55 * burst.strength);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + .3;
            double r0 = R * (.55 + f * .35);
            Vec3 from = new Vec3(Math.cos(a) * r0, .10, Math.sin(a) * r0);
            Vec3 to = new Vec3(Math.cos(a) * r0 * 1.9, .05, Math.sin(a) * r0 * 1.9);
            darkStroke(out, pose, UP, from, to, .05 * (1 - f), STONE_R, STONE_G, STONE_B, alpha);
        }
        darkDisk(out, pose, new Vec3(0, .03, 0), UP, R * (.5 + f * .8),
                DEEP_R, DEEP_G, DEEP_B, alpha * .5F, 32);
    }

    private static void burstGlow(VertexConsumer out, Matrix4f pose, Burst burst, double age) {
        double f = Mth.clamp(age / 14.0, 0, 1);
        double R = 2.8 + burst.tier * 1.0;
        float alpha = (float) ((1 - f) * (.34 + .09 * burst.tier) * burst.strength);
        for (int i = 0; i < 3; i++) {
            double r = R * (.5 + f * (.9 + i * .14));
            WaterGeometry.ringColor(out, pose, new Vec3(0, .07 + i * .012, 0), UP, r,
                    .05 + .03 * (1 - f), i == 1 ? EMBER_R : GOLD_R, i == 1 ? EMBER_G : GOLD_G,
                    i == 1 ? EMBER_B : GOLD_B, alpha * (1 - i * .25F), 48);
        }
        if (burst.tier >= 4) {
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                double r0 = R * (.3 + f * .7);
                Vec3 from = new Vec3(Math.cos(a) * r0, .10, Math.sin(a) * r0);
                Vec3 to = from.add(Math.cos(a) * .5, .9 * (1 - f) * 2.2, Math.sin(a) * .5);
                WaterGeometry.stroke(out, pose, UP, from, to, .035, EMBER_R, EMBER_G, EMBER_B, alpha * .7F);
            }
        }
    }

    // ==================================================================
    //  屏幕：土黄尘幕 + 四周压暗（不是水法的蓝、光法的白）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (dustWash <= .004F && deepVignette <= .004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        if (deepVignette > .004F) {
            int alpha = (int) (deepVignette * 46);
            if (alpha > 1) {
                int top = (int) (height * .30F);
                int bottom = (int) (height * .68F);
                event.getGuiGraphics().fillGradient(0, 0, width, top, (alpha << 24) | 0x1A1208, 0);
                event.getGuiGraphics().fillGradient(0, bottom, width, height, 0,
                        (alpha << 24) | 0x1A1208);
                int side = (int) (width * .12F);
                for (int s = 0; s < 4; s++) {
                    int x0 = side * s / 4, x1 = side * (s + 1) / 4;
                    int aa = alpha * (4 - s) / 4;
                    if (aa <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, (aa << 24) | 0x221708);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height,
                            (aa << 24) | 0x221708);
                }
            }
        }
        if (dustWash > .004F) {
            int line = (int) (dustWash * 46);
            if (line > 1) {
                int y = (int) (height * .70F);
                event.getGuiGraphics().fillGradient(0, y, width, height, 0,
                        (line << 24) | 0x6B4A2E);
                event.getGuiGraphics().fillGradient(0, 0, width, (int) (height * .16F),
                        (line / 2 << 24) | 0x3A2716, 0);
            }
        }
    }

    // ==================================================================
    //  小工具
    // ==================================================================

    /** 一块小石头：三个交叉面，便宜但看得出体积。 */
    private static void cube(VertexConsumer out, Matrix4f pose, Vec3 at, double size,
                             float r, float g, float b, float alpha) {
        Vec3 x = new Vec3(size, 0, 0), y = new Vec3(0, size, 0), z = new Vec3(0, 0, size);
        WaterGeometry.quad(out, pose, at.subtract(x), at.add(x), at.add(x).add(y), at.subtract(x).add(y),
                r, g, b, alpha);
        WaterGeometry.quad(out, pose, at.subtract(z), at.add(z), at.add(z).add(y), at.subtract(z).add(y),
                r * .85F, g * .85F, b * .85F, alpha);
        WaterGeometry.quad(out, pose, at.subtract(x).subtract(z), at.add(x).subtract(z),
                at.add(x).add(z), at.subtract(x).add(z), r * 1.05F, g * 1.0F, b * .95F, alpha);
    }

    private static int crackCount(int tier) {
        return switch (tier) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 10;
            case 4 -> 13;
            default -> 16;
        };
    }

    private static double crackAngle(int i, int cracks) {
        double base = i * Math.PI * 2 / cracks;
        return base + (hash(i, 11) - .5) * (.22 + .3 / cracks);
    }

    private static double crackStart(double R, int i) {
        return R * (.78 + .22 * hash(i, 17));
    }

    /** 缝尖半径：随读条从外圈爬到脚边（0.55 格）。 */
    private static double crackTip(double R, double reach, int i) {
        return Math.max(.55, R * (1 - .93 * reach) * (.92 + .10 * hash(i, 23)));
    }

    private static float reach(float p) {
        float x = Mth.clamp(p / .85F, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float chargeGlow(float p) {
        return .22F + .78F * Mth.clamp(p, 0, 1);
    }

    private static float screenFx(int tier) {
        return tier <= 1 ? .18F : tier == 2 ? .32F : tier == 3 ? .55F : tier == 4 ? .80F : 1F;
    }

    private static float deepShade(int tier, float p) {
        if (tier <= 2) {
            return 0;
        }
        float rise = Mth.clamp(p * 1.2F, 0, 1);
        return (tier == 3 ? .24F : tier == 4 ? .42F : .60F) * rise;
    }

    private static double hash(int a, int b) {
        long h = a * 73856093L ^ b * 19349663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    // ---------------------------------------------------------------- test hooks

    /** Number of radial cracks for a tier (see {@code EarthCastFxTest}). */
    static int crackCountFor(int tier) {
        return crackCount(tier);
    }

    /** Direction of each crack, wrapped into {@code [0, 2pi)}, for a tier. */
    static double[] crackAnglesFor(int tier) {
        int count = crackCount(tier);
        double[] angles = new double[count];
        for (int i = 0; i < count; i++) {
            angles[i] = ((crackAngle(i, count) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        }
        return angles;
    }

    /** Outer radius where a crack starts, for a ground radius {@code R}. */
    static double crackStartFor(double R, int i) {
        return crackStart(R, i);
    }

    /** Radius of the crack tip at cast progress {@code p}: it must shrink towards the caster. */
    static double crackTipFor(double R, float p, int i) {
        return crackTip(R, reach(p), i);
    }

    /** Ease curve driving how far the cracks have crawled inwards. */
    static float reachFor(float p) {
        return reach(p);
    }

    // ==================================================================
    //  读条状态
    // ==================================================================

    private record CastInfo(int tier, float ratio, double radius, Vec3 look) {
    }

    private record Cast(Player player, CastInfo info, Matrix4f pose) {
    }

    private record Bursted(Burst burst, double age, Matrix4f pose) {
    }

    private static final class CastState {
        int tier;
        float ratio;
        float maxRatio;
        double x, y, z;
        long lastSeen;
        long nextBeat;
        float flare;

        CastState(int tier) {
            this.tier = tier;
        }
    }

    private record Burst(double x, double y, double z, int tier, float strength, long start) {
    }

    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/earthfx")
                        .info("TN-C/earthfx: spell_engine absent -> earth cast FX idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/earthfx")
                        .warn("TN-C/earthfx: cast scan failed, earth cast FX disabled ({})", t.toString());
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
        if (spell.group == null || !EARTH_GROUPS.contains(spell.group)) {
            return null;
        }
        int tier = Mth.clamp(spell.learn == null ? 1 : spell.learn.tier, 1, 5);
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0, 1);
        double radius = Math.max(4.6, 4.8 + tier * .85);
        return new CastInfo(tier, ratio, Math.min(9.5, radius), player.getLookAngle());
    }

    private static Vec3 interpolated(Player player, float partial) {
        return new Vec3(
                Mth.lerp(partial, player.xOld, player.getX()),
                Mth.lerp(partial, player.yOld, player.getY()),
                Mth.lerp(partial, player.zOld, player.getZ()));
    }
}
