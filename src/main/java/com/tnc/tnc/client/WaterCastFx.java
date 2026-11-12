package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.TNWaterLeviathanEntity;
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
 * <b>水系读条特效（2026-11-06）</b> —— 作者：「水法的法阵优化优化，然后也给他加上前戏」。
 *
 * <p>水法以前只有释放之后的阵／水体，读条期间只有 SpellEngine 的粒子。这里补的是**释放前**
 * 的仪式场，并且四条链不是同一张阵换颜色：
 * <ul>
 *   <li><b>水球链</b>：脚下潮汐阵 ＋ 胸前凝聚的水珠／三道交叉水环；</li>
 *   <li><b>水波链</b>：脚下阵 ＋ 中高阶才升起的环形潮墙（低阶只留贴地涟漪）；</li>
 *   <li><b>水缚链</b>：脚下阵 ＋ 四周竖起的细水柱／水环笼，高阶才封顶；</li>
 *   <li><b>雨链</b>：脚下阵 ＋ 头顶第二张雨阵 ＋ 落下的水线，高阶雨线更密。</li>
 * </ul>
 *
 * <p>低阶（t1–t2）刻意不画竖墙、不压屏幕、不震屏，读条短而干净；t3 开始出现一条竖向结构
 * 和轻微蓝色暗角；t4/t5 才有完整的潮墙／水笼／天雨和释放震屏。这样低阶不会“小号 t5”，
 * 也不会让每一次水弹都变成屏幕灾难。
 *
 * <p>全部几何都在客户端算，只读 {@code SpellCast.Process.progress()}，不新增服务端实体；
 * 释放后的持久水阵仍由服务端实体铺，所以其他玩家也能看见。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WaterCastFx {

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** 水法的四条链。 */
    private static final Set<String> WATER_GROUPS =
            Set.of("water_ball", "water_wave", "water_bind", "water_rain");

    /** 与实体法阵同一套水色：珍珠白、青、深蓝，金色只做少量刻度。 */
    private static final float W_R = .78F, W_G = .96F, W_B = 1.0F;
    private static final float C_R = .26F, C_G = .78F, C_B = 1.0F;
    private static final float G_R = .95F, G_G = .74F, G_B = .30F;
    private static final float D_R = .02F, D_G = .10F, D_B = .28F;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;

    private static float waterWash;
    private static float deepVignette;

    private WaterCastFx() {
    }

    private enum Family {
        ORB, WAVE, BIND, RAIN
    }

    // ==================================================================
    //  Tick：读条状态、粒子、屏幕强度、释放检测
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
            waterWash = 0;
            deepVignette = 0;
        }
        if (level == null || mc.player == null) {
            waterWash = 0;
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
            CastState state = CASTS.computeIfAbsent(player.getUUID(),
                    id -> new CastState(info.tier(), true, player.getYRot()));
            state.tier = info.tier();
            state.family = info.family();
            state.ratio = info.ratio();
            state.maxRatio = Math.max(state.maxRatio, info.ratio());
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.lastSeen = now;

            double distance = player == mc.player ? 0 : player.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 32);
            if (distance < 64) {
                tickParticles(level, pos, pos.y + player.getBbHeight() * .58D + 1.05D + info.tier() * .08D,
                        info, state, minimal);
            }
            if (player == mc.player) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()));
                vignette = Math.max(vignette, darkVignette(info.tier(), info.ratio()));
                tickHeartbeat(info, state, now);
            } else if (falloff > 0) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()) * (float) falloff * .45F);
                vignette = Math.max(vignette, darkVignette(info.tier(), info.ratio()) * (float) falloff * .3F);
            }
        }

        // Boss casts do not go through SpellEngine, so they publish their windup through four
        // synched ints on the leviathan. Reuse the same ritual at a boss-sized radius.
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof TNWaterLeviathanEntity boss) || !boss.isAlive()) {
                continue;
            }
            CastInfo info = scanBoss(boss);
            if (info == null) {
                continue;
            }
            seen.add(boss.getUUID());
            Vec3 pos = boss.position();
            CastState state = CASTS.computeIfAbsent(boss.getUUID(), id -> new CastState(info.tier(), false));
            state.tier = info.tier();
            state.family = info.family();
            state.ratio = info.ratio();
            state.maxRatio = Math.max(state.maxRatio, info.ratio());
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.lastSeen = now;

            double distance = boss.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 48);
            if (distance < 80) {
                tickParticles(level, pos, pos.y + boss.getBbHeight() * .55D,
                        info, state, minimal);
            }
            if (falloff > 0) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()) * (float) falloff);
                vignette = Math.max(vignette, darkVignette(info.tier(), info.ratio()) * (float) falloff);
                if (distance < 40) {
                    tickHeartbeat(info, state, now);
                }
            }
        }

        Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            CastState state = entry.getValue();
            if (now - state.lastSeen <= 3 && state.maxRatio > .3F) {
                spawnBurst(level, state, Math.min(1, state.maxRatio));
            }
            it.remove();
        }
        CASTS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 40);

        waterWash += (wash - waterWash) * (wash > waterWash ? .35F : .12F);
        deepVignette += (vignette - deepVignette) * (vignette > deepVignette ? .30F : .10F);
        if (waterWash < .004F) {
            waterWash = 0;
        }
        if (deepVignette < .004F) {
            deepVignette = 0;
        }
        BURSTS.removeIf(b -> now - b.start > 18L);
    }

    /** t5 的潮汐心跳：越接近释放越密；低阶没有。 */
    private static void tickHeartbeat(CastInfo info, CastState state, long now) {
        if (info.tier() < 5) {
            return;
        }
        int every = info.ratio() < .45F ? 18 : info.ratio() < .8F ? 11 : 7;
        if (now < state.nextBeat) {
            return;
        }
        state.nextBeat = now + every;
        TNSpellClientVisuals.pulseShake(.08F + .10F * info.ratio());
    }

    private static void tickParticles(ClientLevel level, Vec3 pos, double coreY, CastInfo info,
                                      CastState state, boolean minimal) {
        double cx = pos.x, cy = pos.y, cz = pos.z;
        double R = info.radius();
        int motes = (2 + info.tier()) * (minimal ? 1 : 2);
        for (int i = 0; i < motes; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = R * (.20 + RANDOM.nextDouble() * .72);
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            level.addParticle(ParticleTypes.BUBBLE, x, cy + .08 + RANDOM.nextDouble() * .55, z,
                    -Math.cos(a) * .02, .035 + RANDOM.nextDouble() * .035, -Math.sin(a) * .02);
        }
        double coreHeight = coreY;
        int sparks = (1 + (int) (info.ratio() * 3)) * (minimal ? 1 : 2);
        for (int i = 0; i < sparks; i++) {
            level.addParticle(ParticleTypes.SPLASH,
                    cx + (RANDOM.nextDouble() - .5) * .7,
                    coreHeight + (RANDOM.nextDouble() - .5) * .5,
                    cz + (RANDOM.nextDouble() - .5) * .7,
                    (RANDOM.nextDouble() - .5) * .12,
                    .04 + RANDOM.nextDouble() * .10,
                    (RANDOM.nextDouble() - .5) * .12);
        }
        if (info.family() == Family.RAIN) {
            // 逆雨：雨水是从地面被抽回天上的，所以这里给的是向上的速度，不是从上往下掉。
            int lifts = (3 + info.tier() * 2) * (minimal ? 1 : 2);
            for (int i = 0; i < lifts; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double r = R * (.18 + RANDOM.nextDouble() * .78);
                level.addParticle(ParticleTypes.SPLASH,
                        cx + Math.cos(a) * r, cy + .06, cz + Math.sin(a) * r,
                        0, .30 + RANDOM.nextDouble() * .35, 0);
                if (!minimal && RANDOM.nextBoolean()) {
                    level.addParticle(ParticleTypes.BUBBLE,
                            cx + Math.cos(a) * r, cy + .12, cz + Math.sin(a) * r,
                            0, .18 + RANDOM.nextDouble() * .12, 0);
                }
            }
        }
        if (state.playerCast) {
            float p = info.ratio();
            int feet = minimal ? 1 : 2;
            for (int i = 0; i < feet; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double r = .55 + RANDOM.nextDouble() * .55;
                level.addParticle(ParticleTypes.SPLASH,
                        cx + Math.cos(a) * r, cy + .10, cz + Math.sin(a) * r,
                        Math.cos(a) * .045, .06 + RANDOM.nextDouble() * .05, Math.sin(a) * .045);
            }
            if (p < state.beastStart) {
                state.beastImpacted = false;
                // 门刚开始凝聚时，门心就滴水：即使几何被草丛挡住，玩家也能看到门在哪。
                if (p > .04F) {
                    Vec3 gate = sideCenter(state);
                    level.addParticle(ParticleTypes.FALLING_WATER,
                            gate.x + (RANDOM.nextDouble() - .5) * .35,
                            gate.y + (RANDOM.nextDouble() - .5) * .25,
                            gate.z + (RANDOM.nextDouble() - .5) * .35,
                            0, -.03, 0);
                    if (!minimal && RANDOM.nextBoolean()) {
                        level.addParticle(ParticleTypes.BUBBLE, gate.x, gate.y, gate.z,
                                0, .02, 0);
                    }
                }
            } else if (p < state.beastEnd) {
                float u = (p - state.beastStart) / (state.beastEnd - state.beastStart);
                Vec3 at = beastPoint(state, info, u);
                level.addParticle(ParticleTypes.FALLING_WATER, at.x, at.y, at.z,
                        (RANDOM.nextDouble() - .5) * .04, -.02, (RANDOM.nextDouble() - .5) * .04);
                if (!minimal && RANDOM.nextBoolean()) {
                    level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z,
                            (RANDOM.nextDouble() - .5) * .03, .03, (RANDOM.nextDouble() - .5) * .03);
                }
            } else if (!state.beastImpacted) {
                state.beastImpacted = true;
                absorb(level, state, info);
            }
        }
        // 释放前最后几帧：脚边溅起一圈水花（不重复点火，state 记录）
        if (info.ratio() > .82F && state.lastSplash < .82F) {
            state.lastSplash = 1;
            int count = 10 + info.tier() * 3;
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2 / count;
                level.addParticle(ParticleTypes.SPLASH,
                        cx + Math.cos(a) * R * .55, cy + .15, cz + Math.sin(a) * R * .55,
                        Math.cos(a) * .12, .14, Math.sin(a) * .12);
            }
        }
    }

    /** 小兽撞进胸口：胸口向内收的水花 + 脚边一圈小水波。 */
    private static void absorb(ClientLevel level, CastState state, CastInfo info) {
        // 入体这一下给屏幕一点蓝色潮光，别让“吸收”只发生在世界里。
        waterWash = Math.max(waterWash, .20F + .035F * info.tier());
        double cx = state.x, cy = state.y, cz = state.z;
        int count = 12 + info.tier() * 2;
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .05 + RANDOM.nextDouble() * .16;
            level.addParticle(i % 3 == 0 ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH,
                    cx, cy + .95 + RANDOM.nextDouble() * .35, cz,
                    Math.cos(a) * speed, (RANDOM.nextDouble() - .35) * .12, Math.sin(a) * speed);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            level.addParticle(ParticleTypes.SPLASH,
                    cx + Math.cos(a) * .95, cy + .12, cz + Math.sin(a) * .95,
                    Math.cos(a) * .10, .10, Math.sin(a) * .10);
        }
    }

    private static void spawnBurst(ClientLevel level, CastState state, float strength) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, state.family, strength,
                level.getGameTime()));
        if (state.tier >= 3) {
            TNSpellClientVisuals.pulseShake((.16F + .05F * state.tier) * strength);
        }
        int motes = (int) ((26 + state.tier * 12) * strength);
        boolean minimal = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL;
        if (minimal) {
            motes /= 2;
        }
        for (int i = 0; i < motes; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .10 + RANDOM.nextDouble() * .42;
            level.addParticle(ParticleTypes.SPLASH,
                    state.x, state.y + .2 + RANDOM.nextDouble() * .8, state.z,
                    Math.cos(a) * speed, .25 + RANDOM.nextDouble() * .45, Math.sin(a) * speed);
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.BUBBLE, state.x, state.y + .25, state.z,
                        Math.cos(a) * .08, .20, Math.sin(a) * .08);
            }
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
        if (level == null || level != owner) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        boolean any = false;
        List<GhostDraw> ghosts = new ArrayList<>();

        VertexConsumer out = BUFFERS.getBuffer(RenderType.lightning());
        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            Vec3 base = interpolated(player, partial);
            if (base.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (base.x - camera.x),
                    (float) (base.y - camera.y),
                    (float) (base.z - camera.z));
            CastState state = CASTS.get(player.getUUID());
            ritual(out, pose, info, now + partial, state);
            any = true;
            if (state != null && state.playerCast) {
                GhostDraw echo = ghostFor(info, state, .055F, .26F);
                if (echo != null) {
                    ghosts.add(echo);
                }
                GhostDraw ghost = ghostFor(info, state);
                if (ghost != null) {
                    ghosts.add(ghost);
                }
            }
        }
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof TNWaterLeviathanEntity boss) || !boss.isAlive()) {
                continue;
            }
            CastInfo info = scanBoss(boss);
            if (info == null) {
                continue;
            }
            Vec3 base = boss.getPosition(partial);
            if (base.distanceToSqr(camera) > 128 * 128) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (base.x - camera.x),
                    (float) (base.y - camera.y),
                    (float) (base.z - camera.z));
            bossRitual(out, pose, info, now + partial, CASTS.get(boss.getUUID()));
            any = true;
        }
        for (Burst burst : BURSTS) {
            double age = now - burst.start + partial;
            if (age < 0 || age > 18) {
                continue;
            }
            Vec3 at = new Vec3(burst.x, burst.y, burst.z);
            if (at.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (at.x - camera.x),
                    (float) (at.y - camera.y),
                    (float) (at.z - camera.z));
            burst(out, pose, burst.tier, burst.family, burst.strength, age);
            any = true;
        }
        if (any) {
            BUFFERS.endBatch(RenderType.lightning());
        }
        if (!ghosts.isEmpty()) {
            boolean drew = false;
            boolean wisp = false;
            VertexConsumer wispOut = null;
            PoseStack ghostPose = new PoseStack();
            for (GhostDraw ghost : ghosts) {
                ghostPose.pushPose();
                ghostPose.translate(ghost.at.x - camera.x, ghost.at.y - camera.y,
                        ghost.at.z - camera.z);
                boolean ok = LeviathanGhostVisual.render(ghostPose, BUFFERS, level, ghost.at,
                        ghost.forward, ghost.size, ghost.alpha, partial);
                drew |= ok;
                if (!ok) {
                    // 模型层被光影/批处理挡掉时至少留一条水影，不能让“前戏”整个消失。
                    if (wispOut == null) {
                        wispOut = BUFFERS.getBuffer(RenderType.lightning());
                    }
                    ghostWisp(wispOut, ghostPose.last().pose(), now + partial, ghost.alpha);
                    wisp = true;
                }
                ghostPose.popPose();
            }
            if (wisp) {
                BUFFERS.endBatch(RenderType.lightning());
            }
            if (drew) {
                BUFFERS.endBatch(LeviathanGhostVisual.renderType());
            }
        }
    }

    /** Boss 模型不可用时的兜底水影：一颗亮水珠 + 一圈小环，短而清楚，不冒充原模型。 */
    private static void ghostWisp(VertexConsumer out, Matrix4f pose, double time, float alpha) {
        WaterGeometry.droplet(out, pose, Vec3.ZERO, .28, time * 1.1D,
                C_R, C_G, C_B, alpha * .90F);
        WaterGeometry.ringColor(out, pose, Vec3.ZERO, UP, .24, .012,
                W_R, W_G, W_B, alpha * .48F, 16);
        for (int i = 0; i < 3; i++) {
            double a = time * .5D + i * Math.PI * 2D / 3D;
            Vec3 tip = WaterGeometry.radial(WaterSpellRules.right(UP),
                    WaterSpellRules.right(UP).cross(UP).normalize(), a, .26);
            WaterGeometry.stroke(out, pose, UP, Vec3.ZERO, tip, .010,
                    C_R, C_G, C_B, alpha * .45F);
        }
    }

    /**
     * 读条中的水系仪式场。地面主阵始终存在；四条链各自再加一层结构，但低阶只保留最小版本。
     */
    private static void ritual(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                               CastState state) {
        int tier = info.tier();
        float p = info.ratio();
        double R = info.radius();

        // 逆雨接管前戏：地面先湿、水线往天上收、头顶聚云。
        // 旧的地面潮汐阵 + 四系法阵整段还在下面（ENABLED 改成 false 就退回去），
        // 旁边的门/回声门和水渊兽残影不动——它们和"水往上走"不冲突。
        if (WaterReverseRain.ENABLED) {
            WaterReverseRain.draw(out, pose, tier, p, R, time);
            if (state != null && state.playerCast) {
                sideRitual(out, pose, info, state, time);
            }
            return;
        }

        float form = formation(p);
        float glow = chargeGlow(p);
        double grown = R * (.72 + .28 * form);
        float alpha = .20F + .62F * glow;

        // 地面潮汐阵：同一套环／波纹／水滴母题；半径随读条张开一点。
        WaterGeometry.seal(out, out, pose, new Vec3(0, .045, 0), UP, grown, time, alpha, tier);
        WaterGeometry.disk(out, pose, new Vec3(0, .035, 0), UP, grown * .98,
                D_R, D_G, D_B, alpha * .10F, 48);

        // 脚边始终有一圈很小的水波，不抢主阵，但让“站在水里施法”有落点。
        double ripple = Math.min(2.6, 1.05D + tier * .12D) * (.90D + .10D * form);
        WaterWaveVisuals.rippleGlow(out, pose, ripple, time, .09F + .14F * glow);

        if (state != null && state.playerCast) {
            sideRitual(out, pose, info, state, time);
        }
        switch (info.family()) {
            case ORB -> orb(out, pose, info, time, form, glow);
            case WAVE -> waveRitual(out, pose, info, time, form, glow);
            case BIND -> bindRitual(out, pose, info, time, form, glow);
            case RAIN -> rainRitual(out, pose, info, time, form, glow);
        }
    }

    /** Boss-scale version: the same tide seal under the leviathan, plus a vertical seal at the mouth. */
    private static void bossRitual(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                                   CastState state) {
        ritual(out, pose, info, time, state);
        Vec3 look = info.look().lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : info.look().normalize();
        Vec3 mouth = new Vec3(0, TNWaterLeviathanEntity.MOUTH_HEIGHT, 0)
                .add(look.scale(TNWaterLeviathanEntity.MOUTH_FORWARD));
        float alpha = .20F + .48F * chargeGlow(info.ratio());
        WaterGeometry.seal(out, out, pose, mouth, look, 3.5 + info.tier() * .5,
                time * .8, alpha, info.tier());
        WaterGeometry.ringColor(out, pose, mouth, look, 3.8 + info.tier() * .5, .035,
                W_R, W_G, W_B, .22F + .25F * chargeGlow(info.ratio()), 48);
    }

    // ------------------------------------------------------------------
    // 单侧小法阵 + 蓝金水渊兽残影（玩家读条专属）
    // ------------------------------------------------------------------

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 小法阵在一个读条内固定位置/角度，不会随镜头或 tick 漂移。 */
    private static Vec3 sideCenter(CastState state) {
        return gateCenter(state.sideAngle, state.sideDistance, state.sideHeight,
                state.sideStyle, state.tier);
    }

    private static Vec3 echoCenter(CastState state) {
        return gateCenter(state.echoAngle, state.echoDistance, state.echoHeight,
                state.echoStyle, state.tier);
    }

    private static Vec3 gateCenter(double angle, double distance, double height,
                                   int style, int tier) {
        double x = Math.cos(angle) * distance;
        double z = Math.sin(angle) * distance;
        double gate = .74D + tier * .05D;
        return switch (style) {
            case 0 -> new Vec3(x, .035, z);                 // 平贴地面
            case 1 -> new Vec3(x, Math.max(height, gate + .18D), z); // 立着朝向玩家
            default -> new Vec3(x * .94,
                    Math.max(height, gate + .22D) * .72, z * .94); // 斜插
        };
    }

    private static Vec3 sideNormal(CastState state) {
        return gateNormal(state.sideAngle, state.sideStyle);
    }

    private static Vec3 echoNormal(CastState state) {
        return gateNormal(state.echoAngle, state.echoStyle);
    }

    private static Vec3 gateNormal(double angle, int style) {
        return switch (style) {
            case 0 -> UP;
            case 1 -> new Vec3(-Math.cos(angle), 0, -Math.sin(angle));
            default -> new Vec3(-Math.cos(angle) * .62, .78, -Math.sin(angle) * .62).normalize();
        };
    }

    /**
     * 唤兽门：一张主门 + 一张更小的回声门。主门固定在玩家正前方 ±60° 的扇区里，
     * 第一人称也能第一眼看见；门里先亮水镜，再张开八片水叶，最后才把兽送出来。
     * 回声门只做“周围还有门”的空间层次，不放第二只兽。
     */
    private static void sideRitual(VertexConsumer out, Matrix4f pose, CastInfo info,
                                   CastState state, double time) {
        float p = info.ratio();
        float appear = smooth((p - .03F) / .12F);
        float gone = 1 - smooth((p - (state.beastEnd + .06F)) / .20F);
        float alpha = (.20F + .44F * chargeGlow(p)) * appear * Math.max(0, gone);
        if (alpha > .002F) {
            gateFx(out, pose, sideCenter(state), sideNormal(state),
                    .74D + info.tier() * .05D, alpha, info.tier(), time, p, true);
        }

        float echoAppear = smooth((p - .10F) / .15F);
        float echoGone = 1 - smooth((p - (state.beastEnd + .10F)) / .22F);
        float echoAlpha = (.07F + .20F * chargeGlow(p)) * echoAppear * Math.max(0, echoGone);
        if (echoAlpha > .002F) {
            gateFx(out, pose, echoCenter(state), echoNormal(state),
                    .46D + info.tier() * .03D, echoAlpha, info.tier(), time + 1.7D, p, false);
        }

        ghostTrail(out, pose, info, state, time);
    }

    /**
     * 一扇门本身的几何：水镜、双层外环、八片张开的潮叶、四段留白潮弧、金刻度、
     * 中心水珠和两颗绕行水珠；主门额外补一条落到地面的细水线和一圈地面水波。
     */
    private static void gateFx(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 normal,
                               double radius, float alpha, int tier, double time, float p,
                               boolean primary) {
        Vec3 right = WaterSpellRules.right(normal);
        Vec3 up = right.cross(normal).normalize();

        // 门内水镜：压住背后的草地/石头，门才有“后面”的感觉。
        WaterGeometry.disk(out, pose, center, normal, radius * .88,
                D_R, D_G, D_B, alpha * .18F, 40);

        // 外环 + 一圈更细的青蓝光晕；主门再加一条极淡的外晕。
        WaterGeometry.ringColor(out, pose, center, normal, radius, .022,
                W_R, W_G, W_B, alpha * .95F, 40);
        WaterGeometry.ringColor(out, pose, center, normal, radius * 1.08, .009,
                C_R, C_G, C_B, alpha * .38F, 32);
        if (primary) {
            WaterGeometry.ringColor(out, pose, center, normal, radius * 1.18, .006,
                    C_R, C_G, C_B, alpha * .16F, 28);
        }

        // 八片水叶：从内圈斜着张开到外圈，让“门”有开合动作，而不是一张静止的环。
        double spin = time * .075D;
        for (int i = 0; i < 8; i++) {
            double a = spin + i * Math.PI * .25D;
            Vec3 innerL = center.add(WaterGeometry.radial(right, up, a - .045D, radius * .22D));
            Vec3 outerL = center.add(WaterGeometry.radial(right, up, a - .15D, radius * .90D));
            Vec3 outerR = center.add(WaterGeometry.radial(right, up, a + .15D, radius * .90D));
            Vec3 innerR = center.add(WaterGeometry.radial(right, up, a + .045D, radius * .22D));
            WaterGeometry.quad(out, pose, innerL, outerL, outerR, innerR,
                    C_R, C_G, C_B, alpha * .20F);
        }

        // 四段留白的潮弧：像门框上的浪，不画成交叉旋臂。
        for (int i = 0; i < 4; i++) {
            double start = spin + i * Math.PI * .5D + .20D;
            gateArc(out, pose, center, normal, radius * .76D, .018D,
                    start, Math.PI * .5D - .40D, 9,
                    C_R, C_G, C_B, alpha * .82F);
        }

        // 四枚短金刻度，只放在斜角，和内环错开。
        for (int i = 0; i < 4; i++) {
            double a = -spin * .5D + Math.PI * .25D + i * Math.PI * .5D;
            Vec3 root = center.add(WaterGeometry.radial(right, up, a, radius * .84D));
            Vec3 tip = center.add(WaterGeometry.radial(right, up, a, radius * .95D));
            WaterGeometry.stroke(out, pose, normal, root, tip, .010D,
                    G_R, G_G, G_B, alpha * .72F);
        }

        // 中心：一枚内环、一颗水体、再加一颗随读条变亮的门心。
        WaterGeometry.ringColor(out, pose, center, normal, radius * .22,
                .010, W_R, W_G, W_B, alpha * .68F, 20);
        WaterGeometry.droplet(out, pose, center, radius * .17, time * .9D,
                C_R, C_G, C_B, alpha * .92F);
        WaterGeometry.disk(out, pose, center, normal, radius * (.075 + .025 * chargeGlow(p)),
                W_R, W_G, W_B, alpha * .34F, 12);

        // 三颗水珠绕门慢转，提示“门后还有东西”。
        for (int i = 0; i < 3; i++) {
            double a = time * .09D + i * Math.PI * 2D / 3D;
            Vec3 dot = center.add(WaterGeometry.radial(right, up, a, radius * 1.15));
            WaterGeometry.disk(out, pose, dot, normal, .032,
                    C_R, C_G, C_B, alpha * .78F, 8);
        }

        // 兽出场的瞬间，门心向外爆一圈很短的亮环：读条和“东西出来了”有因果关系。
        if (primary) {
            float door = 1 - smooth((p - .13F) / .12F);
            if (door > .01F) {
                WaterGeometry.ringColor(out, pose, center, normal,
                        radius * (1.02D + .22D * (1 - door)), .016,
                        W_R, W_G, W_B, alpha * door * .55F, 28);
            }
        }

        // 主门落到地面：两条细水线 + 一小圈地面水波。门不再悬在空中。
        if (primary && center.y > .16D) {
            Vec3 ground = new Vec3(center.x, .04D, center.z);
            WaterGeometry.ringColor(out, pose, ground, UP, radius * .60D, .014,
                    C_R, C_G, C_B, alpha * .34F, 28);
            for (int i = -1; i <= 1; i++) {
                Vec3 top = center.add(right.scale(i * radius * .42D))
                        .add(0, -radius * .42D, 0);
                Vec3 foot = ground.add(right.scale(i * radius * .42D));
                WaterGeometry.stroke(out, pose, normal, top, foot, .011,
                        C_R, C_G, C_B, alpha * .20F);
            }
        }
    }

    /** 兽飞行时沿路径留下三颗很小的水珠，把“从门到胸口”的轨迹读出来。 */
    private static void ghostTrail(VertexConsumer out, Matrix4f pose, CastInfo info,
                                   CastState state, double time) {
        float p = info.ratio();
        float u = (p - state.beastStart) / (state.beastEnd - state.beastStart);
        if (u <= .02F || u >= .98F) {
            return;
        }
        for (int i = 1; i <= 3; i++) {
            float behind = u - i * .055F;
            if (behind <= 0) {
                continue;
            }
            Vec3 at = beastPoint(state, info, behind);
            WaterGeometry.droplet(out, pose, at, .055D, time * .8D + i,
                    C_R, C_G, C_B, .20F - i * .045F);
        }
    }

    /** 一小段圆弧，用弦上的细条拼；段数少，但曲率足够读成“潮弧”。 */
    private static void gateArc(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 normal,
                                double radius, double halfWidth, double start, double sweep,
                                int segments, float r, float g, float b, float alpha) {
        Vec3 right = WaterSpellRules.right(normal);
        Vec3 up = right.cross(normal).normalize();
        for (int i = 0; i < segments; i++) {
            double a0 = start + sweep * i / segments;
            double a1 = start + sweep * (i + 1) / segments;
            Vec3 p0 = center.add(WaterGeometry.radial(right, up, a0, radius));
            Vec3 p1 = center.add(WaterGeometry.radial(right, up, a1, radius));
            WaterGeometry.stroke(out, pose, normal, p0, p1, halfWidth, r, g, b, alpha);
        }
    }

    private static Vec3 beastPoint(CastState state, CastInfo info, float u) {
        Vec3 start = sideCenter(state).add(sideNormal(state).scale(.22));
        Vec3 end = new Vec3(0, 1.12 + info.tier() * .035, 0);
        Vec3 tangent = new Vec3(-Math.sin(state.sideAngle), 0, Math.cos(state.sideAngle));
        Vec3 ctrl = start.add(end).scale(.5)
                .add(0, .78 + .08 * info.tier(), 0)
                .add(tangent.scale(state.sideStyle == 1 ? .42 : .24));
        double t = Mth.clamp(u, 0, 1);
        double inv = 1 - t;
        return start.scale(inv * inv).add(ctrl.scale(2 * inv * t)).add(end.scale(t * t));
    }

    /** 调用真实 Boss 模型所需的位置/朝向/尺寸/透明度；几何本身由 LeviathanGhostVisual 负责。 */
    private static GhostDraw ghostFor(CastInfo info, CastState state) {
        return ghostFor(info, state, 0F, 1F);
    }

    /**
     * @param back       向路径后方偏移的 u，做一层很淡的残影（0 就是本体）
     * @param alphaScale 残影的透明度倍率
     */
    private static GhostDraw ghostFor(CastInfo info, CastState state, float back, float alphaScale) {
        float p = info.ratio();
        float u = (p - state.beastStart) / (state.beastEnd - state.beastStart) - back;
        if (u <= 0 || u >= 1) {
            return null;
        }
        Vec3 at = beastPoint(state, info, u);
        Vec3 prev = beastPoint(state, info, Math.max(0, u - .035F));
        Vec3 forward = at.subtract(prev);
        if (forward.lengthSqr() < 1e-6) {
            return null;
        }
        forward = forward.normalize();
        float fadeIn = smooth(u / .12F);
        float fadeOut = 1 - smooth((u - .84F) / .16F);
        float alpha = (.68F + .18F * chargeGlow(p)) * fadeIn * fadeOut * alphaScale;
        if (alpha <= .004F) {
            return null;
        }
        double size = (.64 + .36 * smooth(u)) * (1 - .24 * smooth((u - .80F) / .20F));
        return new GhostDraw(at, forward, size, alpha);
    }

    private record GhostDraw(Vec3 at, Vec3 forward, double size, float alpha) {
    }

    /** 水球链：胸前凝聚的水珠＋三向水环。低阶只有一颗小水珠，高阶才长到拳头大。 */
    private static void orb(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                            float form, float glow) {
        int tier = info.tier();
        Vec3 look = info.look().lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : info.look().normalize();
        Vec3 right = WaterSpellRules.right(look);
        Vec3 up = right.cross(look).normalize();
        double base = .14 + tier * .025 + form * .075;
        Vec3 at = look.scale(.75 + .25 * form).add(0, 1.05 + tier * .07, 0);

        // 三向环：一个正对施法者，一个水平，一个竖直，合起来读作“水球”。
        WaterGeometry.ringColor(out, pose, at, look, base, .022, C_R, C_G, C_B, .40F + .35F * glow, 32);
        WaterGeometry.ringColor(out, pose, at, right, base * .88, .018, W_R, W_G, W_B, .30F + .30F * glow, 28);
        WaterGeometry.ringColor(out, pose, at, UP, base * .88, .018, C_R, C_G, C_B, .26F + .30F * glow, 28);
        WaterGeometry.disk(out, pose, at, look, base * .48, W_R, W_G, W_B, .18F + .35F * glow, 20);

        // 被吸进来的小水珠：沿三条旋臂向核心收束。
        int motes = 3 + tier * 2;
        for (int i = 0; i < motes; i++) {
            double a = time * .16 + i * Math.PI * 2 / motes;
            double orbit = base * (1.55 + .55 * ((i % 3) / 2.0)) * (1 - .45 * form);
            Vec3 p = at.add(right.scale(Math.cos(a) * orbit)).add(up.scale(Math.sin(a) * orbit))
                    .add(look.scale(Math.sin(a * 2 + time * .1) * base * .25));
            WaterGeometry.disk(out, pose, p, look, base * .06, W_R, W_G, W_B, .32F + .25F * glow, 8);
        }
    }

    /** 水波链：中高阶在脚边升起一圈潮墙；低阶只有贴地阵，不挡视线。 */
    private static void waveRitual(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                                   float form, float glow) {
        int tier = info.tier();
        if (tier < 3) {
            WaterWaveVisuals.rippleGlow(out, pose, info.radius() * (.55 + .18 * form), time,
                    .18F + .22F * glow);
            return;
        }
        double height = info.radius() * (.045 + .016 * tier) * (.35 + .65 * form);
        WaterWaveVisuals.circularWave(out, pose, info.radius() * (.82 + .10 * form),
                height, time, .12F + .22F * glow);
        if (tier >= 4) {
            WaterWaveVisuals.rippleGlow(out, pose, info.radius(), time, .14F + .18F * glow);
        }
    }

    /** 水缚链：四周竖起细水柱；t3 起加环，t5 才封顶。 */
    private static void bindRitual(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                                   float form, float glow) {
        int tier = info.tier();
        int posts = tier <= 2 ? 4 : tier <= 4 ? 6 : 8;
        double R = info.radius() * (.78 + .06 * form);
        double h = Math.max((tier <= 2 ? .85 : 1.25 + tier * .28) * (.35 + .65 * form), R * .22);
        float bodyA = .18F + .22F * glow;
        double thick = Math.max(.035 + .012 * tier, R * .0045);

        for (int i = 0; i < posts; i++) {
            double a = time * .012 + i * Math.PI * 2 / posts;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 base = radial.scale(R);
            Vec3 top = base.add(0, h, 0);
            Vec3 side = radial.scale(thick);
            Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(thick);
            // 一条竖向水柱：两片交叉面，远近都能看见。
            WaterGeometry.quad(out, pose, base.add(side), base.subtract(side),
                    top.subtract(side), top.add(side), C_R, C_G, C_B, bodyA);
            WaterGeometry.quad(out, pose, base.add(tangent), base.subtract(tangent),
                    top.subtract(tangent), top.add(tangent), D_R, D_G, D_B, bodyA * .75F);
            WaterGeometry.stroke(out, pose, radial, base, top, .012,
                    W_R, W_G, W_B, .32F + .25F * glow);
        }
        int rings = tier <= 2 ? 0 : tier == 3 ? 2 : tier == 4 ? 3 : 4;
        for (int i = 0; i < rings; i++) {
            double y = .10 + h * (i + 1) / (rings + 1);
            float a = .12F + .18F * glow;
            WaterGeometry.ringColor(out, pose, new Vec3(0, y, 0), UP, R, Math.max(.018, R * .0022),
                    i == rings - 1 ? G_R : C_R, i == rings - 1 ? G_G : C_G,
                    i == rings - 1 ? G_B : C_B, a, 56);
        }
        if (tier >= 5) {
            WaterGeometry.ringColor(out, pose, new Vec3(0, h, 0), UP, R * .92, .035,
                    G_R, G_G, G_B, .28F + .22F * glow, 48);
            WaterGeometry.ringColor(out, pose, new Vec3(0, h * .52, 0), UP, R * .62, .012,
                    W_R, W_G, W_B, .16F + .14F * glow, 40);
        }
    }

    /** 雨链：头顶第二张雨阵＋落下的水线；低阶雨线少，高阶才铺满。 */
    private static void rainRitual(VertexConsumer out, Matrix4f pose, CastInfo info, double time,
                                   float form, float glow) {
        int tier = info.tier();
        double R = info.radius();
        double sky = 4.0 + tier * .65;
        double skyR = R * (.66 + .08 * form);
        float a = .16F + .30F * glow;

        WaterGeometry.disk(out, pose, new Vec3(0, .04, 0), UP, R * (.80 + .08 * form),
                D_R, D_G, D_B, a * .35F, 44);
        WaterGeometry.seal(out, out, pose, new Vec3(0, sky, 0), UP, skyR, time, a, Math.min(4, tier));
        WaterGeometry.ringColor(out, pose, new Vec3(0, sky, 0), UP, skyR * 1.03, .022,
                C_R, C_G, C_B, .20F + .22F * glow, 48);

        int drops = 8 + tier * 4;
        for (int i = 0; i < drops; i++) {
            double angle = i * 2.399963 + time * .008;
            double rr = R * (.18 + .74 * (((i * 37) % 100) / 100.0));
            double fall = ((time * 1.15 + i * 1.73) % sky);
            double y = sky - fall;
            Vec3 p = new Vec3(Math.cos(angle) * rr, y, Math.sin(angle) * rr);
            Vec3 side = new Vec3(-Math.sin(angle) * .018, 0, Math.cos(angle) * .018);
            WaterGeometry.quad(out, pose, p.add(side), p.subtract(side),
                    p.subtract(side).add(0, -.42, 0), p.add(side).add(0, -.42, 0),
                    C_R, C_G, C_B, .18F + .18F * glow);
        }
        if (tier >= 5) {
            WaterGeometry.seal(out, out, pose, new Vec3(0, sky + .2, 0), UP, skyR * .72,
                    -time, a * .55F, 2);
        }
    }

    /** 释放后的短促水环，补上“读条消失”到实体接管之间的空档。 */
    private static void burst(VertexConsumer out, Matrix4f pose, int tier, Family family,
                              float strength, double age) {
        double f = Mth.clamp(age / 16.0, 0, 1);
        double R = 3.2 + tier * 1.15;
        float a = (float) ((1 - f) * (.30 + .10 * tier) * strength);
        for (int i = 0; i < 3; i++) {
            double r = R * (.55 + f * (.9 + i * .12));
            WaterGeometry.ringColor(out, pose, new Vec3(0, .08 + i * .015, 0), UP, r,
                    .06 + .02 * (1 - f), W_R, W_G, W_B, a * (1 - i * .22F), 64);
        }
        if (family == Family.WAVE || family == Family.RAIN) {
            WaterGeometry.disk(out, pose, new Vec3(0, .05, 0), UP, R * (.5 + f * .5),
                    C_R, C_G, C_B, a * .35F, 40);
        }
        if (tier >= 4) {
            WaterGeometry.seal(out, out, pose, new Vec3(0, .06, 0), UP, R * (.7 + .3 * f),
                    age, a * .35F, tier);
        }
    }

    private static float formation(float p) {
        float x = Mth.clamp((p - .05F) / .85F, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float chargeGlow(float p) {
        return .25F + .75F * Mth.clamp(p, 0, 1);
    }

    private static float screenFx(int tier) {
        return tier <= 2 ? 0 : tier == 3 ? .32F : tier == 4 ? .62F : 1F;
    }

    private static float darkVignette(int tier, float p) {
        if (tier <= 2) {
            return 0;
        }
        float rise = Mth.clamp(p * 1.25F, 0, 1);
        return (tier == 3 ? .30F : tier == 4 ? .55F : .78F) * rise;
    }

    // ==================================================================
    //  屏幕：蓝色潮线＋四周压暗（不是光法的暖白过曝）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (waterWash <= .004F && deepVignette <= .004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (deepVignette > .004F) {
            int alpha = (int) (deepVignette * 52);
            if (alpha > 1) {
                int top = (int) (height * .34F);
                int bottom = (int) (height * .64F);
                event.getGuiGraphics().fillGradient(0, 0, width, top, (alpha << 24) | 0x03121E, 0);
                event.getGuiGraphics().fillGradient(0, bottom, width, height, 0,
                        (alpha << 24) | 0x03121E);
                int side = (int) (width * .13F);
                for (int s = 0; s < 5; s++) {
                    int x0 = side * s / 5, x1 = side * (s + 1) / 5;
                    int aa = alpha * (5 - s) / 5;
                    if (aa <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, (aa << 24) | 0x041826);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height,
                            (aa << 24) | 0x041826);
                }
            }
        }
        if (waterWash > .004F) {
            int base = (int) (waterWash * 14);
            if (base > 1) {
                event.getGuiGraphics().fill(0, 0, width, height, (base << 24) | 0x1C6E9A);
            }
            int line = (int) (waterWash * 72);
            if (line > 1) {
                int y = (int) (height * .72F);
                event.getGuiGraphics().fillGradient(0, y, width, height, 0,
                        (line << 24) | 0x2FA6C8);
                event.getGuiGraphics().fillGradient(0, 0, width, (int) (height * .18F),
                        (line / 2 << 24) | 0x0A4160, 0);
            }
        }
    }

    // ==================================================================
    //  读条状态
    // ==================================================================

    private record CastInfo(int tier, float ratio, double radius, Family family, Vec3 look) {
    }

    private static final class CastState {
        int tier;
        Family family = Family.ORB;
        float ratio;
        float maxRatio;
        double x, y, z;
        long lastSeen;
        long nextBeat;
        float lastSplash;

        /** Only player casts get the side gateway + mini leviathan; boss casts keep their own ritual. */
        final boolean playerCast;
        final double sideAngle;
        final double sideDistance;
        final double sideHeight;
        final int sideStyle;
        /** 第二张更小的回声门：只负责“周围不止一扇门”的层次，不额外放兽。 */
        final double echoAngle;
        final double echoDistance;
        final double echoHeight;
        final int echoStyle;
        final float beastStart;
        final float beastEnd;
        boolean beastImpacted;

        CastState(int tier, boolean playerCast) {
            this(tier, playerCast, 0F);
        }

        CastState(int tier, boolean playerCast, float lookYaw) {
            this.tier = tier;
            this.playerCast = playerCast;
            if (playerCast) {
                // 门必须落在玩家正前方 ±60° 的扇区里：第一人称下，随机到背后的门和兽
                // 玩家根本看不见。角度仍是随机的，只是不再随机到视野外。
                double front = Math.toRadians(lookYaw) + Math.PI * .5D;
                this.sideAngle = front + (RANDOM.nextDouble() * 2D - 1D) * 1.05D;
                this.sideDistance = 2.05D + RANDOM.nextDouble() * .85D;
                this.sideHeight = .95D + RANDOM.nextDouble() * .65D;
                // 立门/斜门优先：平贴地面的门在草里只露一条边，第一眼看不见。
                this.sideStyle = RANDOM.nextBoolean() ? 1 : 2;

                boolean left = RANDOM.nextBoolean();
                this.echoAngle = this.sideAngle + (left ? -1D : 1D) * (1.85D + RANDOM.nextDouble() * .55D);
                this.echoDistance = 2.25D + RANDOM.nextDouble() * 1.15D;
                this.echoHeight = .78D + RANDOM.nextDouble() * .70D;
                this.echoStyle = RANDOM.nextInt(3);

                this.beastStart = .13F + RANDOM.nextFloat() * .05F;
                this.beastEnd = this.beastStart + .55F + RANDOM.nextFloat() * .12F;
            } else {
                this.sideAngle = 0;
                this.sideDistance = 0;
                this.sideHeight = 0;
                this.sideStyle = 0;
                this.echoAngle = 0;
                this.echoDistance = 0;
                this.echoHeight = 0;
                this.echoStyle = 0;
                this.beastStart = 2;
                this.beastEnd = 2;
            }
        }
    }

    private record Burst(double x, double y, double z, int tier, Family family,
                         float strength, long start) {
    }

    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/waterfx")
                        .info("TN-C/waterfx: spell_engine absent -> water cast FX idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/waterfx")
                        .warn("TN-C/waterfx: cast scan failed, water cast FX disabled ({})", t.toString());
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
        Family family = switch (spell.group) {
            case "water_wave" -> Family.WAVE;
            case "water_bind" -> Family.BIND;
            case "water_rain" -> Family.RAIN;
            default -> Family.ORB;
        };
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0, 1);
        double radius = 3.4 + tier * 1.15;
        if (family == Family.RAIN) {
            radius += .35;
        } else if (family == Family.WAVE) {
            radius += .25;
        }
        return new CastInfo(tier, ratio, Math.max(4, Math.min(10.5, radius)), family,
                player.getLookAngle());
    }

    private static CastInfo scanBoss(TNWaterLeviathanEntity boss) {
        int total = boss.castTotalTicks();
        if (total <= 0) {
            return null;
        }
        int remaining = Mth.clamp(boss.castRemainingTicks(), 0, total);
        float ratio = Mth.clamp(1F - remaining / (float) total, .01F, 1F);
        int tier = Mth.clamp(boss.castVisualTier(), 1, 5);
        Family family = boss.castVisualFamily() == 2 ? Family.BIND : Family.ORB;
        double radius = Math.max(10.5, 8.5 + tier * 1.9);
        Vec3 look = boss.getLookAngle();
        if (look.lengthSqr() < 1e-5) {
            look = Vec3.directionFromRotation(boss.getXRot(), boss.getYRot());
        }
        return new CastInfo(tier, ratio, radius, family, look);
    }

    private static Vec3 interpolated(Player player, float partial) {
        return new Vec3(
                Mth.lerp(partial, player.xOld, player.getX()),
                Mth.lerp(partial, player.yOld, player.getY()),
                Mth.lerp(partial, player.zOld, player.getZ()));
    }
}
