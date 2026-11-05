package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.light.LightRitualSequence;
import com.tnc.tnc.magic.water.WaterSpellRules;
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
 * <b>光系读条特效（2026-11-05）</b> —— 作者：「我希望有更多的小法阵和更久的施法前戏，
 * 让我能感受到暴风雨的前夕」＋「光污染」。
 *
 * <h2>这一版做了什么</h2>
 * 以前光耀 / 召唤天使是「读条 1 秒 → 脚下突然一张阵」✓——只有<b>一个</b>法阵，而且都在释放之后 ✗。
 * 现在释放之前就有一整套仪式场，并且读条越长、阵越完整：
 * <ol>
 *   <li><b>主阵被一笔一笔“写”出来</b>：外环从 0 扫到 360°，24 道符文按读条依次点亮；</li>
 *   <li><b>一圈小法阵</b>（3~7 颗，{@link LightRitualSequence#satelliteSeals}）分两圈绕着施法者转，
 *       每一颗有自己的点亮时刻与呼吸节奏；每颗小阵还带一枚<b>竖起来的伴生环</b>；</li>
 *   <li><b>竖环</b>（2~5 枚）绕身翻转 —— 就是“铁魔法”高阶法术那种立起来穿过去的法阵；</li>
 *   <li><b>头顶太阳种子</b>：核心、光柱、8 向光芒、3 条从地面盘旋上去的光带，越到后面越亮；</li>
 *   <li><b>屏幕：先压暗、再爆亮</b>：读条前段四周压暗（暴风雨前夕），最后 40% 暖白光晕爬满屏幕，
 *       释放瞬间白闪 ＋ 震屏；</li>
 *   <li><b>释放后还有一圈扩散光环</b>（几何爆发），配合服务端新铺的小阵与粒子 ✓。</li>
 * </ol>
 *
 * <h2>为什么客户端自己算，不走服务端实体</h2>
 * 读条进度 {@code SpellCast.Process.progress()} 在客户端本来就有 ✓（引擎会把别的玩家的读条同步过来）——
 * 不需要我们发任何网络包，也不会给服务端增加实体 ✓。释放后的持久法阵仍由服务端铺
 * （见 {@code TNLightChainMechanics}），所以<b>别人也能看见</b> ✓。
 *
 * <h2>兼容</h2>
 * 整类只在 {@code Dist.CLIENT} 注册；引擎相关的类型只在 {@link #scan(Player)} 里出现，
 * 且外面有 {@code ModList.isLoaded("spell_engine")} 与 {@code try/catch Throwable} 兜底 ⇒
 * dev 环境（没有 SpellEngine）也能正常启动 ✓。渲染用 {@code RenderType.lightning()} ＋
 * 独立 immediate buffer（和 {@code PortalRitualRenderer} 同一套），不受 Oculus 的
 * {@code endBatch(RenderType)} 空实现影响 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class LightCastFx {

    // ------------------------------------------------------------------
    //  调色板（全部走加色通道；越叠越白，这就是“光污染”的来源 ✓）
    // ------------------------------------------------------------------

    /** 暖白：核心、主环。 */
    private static final float W_R = 1.00F;
    private static final float W_G = 0.985F;
    private static final float W_B = 0.90F;
    /** 金：外圈、符文、光芒。 */
    private static final float G_R = 1.00F;
    private static final float G_G = 0.82F;
    private static final float G_B = 0.38F;
    /** 冷白：竖环、伴生环，给暖色一点对比，不然整屏发黄 ✗。 */
    private static final float C_R = 0.84F;
    private static final float C_G = 0.94F;
    private static final float C_B = 1.00F;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** 光耀链、召唤天使链、光线链（t1~t3 现在也有 0.5~1.0 秒起手；t4/t5 本来就有 1.2~1.6 秒）。 */
    private static final Set<String> LIGHT_GROUPS =
            Set.of("light_grace", "light_summon", "light_beam");

    /** 每帧的顶点缓冲（1 MB）。和 PortalRitualRenderer 一样：自己 flush，不碰共享 source ✓。 */
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 20));

    private static final RandomSource RANDOM = RandomSource.create();

    /** 正在读条的玩家（tick 更新，用来撒粒子 / 判断“读条结束”）。 */
    private static final Map<UUID, CastState> CASTS = new HashMap<>();

    /** 释放后的几何爆发（18 tick）。 */
    private static final List<Burst> BURSTS = new ArrayList<>();

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;

    /** 屏幕暖白光晕（读条最后阶段）。 */
    private static float lightWash;
    /** 屏幕四周压暗（读条前段，“暴风雨前夕”）。 */
    private static float darkVignette;

    private LightCastFx() {
    }

    // ==================================================================
    //  每 tick：读条状态 / 粒子 / 屏幕强度 / 释放检测
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
            lightWash = 0.0F;
            darkVignette = 0.0F;
        }
        if (level == null || mc.player == null) {
            lightWash = 0.0F;
            darkVignette = 0.0F;
            return;
        }
        long now = level.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;

        Set<UUID> seen = new HashSet<>();
        float wash = 0.0F;
        float vignette = 0.0F;

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

            double distance = player == mc.player ? 0.0D : player.distanceTo(mc.player);
            double falloff = Math.max(0.0D, 1.0D - distance / 28.0D);
            if (distance < 64.0D) {
                tickParticles(level, player, info, state, minimal);
            }
            if (player == mc.player) {
                wash = Math.max(wash, LightRitualSequence.chargeGlow(info.ratio())
                        * LightRitualSequence.screenFx(info.tier()));
                vignette = Math.max(vignette, LightRitualSequence.darkVignette(info.tier(), info.ratio()));
                tickHeartbeat(level, info, state, now);
            } else if (falloff > 0.0D) {
                wash = Math.max(wash, LightRitualSequence.chargeGlow(info.ratio())
                        * LightRitualSequence.screenFx(info.tier()) * (float) falloff * 0.55F);
                vignette = Math.max(vignette, LightRitualSequence.darkVignette(info.tier(), info.ratio())
                        * (float) falloff * 0.35F);
            }
        }

        // 读条结束（释放 / 取消 / 死亡）—— 按最后一刻的比例给一次爆发 ✓
        Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            CastState state = entry.getValue();
            if (now - state.lastSeen <= 3) {
                float strength = Math.max(0.0F, Math.min(1.0F, state.maxRatio));
                if (strength > 0.30F) {
                    spawnBurst(level, state, strength);
                }
            }
            it.remove();
        }
        // 离开视野很久的残留状态
        CASTS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 40);

        // 平滑：抬头要快（有“压上来”的感觉），回落稍慢 ✓
        lightWash += (wash - lightWash) * (wash > lightWash ? 0.55F : 0.16F);
        darkVignette += (vignette - darkVignette) * (vignette > darkVignette ? 0.35F : 0.12F);
        if (lightWash < 0.004F) {
            lightWash = 0.0F;
        }
        if (darkVignette < 0.004F) {
            darkVignette = 0.0F;
        }

        BURSTS.removeIf(b -> now - b.start > 20L);
    }

    /** 读条后期的“心跳”震屏：越接近释放越密、越明显 ✓；低阶没有心跳。 */
    private static void tickHeartbeat(ClientLevel level, CastInfo info, CastState state, long now) {
        int every = LightRitualSequence.heartbeatTicks(info.tier(), info.ratio());
        if (every <= 0 || now < state.nextBeat) {
            return;
        }
        state.nextBeat = now + every;
        TNSpellClientVisuals.pulseShake(LightRitualSequence.heartbeatShake(info.tier(), info.ratio()));
    }

    /**
     * 每 tick 的粒子：地面向中心汇聚的白色光点 ＋ 核心电火花 ＋ 小阵点亮时的一小簇烟花。
     *
     * <p>全部是客户端本地粒子（不占服务端带宽）✓；粒子设置调成 MINIMAL 时数量砍半，
     * 保证低配机器也能读出“阵在动” ✓。
     */
    private static void tickParticles(ClientLevel level, Player player, CastInfo info,
                                      CastState state, boolean minimal) {
        double range = info.range();
        double cx = player.getX();
        double cy = player.getY();
        double cz = player.getZ();

        int motes = (2 + info.tier()) * (minimal ? 1 : 2);
        for (int i = 0; i < motes; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double radius = range * (0.25D + RANDOM.nextDouble() * 0.72D);
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            level.addParticle(ParticleTypes.END_ROD, x, cy + 0.06D + RANDOM.nextDouble() * 0.45D, z,
                    -Math.cos(angle) * 0.035D, 0.055D + RANDOM.nextDouble() * 0.035D,
                    -Math.sin(angle) * 0.035D);
        }
        // 头顶核心附近：蓝白电火花，读条越后越密
        int sparks = (1 + (int) (info.ratio() * 3.0F)) * (minimal ? 1 : 2);
        double coreY = cy + 2.55D + 0.12D * info.tier() + 0.3D * info.ratio();
        for (int i = 0; i < sparks; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    cx + (RANDOM.nextDouble() - 0.5D) * 0.8D,
                    coreY + (RANDOM.nextDouble() - 0.5D) * 0.5D,
                    cz + (RANDOM.nextDouble() - 0.5D) * 0.8D,
                    (RANDOM.nextDouble() - 0.5D) * 0.08D,
                    RANDOM.nextDouble() * 0.06D,
                    (RANDOM.nextDouble() - 0.5D) * 0.08D);
        }
        // 小阵点亮：每颗只炸一次（记录在 state 里，跨 tick 不重复）
        int satellites = LightRitualSequence.satelliteSeals(info.tier());
        for (int i = 0; i < satellites; i++) {
            float ignition = LightRitualSequence.satelliteIgnition(info.tier(), i, info.ratio());
            if (ignition < 0.55F || state.ignited[i] >= 0.55F) {
                continue;
            }
            state.ignited[i] = ignition;
            double angle = LightRitualSequence.satelliteAngle(info.tier(), i) + 0.35D;
            double orbit = range * LightRitualSequence.satelliteOrbitFraction(i);
            double x = cx + Math.cos(angle) * orbit;
            double z = cz + Math.sin(angle) * orbit;
            level.addParticle(ParticleTypes.FIREWORK, x, cy + 0.35D, z, 0.0D, 0.12D, 0.0D);
            level.addParticle(ParticleTypes.END_ROD, x, cy + 0.15D, z, 0.0D, 0.10D, 0.0D);
            if (!minimal) {
                level.addParticle(ParticleTypes.FIREWORK, x, cy + 0.45D, z, 0.05D, 0.05D, 0.05D);
            }
        }
    }

    /** 释放爆发：记一笔几何爆发 ＋ 白闪/震屏 ＋ 一簇客户端粒子 ✓。 */
    private static void spawnBurst(ClientLevel level, CastState state, float strength) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, strength, level.getGameTime()));

        Minecraft mc = Minecraft.getInstance();
        double distance = mc.player == null ? 0.0D
                : mc.player.position().distanceTo(new Vec3(state.x, state.y, state.z));
        float falloff = (float) Math.max(0.0D, 1.0D - distance / 28.0D);
        boolean local = distance < 0.5D;
        float flash = LightRitualSequence.releaseFlash(state.tier) * (local ? 1.0F : falloff * 0.85F);
        float shake = LightRitualSequence.releaseShake(state.tier) * (local ? 1.0F : falloff * 0.8F);
        TNSpellClientVisuals.pulseFlash(flash * strength);
        TNSpellClientVisuals.pulseShake(shake * strength);

        int motes = (int) ((40 + state.tier * 14) * strength);
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        if (minimal) {
            motes /= 2;
        }
        for (int i = 0; i < motes; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double speed = 0.12D + RANDOM.nextDouble() * 0.45D;
            level.addParticle(ParticleTypes.END_ROD,
                    state.x, state.y + 0.35D + RANDOM.nextDouble() * 0.8D, state.z,
                    Math.cos(angle) * speed, 0.15D + RANDOM.nextDouble() * 0.55D,
                    Math.sin(angle) * speed);
        }
        for (int i = 0; i < 18 + state.tier * 4; i++) {
            level.addParticle(ParticleTypes.FIREWORK,
                    state.x + (RANDOM.nextDouble() - 0.5D) * 1.4D,
                    state.y + 0.4D + RANDOM.nextDouble() * 1.2D,
                    state.z + (RANDOM.nextDouble() - 0.5D) * 1.4D,
                    (RANDOM.nextDouble() - 0.5D) * 0.35D,
                    RANDOM.nextDouble() * 0.25D,
                    (RANDOM.nextDouble() - 0.5D) * 0.35D);
        }
    }

    // ==================================================================
    //  渲染：AFTER_WEATHER（世界空间；和传送门仪式同一个时机/缓冲策略）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != owner) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        boolean any = false;

        VertexConsumer out = BUFFERS.getBuffer(RenderType.lightning());
        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            Vec3 base = interpolated(player, partial);
            if (base.distanceToSqr(camera) > 96.0D * 96.0D) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (base.x - camera.x),
                    (float) (base.y - camera.y),
                    (float) (base.z - camera.z));
            ritual(out, pose, info, now + partial);
            any = true;
        }
        for (Burst burst : BURSTS) {
            double age = now - burst.start + partial;
            if (age < 0.0D || age > 20.0D) {
                continue;
            }
            Vec3 at = new Vec3(burst.x, burst.y, burst.z);
            if (at.distanceToSqr(camera) > 96.0D * 96.0D) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (at.x - camera.x),
                    (float) (at.y - camera.y),
                    (float) (at.z - camera.z));
            burst(out, pose, burst.tier, burst.strength, age);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(RenderType.lightning());
        }
    }

    /**
     * 一座读条中的光系仪式场。
     *
     * @param out   POSITION_COLOR 加色顶点
     * @param pose  已平移到施法者原点的矩阵（单位：格）
     * @param info  档位 / 比例 / 法术范围
     * @param time  动画相位（tick，带 partial）
     */
    private static void ritual(VertexConsumer out, Matrix4f pose, CastInfo info,
                               double time) {
        int tier = info.tier();
        float p = info.ratio();
        double range = info.range();
        float form = LightRitualSequence.formation(p);
        float glow = LightRitualSequence.chargeGlow(p);
        LightRitualSequence.Rite rite = LightRitualSequence.rite(tier);
        boolean grand = rite != LightRitualSequence.Rite.INSCRIPTION;

        groundSeal(out, pose, range, tier, p, form, glow, time, grand);
        satellites(out, pose, range, tier, p, form, time, grand);
        gates(out, pose, range, tier, p, form, time);
        core(out, pose, range, tier, p, form, glow, time, grand);
    }

    /** 地面主阵：低阶只画一圈符 + 6/8 道光芒；中高阶才有双层环、内层八角星与满圈符文。 */
    private static void groundSeal(VertexConsumer out, Matrix4f pose, double range, int tier,
                                   float p, float form, float glow, double time, boolean grand) {
        double R = range;

        // 贴地的一层很淡的光晕（读条越久越亮；别太实，否则像铺了一张白纸 ✗）
        int layers = grand ? 3 : 1;
        for (int i = 0; i < layers; i++) {
            WaterGeometry.disk(out, pose, new Vec3(0.0D, 0.045D + i * 0.003D, 0.0D), UP,
                    R * (0.58D + i * 0.20D), W_R, W_G, W_B, 0.012F + form * 0.008F, 48);
        }

        // 辅助整环（虚线感：用很低的透明度，让“还没写完”的阵有个底）
        WaterGeometry.ringColor(out, pose, Vec3.ZERO, UP, R, R * 0.004D,
                W_R, W_G, W_B, 0.055F + 0.05F * form, 96);
        if (grand) {
            WaterGeometry.ringColor(out, pose, new Vec3(0.0D, 0.012D, 0.0D), UP, R * 0.86D,
                    R * 0.003D, G_R, G_G, G_B, 0.05F + 0.05F * form, 80);
        }

        // 两段反向扫出来的主环：读条 = 笔尖在阵上走一圈 ✓
        double phase = time * 0.012D;
        arc(out, pose, Vec3.ZERO, UP, R, R * 0.020D, phase,
                Math.PI * 2.0D * form, W_R, W_G, W_B, 0.55F, 96);
        arc(out, pose, new Vec3(0.0D, 0.008D, 0.0D), UP, R * 0.955D, R * 0.010D,
                -phase * 1.3D, Math.PI * 2.0D * form * 0.78D, G_R, G_G, G_B, 0.35F, 80);

        // 外圈符文：按读条顺序依次亮起；低阶只刻 6/8 道，高阶 18/24 道
        int runes = LightRitualSequence.outerRunes(tier);
        for (int i = 0; i < runes; i++) {
            double angle = i * Math.PI * 2.0D / runes + phase * 0.35D;
            float lit = LightRitualSequence.smooth(
                    LightRitualSequence.clamp((p - i / (float) runes * 0.72F) / 0.16F));
            if (lit <= 0.02F) {
                continue;
            }
            rune(out, pose, angle, R * 0.885D, R * 0.048D, R * 0.0042D,
                    G_R, G_G, G_B, 0.55F * lit);
        }

        // 向外的光芒（日冕）：越接近释放越长
        int rays = LightRitualSequence.groundRays(tier);
        for (int i = 0; i < rays; i++) {
            double angle = i * Math.PI * 2.0D / rays + time * 0.006D;
            Vec3 inner = polar(angle, R * 0.92D, 0.06D);
            Vec3 outer = polar(angle, R * (1.02D + 0.22D * glow), 0.06D);
            float alpha = 0.10F + 0.22F * glow;
            WaterGeometry.tube(out, pose, inner, outer, R * 0.0055D, G_R, G_G, G_B, alpha, 4);
        }

        // 内层八角星：只有中高阶才叠这两枚反向方框（低阶保持“一个干净的符”）
        if (grand) {
            square(out, pose, R * 0.58D, time * 0.010D, R * 0.0045D,
                    W_R, W_G, W_B, 0.30F * form);
            square(out, pose, R * 0.44D, -time * 0.014D, R * 0.0035D,
                    G_R, G_G, G_B, 0.26F * form);
        }

        // 中心：小环 + 亮核
        WaterGeometry.ringColor(out, pose, Vec3.ZERO, UP, R * 0.20D, R * 0.006D,
                W_R, W_G, W_B, 0.35F * form, 48);
        WaterGeometry.disk(out, pose, new Vec3(0.0D, 0.055D, 0.0D), UP, R * 0.045D,
                1.0F, 0.98F, 0.90F, 0.16F + 0.22F * glow, 24);
    }

    /** 2~8 颗绕行小阵；中高阶每颗再顶一枚竖立伴生环（低阶只有贴地小阵）。 */
    private static void satellites(VertexConsumer out, Matrix4f pose, double range, int tier,
                                   float p, float form, double time, boolean grand) {
        int count = LightRitualSequence.satelliteSeals(tier);
        double baseAngle = time * 0.0045D + tier * 0.21D;
        for (int i = 0; i < count; i++) {
            float ignition = LightRitualSequence.satelliteIgnition(tier, i, p);
            if (ignition <= 0.02F) {
                continue;
            }
            boolean even = (i & 1) == 0;
            double angle = baseAngle + LightRitualSequence.satelliteAngle(tier, i)
                    + time * 0.006D * (even ? 1.0D : -1.0D);
            double orbit = range * LightRitualSequence.satelliteOrbitFraction(i)
                    * (1.0D - 0.10D * form);
            double size = range * LightRitualSequence.satelliteSizeFraction(tier)
                    * (0.45D + 0.55D * ignition);
            double pulse = 1.0D + 0.08D * Math.sin(time * 0.12D
                    + LightRitualSequence.satellitePulsePhase(i));
            double x = Math.cos(angle) * orbit;
            double z = Math.sin(angle) * orbit;
            Vec3 center = new Vec3(x, 0.06D, z);
            float alpha = ignition * (0.45F + 0.40F * form);
            miniSeal(out, pose, center, UP, size * pulse, even ? 1.0D : -1.0D,
                    W_R, W_G, W_B, G_R, G_G, G_B, alpha);

            if (!grand) {
                continue;
            }
            // 竖起来的伴生环：从地面慢慢升到胸口高度 —— 一抬头就能看到“不止一张阵”
            double height = 0.55D + 0.55D * ignition + 0.10D * Math.sin(time * 0.04D + i);
            Vec3 vertical = new Vec3(x * 0.94D, height, z * 0.94D);
            Vec3 normal = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            float vAlpha = ignition * 0.32F * (0.6F + 0.4F * form);
            WaterGeometry.ringColor(out, pose, vertical, normal, size * 0.72D,
                    size * 0.075D, C_R, C_G, C_B, vAlpha, 32);
            WaterGeometry.ringColor(out, pose, vertical, normal, size * 0.44D,
                    size * 0.05D, W_R, W_G, W_B, vAlpha * 0.8F, 24);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI * 0.5D + time * 0.03D;
                Vec3 edge = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
                Vec3 s = vertical.add(edge.scale(Math.cos(a) * size * 0.70D))
                        .add(0.0D, Math.sin(a) * size * 0.70D, 0.0D);
                Vec3 e = vertical.add(edge.scale(Math.cos(a) * size * 0.42D))
                        .add(0.0D, Math.sin(a) * size * 0.42D, 0.0D);
                WaterGeometry.tube(out, pose, s, e, Math.max(0.006D, size * 0.03D),
                        C_R, C_G, C_B, vAlpha * 0.9F, 4);
            }
        }
    }

    /** 绕身竖环（“铁魔法”那种立起来的大法阵）。 */
    private static void gates(VertexConsumer out, Matrix4f pose, double range, int tier,
                              float p, float form, double time) {
        int count = LightRitualSequence.gateRings(tier);
        double orbit = 1.55D + 0.12D * tier;
        double size = 0.55D + 0.10D * tier;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2.0D / count + time * 0.006D * ((i & 1) == 0 ? 1.0D : -1.0D);
            double height = 1.05D + i * 0.42D + 0.10D * Math.sin(time * 0.03D + i * 1.3D);
            Vec3 center = new Vec3(Math.cos(angle) * orbit, height, Math.sin(angle) * orbit);
            // 环面法线取切线方向 ⇒ 环像一道门一样立着，玩家可以“穿过去”
            Vec3 normal = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
            float alpha = (0.10F + 0.20F * form) * (0.45F + 0.55F * Math.min(1.0F, p * 2.0F));
            double spin = time * 0.02D * ((i & 1) == 0 ? 1.0D : -1.0D);
            WaterGeometry.ringColor(out, pose, center, normal, size, size * 0.035D,
                    C_R, C_G, C_B, alpha, 48);
            WaterGeometry.ringColor(out, pose, center, normal, size * 1.14D, size * 0.016D,
                    G_R, G_G, G_B, alpha * 0.75F, 48);
            WaterGeometry.ringColor(out, pose, center, normal, size * 0.72D, size * 0.022D,
                    W_R, W_G, W_B, alpha * 0.9F, 40);
            for (int k = 0; k < 12; k++) {
                double a = spin + k * Math.PI / 6.0D;
                Vec3 tangent = normal;
                Vec3 edge = tangent.scale(Math.cos(a)).add(0.0D, Math.sin(a), 0.0D);
                Vec3 s = center.add(edge.scale(size * 0.78D));
                Vec3 e = center.add(edge.scale(size));
                WaterGeometry.tube(out, pose, s, e, Math.max(0.006D, size * 0.018D),
                        G_R, G_G, G_B, alpha * 0.8F, 4);
            }
        }
    }

    /** 头顶核心：低阶只是一颗贴地的小光种；中高阶才有光柱、日冕、盘旋光带、脉冲环与光雨。 */
    private static void core(VertexConsumer out, Matrix4f pose, double range, int tier,
                             float p, float form, float glow, double time, boolean grand) {
        double coreY = grand ? 2.55D + 0.12D * tier + 0.30D * p
                : 1.45D + 0.08D * tier + 0.16D * p;
        double scale = LightRitualSequence.coreScale(p) * (grand ? 1.0D : 0.62D)
                * (1.0D + 0.05D * Math.sin(time * 0.50D));
        Vec3 core = new Vec3(0.0D, coreY, 0.0D);

        // 地面到核心的光柱：低阶不竖柱，只留核心本身
        if (grand) {
            WaterGeometry.tube(out, pose, new Vec3(0.0D, 0.10D, 0.0D), core,
                    0.32D * (0.35D + 0.65D * form), W_R, W_G, W_B, 0.030F + 0.035F * glow, 6);
            WaterGeometry.tube(out, pose, new Vec3(0.0D, 0.10D, 0.0D), core,
                    0.15D * (0.35D + 0.65D * form), G_R, G_G, G_B, 0.075F + 0.075F * glow, 6);
            WaterGeometry.tube(out, pose, new Vec3(0.0D, 0.10D, 0.0D), core,
                    0.055D, 1.0F, 1.0F, 1.0F, 0.16F + 0.20F * glow, 4);
        }

        // 核心：三个正交圆盘叠成一颗发光球（加色通道 ⇒ 重叠处自然爆白 ✓）
        double r = 0.46D * scale;
        WaterGeometry.disk(out, pose, core, UP, r, W_R, W_G, W_B, 0.20F + 0.25F * glow, 32);
        WaterGeometry.disk(out, pose, core, new Vec3(1.0D, 0.0D, 0.0D), r * 0.84D,
                W_R, W_G, W_B, 0.16F + 0.22F * glow, 28);
        WaterGeometry.disk(out, pose, core, new Vec3(0.0D, 0.0D, 1.0D), r * 0.84D,
                W_R, W_G, W_B, 0.16F + 0.22F * glow, 28);
        WaterGeometry.disk(out, pose, core, UP, r * 0.42D, 1.0F, 1.0F, 1.0F,
                0.45F + 0.30F * glow, 24);

        // 核心外环：水平 + 两道斜环，慢慢转（低阶只有一道水平环）
        WaterGeometry.ringColor(out, pose, core, UP, r * 2.0D, r * 0.10D,
                G_R, G_G, G_B, 0.30F + 0.25F * glow, 48);
        if (grand) {
            WaterGeometry.ringColor(out, pose, core, new Vec3(1.0D, 0.35D, 0.55D).normalize(),
                    r * 1.7D, r * 0.06D, C_R, C_G, C_B, 0.22F + 0.20F * glow, 40);
            WaterGeometry.ringColor(out, pose, core, new Vec3(-0.45D, 0.6D, 1.0D).normalize(),
                    r * 1.55D, r * 0.05D, W_R, W_G, W_B, 0.18F + 0.18F * glow, 40);
        }

        // 水平光芒（低阶 4 道、高阶 8 道；竖直光芒只有高阶才有）
        int spikes = grand ? 8 : 4;
        for (int i = 0; i < spikes; i++) {
            double angle = i * Math.PI * 2.0D / spikes + time * 0.02D;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            WaterGeometry.tube(out, pose, core.add(dir.scale(r * 0.55D)),
                    core.add(dir.scale(r * (1.55D + 0.55D * glow))),
                    Math.max(0.008D, r * 0.045D), G_R, G_G, G_B, 0.28F + 0.30F * glow, 4);
        }
        if (grand) {
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI * 0.5D + time * 0.012D;
                Vec3 dir = new Vec3(0.0D, Math.cos(angle), Math.sin(angle));
                WaterGeometry.tube(out, pose, core.add(dir.scale(r * 0.55D)),
                        core.add(dir.scale(r * 1.35D)),
                        Math.max(0.007D, r * 0.04D), W_R, W_G, W_B, 0.20F + 0.20F * glow, 4);
            }
        }

        // 三条从地面盘旋上去的光带：低阶没有
        if (grand && p > 0.10F) {
            int segments = 30;
            for (int ribbon = 0; ribbon < 3; ribbon++) {
                for (int i = 0; i < segments; i++) {
                    double t0 = i / (double) segments;
                    double t1 = (i + 1) / (double) segments;
                    double a0 = time * 0.03D + t0 * Math.PI * 4.0D + ribbon * Math.PI * 2.0D / 3.0D;
                    double a1 = time * 0.03D + t1 * Math.PI * 4.0D + ribbon * Math.PI * 2.0D / 3.0D;
                    double r0 = range * 0.70D * (1.0D - t0 * 0.92D);
                    double r1 = range * 0.70D * (1.0D - t1 * 0.92D);
                    double y0 = 0.15D + coreY * t0 * (0.30D + 0.70D * form);
                    double y1 = 0.15D + coreY * t1 * (0.30D + 0.70D * form);
                    Vec3 p0 = new Vec3(Math.cos(a0) * r0, y0, Math.sin(a0) * r0);
                    Vec3 p1 = new Vec3(Math.cos(a1) * r1, y1, Math.sin(a1) * r1);
                    Vec3 e0 = new Vec3(-Math.sin(a0), 0.0D, Math.cos(a0)).scale(0.045D);
                    Vec3 e1 = new Vec3(-Math.sin(a1), 0.0D, Math.cos(a1)).scale(0.045D);
                    float alpha = (0.12F + 0.10F * glow) * (0.35F + 0.65F * (float) t0);
                    WaterGeometry.quad(out, pose, p0.subtract(e0), p0.add(e0),
                            p1.add(e1), p1.subtract(e1), W_R, W_G, W_B, alpha);
                }
            }
        }

        // 脉冲环：中高阶才从核心向外扩
        if (grand) {
            for (int k = 0; k < 2; k++) {
                double phase = (time * 0.018D + k * 0.5D) % 1.0D;
                double radius = range * (0.15D + 0.85D * phase);
                float alpha = (1.0F - (float) phase) * 0.18F * form;
                WaterGeometry.ringColor(out, pose, new Vec3(0.0D, 0.07D + k * 0.01D, 0.0D), UP,
                        radius, range * 0.006D, C_R, C_G, C_B, alpha, 64);
            }
        }

        // 光雨：只有 t4/t5 的“天穹”才下
        if (LightRitualSequence.hasLightRain(tier) && p > 0.30F) {
            int streaks = 10;
            for (int i = 0; i < streaks; i++) {
                double phase = (time * 0.020D + i * 0.173D) % 1.0D;
                double a = i * 2.399D + time * 0.004D;
                double rr = range * (0.32D + 0.50D * ((i * 37) % 10) / 10.0D);
                double y = 0.15D + (1.0D - phase) * 5.4D;
                Vec3 base = new Vec3(Math.cos(a) * rr, y, Math.sin(a) * rr);
                Vec3 top = base.add(0.0D, 0.95D, 0.0D);
                Vec3 e1 = new Vec3(-Math.sin(a), 0.0D, Math.cos(a)).scale(0.035D);
                Vec3 e2 = new Vec3(Math.cos(a), 0.0D, Math.sin(a)).scale(0.035D);
                float alpha = (float) (1.0D - phase) * 0.16F * (0.35F + 0.65F * form);
                if (alpha <= 0.01F) {
                    continue;
                }
                WaterGeometry.quad(out, pose, base.subtract(e1), base.add(e1), top.add(e1),
                        top.subtract(e1), W_R, W_G, W_B, alpha);
                WaterGeometry.quad(out, pose, base.subtract(e2), base.add(e2), top.add(e2),
                        top.subtract(e2), C_R, C_G, C_B, alpha * 0.8F);
            }
        }
    }

    /** 释放爆发：三圈扩散环 + 一圈迟到的余波 + 一根光柱 + 12 道星芒。 */
    private static void burst(VertexConsumer out, Matrix4f pose, int tier, float strength, double age) {
        double u = Math.min(1.0D, age / 18.0D);
        double R = 3.2D + tier * 1.15D;
        float fade = (float) (1.0D - u) * strength;

        for (int i = 0; i < 3; i++) {
            double radius = R * (0.22D + 1.10D * u) * (1.0D + i * 0.22D);
            WaterGeometry.ringColor(out, pose, new Vec3(0.0D, 0.07D + i * 0.02D, 0.0D), UP,
                    radius, Math.max(0.05D, R * 0.020D), W_R, W_G, W_B,
                    fade * (0.62F / (i + 1)), 72);
        }
        // 迟到的第二层余波：主环快散时再推一圈，听起来才像“雷声滚过去” ✓
        if (age > 4.0D) {
            double u2 = Math.min(1.0D, (age - 4.0D) / 14.0D);
            double radius = R * (0.18D + 1.35D * u2);
            float alpha = (float) (1.0D - u2) * strength * 0.35F;
            WaterGeometry.ringColor(out, pose, new Vec3(0.0D, 0.10D, 0.0D), UP,
                    radius, Math.max(0.04D, R * 0.014D), C_R, C_G, C_B, alpha, 72);
        }
        // 竖直光柱：先冲高、再淡出
        double height = (5.5D + tier * 1.4D) * Math.min(1.0D, u * 4.0D + 0.25D);
        WaterGeometry.tube(out, pose, new Vec3(0.0D, 0.10D, 0.0D), new Vec3(0.0D, height, 0.0D),
                (0.45D - 0.35D * u) * strength + 0.05D, 1.0F, 0.98F, 0.92F, fade * 0.55F, 8);
        // 12 道星芒
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6.0D;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
            WaterGeometry.tube(out, pose, center.add(dir.scale(R * 0.2D)),
                    center.add(dir.scale(R * (0.35D + 1.25D * u))),
                    Math.max(0.02D, R * 0.012D * (1.0D - u)), G_R, G_G, G_B, fade * 0.45F, 4);
        }
    }

    // ==================================================================
    //  几何小工具
    // ==================================================================

    /** 水平面上的极坐标点（相对 pose 原点）✓。 */
    private static Vec3 polar(double angle, double radius, double y) {
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    /** 一段圆弧（用扁 quad 拼），用于“把主阵一笔一笔描出来”✓。 */
    private static void arc(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 normal,
                            double radius, double width, double from, double sweep,
                            float r, float g, float b, float alpha, int segments) {
        if (segments < 2 || Math.abs(sweep) < 1.0E-4D) {
            return;
        }
        Vec3 right = WaterSpellRules.right(normal);
        Vec3 up = right.cross(normal).normalize();
        double inner = Math.max(0.0D, radius - width);
        for (int i = 0; i < segments; i++) {
            double a0 = from + sweep * i / segments;
            double a1 = from + sweep * (i + 1) / segments;
            Vec3 p0 = center.add(WaterGeometry.radial(right, up, a0, radius));
            Vec3 p1 = center.add(WaterGeometry.radial(right, up, a1, radius));
            Vec3 q0 = center.add(WaterGeometry.radial(right, up, a0, inner));
            Vec3 q1 = center.add(WaterGeometry.radial(right, up, a1, inner));
            WaterGeometry.quad(out, pose, p0, p1, q1, q0, r, g, b, alpha);
        }
    }

    /** 一道符文：径向短划 ＋ 外端小菱形（几何体画不出真字，但远看是“文字带”✓）。 */
    private static void rune(VertexConsumer out, Matrix4f pose, double angle, double radius,
                             double halfHeight, double width,
                             float r, float g, float b, float alpha) {
        Vec3 a = polar(angle, radius - halfHeight, 0.06D);
        Vec3 c = polar(angle, radius + halfHeight, 0.06D);
        WaterGeometry.tube(out, pose, a, c, width, r, g, b, alpha, 4);
        Vec3 tipA = polar(angle, radius + halfHeight + width * 2.2D, 0.06D);
        Vec3 tipB = polar(angle - width * 6.0D / Math.max(0.2D, radius), radius + halfHeight, 0.06D);
        Vec3 tipC = polar(angle + width * 6.0D / Math.max(0.2D, radius), radius + halfHeight, 0.06D);
        WaterGeometry.tube(out, pose, tipA, tipB, width * 0.7D, r, g, b, alpha * 0.85F, 4);
        WaterGeometry.tube(out, pose, tipA, tipC, width * 0.7D, r, g, b, alpha * 0.85F, 4);
    }

    /** 一颗小法阵：两圈环 + 四向刻痕 + 中心亮核 ✓。 */
    private static void miniSeal(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 normal,
                                 double radius, double spin, float r1, float g1, float b1,
                                 float r2, float g2, float b2, float alpha) {
        WaterGeometry.ringColor(out, pose, center, normal, radius, radius * 0.075D,
                r1, g1, b1, alpha, 28);
        WaterGeometry.ringColor(out, pose, center, normal, radius * 0.70D, radius * 0.055D,
                r2, g2, b2, alpha * 0.9F, 24);
        for (int k = 0; k < 4; k++) {
            double a = spin + k * Math.PI * 0.5D;
            Vec3 dir = WaterGeometry.radial(WaterSpellRules.right(normal),
                    WaterSpellRules.right(normal).cross(normal).normalize(), a, 1.0D);
            WaterGeometry.tube(out, pose, center.add(dir.scale(radius * 0.22D)),
                    center.add(dir.scale(radius * 0.94D)), Math.max(0.006D, radius * 0.045D),
                    r2, g2, b2, alpha * 0.9F, 4);
        }
        WaterGeometry.disk(out, pose, center.add(normal.scale(0.008D)), normal,
                radius * 0.16D, 1.0F, 1.0F, 1.0F, alpha * 0.75F, 16);
    }

    /** 水平八角星的一环（正方形边线，中心在原点）✓。 */
    private static void square(VertexConsumer out, Matrix4f pose, double radius, double spin,
                               double width, float r, float g, float b, float alpha) {
        for (int i = 0; i < 4; i++) {
            double a0 = spin + Math.PI * 0.5D * i + Math.PI * 0.25D;
            double a1 = a0 + Math.PI * 0.5D;
            Vec3 p0 = polar(a0, radius, 0.06D);
            Vec3 p1 = polar(a1, radius, 0.06D);
            WaterGeometry.tube(out, pose, p0, p1, width, r, g, b, alpha, 4);
        }
    }

    private static Vec3 interpolated(Player player, float partial) {
        return new Vec3(
                Mth.lerp(partial, player.xo, player.getX()),
                Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));
    }

    // ==================================================================
    //  读条状态
    // ==================================================================

    /** 一次读条的纯数据快照（不引用引擎类型，渲染/粒子都用它）✓。 */
    private record CastInfo(int tier, float ratio, double range) {
    }

    /** 正在读条的玩家（tick 状态）。 */
    private static final class CastState {
        int tier;
        float ratio;
        float maxRatio;
        double x;
        double y;
        double z;
        long lastSeen;
        long nextBeat;
        final float[] ignited = new float[8];

        CastState(int tier) {
            this.tier = tier;
        }
    }

    /** 释放爆发。 */
    private record Burst(double x, double y, double z, int tier, float strength, long start) {
    }

    /**
     * 读一个玩家的读条状态。引擎不在 / 不是光系 / 没有读条 ⇒ {@code null}。
     *
     * <p>⚠️ 引擎类型只出现在这个方法里；外面永远先查 {@code ModList}，再包一层
     * {@code Throwable} 兜底 ⇒ dev 环境（无引擎）不会因为类找不到而崩 ✓。
     */
    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/lightfx")
                        .info("TN-C/lightfx: spell_engine absent -> light cast FX idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/lightfx")
                        .warn("TN-C/lightfx: cast scan failed, light cast FX disabled ({})", t.toString());
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
        if (spell.group == null || !LIGHT_GROUPS.contains(spell.group)) {
            return null;
        }
        int tier = LightRitualSequence.tier(spell.learn == null ? 1 : spell.learn.tier);
        long now = player.level().getGameTime();
        float ratio = process.progress(now).ratio();
        // 光耀的 range 就是阵的半径；召唤链的 range = 0（不靠范围选目标）⇒ 给一个随档位变大的仪式半径，
        // 不然 t5 召唤也只有 4 格，读条气势起不来 ✗。
        double range = spell.range > 1.0F ? spell.range : 3.5D + tier * 0.9D;
        return new CastInfo(tier, LightRitualSequence.clamp(ratio),
                Math.max(4.0D, Math.min(24.0D, range)));
    }

    // ==================================================================
    //  屏幕：先压暗、再暖白过曝（“光污染”）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (lightWash <= 0.004F && darkVignette <= 0.004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // ① 暴风雨前夕：上下两道黑边 + 左右各五级收边（中间留给世界，不闷）
        if (darkVignette > 0.004F) {
            int alpha = (int) (darkVignette * 58.0F);
            if (alpha > 1) {
                int top = (int) (height * 0.38F);
                int bottom = (int) (height * 0.62F);
                event.getGuiGraphics().fillGradient(0, 0, width, top, alpha << 24, 0);
                event.getGuiGraphics().fillGradient(0, bottom, width, height, 0, alpha << 24);
                // 左右收边：五级竖条近似径向渐晕（GuiGraphics 的渐变只能竖着走）
                int side = (int) (width * 0.14F);
                for (int s = 0; s < 5; s++) {
                    int x0 = side * s / 5;
                    int x1 = side * (s + 1) / 5;
                    int a = alpha * (5 - s) / 5;
                    if (a <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, a << 24);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height, a << 24);
                }
            }
        }

        // ② 过曝：整屏一层暖白（很低，靠下面两层叠出“中心亮、四周亮”）
        if (lightWash > 0.004F) {
            int base = (int) (lightWash * 24.0F);
            if (base > 1) {
                event.getGuiGraphics().fill(0, 0, width, height, (base << 24) | 0xFFF3D9);
            }
            int edge = (int) (lightWash * 26.0F);
            if (edge > 1) {
                int top = (int) (height * 0.45F);
                event.getGuiGraphics().fillGradient(0, 0, width, top,
                        (edge << 24) | 0xFFFFFF, 0);
                event.getGuiGraphics().fillGradient(0, height - top, width, height,
                        0, (edge << 24) | 0xFFFFFF);
                // 左右也吃一点暖白：光不是从正上方来的，是从四周“溢”进来的 ✓
                int side = (int) (width * 0.12F);
                int sideGlow = (int) (lightWash * 18.0F);
                for (int s = 0; s < 4; s++) {
                    int x0 = side * s / 4;
                    int x1 = side * (s + 1) / 4;
                    int a = sideGlow * (4 - s) / 4;
                    if (a <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, (a << 24) | 0xFFF3D9);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height,
                            (a << 24) | 0xFFF3D9);
                }
            }
        }
    }
}
