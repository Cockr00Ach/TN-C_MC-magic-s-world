package com.tnc.tnc.light;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TnSpellMechanics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>光系第三条链「光线」</b> ✓ —— 作者 2026-10-01 给的五档：
 *
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>表现</th><th>伤害</th></tr>
 *   <tr><td>t1</td><td>光线</td><td>向前射出 <b>3 道细的彩色光线</b>（扇形散开 ✓）</td><td>每道 6</td></tr>
 *   <tr><td>t2</td><td>大光线</td><td>一道 <b>粗</b>的彩色光线</td><td>16</td></tr>
 *   <tr><td>t3</td><td>巨大光线</td><td>一道 <b>极粗</b>的彩色光线</td><td>28</td></tr>
 *   <tr><td>t4</td><td>圣光天降</td><td>天上展开魔法阵（<b>雷法那种线条型</b> ✓），从阵里<b>垂直向下</b>射出极粗光线</td><td>36</td></tr>
 *   <tr><td>t5</td><td>五光十射</td><td>天上展开 <b>5 个</b>魔法阵，每个都放一次圣光天降（错开落下 ✓）</td><td>每个 32</td></tr>
 * </table>
 *
 * <h2>为什么整条链都在 Java 里做（JSON 只负责表演）</h2>
 * 和「光耀」那条链同一个理由 ✓：{@code release.target = SELF} 的法术**绝不能在 JSON 里放
 * {@code area_impact}** ✗（引擎会在 {@code ImpactContext.position} 为 null 时 NPE 崩服，本仓库踩过 ✓）。
 * 所以 JSON 只做起手粒子/动作/音效 ✓，真正的光线、法阵、伤害都在这里 ✓。
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li><b>光线是"活动对象"</b>：每道光线活 {@link #BEAM_LIFE} tick ✓，每 tick 画一串彩色粒子管 ✓
 *       （{@link DustParticleOptions} 上色 ✓ —— "彩色"就是靠它 ✓），进场的敌人**只挨一次**伤害 ✓
 *       （用 {@link Beam#hit} 记账 ✓，免得贴着脸被打几十下 ✗）。</li>
 *   <li><b>每 tick 的驱动走"已证活着"的那条路</b> ✓（{@code TnSpellMechanics.tickPlayer} ✓）——
 *       2026-10-01 光翼链就是挂在死事件上才一直不出效果的 ✗。</li>
 *   <li><b>伤害只打敌人</b>：复用 {@link TnSpellMechanics#isEnemy} ✓（村民/动物/剧情 NPC 不算 ✗）。</li>
 *   <li><b>法阵用雷法那种</b> ✓：{@link TNMagicCircleEntity#STYLE_STORM}（线条多 ✓，作者原话 ✓）。</li>
 * </ul>
 *
 * <p>★ 数值全在 {@link #SPELLS} 一张表里 ✓（想调手感只改那里 ✓）。
 */
public final class TNLightBeamMechanics {

    /**
     * 一档的全部数值 ✓。
     *
     * @param path      法术 id 的 path ✓
     * @param tier      档位 ✓
     * @param rays      射出几道 ✓（t1 = 3 ✓）
     * @param spreadDeg 多道之间的总散角（度 ✓）
     * @param radius    光线的粗细半径（格 ✓）—— 粒子管半径 + 伤害判定半径 ✓
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
            // 向前射的三道细光线：细（0.10 格）＋ 散角 14° ⇒ 三根能明显分开 ✓
            new Spell("light_beam", 1, 3, 14.0D, 0.10D, 6.0F, 26.0D, false, 0),
            // 大光线：一下变粗（0.34 格）✓
            new Spell("great_light_beam", 2, 1, 0.0D, 0.34D, 16.0F, 30.0D, false, 0),
            // 巨大光线：极粗（0.75 格）✓
            new Spell("giant_light_beam", 3, 1, 0.0D, 0.75D, 28.0F, 34.0D, false, 0),
            // 圣光天降：天上一张阵，垂直落下极粗光柱（高度见 SKY_HEIGHT ✓）
            new Spell("holy_light_descent", 4, 1, 0.0D, 1.1D, 36.0F, 40.0D, true, 1),
            // 五光十射：天上五张阵，各自落一道（错开 SKY_STAGGER ✓）
            new Spell("radiant_barrage", 5, 1, 0.0D, 1.1D, 32.0F, 40.0D, true, 5),
    };

    /** 光线存活时长（tick）：12 tick = 0.6 秒 ✓（够看清，又不至于长时间糊屏 ✓）。 */
    private static final int BEAM_LIFE = 12;
    /** 天降的魔法阵留在天上的时长（tick）✓ —— 阵先亮、光柱随后落下 ✓。 */
    private static final int SKY_CIRCLE_LIFE = 90;
    /** 天降时阵离地多高（格 ✓）。 */
    private static final double SKY_HEIGHT = 14.0D;
    /** t5 五张阵之间的错开（tick ✓）：一个一个落下来，像连射 ✓。 */
    private static final int SKY_STAGGER = 4;
    /** t5 五个阵里，外围四个离中心多远（格 ✓）。 */
    private static final double BARRAGE_RING = 4.5D;
    /** 每 tick 沿光线每走多远画一"圈"粒子（格 ✓）。 */
    private static final double PARTICLE_STEP = 0.45D;
    /** 每个截面撒几颗粒子 ✓（越大越"实" ✓，也越费 ✓）。 */
    private static final int PARTICLE_PER_STEP = 6;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light");

    /** 正在活动的光线 ✓（每 tick 由 {@link #tick} 推进 ✓）。 */
    private static final List<Beam> ACTIVE = new ArrayList<>();

    private TNLightBeamMechanics() {
    }

    /** 一条正在射的光线 ✓。 */
    private static final class Beam {
        ServerLevel level;
        UUID caster;
        Vec3 from;
        Vec3 dir;
        double radius;
        double length;
        float damage;
        /** 还要等几 tick 才出现 ✓（天降的"连射"靠它错开 ✓）。 */
        int delay;
        /** 总寿命与剩余寿命（tick ✓）。 */
        int maxLife;
        int life;
        /** 已经挨过这条光线伤害的实体 ✓（只打一次 ✓）。 */
        final Set<UUID> hit = new HashSet<>();

        Beam(ServerLevel level, UUID caster, Vec3 from, Vec3 dir, double radius,
             double length, float damage, int life, int delay) {
            this.level = level;
            this.caster = caster;
            this.from = from;
            this.dir = dir;
            this.radius = radius;
            this.length = length;
            this.damage = damage;
            this.maxLife = Math.max(1, life);
            this.life = this.maxLife;
            this.delay = delay;
        }
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
            double f = count == 1 ? 0.0D : (double) i / (count - 1) - 0.5D;   // -0.5 .. +0.5 ✓
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
        Vec3 term1 = v.scale(c);
        Vec3 term2 = k.cross(v).scale(s);
        Vec3 term3 = k.scale(k.dot(v) * (1.0D - c));
        return term1.add(term2).add(term3).normalize();
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
     * 五光十射的五个落点偏移 ✓（中心一个 ＋ 四个围一圈 ✓，全部在 y=0 平面上 ✓）。
     * 纯函数 ✓（单测查"5 个、对称、都在同一个平面上" ✓）。
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

    /** t1~t3：从眼睛顺着视线射出（t1 是扇形散开的三道 ✓）。 */
    private static void castForward(ServerPlayer caster, Spell spell) {
        ServerLevel level = caster.serverLevel();
        Vec3 from = caster.getEyePosition();
        List<Vec3> dirs = fanDirections(caster.getLookAngle(), spell.rays(), spell.spreadDeg());
        for (Vec3 dir : dirs) {
            ACTIVE.add(new Beam(level, caster.getUUID(), from, dir, spell.radius(),
                    spell.reach(), spell.damage(), BEAM_LIFE, 0));
        }
        play(level, from, spell.tier() >= 3 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME);
        caster.displayClientMessage(Component.literal("§e[TN-C] §r" + name(spell)
                + " §7（" + spell.rays() + " 道，粗细 " + String.format(java.util.Locale.ROOT, "%.2f", spell.radius())
                + " 格）"), true);
        LOGGER.info("TN-C/light: 光线 {} rays={} radius={} dmg={}",
                spell.path(), spell.rays(), spell.radius(), spell.damage());
    }

    /** t4/t5：天上开阵，然后从阵里垂直往下落光柱 ✓。 */
    private static void castSky(ServerPlayer caster, Spell spell) {
        ServerLevel level = caster.serverLevel();
        Vec3 aim = aimPoint(caster, spell.reach());
        List<Vec3> offsets = barrageOffsets(spell.circles(), BARRAGE_RING);
        for (int i = 0; i < offsets.size(); i++) {
            Vec3 at = aim.add(offsets.get(i));
            // ① 天上的阵（雷法那种线条型 ✓）—— 阵先出现，随后光柱落下 ✓
            TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
            if (circle != null) {
                circle.configure(Math.max(2.5D, spell.radius() * 4.0D), SKY_CIRCLE_LIFE,
                        TNMagicCircleEntity.STYLE_STORM);
                circle.moveTo(at.x, at.y + SKY_HEIGHT, at.z, 0.0F, 0.0F);
                level.addFreshEntity(circle);
            }
            // ② 地面也补一张小阵，让"落点"看得见 ✓
            TNMagicCircleEntity ground = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
            if (ground != null) {
                ground.configure(Math.max(2.0D, spell.radius() * 2.6D), SKY_CIRCLE_LIFE,
                        TNMagicCircleEntity.STYLE_STORM);
                ground.moveTo(at.x, at.y + 0.04D, at.z, 0.0F, 0.0F);
                level.addFreshEntity(ground);
            }
            // ③ 光柱：从阵往下 ✓（第 i 张阵延迟 i*SKY_STAGGER tick 才落下 ⇒ 像连射 ✓）
            Vec3 from = new Vec3(at.x, at.y + SKY_HEIGHT, at.z);
            Vec3 down = new Vec3(0.0D, -1.0D, 0.0D);
            ACTIVE.add(new Beam(level, caster.getUUID(), from, down, spell.radius(),
                    SKY_HEIGHT + 1.0D, spell.damage(), BEAM_LIFE, i * SKY_STAGGER));
        }
        play(level, aim, SoundEvents.TRIDENT_THUNDER);
        caster.displayClientMessage(Component.literal("§e[TN-C] §r" + name(spell)
                + " §7（天上 " + spell.circles() + " 个阵）"), true);
        LOGGER.info("TN-C/light: 光柱 {} circles={} radius={} dmg={}",
                spell.path(), spell.circles(), spell.radius(), spell.damage());
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
        // 没打到方块：取落点正下方最近的地面（用高度图 ✓）
        Vec3 level = end;
        var pos = net.minecraft.core.BlockPos.containing(level.x, level.y, level.z);
        var top = caster.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
        return new Vec3(level.x, top.getY(), level.z);
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
    //  每 tick：画 + 判伤（由 TnSpellMechanics.tickPlayer 调 ✓ 已证活着的路 ✓）
    // ------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Beam> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Beam beam = it.next();
            if (beam.delay > 0) {                // 天降的"连射"错开 ✓
                beam.delay--;
                continue;
            }
            if (beam.life <= 0) {
                it.remove();
                continue;
            }
            beam.life--;
            draw(beam);
            damage(beam);
        }
    }

    /** 画：沿光线每 {@link #PARTICLE_STEP} 格撒一圈彩色粒子 ✓。 */
    private static void draw(Beam beam) {
        int steps = (int) Math.max(1.0D, beam.length / PARTICLE_STEP);
        double age = 1.0D - (double) beam.life / Math.max(1, beam.maxLife);   // 0..1 ✓
        for (int i = 1; i <= steps; i++) {
            double d = i * PARTICLE_STEP;
            if (d > beam.length) {
                break;
            }
            Vec3 p = beam.from.add(beam.dir.scale(d));
            // 颜色沿光线走一整圈彩虹 ✓，并随时间轻微流动 ⇒ "彩色的光线" ✓
            Vector3f color = rainbow(d / Math.max(1.0D, beam.length) + age * 0.35D);
            float scale = (float) Math.max(0.35D, beam.radius * 1.6D);
            DustParticleOptions dust = new DustParticleOptions(color, scale);
            double r = beam.radius;
            double spread = Math.max(0.05D, r);
            beam.level.sendParticles(dust, p.x, p.y, p.z, PARTICLE_PER_STEP,
                    spread, spread, spread, 0.0D);
            // 中心再补一道白芯，看着更"实" ✓（粗光线尤其明显 ✓）
            if (beam.radius >= 0.3D) {
                beam.level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 1, r * 0.3D, r * 0.3D, r * 0.3D, 0.0D);
            }
        }
    }

    /** 判伤：在线段 {@code from -> from+dir*length} 周围 {@code radius} 内的敌人，**只挨一次** ✓。 */
    private static void damage(Beam beam) {
        Vec3 end = beam.from.add(beam.dir.scale(beam.length));
        AABB box = new AABB(beam.from, end).inflate(beam.radius + 0.6D);
        LivingEntity owner = beam.level.getServer() == null ? null
                : beam.level.getServer().getPlayerList().getPlayer(beam.caster);
        for (LivingEntity target : beam.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner || !target.isAlive()) {
                continue;
            }
            if (owner instanceof ServerPlayer caster && !TnSpellMechanics.isEnemy(caster, target)) {
                continue;                        // 村民/动物/剧情 NPC 不打 ✗
            }
            if (!beam.hit.add(target.getUUID())) {
                continue;                        // 这条光线已经打过它了 ✓
            }
            if (owner != null) {
                target.hurt(beam.level.damageSources().indirectMagic(owner, owner), beam.damage);
            } else {
                target.hurt(beam.level.damageSources().magic(), beam.damage);
            }
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    // ------------------------------------------------------------------
    //  自检/命令用
    // ------------------------------------------------------------------

    /** 现在有几条光线在飞 ✓（自检用 ✓）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 清空（换世界/自检用 ✓）。 */
    public static void clear() {
        ACTIVE.clear();
    }
}
