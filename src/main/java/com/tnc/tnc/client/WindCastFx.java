package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>风系读条前戏（2026-11-07）</b>——作者："来改改风，改改法阵，加上前戏"。
 *
 * <p>风系此前是五系里唯一<b>没有</b>读条表现的 ✗：水系有潮汐阵 + 水渊兽残影、雷系有雷云、
 * 暗系有黑手、光系有光环，风系放技能时只有一圈白线，读条时间和瞬发分不出来 ✗。
 *
 * <h2>这一版前戏分五层（全部是客户端顶点几何，不新增实体）</h2>
 * <ol>
 *   <li><b>脚下法阵</b>：{@link WindEarthGeometry#windSeal} 那张三旋臂阵，跟着读条从 45% 长到
 *       100% 半径 —— 和释放后在场上留下的阵是<b>同一张阵</b> ✓（不是两套风格 ✗）；</li>
 *   <li><b>上升气流</b>：3 条从阵沿升到腰/胸高的螺旋带，越高越淡；</li>
 *   <li><b>风羽汇聚</b>：8~18 片小羽毛沿螺旋线向胸口收拢、边飞边转（风系的记号）；</li>
 *   <li><b>风灵入体</b>：读条后段，1~3 只<b>真正的风灵模型</b>（{@link WindSpiritGhost}）从侧后方
 *       螺旋飞进玩家胸口，入体瞬间炸开一圈风环（{@link #DIVES}）✓；</li>
 *   <li><b>屏幕风压</b>：薄荷白薄雾 + 上下风痕；t4/t5 每次心跳轻微推镜（复用
 *       {@code TNSpellClientVisuals.pulseShake}）。</li>
 * </ol>
 *
 * <h2>两条链的风格差异</h2>
 * {@code wind_ball}（风球链）在阵沿上多两三颗绕转的小风球；{@code wind_weapon}（风刃链）
 * 改成 3~5 道从阵外扫进来的<b>刃光</b>。同属风系，读条气质一致 ✓，但一眼能看出这次放的是哪条链 ✓。
 *
 * <h2>边界</h2>
 * 只读 {@code SpellCast.Process} 的进度与 spell 的 group/tier，<b>不改任何数值</b>；
 * spell_engine 缺失、扫描抛异常、模型烘焙失败都只是让这一层降级，不会抛到渲染线程 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WindCastFx {

    private static final Set<String> WIND_GROUPS = Set.of("wind_ball", "wind_weapon");
    private static final Vec3 UP = new Vec3(0D, 1D, 0D);
    private static final DustParticleOptions MINT_DUST =
            new DustParticleOptions(new Vector3f(.58F, .91F, .89F), 1.15F);
    private static final DustParticleOptions PALE_DUST =
            new DustParticleOptions(new Vector3f(.86F, .98F, .96F), .85F);

    /** 风系配色（0..1），和 {@code WindEarthGeometry.windSeal} 的常量一一对应。 */
    private static final float PEARL_R = .910F, PEARL_G = .980F, PEARL_B = .965F;
    private static final float MINT_R = .576F, MINT_G = .910F, MINT_B = .886F;
    private static final float PALE_R = .776F, PALE_G = .941F, PALE_B = .933F;

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Dive> DIVES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final MultiBufferSource.BufferSource BODY =
            MultiBufferSource.immediate(new BufferBuilder(1 << 19));
    private static final MultiBufferSource.BufferSource GLOW =
            MultiBufferSource.immediate(new BufferBuilder(1 << 19));
    private static final MultiBufferSource.BufferSource MODEL =
            MultiBufferSource.immediate(new BufferBuilder(1 << 18));

    private static ClientLevel owner;
    private static float wash;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;

    private WindCastFx() {
    }

    // ==================================================================
    //  每 tick：扫读条、发粒子、攒屏幕风压
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
            DIVES.clear();
            owner = level;
            wash = 0F;
        }
        if (level == null || mc.player == null) {
            wash = 0F;
            return;
        }
        long now = level.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;

        Set<UUID> seen = new HashSet<>();
        float target = 0F;

        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            seen.add(player.getUUID());
            Vec3 pos = player.position();
            CastState state = CASTS.computeIfAbsent(player.getUUID(),
                    id -> new CastState(info.tier(), info.family()));
            state.tier = info.tier();
            state.family = info.family();
            state.ratio = info.ratio();
            state.maxRatio = Math.max(state.maxRatio, info.ratio());
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.lastSeen = now;

            double distance = player == mc.player ? 0D : player.distanceTo(mc.player);
            double falloff = Math.max(0D, 1D - distance / 32D);
            if (distance < 64D) {
                tickParticles(level, pos, info, state, minimal);
            }
            float intensity = screenFx(info.tier()) * chargeGlow(info.ratio());
            if (player == mc.player) {
                target = Math.max(target, intensity);
                tickHeartbeat(info, state, now);
            } else if (falloff > 0D) {
                target = Math.max(target, intensity * (float) falloff * .45F);
            }
        }

        // 读条结束（process 消失）＝ 风灵入体的那一瞬 ⇒ 放一圈风环。
        Iterator<Map.Entry<UUID, CastState>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            CastState state = entry.getValue();
            if (now - state.lastSeen <= 3L && state.maxRatio > .35F) {
                DIVES.add(new Dive(state.x, state.y, state.z, state.tier,
                        Mth.clamp(state.maxRatio, .35F, 1F), now));
            }
            it.remove();
        }
        CASTS.entrySet().removeIf(e -> now - e.getValue().lastSeen > 40L);
        DIVES.removeIf(d -> now - d.start > 22L);

        wash += (target - wash) * (target > wash ? .35F : .12F);
        if (wash < .004F) {
            wash = 0F;
        }
    }

    /** t4/t5 的风压心跳：越接近释放越密；低阶没有。 */
    private static void tickHeartbeat(CastInfo info, CastState state, long now) {
        if (info.tier() < 4) {
            return;
        }
        int every = info.ratio() < .5F ? 16 : info.ratio() < .82F ? 10 : 6;
        if (now < state.nextBeat) {
            return;
        }
        state.nextBeat = now + every;
        TNSpellClientVisuals.pulseShake(.05F + (info.tier() >= 5 ? .09F : .05F) * info.ratio());
    }

    /** 阵沿上的薄荷色风尘 + 上升的白色气团；风刃链在释放前还会甩出白色弧光。 */
    private static void tickParticles(ClientLevel level, Vec3 pos, CastInfo info,
                                      CastState state, boolean minimal) {
        long now = level.getGameTime();
        double radius = 2.4D + info.tier() * .48D;
        float charge = chargeGlow(info.ratio());
        int perTick = minimal ? 1 : 1 + info.tier();
        for (int i = 0; i < perTick; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            double r = radius * (.55D + RANDOM.nextDouble() * .5D);
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            double y = pos.y + .06D + RANDOM.nextDouble() * .10D;
            level.addParticle(RANDOM.nextBoolean() ? MINT_DUST : PALE_DUST,
                    x, y, z,
                    -Math.sin(angle) * .06D * (1D + charge),
                    .045D + RANDOM.nextDouble() * .09D * (1D + charge * 2D),
                    Math.cos(angle) * .06D * (1D + charge));
        }
        if (!minimal && info.ratio() > .35F && now % 3L == 0L) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            level.addParticle(ParticleTypes.CLOUD,
                    pos.x + Math.cos(angle) * radius * .8D, pos.y + .18D,
                    pos.z + Math.sin(angle) * radius * .8D,
                    -Math.sin(angle) * .10D, .06D, Math.cos(angle) * .10D);
        }
        if (!minimal && state.family == Family.WEAPON && info.tier() >= 4
                && info.ratio() > .6F && now % 6L == 0L) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            level.addParticle(ParticleTypes.SWEEP_ATTACK,
                    pos.x + Math.cos(angle) * radius * .9D, pos.y + 1.0D,
                    pos.z + Math.sin(angle) * radius * .9D, 0D, 0D, 0D);
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
        double time = now + partial;

        VertexConsumer body = BODY.getBuffer(WindEarthRenderTypes.geometry());
        VertexConsumer glow = GLOW.getBuffer(WindEarthRenderTypes.glow());
        boolean drewBody = false;
        List<Spirit> spirits = new ArrayList<>();

        for (Player player : level.players()) {
            CastInfo info = scan(player);
            if (info == null) {
                continue;
            }
            Vec3 base = interpolated(player, partial);
            if (base.distanceToSqr(camera) > 96D * 96D) {
                continue;
            }
            Matrix4f pose = new Matrix4f().translation(
                    (float) (base.x - camera.x),
                    (float) (base.y - camera.y),
                    (float) (base.z - camera.z));
            ritual(body, glow, pose, info, time);
            drewBody = true;
            collectSpirits(spirits, base, info, time);
        }
        if (drewBody) {
            BODY.endBatch(WindEarthRenderTypes.geometry());
            GLOW.endBatch(WindEarthRenderTypes.glow());
        }

        // 释放瞬间的风环：单独一段批次（上一段已经 endBatch，必须重新 getBuffer）。
        if (!DIVES.isEmpty()) {
            boolean drewBurst = false;
            body = BODY.getBuffer(WindEarthRenderTypes.geometry());
            glow = GLOW.getBuffer(WindEarthRenderTypes.glow());
            for (Dive dive : DIVES) {
                double age = now - dive.start + partial;
                if (age < 0D || age > 22D) {
                    continue;
                }
                Vec3 at = new Vec3(dive.x, dive.y, dive.z);
                if (at.distanceToSqr(camera) > 96D * 96D) {
                    continue;
                }
                Matrix4f pose = new Matrix4f().translation(
                        (float) (at.x - camera.x),
                        (float) (at.y - camera.y),
                        (float) (at.z - camera.z));
                diveBurst(body, glow, pose, dive, age);
                drewBurst = true;
            }
            if (drewBurst) {
                BODY.endBatch(WindEarthRenderTypes.geometry());
                GLOW.endBatch(WindEarthRenderTypes.glow());
            }
        }

        if (!spirits.isEmpty()) {
            boolean model = false;
            VertexConsumer fallback = null;
            PoseStack pose = new PoseStack();
            for (Spirit spirit : spirits) {
                pose.pushPose();
                pose.translate(spirit.at.x - camera.x, spirit.at.y - camera.y, spirit.at.z - camera.z);
                boolean ok = WindSpiritGhost.render(pose, MODEL, level, spirit.at,
                        spirit.forward, spirit.size, spirit.alpha, partial);
                model |= ok;
                if (!ok) {
                    // 模型层被光影/批处理挡掉时至少留一道风痕，不能让前戏整个消失。
                    if (fallback == null) {
                        fallback = BODY.getBuffer(WindEarthRenderTypes.geometry());
                    }
                    spiritWisp(fallback, pose.last().pose(), time, spirit.alpha);
                }
                pose.popPose();
            }
            if (model) {
                MODEL.endBatch(WindSpiritGhost.renderType());
            }
            if (fallback != null) {
                BODY.endBatch(WindEarthRenderTypes.geometry());
            }
        }
    }

    /** 模型不可用时的兜底风灵：一小圈旋转羽毛 + 亮点，短而清楚，不冒充原模型。 */
    private static void spiritWisp(VertexConsumer out, Matrix4f pose, double time, float alpha) {
        for (int i = 0; i < 6; i++) {
            double a = time * .22 + i * Math.PI / 3D;
            double x0 = Math.cos(a) * .34D, z0 = Math.sin(a) * .34D;
            double x1 = Math.cos(a + .55D) * .12D, z1 = Math.sin(a + .55D) * .12D;
            WaterGeometry.stroke(out, pose, UP,
                    new Vec3(x0, .52D, z0), new Vec3(x1, .58D, z1), .045D,
                    PALE_R, PALE_G, PALE_B, alpha * .85F);
        }
        WaterGeometry.ringColor(out, pose, new Vec3(0D, .52D, 0D), UP, .20D, .03D,
                MINT_R, MINT_G, MINT_B, alpha * .9F, 20);
    }

    /** 主仪式：法阵 + 上升气流 + 风羽 + 链风格特征 + 胸口聚风。 */
    private static void ritual(VertexConsumer body, VertexConsumer glow, Matrix4f pose,
                               CastInfo info, double time) {
        int tier = info.tier();
        float charge = chargeGlow(info.ratio());
        float alpha = .35F + .65F * charge;
        double radius = (2.4D + tier * .48D) * (.45D + .55D * charge);

        // 1) 脚下法阵（和释放后的实体阵同一份几何）
        WindEarthGeometry.windSeal(body, glow, pose, radius, time, alpha, tier, WindEarthGeometry.FLAT);

        // 2) 三条上升气流：从阵沿 0.62R 起，越往上越细越淡
        double height = 1.15D + tier * .30D;
        for (int i = 0; i < 3; i++) {
            WaterGeometry.spiral(body, pose, new Vec3(0D, .05D, 0D), UP,
                    radius * .62D, height, time * .020D + i * 2.094D,
                    MINT_R * .92F, MINT_G, MINT_B, alpha * .26F);
        }

        // 3) 风羽：沿螺旋线往胸口收
        int feathers = 8 + tier * 2;
        for (int i = 0; i < feathers; i++) {
            double seed = i * 2.399963D;
            double p = (time * .014D + i * .137D) % 1D;
            double inward = 1D - p;
            double r = radius * (1.55D * inward + .30D);
            double angle = seed + p * 3.4D + time * .035D;
            double y = .30D + p * (1.05D + tier * .06D);
            double x = Math.cos(angle) * r, z = Math.sin(angle) * r;
            double len = .34D + .10D * Math.sin(seed);
            double tx = -Math.sin(angle) * len, tz = Math.cos(angle) * len;
            float fade = (float) (Math.min(1D, p * 4D) * Math.min(1D, (1D - p) * 5D));
            WaterGeometry.stroke(body, pose, UP,
                    new Vec3(x - tx * .5D, y, z - tz * .5D),
                    new Vec3(x + tx * .5D, y + .02D, z + tz * .5D), .035D,
                    PALE_R, PALE_G, PALE_B, alpha * .55F * fade);
            WaterGeometry.stroke(glow, pose, UP,
                    new Vec3(x - tx * .5D, y, z - tz * .5D),
                    new Vec3(x + tx * .5D, y + .02D, z + tz * .5D), .014D,
                    PEARL_R, PEARL_G, PEARL_B, alpha * .40F * fade);
        }

        // 4) 两条链的区别：风球链绕小球，风刃链扫刃光
        if (info.family() == Family.BALL) {
            for (int i = 0; i < 2 + (tier >= 4 ? 1 : 0); i++) {
                double angle = time * .10D + i * 2.094D;
                double r = radius * (.92D + .06D * Math.sin(time * .09D + i));
                double x = Math.cos(angle) * r, z = Math.sin(angle) * r;
                double y = .55D + .10D * Math.sin(time * .13D + i);
                WaterGeometry.ringColor(glow, pose, new Vec3(x, y, z), UP, .17D, .05D,
                        PEARL_R, PEARL_G, PEARL_B, alpha * .8F, 18);
                WaterGeometry.ringColor(body, pose, new Vec3(x, y, z), UP, .26D, .03D,
                        MINT_R, MINT_G, MINT_B, alpha * .45F, 18);
            }
        } else {
            for (int i = 0; i < 3 + tier; i++) {
                double phase = time * .045D + i * (Math.PI * 2D / (3D + tier));
                double sweep = (time * .028D) % 1D;
                double outer = radius * (1.25D - .35D * sweep);
                double inner = radius * .34D;
                double x0 = Math.cos(phase) * outer, z0 = Math.sin(phase) * outer;
                double x1 = Math.cos(phase + .55D) * inner, z1 = Math.sin(phase + .55D) * inner;
                WaterGeometry.stroke(glow, pose, UP,
                        new Vec3(x0, .34D, z0), new Vec3(x1, .40D, z1), .030D,
                        PEARL_R, PEARL_G, PEARL_B, alpha * .55F);
                WaterGeometry.stroke(body, pose, UP,
                        new Vec3(x0, .32D, z0), new Vec3(x1, .38D, z1), .055D,
                        MINT_R, MINT_G, MINT_B, alpha * .28F);
            }
        }

        // 5) 胸口聚风：越接近释放越亮越小（风被“吸”进去了）
        double chest = 1.12D;
        double core = .34D - .12D * charge;
        WaterGeometry.ringColor(glow, pose, new Vec3(0D, chest, 0D), UP, core, .045D,
                PEARL_R, PEARL_G, PEARL_B, alpha * (.35F + .5F * charge), 24);
        WaterGeometry.ringColor(body, pose, new Vec3(0D, chest, 0D), UP, core * 1.7D, .025D,
                MINT_R, MINT_G, MINT_B, alpha * .35F, 24);
        if (tier >= 4) {
            double halo = Math.max(0D, Math.sin(time * .3D));
            WaterGeometry.ringColor(glow, pose, new Vec3(0D, chest, 0D), UP, core * 2.4D, .012D,
                    PALE_R, PALE_G, PALE_B, alpha * .30F * (float) halo, 24);
        }
    }

    /** 读条后段飞向胸口的 1~3 只风灵。 */
    private static void collectSpirits(List<Spirit> out, Vec3 base, CastInfo info, double time) {
        int count = info.tier() >= 5 ? 3 : info.tier() >= 4 ? 2 : 1;
        double start = info.tier() >= 3 ? .20D : .30D;
        float ratio = info.ratio();
        if (ratio < start) {
            return;
        }
        double p = Mth.clamp((float) ((ratio - start) / (1D - start)), 0F, 1F);
        double eased = p * p * (3D - 2D * p);
        double spin = 2.8D * eased + 1.2D;
        float fade = (float) (p > .90D ? Math.max(0D, (1D - p) / .10D) : 1D);
        float alpha = (info.tier() >= 4 ? .90F : .72F) * fade;
        if (alpha <= .01F) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = time * .004D + i * (Math.PI * 2D / count) + .7D + eased * spin;
            double radius = 2.9D * (1D - eased) + .55D;
            double y = .55D + eased * 1.05D + (count > 1 ? .06D * Math.sin(angle * 2D) : 0D);
            Vec3 at = new Vec3(base.x + Math.cos(angle) * radius,
                    base.y + y,
                    base.z + Math.sin(angle) * radius);
            Vec3 forward = new Vec3(-Math.sin(angle) - Math.cos(angle) * .6D, 0D,
                    Math.cos(angle) - Math.sin(angle) * .6D);
            float size = (float) ((count > 2 ? .72D : .86D) * (1D - .25D * eased));
            out.add(new Spirit(at, forward, size, alpha));
        }
    }

    /** 风灵入体 / 释放：地上的爆环 + 胸口的风柱。 */
    private static void diveBurst(VertexConsumer body, VertexConsumer glow, Matrix4f pose,
                                  Dive dive, double age) {
        double t = Math.min(1D, age / 16D);
        float fade = (float) Math.max(0D, 1D - t);
        double radius = .45D + 3.3D * t;
        WaterGeometry.ringColor(glow, pose, new Vec3(0D, .07D, 0D), UP, radius, .10D * fade + .02D,
                PEARL_R, PEARL_G, PEARL_B, fade * .9F, 40);
        WaterGeometry.ringColor(body, pose, new Vec3(0D, .05D, 0D), UP, radius * .82D, .16D * fade + .03D,
                MINT_R, MINT_G, MINT_B, fade * .5F, 40);
        if (dive.tier >= 4) {
            WaterGeometry.ringColor(glow, pose, new Vec3(0D, .09D, 0D), UP, radius * 1.28D, .05D,
                    PALE_R, PALE_G, PALE_B, fade * .5F, 40);
        }
        // 胸口那一下：一道上升的细风柱，越强越高，16 tick 散掉。
        double height = 1.2D + dive.strength * 1.6D;
        for (int i = 0; i < 4; i++) {
            double y0 = .25D + height * (i / 4D) * (1D - t * .5D);
            double y1 = y0 + height * .28D;
            double spin = age * .12D + i * 1.2D;
            double r = (.20D + .04D * i) * (1D + t * .8D);
            double x0 = Math.cos(spin) * r, z0 = Math.sin(spin) * r;
            double x1 = Math.cos(spin + 1.1D) * r, z1 = Math.sin(spin + 1.1D) * r;
            WaterGeometry.stroke(glow, pose, UP, new Vec3(x0, y0, z0), new Vec3(x1, y1, z1),
                    .028D, PEARL_R, PEARL_G, PEARL_B, fade * .6F);
        }
    }

    // ==================================================================
    //  屏幕风压
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (wash <= .004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int base = (int) (wash * 12F);
        if (base > 1) {
            event.getGuiGraphics().fill(0, 0, width, height, (base << 24) | 0xBFF2EA);
        }
        int line = (int) (wash * 62F);
        if (line > 1) {
            event.getGuiGraphics().fillGradient(0, (int) (height * .78F), width, height, 0,
                    (line << 24) | 0x8FE3D8);
            event.getGuiGraphics().fillGradient(0, 0, width, (int) (height * .15F),
                    (line / 2 << 24) | 0x4FBFB4, 0);
        }
    }

    // ==================================================================
    //  读条扫描
    // ==================================================================

    private static CastInfo scan(Player player) {
        if (player == null) {
            return null;
        }
        if (!ModList.get().isLoaded("spell_engine")) {
            if (!engineLogged) {
                engineLogged = true;
                org.apache.logging.log4j.LogManager.getLogger("TN-C/windfx")
                        .info("TN-C/windfx: spell_engine absent -> wind cast FX idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/windfx")
                        .warn("TN-C/windfx: cast scan failed, wind cast FX disabled ({})", t.toString());
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
        if (spell.group == null || !WIND_GROUPS.contains(spell.group)) {
            return null;
        }
        int tier = Mth.clamp(spell.learn == null ? 1 : spell.learn.tier, 1, 5);
        Family family = "wind_weapon".equals(spell.group) ? Family.WEAPON : Family.BALL;
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0F, 1F);
        return new CastInfo(tier, ratio, family);
    }

    private static Vec3 interpolated(Player player, float partial) {
        return new Vec3(
                Mth.lerp(partial, player.xo, player.getX()),
                Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));
    }

    /** 读条越久越亮：前 15% 给一点存在感，之后线性推到 1。 */
    private static float chargeGlow(float ratio) {
        float t = Mth.clamp(ratio, 0F, 1F);
        return Math.min(1F, .15F + t * .85F);
    }

    private static float screenFx(int tier) {
        return switch (tier) {
            case 1 -> .18F;
            case 2 -> .26F;
            case 3 -> .36F;
            case 4 -> .48F;
            default -> .60F;
        };
    }

    // ==================================================================
    //  数据
    // ==================================================================

    private enum Family {
        BALL, WEAPON
    }

    private record CastInfo(int tier, float ratio, Family family) {
    }

    private record Spirit(Vec3 at, Vec3 forward, float size, float alpha) {
    }

    private record Dive(double x, double y, double z, int tier, float strength, long start) {
    }

    private static final class CastState {
        int tier;
        Family family;
        float ratio;
        float maxRatio;
        double x, y, z;
        long lastSeen;
        long nextBeat;

        CastState(int tier, Family family) {
            this.tier = tier;
            this.family = family;
        }
    }
}
