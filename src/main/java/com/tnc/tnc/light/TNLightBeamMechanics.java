package com.tnc.tnc.light;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TnSpellMechanics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <b>光系第三条链「光线」</b> ✓ —— 作者 2026-10-01 给的五档：
 *
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>表现</th><th>伤害</th></tr>
 *   <tr><td>t1</td><td>光线</td><td>向前射出 <b>3 道细的彩色光线</b>（扇形散开 ✓）</td><td>每道 6</td></tr>
 *   <tr><td>t2</td><td>大光线</td><td>一道 <b>粗</b>的彩色光线（1.6 格宽 ✓）</td><td>16</td></tr>
 *   <tr><td>t3</td><td>巨大光线</td><td>一道 <b>极粗</b>的彩色光线（3.2 格宽 ✓）</td><td>28</td></tr>
 *   <tr><td>t4</td><td>圣光天降</td><td>天上展开魔法阵（<b>雷法那种线条型</b> ✓），从阵里<b>垂直向下</b>射出极粗光线（<b>9 格宽</b> ✓）</td><td>36</td></tr>
 *   <tr><td>t5</td><td>五光十射</td><td>天上展开 <b>5 个</b>魔法阵（彼此分开 12 格 ✓），每个都放一次圣光天降</td><td>每个 32</td></tr>
 * </table>
 *
 * <h2>★ 2026-10-01 第二版：光线改成<b>实体</b>（作者："光线我想要实体的"）</h2>
 * 原来是用 {@code DustParticleOptions} 撒一条粒子管 ✗ —— 看着像"一串点"、边缘毛 ✗。
 * 现在每道光线是一个 {@link TNLightBeamEntity} ✓（一整个棱柱 ✓，颜色沿轴线走彩虹 ✓），
 * 加粗只是改半径、变长只是改长度 ✗，和魔法阵同一个思路 ✓。
 * <b>伤害也搬进实体了</b> ✓（{@code TNLightBeamEntity.tick} 自己判伤 ✓，每个敌人每条光线只挨一次 ✓），
 * 所以这里只剩"什么时候、从哪儿、朝哪儿、放几道"✓。
 *
 * <h2>数值（作者 2026-10-01 第二版要求："全部加粗 / 圣光天降加个10倍 / 五光十射分开点"）</h2>
 * 全在 {@link #SPELLS} 一张表里 ✓ —— 半径是"格"，写 4.5 就是 9 格宽的光柱 ✓。
 */
public final class TNLightBeamMechanics {

    /**
     * 一档的全部数值 ✓。
     *
     * @param path      法术 id 的 path ✓
     * @param tier      档位 ✓
     * @param rays      射出几道 ✓（t1 = 3 ✓）
     * @param spreadDeg 多道之间的总散角（度 ✓）
     * @param radius    光柱半径（格 ✓）—— 直径 = 2×radius ✓
     * @param damage    每道光线对每个敌人的伤害 ✓
     * @param reach     射程（格 ✓）
     * @param sky       是否"天降"形态 ✓（false = 从眼睛向前射 ✗）
     * @param circles   天上开几个阵 ✓（天降形态用的是它 ✓）
     */
    private record Spell(String path, int tier, int rays, double spreadDeg, double radius,
                         float damage, double reach, boolean sky, int circles) {
    }

    /** 每个档位的数值（作者给的是"表现"，具体数字是这里定的 ✓，要调就调这里 ✓）。 */
    private static final Spell[] SPELLS = {
            // 向前射的三道细光线：现在是**实体光柱** ✓（半径 0.22 格 ⇒ 约 0.44 格宽 ✓）
            new Spell("light_beam", 1, 3, 14.0D, 0.22D, 6.0F, 26.0D, false, 0),
            // 大光线：粗（0.80 格 ⇒ 1.6 格宽 ✓）
            new Spell("great_light_beam", 2, 1, 0.0D, 0.80D, 16.0F, 30.0D, false, 0),
            // 巨大光线：极粗（1.60 格 ⇒ 3.2 格宽 ✓）
            new Spell("giant_light_beam", 3, 1, 0.0D, 1.60D, 28.0F, 34.0D, false, 0),
            // 圣光天降：作者"感觉可以加个10倍都" ⇒ 4.5 格半径 ＝ **9 格宽**的光柱 ✓
            //   （原来是 1.1 ⇒ 现在约 4 倍；真要 10 倍就把 4.5 改成 11.0 ⇒ 一根柱子 22 格宽 ✗ 很夸张 ✓）
            new Spell("holy_light_descent", 4, 1, 0.0D, 4.50D, 36.0F, 40.0D, true, 1),
            // 五光十射：作者"每个法阵可以分开点" ⇒ 环半径 4.5 → **12 格** ✓
            //   （光柱半径 4.5 ⇒ 相邻两根要离 ≥9 格才不叠 ✗，12 格刚好各自独立 ✓）
            new Spell("radiant_barrage", 5, 1, 0.0D, 4.50D, 32.0F, 40.0D, true, 5),
    };

    /** 光柱存活时长（tick）：14 tick ≈ 0.7 秒 ✓（实体，够看清又不糊屏 ✓）。 */
    private static final int BEAM_LIFE = 14;
    /** 天降的魔法阵留在天上的时长（tick）✓ —— 阵先亮、光柱随后落下 ✓。 */
    private static final int SKY_CIRCLE_LIFE = 120;
    /** 天降时阵离地多高（格 ✓）。 */
    private static final double SKY_HEIGHT = 16.0D;
    /**
     * ★ 天降的"阵先亮 → 光柱落下"间隔（tick）：作者 2026-10-01："要等魔法阵出来之后个一秒，再放激光" ✓
     * ⇒ 20 tick = 1 秒 ✓（原来是 4 tick = 0.2 秒 ✗，等于阵和光柱一起冒出来 ✗）。
     */
    private static final int SKY_DELAY = 20;
    /** t5 五张阵之间的额外错开（tick ✓）：一个一个落下来，像连射 ✓（在 {@link #SKY_DELAY} 之后再叠 ✓）。 */
    private static final int SKY_STAGGER = 5;
    /** t5 外围四个阵离中心多远（格 ✓）—— 要 ≥ 光柱直径才不叠 ✓（见 {@link #SPELLS} ✓）。 */
    private static final double BARRAGE_RING = 12.0D;
    /** 天降的阵画多大（相对光柱半径 ✓）。 */
    private static final double SKY_CIRCLE_SCALE = 1.6D;

    /**
     * ★ 索敌的"瞄准锥"（度 ✓）—— 作者 2026-10-01："t1234激光怎么没有索敌啊" ✗。
     *
     * <p>只认**与准星夹角 ≤ 这个度数**的敌人 ✓（和雷法 t5 挑锚点用的是同一个思路 ✓）：
     * 全 360° 找最近的话，会打到身后/墙后的怪 ✗（玩家看不见，手感很差 ✗）。
     * 35° 差不多就是"屏幕正中间那一片" ✓。
     */
    private static final double AIM_CONE_DEGREES = 35.0D;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    /** 还没到时间落下的光柱 ✓（t5 的"连射"靠它错开 ✓）——每 tick 由 {@link #tick} 推进 ✓。 */
    private static final class Pending {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 from;
        final Vec3 dir;
        final Spell spell;
        final int style;
        int delay;

        Pending(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 dir,
                Spell spell, int style, int delay) {
            this.level = level;
            this.caster = caster;
            this.from = from;
            this.dir = dir;
            this.spell = spell;
            this.style = style;
            this.delay = delay;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private TNLightBeamMechanics() {
    }

    // ------------------------------------------------------------------
    //  纯几何（可单测 ✓）：散角、彩虹色、五阵位置
    // ------------------------------------------------------------------

    /**
     * 把一束方向按"扇形散开"分成 {@code count} 道 ✓（绕竖直轴左右平分 ✓）。
     *
     * <p>纯函数 ✓ —— 单测直接喂一个方向就能查"是不是 3 道、角度对不对" ✓。
     */
    public static List<Vec3> fanDirections(Vec3 look, int count, double spreadDeg) {
        List<Vec3> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        if (count == 1 || spreadDeg <= 0.0D) {
            for (int i = 0; i < count; i++) {
                out.add(look.normalize());
            }
            return out;
        }
        // 以 (0,1,0) 为轴左右转；视线接近竖直时退化，改用 (1,0,0) ✓
        Vec3 axis = Math.abs(look.normalize().y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < count; i++) {
            double f = (double) i / (count - 1) - 0.5D;   // -0.5 .. +0.5 ✓
            out.add(rotateAround(look.normalize(), axis, f * spreadDeg));
        }
        return out;
    }

    /** 绕任意轴旋转（度 ✓）—— 自己算，免得依赖渲染侧的类 ✓。 */
    static Vec3 rotateAround(Vec3 v, Vec3 axis, double degrees) {
        double rad = Math.toRadians(degrees);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        Vec3 k = axis.normalize();
        // 罗德里格斯公式 ✓
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1.0D - c))).normalize();
    }

    /**
     * 彩虹色 ✓ —— {@code t} 在 0..1 之间走一整圈色相 ✓（"彩色的光线"就是靠它 ✓）。
     * 纯函数 ✓（单测查首尾不同、分量都在 0..1 ✓）。
     */
    public static Vector3f rainbow(double t) {
        double h = (t % 1.0D + 1.0D) % 1.0D * 6.0D;
        float x = (float) (1.0D - Math.abs(h % 2.0D - 1.0D));
        return switch ((int) h) {
            case 0 -> new Vector3f(1.0F, x, 0.0F);
            case 1 -> new Vector3f(x, 1.0F, 0.0F);
            case 2 -> new Vector3f(0.0F, 1.0F, x);
            case 3 -> new Vector3f(0.0F, x, 1.0F);
            case 4 -> new Vector3f(x, 0.0F, 1.0F);
            default -> new Vector3f(1.0F, 0.0F, x);
        };
    }

    /**
     * 五光十射的五个落点偏移 ✓（中心一个 ＋ 四个围一圈 ✓，全在 y=0 平面上 ✓）。
     * 纯函数 ✓（单测查"5 个、对称、间距够大" ✓）。
     */
    public static List<Vec3> barrageOffsets(int circles, double ringRadius) {
        List<Vec3> out = new ArrayList<>();
        if (circles <= 0) {
            return out;
        }
        out.add(new Vec3(0.0D, 0.0D, 0.0D));
        int ring = circles - 1;
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2.0D * i / Math.max(1, ring) + Math.PI / 4.0D;
            out.add(new Vec3(Math.cos(a) * ringRadius, 0.0D, Math.sin(a) * ringRadius));
        }
        return out;
    }

    /**
     * 一个点**在不在准星锥 + 射程里** ✓ —— 抽成纯函数，单测直接验 ✓
     * （"身后不要、旁边不要、太远不要" 这三条就是索敌的全部规矩 ✓）。
     */
    public static boolean inAimCone(Vec3 eye, Vec3 look, Vec3 point, double reach, double coneDegrees) {
        Vec3 to = point.subtract(eye);
        double dist = to.length();
        if (dist > reach || dist < 1.0E-3D) {
            return false;
        }
        return to.normalize().dot(look.normalize()) >= Math.cos(Math.toRadians(coneDegrees));
    }

    /**
     * ★ <b>索敌</b>（作者 2026-10-01："t1234激光怎么没有索敌啊" ✗）：
     * 在**准星锥**（{@link #AIM_CONE_DEGREES} ✓）里、射程内的敌人，按距离从近到远排 ✓。
     */
    public static List<LivingEntity> coneTargets(ServerPlayer caster, List<LivingEntity> candidates,
                                                 Vec3 look, double reach, double coneDegrees) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : candidates) {
            if (e == caster || !e.isAlive()) {
                continue;
            }
            if (!TnSpellMechanics.isEnemy(caster, e)) {
                continue;                        // 村民/动物/剧情 NPC 不算 ✓
            }
            Vec3 centre = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            if (!inAimCone(caster.getEyePosition(), look, centre, reach, coneDegrees)) {
                continue;                        // 不在准星锥里（身后/旁边/太远）✗
            }
            out.add(e);
        }
        out.sort(java.util.Comparator.comparingDouble(
                e -> e.position().distanceToSqr(caster.position())));
        return out;
    }

    /** 找一个目标（最近的 ✓）；没有就返回 null ⇒ 调用方回退成"顺着视线射" ✓。 */
    private static LivingEntity findTarget(ServerPlayer caster, Spell spell) {
        ServerLevel level = caster.serverLevel();
        double reach = Math.max(12.0D, spell.reach());
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(reach));
        List<LivingEntity> targets = coneTargets(caster, candidates, caster.getLookAngle(),
                reach, AIM_CONE_DEGREES);
        return targets.isEmpty() ? null : targets.get(0);
    }

    // ------------------------------------------------------------------
    //  释放
    // ------------------------------------------------------------------

    /** 这个法术是不是本链的 ✓（供 SPELL_CAST 钩子判断 ✓）。 */
    public static boolean isLightBeamSpell(String path) {
        return find(path) != null;
    }

    private static Spell find(String path) {
        for (Spell spell : SPELLS) {
            if (spell.path().equals(path)) {
                return spell;
            }
        }
        return null;
    }

    /** 释放时调用（由 {@code TnSpellMechanics.onSpellCast} 转发 ✓）。 */
    public static void onSpellCast(ServerPlayer caster, String path) {
        Spell spell = find(path);
        if (spell == null) {
            return;
        }
        if (spell.sky()) {
            castSky(caster, spell);
        } else {
            castForward(caster, spell);
        }
    }

    /**
     * t1~t3：从眼睛射出（t1 是扇形散开的三道 ✓）—— 每道一个**实体光柱** ✓。
     *
     * <p>★ 索敌（作者 2026-10-01："t1234激光怎么没有索敌啊"✗）：先按准星锥找最近的一个敌人 ✓，
     * 找到就把**中心那道对准它**（侧面几道围着它散开 ✓）；一个都没找到就顺着视线射 ✓（不至于打空 ✗）。
     */
    private static void castForward(ServerPlayer caster, Spell spell) {
        ServerLevel level = caster.serverLevel();
        Vec3 from = caster.getEyePosition();
        LivingEntity target = findTarget(caster, spell);
        Vec3 center = caster.getLookAngle();
        if (target != null) {
            center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(from).normalize();
        }
        List<Vec3> dirs = fanDirections(center, spell.rays(), spell.spreadDeg());
        for (Vec3 dir : dirs) {
            spawn(level, caster, from, dir, spell, TNLightBeamEntity.STYLE_RAY);
        }
        play(level, from, spell.tier() >= 3 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME);
        caster.displayClientMessage(Component.literal("§e[TN-C] §r" + name(spell)
                + " §7（" + spell.rays() + " 道实体光线，每道粗 "
                + String.format(java.util.Locale.ROOT, "%.1f", spell.radius() * 2.0D) + " 格"
                + (target == null ? "，无目标" : "，锁定 " + target.getName().getString()) + "）"), true);
        LOGGER.info("TN-C/light: 光线 {} rays={} radius={} dmg={} target={}",
                spell.path(), spell.rays(), spell.radius(), spell.damage(),
                target == null ? "none" : target.getName().getString());
    }

    /**
     * t4/t5：天上开阵 → **等 1 秒** → 从阵里垂直往下落光柱 ✓（作者："要等魔法阵出来之后个一秒，再放激光"✓）。
     *
     * <p>★ 索敌：阵**开在目标头顶**（准星锥里最近的敌人 ✓）；没有目标才落在准星落点上 ✓。
     * t5 会一次锁定最多 5 个敌人，一人一个阵 ✓（不够就围着落点补圈 ✓）。
     */
    private static void castSky(ServerPlayer caster, Spell spell) {
        ServerLevel level = caster.serverLevel();
        double reach = Math.max(16.0D, spell.reach());
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(reach));
        List<LivingEntity> targets = coneTargets(caster, candidates, caster.getLookAngle(),
                reach, AIM_CONE_DEGREES);

        List<Vec3> spots = new ArrayList<>();
        for (LivingEntity target : targets) {
            if (spots.size() >= spell.circles()) {
                break;
            }
            spots.add(target.position());        // ★ 每个阵开在目标头上 ✓
        }
        if (spots.isEmpty()) {
            // 没锁到敌人：中心一个 + 围着准星落点铺一圈 ✓（t4 就只铺中心那一个 ✓）
            Vec3 aim = aimPoint(caster, spell.reach());
            for (Vec3 off : barrageOffsets(spell.circles(), BARRAGE_RING)) {
                spots.add(aim.add(off));
            }
        } else if (spots.size() < spell.circles()) {
            // 锁到的人不够：以第一个目标为中心补圈 ✓
            Vec3 center = spots.get(0);
            for (Vec3 off : barrageOffsets(spell.circles() - spots.size() + 1, BARRAGE_RING)) {
                if (off.length() < 0.01D) {
                    continue;                    // 跳过那个"中心"（已经有目标了 ✓）
                }
                spots.add(center.add(off));
            }
        }

        for (int i = 0; i < spots.size(); i++) {
            Vec3 at = spots.get(i);
            double ground = groundY(level, at);
            // ① 天上的阵（雷法那种线条型 ✓）—— 阵先亮，**1 秒后**光柱才落下 ✓
            circle(level, at.x, ground + SKY_HEIGHT, at.z, spell.radius() * SKY_CIRCLE_SCALE, 0.0F);
            // ② 地面也补一张阵，让"落点"看得见 ✓
            circle(level, at.x, ground + 0.04D, at.z, spell.radius() * SKY_CIRCLE_SCALE * 0.8D, 0.0F);
            // ③ 光柱：等 SKY_DELAY + i*SKY_STAGGER 才落 ✓（t5 一根接一根 ✓）
            PENDING.add(new Pending(level, caster, new Vec3(at.x, ground + SKY_HEIGHT, at.z),
                    new Vec3(0.0D, -1.0D, 0.0D), spell, TNLightBeamEntity.STYLE_DESCENT,
                    SKY_DELAY + i * SKY_STAGGER));
        }
        play(level, caster.position(), SoundEvents.TRIDENT_THUNDER);
        caster.displayClientMessage(Component.literal("§e[TN-C] §r" + name(spell)
                + " §7（天上 " + spots.size() + " 个阵，1 秒后落下，每道粗 "
                + String.format(java.util.Locale.ROOT, "%.1f", spell.radius() * 2.0D) + " 格"
                + (targets.isEmpty() ? "，无目标" : "，锁定 " + Math.min(targets.size(), spell.circles()) + " 个目标")
                + "）"), true);
        LOGGER.info("TN-C/light: 光柱 {} circles={} radius={} delay={}t dmg={} targets={}",
                spell.path(), spots.size(), spell.radius(), SKY_DELAY, spell.damage(), targets.size());
    }

    /** 某个水平位置的地面高度 ✓（用高度图，找不到就退回实体脚下的 y ✓）。 */
    private static double groundY(ServerLevel level, Vec3 at) {
        var pos = net.minecraft.core.BlockPos.containing(at.x, at.y, at.z);
        var top = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
        return Math.max(top.getY(), at.y);
    }

    private static void circle(ServerLevel level, double x, double y, double z, double radius, float yaw) {
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle == null) {
            return;
        }
        circle.configure(radius, SKY_CIRCLE_LIFE, TNMagicCircleEntity.STYLE_STORM);
        circle.moveTo(x, y, z, yaw, 0.0F);
        level.addFreshEntity(circle);
    }

    /**
     * 生成一根实体光柱 ✓（作者 2026-10-01："光线我想要实体的"✗）。
     *
     * <p>光柱沿**本地 +Z** 长 {@code length} 格 ✓ ⇒ 这里只要把实体的朝向摆成 {@code dir} ✓
     * （{@code yRot/xRot} 那套和原版投射物一致 ✓）。
     */
    private static void spawn(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 dir,
                              Spell spell, int style) {
        TNLightBeamEntity beam = TNOrbEntities.LIGHT_BEAM.get().create(level);
        if (beam == null) {
            return;
        }
        Vec3 d = dir.normalize();
        float yaw = (float) (Mth.atan2(d.x, d.z) * (180.0F / (float) Math.PI));
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0F / (float) Math.PI));
        beam.moveTo(from.x, from.y, from.z, yaw, pitch);
        double length = spell.sky() ? SKY_HEIGHT + 1.0D : spell.reach();
        beam.configure(caster, spell.radius(), length, spell.damage(), BEAM_LIFE, style);
        level.addFreshEntity(beam);
    }

    /** 准星落点（打到地面/方块就用它，否则取射程尽头 ✓）。 */
    private static Vec3 aimPoint(ServerPlayer caster, double reach) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(reach));
        BlockHitResult hit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        var pos = net.minecraft.core.BlockPos.containing(end.x, end.y, end.z);
        var top = caster.level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
        return new Vec3(end.x, top.getY(), end.z);
    }

    private static String name(Spell spell) {
        return switch (spell.tier()) {
            case 1 -> "光线";
            case 2 -> "大光线";
            case 3 -> "巨大光线";
            case 4 -> "圣光天降";
            default -> "五光十射";
        };
    }

    // ------------------------------------------------------------------
    //  每 tick：把"还没落下"的光柱放出来（伤害/粒子都在实体自己身上 ✓）
    //  驱动走 TnSpellMechanics.tickPlayer ✓（已证活着的路 ✓）
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (pending.delay > 0) {
                pending.delay--;                 // 还没到点：只减计数 ✓
                continue;
            }
            spawn(pending.level, pending.caster, pending.from, pending.dir, pending.spell, pending.style);
            it.remove();
        }
    }

    // ------------------------------------------------------------------
    //  自检/命令用
    // ------------------------------------------------------------------

    /** 还有几根光柱没落下 ✓（自检用 ✓）。 */
    public static int pendingCount() {
        return PENDING.size();
    }

    /** 清空（换世界/自检用 ✓）。 */
    public static void clear() {
        PENDING.clear();
    }

    /** 声音（服务端播 ✓）。 */
    private static void play(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.2F, 1.0F);
    }
}
