package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.TNDarkGiantEntity;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * <b>暗系读条前戏（2026-11-06）</b> —— 作者：「暗法术的前戏你有没有想法，我想的是周围随机出现
 * 不同角度的小法阵，然后跑出幽灵飞进玩家的身体，还有别的吗，你做做」✓
 *
 * <h2>这一版做了什么</h2>
 * <ol>
 *   <li><b>脚下主阵</b>：读条一开始就亮，随进度张开、旋转加速；t3 起外圈多一圈反着转的刻度 ✓；</li>
 *   <li><b>周围的小法阵</b>：按 {@code ratio} 分 3~6 次出现，位置随机、<b>朝向也随机</b>
 *       —— 有的平贴地面、有的立着朝外、有的斜着插进地里（作者要的"不同角度"✓）；</li>
 *   <li><b>幽灵</b>：每个小法阵亮几下之后"开"掉，一只<b>线勾的幽灵</b>从阵心窜出来，
 *       沿一条弧线飞进施法者的胸口 —— 入体那一刻胸口炸一圈黑线环、溅一把墨 ✓；</li>
 *   <li><b>额外加的（"还有别的吗"）</b>：
 *     <ul>
 *       <li>t3+：身边绕着几颗<b>鬼火</b>，越接近释放收得越紧；</li>
 *       <li>t4+：头顶一轮<b>黑日</b>（黑环＋向内扎的尖齿），慢慢转；</li>
 *       <li>地面爬线：主阵边缘不断有<b>黑线往外爬</b>再缩回去，像雾在动 ✓；</li>
 *       <li>t5：读条每到心跳点推一下镜头（{@link TNSpellClientVisuals#pulseShake}）✓；</li>
 *       <li>释放瞬间：黑线环外扩 ＋ 墨雾爆发 ＋ 屏幕暗一下再退 ✓。</li>
 *     </ul></li>
 * </ol>
 *
 * <h2>为什么全在客户端</h2>
 * 和 {@link WaterCastFx} / {@link LightCastFx} 完全同一套思路：只读
 * {@code SpellCast.Process.progress()}，<b>不生成任何服务端实体</b>、不碰伤害 ✓。
 * 读条前戏是"这个人自己看到的仪式"，别的玩家看到的是他自己那份 —— 数量与角度都按他的
 * 视角随机，反而更自然 ✓。释放之后的持久法阵仍然由服务端的 {@code TNMagicCircleEntity} 铺 ✓。
 *
 * <h2>为什么幽灵必须用 {@code DARK} 层</h2>
 * 幽灵是"黑芯＋血边"的线：{@code BOLT}（加色）会把黑芯加没 ✗，所以整段都用
 * {@link LightningRenderTypes#DARK}（普通 alpha 混合）✓ —— 和 §9 的线手、§10 的黑雾手同一层。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class DarkCastFx {

    /** 暗系的四条链 ＋ 暗龙（它们的 {@code spell.group}）✓。 */
    private static final Set<String> DARK_GROUPS = Set.of(
            "dark_hand", "dark_sacrifice", "dark_summon", "dark_fog", "dark_dragon");

    /** 和 §9/§10 同一套黑线层（念一下为什么：加色会让黑芯消失 ✗）。 */
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 22));
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    /** 巨人读条没有对外暴露进度 ⇒ 用本地计时估一个（只影响观感 ✓）。 */
    private static final int BOSS_CAST_TICKS = 70;

    private static ClientLevel owner;
    private static boolean engineLogged;
    private static boolean scanBroken;
    private static boolean scanErrorLogged;

    private static float darkWash;
    private static float blackVignette;

    private DarkCastFx() {
    }

    // ==================================================================
    //  Tick：读条状态 / 生成法阵 / 放幽灵 / 粒子 / 屏幕强度
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
            darkWash = 0;
            blackVignette = 0;
        }
        if (level == null || mc.player == null) {
            darkWash = 0;
            blackVignette = 0;
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
            CastState state = CASTS.computeIfAbsent(player.getUUID(), id -> new CastState(now));
            state.update(level, player, info, now, minimal);

            double distance = player == mc.player ? 0 : player.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 32);
            if (player == mc.player) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()));
                vignette = Math.max(vignette, blackVignette(info.tier(), info.ratio()));
                tickHeartbeat(info, state, now);
            } else if (falloff > 0) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio())
                        * (float) falloff * .45F);
                vignette = Math.max(vignette, blackVignette(info.tier(), info.ratio())
                        * (float) falloff * .3F);
            }
        }

        // Boss（巨兽人领主 / 黑魔法师）读条：它们不走 SpellEngine，只有 isCasting 同步过来 ⇒ 本地计时。
        // ★ 2026-11-07：黑魔法师和巨人**共用同一套法术**（同读 TNDarkGiantSpells ✓），
        //   所以读条表现也必须一样 ✓ —— 这里改成"只要是暗系 boss 就读"，别再写死某一个类 ✗。
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity boss) || !boss.isAlive()) {
                continue;
            }
            final boolean casting;
            final double ringRadius;
            if (boss instanceof TNDarkGiantEntity giant) {
                casting = giant.isCasting();
                ringRadius = 8.5D;
            } else if (boss instanceof com.tnc.tnc.boss.TNDarkMageEntity mage) {
                casting = mage.isCasting();
                ringRadius = 5.5D;          // 他只有 3.9 格高，环给 8.5 会大得离谱 ✗
            } else {
                continue;
            }
            if (!casting) {
                continue;
            }
            CastState state = CASTS.computeIfAbsent(boss.getUUID(), id -> new CastState(now));
            float ratio = Mth.clamp((now - state.startTick) / (float) BOSS_CAST_TICKS, .01F, 1F);
            CastInfo info = new CastInfo(5, ratio, ringRadius, boss.getLookAngle());
            seen.add(boss.getUUID());
            state.update(level, boss, info, now, minimal);

            double distance = boss.distanceTo(mc.player);
            double falloff = Math.max(0, 1 - distance / 48);
            if (falloff > 0) {
                wash = Math.max(wash, screenFx(info.tier()) * chargeGlow(info.ratio()) * (float) falloff);
                vignette = Math.max(vignette, blackVignette(info.tier(), info.ratio()) * (float) falloff);
                if (distance < 40) {
                    tickHeartbeat(info, state, now);
                }
            }
        }

        // 读条结束：留下一次"释放"的收束＋墨爆 ✓
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

        darkWash += (wash - darkWash) * (wash > darkWash ? .35F : .12F);
        blackVignette += (vignette - blackVignette) * (vignette > blackVignette ? .30F : .10F);
        if (darkWash < .004F) {
            darkWash = 0;
        }
        if (blackVignette < .004F) {
            blackVignette = 0;
        }
        BURSTS.removeIf(b -> now - b.start > 18L);
    }

    /** t5 的心跳：越接近释放越密；低阶没有 ✓。 */
    private static void tickHeartbeat(CastInfo info, CastState state, long now) {
        if (info.tier() < 5) {
            return;
        }
        int every = info.ratio() < .45F ? 18 : info.ratio() < .8F ? 11 : 7;
        if (now < state.nextBeat) {
            return;
        }
        state.nextBeat = now + every;
        TNSpellClientVisuals.pulseShake(.06F + .08F * info.ratio());
    }

    private static void spawnBurst(ClientLevel level, CastState state, float strength, boolean minimal) {
        BURSTS.add(new Burst(state.x, state.y, state.z, state.tier, strength, level.getGameTime()));
        if (state.tier >= 3) {
            TNSpellClientVisuals.pulseShake((.14F + .05F * state.tier) * strength);
        }
        int puffs = (int) ((22 + state.tier * 12) * strength);
        if (minimal) {
            puffs /= 2;
        }
        for (int i = 0; i < puffs; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = .08 + RANDOM.nextDouble() * .34;
            level.addParticle(ParticleTypes.SQUID_INK,
                    state.x, state.y + .15 + RANDOM.nextDouble() * 1.1, state.z,
                    Math.cos(a) * speed, .12 + RANDOM.nextDouble() * .30, Math.sin(a) * speed);
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.SMOKE, state.x, state.y + .2, state.z,
                        Math.cos(a) * .06, .16, Math.sin(a) * .06);
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
        if (level == null || level != owner || CASTS.isEmpty() && BURSTS.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        double time = now + partial;

        VertexConsumer vc = BUFFERS.getBuffer(LightningRenderTypes.DARK);
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
            if (age < 0 || age > 18) {
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
            BUFFERS.endBatch(LightningRenderTypes.DARK);
        }
    }

    private static void ritual(VertexConsumer vc, CastState state, Vec3 base, Vec3 cam,
                               double time, float partial) {
        int tier = state.tier;
        float p = state.ratio;
        float glow = chargeGlow(p);
        // ★ 2026-11-06：整张阵"淡入"（作者：「雷锲出现的太突兀」）——
        //   原来第 1 tick 就是 0.32 亮度、直接出现 ✗；现在 8 tick 内 0 → 1 ✓
        float born = smooth((float) Mth.clamp((time - state.startTick) / 8.0D, 0.0D, 1.0D));
        // 阵也放大一档（"暗的也不错，但是也可以优化一下"）：1.55 → 1.95 起
        double R = 1.95D + tier * .46D;

        // ① 脚下主阵：从读条第 1 tick 就在，随进度张开、转快 ✓
        seal(vc, base.add(0, .045D, 0), new Vec3(0, 1, 0), R, time * (.10D + .05D * p),
                time, 900L + state.seed, (.32F + .48F * glow) * born, 12 + tier * 3, true, cam);
        // 反着转的外圈（t2+）：读起来像"阵在吸东西"✓
        if (tier >= 2) {
            seal(vc, base.add(0, .055D, 0), new Vec3(0, 1, 0), R * 1.22D, -time * .07D,
                    time, 1900L + state.seed, (.18F + .30F * glow) * born, 8 + tier * 2, false, cam);
        }
        // 地面爬线：边缘不断有黑线往外爬再缩回（"还有别的吗"之一）✓
        int crawlers = 6 + tier * 2;
        for (int i = 0; i < crawlers; i++) {
            double a = i * Math.PI * 2 / crawlers + Math.sin(time * .02D + i) * .18D;
            double out = R * (1.02D + .26D * (.5D + .5D * Math.sin(time * .09D + i * 1.7D)));
            double g = .35D + .65D * (.5D + .5D * Math.sin(time * .11D + i * 2.3D));
            Vec3 from = base.add(Math.cos(a) * R * .62D, .05D, Math.sin(a) * R * .62D);
            Vec3 to = base.add(Math.cos(a) * out, .05D, Math.sin(a) * out);
            matrixLine(vc, from, to, state.seed + 400L + i, .022F, .34F * glow, g, cam);
        }

        // ①b t4+：阵边"伸出手"—— 从地里钻出来、胡乱抓一把再沉回去 ✓（"还有别的吗"之一）
        if (tier >= 4) {
            int hands = tier >= 5 ? 3 : 2;
            for (int i = 0; i < hands; i++) {
                double a = i * Math.PI * 2 / hands + time * .028D + state.seed * .01D;
                double hr = R * (.80D + .10D * i);
                Vec3 at = base.add(Math.cos(a) * hr, 0, Math.sin(a) * hr);
                // 循环相位：慢慢伸出来、抓一下、再沉回去
                float cyc = (float) (((time * .022D + i * .41D + state.seed * .013D) % 1.0D + 1.0D) % 1.0D);
                float grow = smooth(Mth.clamp(cyc * 2.8F, 0, 1))
                        * (1F - smooth(Mth.clamp((cyc - .58F) / .42F, 0, 1)));
                if (grow <= .02F) {
                    continue;
                }
                float clench = .12F + .62F * smooth(Mth.clamp((cyc - .22F) / .55F, 0, 1));
                Vec3 palm = base.subtract(at);
                handAt(vc, at, palm, 1.35F, grow, clench, .62F * grow * born,
                        state.seed + 2300L + i * 37L, cam);
            }
        }

        // ② 周围的小法阵：不同位置、不同角度（作者要的那一条）✓
        for (Circle circle : state.circles) {
            double age = time - circle.born;
            if (age < 0 || age > circle.life) {
                continue;
            }
            float f = (float) Mth.clamp(age / (double) circle.life, 0, 1);
            // 小阵也淡入（9 tick 内涨到满），不是"啪"一下出现 ✓
            float a = smooth((float) Mth.clamp(age / 9.0D, 0.0D, 1.0D))
                    * (1F - smooth(Mth.clamp((f - .82F) / .18F, 0, 1)));
            if (a <= .01F) {
                continue;
            }
            double r = circle.radius * (.78D + .22D * Math.min(1D, age / 7D));
            seal(vc, circle.center, circle.normal, r, time * .16D + circle.seed * .001D,
                    time, circle.seed, a * .85F, 7, false, cam);
            // 阵心先"亮"一下再开：越接近开门越亮 ✓
            float door = Mth.clamp((f - .45F) / .5F, 0, 1);
            if (door > .01F) {
                beadAt(vc, circle.center, .05F + .06F * door, a * .85F * door, cam);
            }
            // 阵散掉的时候：边缘甩出几道短短的黑线（像墨被甩出去），不是无声消失 ✓
            if (f > .80F) {
                float die = (f - .80F) / .20F;
                for (int d = 0; d < 5; d++) {
                    double ang = d * Math.PI * 2 / 5 + time * .4D + circle.seed;
                    Vec3 from = planePoint(circle.center, circle.normal, ang, r);
                    Vec3 to = planePoint(circle.center, circle.normal, ang + .1D,
                            r * (1.25D + .55D * die));
                    matrixLine(vc, from, to, circle.seed + 800L + d, .026F,
                            a * (1F - die) * .7F, 1D, cam);
                }
            }
        }

        // ③ 幽灵：从阵心飞进胸口 ✓
        for (Ghost ghost : state.ghosts) {
            double age = time - ghost.born;
            if (age < 0 || age > ghost.life) {
                continue;
            }
            float f = (float) Mth.clamp(age / (double) ghost.life, 0, 1);
            Vec3 at = bezier(ghost.from, ghost.ctrl, ghost.to, f);
            // ★ 2026-11-06：让幽灵"绕着"飞进来（sin 一圈的侧移），不是沿一条死直线滑过来
            Vec3 axis = ghost.to.subtract(ghost.from);
            if (axis.lengthSqr() > 1e-6) {
                Vec3 side = axis.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-6) {
                    side = axis.cross(new Vec3(1, 0, 0));
                }
                side = side.normalize();
                at = at.add(side.scale(Math.sin(f * Math.PI * 2.2D) * .22D * (1D - f)));
            }
            Vec3 prev = bezier(ghost.from, ghost.ctrl, ghost.to, Math.max(0, f - .16F));
            float a = Math.min(1F, f * 7F) * (1F - smooth(Mth.clamp((f - .80F) / .20F, 0, 1)));
            if (a <= .01F) {
                continue;
            }
            // 拖尾：幽灵身后那一段残影（"飞过去"的动量感）✓
            matrixLine(vc, prev, at, ghost.seed + 3L, .030F, a * .45F, 1D, cam);
            wraith(vc, at, ghost.scale, a, time, ghost.seed, cam);
        }

        // ④ 入体冲击：胸口炸一圈黑线 ✓
        for (Impact impact : state.impacts) {
            double age = time - impact.born;
            if (age < 0 || age > 10) {
                continue;
            }
            float f = (float) Mth.clamp(age / 10D, 0, 1);
            float a = (1F - f) * .85F;
            double r = impact.r * (.35D + 1.05D * f);
            seal(vc, impact.at, new Vec3(0, 0, 1), r, -time * .3D, time, impact.seed, a, 6, false, cam);
            for (int i = 0; i < 5; i++) {
                double ang = i * Math.PI * 2 / 5 + impact.seed * .01D;
                Vec3 to = impact.at.add(Math.cos(ang) * r * 1.15D, Math.sin(ang) * r * 1.15D, .01D);
                matrixLine(vc, impact.at, to, impact.seed + 10L + i, .026F, a * .8F, 1D, cam);
            }
        }

        // ⑤ t3+：绕在身边的鬼火，越到后面收得越紧 ✓
        if (tier >= 3) {
            int wisps = 3 + tier;
            double orbit = (1.75D - .75D * p) * (.9D + .1D * tier / 5D);
            for (int i = 0; i < wisps; i++) {
                double a = time * (.05D + .01D * tier) + i * Math.PI * 2 / wisps;
                Vec3 pnt = base.add(Math.cos(a) * orbit, 1.05D + Math.sin(a * 2 + time * .07D) * .35D,
                        Math.sin(a) * orbit);
                beadAt(vc, pnt, .045F, .40F + .35F * glow, cam);
            }
        }

        // ⑥ t4+：头顶一轮黑日（黑环＋向内扎的尖齿）✓
        if (tier >= 4) {
            double hy = base.y + 2.35D + .12D * Math.sin(time * .06D);
            Vec3 halo = new Vec3(base.x, hy, base.z);
            double hr = .78D + .1D * tier;
            seal(vc, halo, new Vec3(0, 1, 0), hr, time * .22D, time, state.seed + 77L,
                    .30F + .35F * glow, 0, false, cam);
            for (int i = 0; i < 8; i++) {
                double a = -(time * .12D) + i * Math.PI * 2 / 8;
                Vec3 from = halo.add(Math.cos(a) * hr, 0, Math.sin(a) * hr);
                Vec3 to = halo.add(Math.cos(a) * hr * .55D, 0, Math.sin(a) * hr * .55D);
                matrixLine(vc, from, to, state.seed + 200L + i, .020F, .26F + .30F * glow,
                        .55D + .45D * glow, cam);
            }
        }
    }

    private static void burstFx(VertexConsumer vc, Vec3 at, Burst burst, double age, Vec3 cam) {
        double f = Mth.clamp(age / 16D, 0, 1);
        double R = 3.0D + burst.tier * 1.1D;
        float a = (float) ((1 - f) * (.30 + .10 * burst.tier) * burst.strength);
        for (int i = 0; i < 3; i++) {
            seal(vc, at.add(0, .08D + i * .02D, 0), new Vec3(0, 1, 0),
                    R * (.55D + f * (.9D + i * .12D)), age * .2D + i, age, burst.start + i, a * (1 - i * .22F),
                    0, false, cam);
        }
        for (int i = 0; i < 10; i++) {
            double ang = i * Math.PI * 2 / 10 + burst.start * .01D;
            Vec3 to = at.add(Math.cos(ang) * R * (.35D + f), .35D + f * 1.1D, Math.sin(ang) * R * (.35D + f));
            matrixLine(vc, at.add(0, .25D, 0), to, burst.start + 50L + i, .034F, a * .75F, .7D, cam);
        }
    }

    // ==================================================================
    //  几何：法阵 / 幽灵
    // ==================================================================

    /**
     * 在任意朝向的平面里画一张暗系"阵"—— <b>不是雷锲</b>（作者 2026-11-06：「雷锲出现的太突兀」）。
     *
     * <p>雷锲 = 完整圆环 ＋ 一圈等距齿；这里刻意<b>不画完整环</b>：
     * <ul>
     *   <li><b>三道断弧</b>（每道 ~110°，缺口错开）—— 线本身带抖动，读作"谁在石头上划出来的"✓；</li>
     *   <li><b>五道爪痕</b>从环外擦进去、角度不均、长短不一 —— 而不是机械的刻度齿 ✓；</li>
     *   <li>环上三块<b>墨渍</b>（血珠压扁）＋ 阵心一团暗雾 ✓。</li>
     * </ul>
     * 全部走深暗红（{@code darkBoltDeep}）⇒ 黑里透暗红，不发光 ✓。
     *
     * <p>{@code normal} 就是这张阵的"正面朝向"：{@code (0,1,0)} = 平贴地面，
     * 水平向量 = 立着，斜向量 = 斜插 ✓ —— 作者要的"不同角度"全靠这一个参数。
     */
    private static void seal(VertexConsumer vc, Vec3 center, Vec3 normal, double radius, double spin,
                             double time, long seed, float alpha, int ticks, boolean core, Vec3 cam) {
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
        // 相机在这个局部空间里的位置：-Rᵀ·t ✓（和 DarkHandLines 用逆矩阵求的是同一件事）
        double camX = -(u.x * tx + u.y * ty + u.z * tz);
        double camY = -(v.x * tx + v.y * ty + v.z * tz);
        double camZ = -(n.x * tx + n.y * ty + n.z * tz);

        // ① 三道断弧（不是一整圈 ✗）：每道 ~110°，缺口长度也不一样
        int arcs = 3;
        for (int k = 0; k < arcs; k++) {
            double base = k * Math.PI * 2 / arcs + spin + .37D * Math.sin(seed * .017D + k * 2.1D);
            double span = 1.70D + .28D * Math.sin(seed * .011D + k * 1.3D);
            int steps = 4;
            for (int s = 0; s < steps; s++) {
                double a0 = base + span * s / steps;
                double a1 = base + span * (s + 1) / steps;
                double wob0 = 1.0D + .028D * Math.sin(a0 * 3.7D + seed * .03D);
                double wob1 = 1.0D + .028D * Math.sin(a1 * 3.7D + seed * .03D);
                LightningBoltGeometry.darkBoltDeep(vc, m,
                        Math.cos(a0) * radius * wob0, Math.sin(a0) * radius * wob0, 0,
                        Math.cos(a1) * radius * wob1, Math.sin(a1) * radius * wob1, 0,
                        seed + k * 17L + s, .034F, alpha * (0.92F - .04F * s), 1.0D,
                        camX, camY, camZ);
            }
        }

        // ② 五道爪痕：从环外斜着擦进环内，长短/角度都不均 ✓（取代雷锲的刻度齿）
        int claws = Math.max(3, Math.min(7, ticks));
        for (int i = 0; i < claws; i++) {
            double a = i * Math.PI * 2 / claws + seed * .021D + .6D * Math.sin(seed * .005D + i);
            double outer = radius * (1.10D + .10D * Math.sin(seed * .013D + i * 1.9D));
            double inner = radius * (.58D + .18D * Math.sin(seed * .007D + i * 2.7D));
            double bend = a + .20D * Math.sin(seed * .03D + i);
            double midR = radius * .86D;
            LightningBoltGeometry.darkBoltDeep(vc, m,
                    Math.cos(a) * outer, Math.sin(a) * outer, 0,
                    Math.cos(bend) * midR, Math.sin(bend) * midR, .01D,
                    seed + 300L + i, .030F, alpha * .9F, 1.0D, camX, camY, camZ);
            LightningBoltGeometry.darkBoltDeep(vc, m,
                    Math.cos(bend) * midR, Math.sin(bend) * midR, .01D,
                    Math.cos(bend + .12D) * inner, Math.sin(bend + .12D) * inner, 0,
                    seed + 340L + i, .022F, alpha * .8F, 1.0D, camX, camY, camZ);
        }

        // ③ 环上三块墨渍（压扁的血珠）⇒ 这几处"墨没干"✓
        for (int i = 0; i < 3; i++) {
            double a = seed * .013D + i * 2.094D;
            LightningBoltGeometry.darkBeadDeep(vc, m,
                    Math.cos(a) * radius, Math.sin(a) * radius, 0,
                    (float) (radius * .10F), alpha * .75F, camX, camY, camZ);
        }

        // ④ 阵心：一团暗雾 ＋ 四根往里收的短须（幽灵就是从这里出来的）✓
        if (core) {
            LightningBoltGeometry.darkBeadDeep(vc, m, 0, 0, 0, (float) radius * .34F, alpha * .55F,
                    camX, camY, camZ);
            for (int i = 0; i < 4; i++) {
                double a = seed * .019D + i * Math.PI / 2;
                LightningBoltGeometry.darkBoltDeep(vc, m,
                        Math.cos(a) * radius * .55D, Math.sin(a) * radius * .55D, 0,
                        Math.cos(a) * radius * .18D, Math.sin(a) * radius * .18D, 0,
                        seed + 500L + i, .018F, alpha * .6F, 1.0D, camX, camY, camZ);
            }
        }
    }

    /**
     * 在世界里画一只<b>线勾的手</b>（复用 {@link DarkHandLines} 那份骨架 ⇒ 和黑手/黑雾手同一个东西 ✓）。
     *
     * @param palmDir 掌心朝向（会被压成水平方向；＋Z 就是掌心）
     */
    /** 给旅行形态复用：和黑手／黑雾手同一只线勾手，不另画一套。 */
    public static void renderHand(VertexConsumer vc, Vec3 at, Vec3 palmDir,
                                  float scale, float grow, float clench, float alpha,
                                  long seed, Vec3 cam) {
        handAt(vc, at, palmDir, scale, grow, clench, alpha, seed, cam);
    }

    private static void handAt(VertexConsumer vc, Vec3 at, Vec3 palmDir,
                               float scale, float grow, float clench, float alpha, long seed, Vec3 cam) {
        if (alpha <= .01F || grow <= .01F) {
            return;
        }
        Vector3f z = new Vector3f((float) palmDir.x, 0, (float) palmDir.z);
        if (z.lengthSquared() < 1e-6F) {
            z.set(0, 0, 1);
        }
        z.normalize();
        Vector3f y = new Vector3f(0, 1, 0);
        Vector3f x = new Vector3f(y).cross(z).normalize();
        // 列主序：x = 局部 +X、y = 局部 +Y（手指朝上）、z = 局部 +Z（掌心）
        float tx = (float) (at.x - cam.x);
        float ty = (float) (at.y - cam.y);
        float tz = (float) (at.z - cam.z);
        Matrix4f m = new Matrix4f(
                x.x * scale, x.y * scale, x.z * scale, 0,
                y.x * scale, y.y * scale, y.z * scale, 0,
                z.x * scale, z.y * scale, z.z * scale, 0,
                tx, ty, tz, 1);
        // 相机 local = −Rᵀ·t / scale
        double camX = -(x.x * tx + x.y * ty + x.z * tz) / scale;
        double camY = -(y.x * tx + y.y * ty + y.z * tz) / scale;
        double camZ = -(z.x * tx + z.y * ty + z.z * tz) / scale;
        DarkHandLines.hand(vc, m, camX, camY, camZ, grow, alpha, clench, seed);
    }

    /** 平面里的一点（{@code u,v} 张成垂直于 {@code normal} 的平面）✓。 */
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

    /** 世界坐标 → 相机相对，然后画一段黑线（{@code growth} 用来"一节一节"长）✓。 */    private static void matrixLine(VertexConsumer vc, Vec3 from, Vec3 to, long seed,
                                   float width, float alpha, double growth, Vec3 cam) {
        Matrix4f m = new Matrix4f().translation(
                (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z));
        // 矩阵只有平移 ⇒ 相机在这个局部空间里的位置就是 (from - cam) 的相反数 ✓
        double camX = -(from.x - cam.x);
        double camY = -(from.y - cam.y);
        double camZ = -(from.z - cam.z);
        LightningBoltGeometry.darkBolt(vc, m, 0, 0, 0,
                to.x - from.x, to.y - from.y, to.z - from.z,
                seed, width, alpha, growth, camX, camY, camZ);
    }

    private static void beadAt(VertexConsumer vc, Vec3 at, float radius, float alpha, Vec3 cam) {
        // 用单位矩阵 + 相机相对坐标：相机在原点，血珠的朝向由 (0,0,0) → 点位的方向算 ✓
        LightningBoltGeometry.darkBead(vc, new Matrix4f(),
                at.x - cam.x, at.y - cam.y, at.z - cam.z,
                radius, alpha, 0, 0, 0);
    }

    /**
     * 一只<b>线勾的幽灵</b>（局部坐标：+Y 向上、尺寸约 0.9 格高）。
     *
     * <p>不画模型：兜帽 ＋ 肩线 ＋ 三条下垂的袍线 ＋ 两条伸出来的手臂 ＋ 两颗血眼，
     * 全用 {@code darkArc} 勾出来 —— 和 §9 的线手、§10 的黑雾手是同一套语言 ✓。
     */
    /** 给旅行形态复用：就是读条前戏里那只线勾幽灵，不另画一套。 */
    public static void renderWraith(VertexConsumer vc, Vec3 at, float scale, float alpha,
                                    double time, long seed, Vec3 cam) {
        wraith(vc, at, scale, alpha, time, seed, cam);
    }

    private static void wraith(VertexConsumer vc, Vec3 at, float scale, float alpha,
                               double time, long seed, Vec3 cam) {
        Matrix4f m = new Matrix4f().translation(
                (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z))
                .scale(scale);
        double camX = -(at.x - cam.x) / scale;
        double camY = -(at.y - cam.y) / scale;
        double camZ = -(at.z - cam.z) / scale;
        float a = alpha;
        double sway = Math.sin(time * .22D + seed) * .035D;

        // 兜帽：一个压扁的圈 ＋ 两条落下来的边
        arcCircle(vc, m, .30D, .135D, 1.0D, 6, seed + 1L, .030F, a, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, -.215D, .19D, .02D, -.075D, .365D, 0,
                seed + 2L, .028F, a, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, .215D, .19D, .02D, .075D, .365D, 0,
                seed + 3L, .028F, a, camX, camY, camZ);
        // 肩线（略微塌下去，像披着）
        LightningBoltGeometry.darkArc(vc, m, -.235D, .185D, 0, -.115D, .075D, .015D,
                seed + 4L, .030F, a, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, -.115D, .075D, .015D, .115D, .075D, .015D,
                seed + 5L, .030F, a, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, .115D, .075D, .015D, .235D, .185D, 0,
                seed + 6L, .030F, a, camX, camY, camZ);

        // 袍：三条往下垂并轻轻摆动的线，越往下越细 ⇒ 下摆"散开"✓
        for (int i = 0; i < 3; i++) {
            double x0 = (i - 1) * .085D;
            double x1 = (i - 1) * .13D + sway * (i - 1);
            double mid = .075D - .16D - i * .02D;
            LightningBoltGeometry.darkArc(vc, m, x0, .075D, .015D, x1 * .7D, mid, .01D,
                    seed + 20L + i, .026F, a * .95F, camX, camY, camZ);
            LightningBoltGeometry.darkArc(vc, m, x1 * .7D, mid, .01D,
                    x1 + sway, -.34D - i * .03D, -.02D,
                    seed + 30L + i, .022F, a * .85F, camX, camY, camZ);
        }
        // 手臂：伸向前方（+Z），指尖略收 —— "伸手来抓"✓
        LightningBoltGeometry.darkArc(vc, m, -.235D, .185D, 0, -.32D, .045D, .12D,
                seed + 40L, .026F, a * .9F, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, -.32D, .045D, .12D, -.27D, -.10D, .22D,
                seed + 41L, .022F, a * .85F, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, .235D, .185D, 0, .32D, .045D, .12D,
                seed + 42L, .026F, a * .9F, camX, camY, camZ);
        LightningBoltGeometry.darkArc(vc, m, .32D, .045D, .12D, .27D, -.10D, .22D,
                seed + 43L, .022F, a * .85F, camX, camY, camZ);

        // 眼：两颗血珠（全身上下唯一的"红"）✓
        LightningBoltGeometry.darkBead(vc, m, -.055D, .305D, .11D, .024F, a, camX, camY, camZ);
        LightningBoltGeometry.darkBead(vc, m, .055D, .305D, .11D, .024F, a, camX, camY, camZ);
        // 胸口一点暗光（入体时最亮的是这里）✓
        LightningBoltGeometry.darkBead(vc, m, 0, .10D, .02D, .05F, a * .55F, camX, camY, camZ);
    }

    /** 一个正对法线的小圆（幽灵的兜帽用它，省得算平面基）✓。 */
    private static void arcCircle(VertexConsumer vc, Matrix4f m, double cy, double radius, double ch,
                                  int segs, long seed, float width, float alpha,
                                  double camX, double camY, double camZ) {
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs;
            double a1 = (i + 1) * Math.PI * 2 / segs;
            LightningBoltGeometry.darkArc(vc, m,
                    Math.cos(a0) * radius, cy + Math.sin(a0) * radius * ch, 0,
                    Math.cos(a1) * radius, cy + Math.sin(a1) * radius * ch, 0,
                    seed + i, width, alpha, camX, camY, camZ);
        }
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

    private static float formation(float p) {
        float x = Mth.clamp((p - .05F) / .85F, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float chargeGlow(float p) {
        return .25F + .75F * Mth.clamp(p, 0, 1);
    }

    private static float screenFx(int tier) {
        return tier <= 2 ? 0 : tier == 3 ? .30F : tier == 4 ? .60F : 1F;
    }

    private static float blackVignette(int tier, float p) {
        if (tier <= 2) {
            return 0;
        }
        float rise = Mth.clamp(p * 1.25F, 0, 1);
        return (tier == 3 ? .30F : tier == 4 ? .55F : .82F) * rise;
    }

    // ==================================================================
    //  屏幕：紫黑暗角（不是水法的蓝、也不是光法的过曝）
    // ==================================================================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (darkWash <= .004F && blackVignette <= .004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (blackVignette > .004F) {
            int alpha = (int) (blackVignette * 62);
            if (alpha > 1) {
                int top = (int) (height * .32F);
                int bottom = (int) (height * .66F);
                event.getGuiGraphics().fillGradient(0, 0, width, top, (alpha << 24) | 0x06030A, 0);
                event.getGuiGraphics().fillGradient(0, bottom, width, height, 0,
                        (alpha << 24) | 0x06030A);
                int side = (int) (width * .15F);
                for (int s = 0; s < 5; s++) {
                    int x0 = side * s / 5;
                    int x1 = side * (s + 1) / 5;
                    int aa = alpha * (5 - s) / 5;
                    if (aa <= 1) {
                        continue;
                    }
                    event.getGuiGraphics().fill(x0, 0, x1, height, (aa << 24) | 0x0B0512);
                    event.getGuiGraphics().fill(width - x1, 0, width - x0, height,
                            (aa << 24) | 0x0B0512);
                }
            }
        }
        if (darkWash > .004F) {
            int base = (int) (darkWash * 16);
            if (base > 1) {
                event.getGuiGraphics().fill(0, 0, width, height, (base << 24) | 0x2A0E3A);
            }
            int line = (int) (darkWash * 64);
            if (line > 1) {
                int y = (int) (height * .74F);
                event.getGuiGraphics().fillGradient(0, y, width, height, 0,
                        (line << 24) | 0x3A1052);
                event.getGuiGraphics().fillGradient(0, 0, width, (int) (height * .16F),
                        (line / 2 << 24) | 0x160622, 0);
            }
        }
    }

    // ==================================================================
    //  读条状态与几何数据
    // ==================================================================

    private record CastInfo(int tier, float ratio, double radius, Vec3 look) {
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
        boolean released;

        Circle(Vec3 center, Vec3 normal, double radius, long born, long life, long seed) {
            this.center = center;
            this.normal = normal;
            this.radius = radius;
            this.born = born;
            this.life = life;
            this.seed = seed;
        }
    }

    private static final class Ghost {
        final Vec3 from;
        final Vec3 ctrl;
        final Vec3 to;
        final long born;
        final long life;
        final long seed;
        final float scale;
        boolean done;

        Ghost(Vec3 from, Vec3 ctrl, Vec3 to, long born, long life, long seed, float scale) {
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
        final float r;
        final long seed;

        Impact(Vec3 at, long born, float r, long seed) {
            this.at = at;
            this.born = born;
            this.r = r;
            this.seed = seed;
        }
    }

    private static final class CastState {
        int tier = 1;
        float ratio;
        float maxRatio;
        double x, y, z;
        Vec3 look = new Vec3(0, 0, 1);
        long lastSeen;
        long startTick;
        final long seed;
        long nextBeat;
        int spawned;
        final List<Circle> circles = new ArrayList<>();
        final List<Ghost> ghosts = new ArrayList<>();
        final List<Impact> impacts = new ArrayList<>();

        CastState(long now) {
            this.startTick = now;
            this.seed = RANDOM.nextLong() & 0x7FFFFFFFL;
        }

        void update(ClientLevel level, net.minecraft.world.entity.Entity caster, CastInfo info,
                    long now, boolean minimal) {
            this.tier = info.tier();
            this.ratio = info.ratio();
            // 同一个人连着放第二发：状态还留着（40 tick 才回收），必须把"这一发"的仪式清掉 ✗
            if (now - this.lastSeen > 2L || this.ratio - info.ratio() > .35F) {
                this.spawned = 0;
                this.circles.clear();
                this.ghosts.clear();
                this.impacts.clear();
                this.maxRatio = 0;
                this.startTick = now;
            }
            this.maxRatio = Math.max(this.maxRatio, info.ratio());
            this.x = caster.getX();
            this.y = caster.getY();
            this.z = caster.getZ();
            this.look = info.look() == null || info.look().lengthSqr() < 1e-5
                    ? new Vec3(0, 0, 1) : info.look().normalize();
            this.lastSeen = now;

            int wanted = circlesWanted(this.tier);
            for (int i = this.spawned; i < wanted; i++) {
                float at = .08F + i * (.72F / wanted);
                if (this.ratio < at) {
                    break;
                }
                spawnCircle(level, caster, i, minimal);
                this.spawned = i + 1;
            }

            // 法阵亮够了就"开"：放一只幽灵出来 ✓
            //   读条快结束时（ratio > .88）不管亮没亮够都直接开 —— 否则短读条的法阵会"没放出幽灵
            //   就已经施法结束"，那作者要的"跑出幽灵飞进身体"就丢了一半 ✗
            for (Circle circle : this.circles) {
                if (circle.released) {
                    continue;
                }
                if (now >= circle.born + Math.max(6L, circle.life * 6L / 10L) || this.ratio > .88F) {
                    circle.released = true;
                    spawnGhost(level, caster, circle, minimal);
                }
            }
            // 幽灵飞到了：胸口那一下 ✓
            for (Ghost ghost : this.ghosts) {
                if (!ghost.done && now >= ghost.born + ghost.life) {
                    ghost.done = true;
                    this.impacts.add(new Impact(ghost.to, now, .55F + .09F * this.tier,
                            ghost.seed + 17L));
                    if (!minimal) {
                        for (int i = 0; i < 10; i++) {
                            double ang = i * Math.PI * 2 / 10;
                            level.addParticle(ParticleTypes.SQUID_INK,
                                    ghost.to.x + Math.cos(ang) * .16D,
                                    ghost.to.y + Math.sin(ang) * .16D,
                                    ghost.to.z, Math.cos(ang) * .10D,
                                    Math.sin(ang) * .10D, Math.cos(ang) * .04D);
                        }
                    }
                }
            }
            this.circles.removeIf(c -> now > c.born + c.life + 2);
            this.ghosts.removeIf(g -> now > g.born + g.life + 2);
            this.impacts.removeIf(i -> now > i.born + 12);
        }

        /**
         * 估一下这次读条一共有多少 tick（用"已过 tick ÷ 当前进度"反推）——
         * 法阵的寿命要压在读条以内，不然短读条的法阵来不及开就结束了 ✗。
         */
        private long estimateLife(long now) {
            double elapsed = Math.max(1.0D, now - this.startTick + 1.0D);
            double total = Math.max(16.0D, elapsed / Math.max(.06F, this.ratio));
            double remain = Math.max(6.0D, total * (1.0D - this.ratio));
            return (long) Mth.clamp(remain * 1.25D + 6.0D, 12.0D, 44.0D);
        }

        private int circlesWanted(int tier) {
            return tier <= 2 ? 3 : tier == 3 ? 4 : tier == 4 ? 5 : 6;
        }

        private void spawnCircle(ClientLevel level, net.minecraft.world.entity.Entity caster, int index,
                                 boolean minimal) {
            // ★ 2026-11-06：小阵挪远一点（2.15 → 3.30 起），并放大一圈 —— 原来贴着人太"小"✗
            double radius = (3.30D + index * .34D) * (this.tier >= 4 ? 1.10D : 1.0D);
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double y = caster.getY() + .75D + RANDOM.nextDouble() * (1.35D + .12D * this.tier);
            Vec3 center = new Vec3(caster.getX() + Math.cos(a) * radius, y,
                    caster.getZ() + Math.sin(a) * radius);
            // ★ 朝向随机：平贴 / 立着朝外 / 斜着插进地里 —— 作者要的"不同角度"✓
            double kind = RANDOM.nextDouble();
            Vec3 normal;
            if (kind < .34D) {
                normal = new Vec3(0, 1, 0);
                center = new Vec3(center.x, caster.getY() + .06D + RANDOM.nextDouble() * .1D, center.z);
            } else if (kind < .72D) {
                double outward = a + (RANDOM.nextDouble() - .5D) * 1.1D;
                normal = new Vec3(Math.cos(outward), (RANDOM.nextDouble() - .5D) * .55D, Math.sin(outward))
                        .normalize();
            } else {
                double tilt = .5D + RANDOM.nextDouble() * .7D;
                double yaw = RANDOM.nextDouble() * Math.PI * 2;
                normal = new Vec3(Math.cos(yaw) * tilt, 1.0D, Math.sin(yaw) * tilt).normalize();
            }
            this.circles.add(new Circle(center, normal, .70D + .14D * this.tier,
                    level.getGameTime(),
                    // 寿命压在"这次读条还剩多少"以内：太长的阵会拖到施法结束之后 ✗
                    (int) estimateLife(level.getGameTime()),
                    this.seed + 700L + index * 31L));

            if (!minimal) {
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI * 2 / 8;
                    level.addParticle(ParticleTypes.SQUID_INK,
                            center.x + Math.cos(ang) * .3D, center.y, center.z + Math.sin(ang) * .3D,
                            Math.cos(ang) * .05D, .03D, Math.sin(ang) * .05D);
                }
            }
        }

        private void spawnGhost(ClientLevel level, net.minecraft.world.entity.Entity caster,
                               Circle circle, boolean minimal) {
            Vec3 chest = new Vec3(caster.getX(), caster.getY() + caster.getBbHeight() * .62D, caster.getZ());
            Vec3 to = chest.add(0, .05D, 0);
            Vec3 mid = circle.center.add(to).scale(.5D);
            double a = RANDOM.nextDouble() * Math.PI * 2;
            // 阵挪远了（3.3 格）⇒ 弧线抬更高、飞更久，让"飘过来"这件事有过程 ✓
            Vec3 ctrl = mid.add(Math.cos(a) * (.85D + RANDOM.nextDouble() * .75D),
                    1.05D + RANDOM.nextDouble() * 1.05D,
                    Math.sin(a) * (.85D + RANDOM.nextDouble() * .75D));
            long life = 18L + this.tier;
            this.ghosts.add(new Ghost(circle.center, ctrl, to, level.getGameTime(), life,
                    this.seed + 1100L + this.ghosts.size() * 53L,
                    .64F + .07F * this.tier + RANDOM.nextFloat() * .08F));

            if (!minimal) {
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI * 2 / 6;
                    level.addParticle(ParticleTypes.SQUID_INK,
                            circle.center.x + Math.cos(ang) * .18D, circle.center.y,
                            circle.center.z + Math.sin(ang) * .18D,
                            Math.cos(ang) * .04D, .03D, Math.sin(ang) * .04D);
                }
            }
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/darkfx")
                        .info("TN-C/darkfx: spell_engine absent -> dark cast FX idle");
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
                org.apache.logging.log4j.LogManager.getLogger("TN-C/darkfx")
                        .warn("TN-C/darkfx: cast scan failed, dark cast FX disabled ({})", t.toString());
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
        if (spell.group == null || !DARK_GROUPS.contains(spell.group)) {
            return null;
        }
        int tier = Mth.clamp(spell.learn == null ? 1 : spell.learn.tier, 1, 5);
        long now = player.level().getGameTime();
        float ratio = Mth.clamp(process.progress(now).ratio(), 0, 1);
        return new CastInfo(tier, ratio, 3.0 + tier * 1.1, player.getLookAngle());
    }
}
