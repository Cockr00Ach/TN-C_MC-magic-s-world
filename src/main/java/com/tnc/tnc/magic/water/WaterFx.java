package com.tnc.tnc.magic.water;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 水法的服务端粒子表演层（2026-10-07 水球链特效增强）。
 *
 * <p>纯表现：不改伤害、不改时长、不放方块、不建实体、不留状态 ✗。
 * 所有方法都可以在没人的区块被调用 —— 原版 {@code sendParticles} 自己会丢弃
 * 没有玩家的位置 ✓。
 *
 * <p>两种发粒子的方式（区别很重要 ✗）：
 * <ul>
 *   <li>{@link #puff}：{@code count>0} ⇒ 原版在 spread 范围内随机撒点，速度方向也是随机的 ✗；</li>
 *   <li>{@link #aimed}：{@code count==0} ⇒ 原版把三个"散布"参数当成<b>精确速度矢量</b> ✓
 *       （本工程 {@code TnSpellMechanics} 早就在用这招 ✓）。要做出"向内收拢 / 向外炸开 / 顺着
 *       光束喷"这种有方向的水花，只能用这个 ✗。</li>
 * </ul>
 */
public final class WaterFx {
    private WaterFx() {}

    /** 地面环/竖直面的共同参考轴（服务端没有 {@code WaterGeometry} ✗）。 */
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** 与客户端 {@code WaterGeometry.radial} 同一条公式（服务端没有那个类 ✗）。 */
    private static Vec3 radial(Vec3 axis, double angle, double radius) {
        Vec3 right = WaterSpellRules.right(axis), up = right.cross(axis).normalize();
        return radial(right, up, angle, radius);
    }

    /** 用给定基（right,up）在平面内取点 ✓ —— 与客户端 {@code WaterGeometry.radial} 一致 ✓。 */
    private static Vec3 radial(Vec3 right, Vec3 up, double angle, double radius) {
        return right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
    }

    /** 绕飞行轴滚转 roll 弧度后的刃面基 ✓ —— 与客户端 {@code WaterGeometry.rolledBasis} 一致 ✓。 */
    private static Vec3[] rolledBasis(Vec3 dir, double roll) {
        Vec3 fwd = safeAxis(dir);
        Vec3 right = WaterSpellRules.right(fwd), up = right.cross(fwd).normalize();
        double c = Math.cos(roll), s = Math.sin(roll);
        return new Vec3[]{right.scale(c).add(up.scale(s)), up.scale(c).subtract(right.scale(s))};
    }

    private static Vec3 safeAxis(Vec3 axis) {
        return axis.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : axis.normalize();
    }

    /** 随机扩散填充：速度方向随机，用来铺"雾/泡/杂点" ✓。 */
    private static void puff(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (count <= 0) return;
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** 定向粒子：count==0 ⇒ (xDist,yDist,zDist) 就是精确速度矢量 ✓。 */
    private static void aimed(ServerLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.sendParticles(type, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 0);
    }

    /**
     * 三轴各自给半宽的随机填充 ✓ —— {@link #puff} 只能给一个 spread ✗，铺不出"高瘦的雨柱"✗。
     *
     * <p>为什么雨幕必须**撒满整个体积**而不是从顶上撒 ✗：原版雨滴粒子
     * （{@code WaterDropParticle}）寿命只有 {@code 8/(rand*.8+.2)} = **8~40 tick**、
     * 重力 .06，飞不出两三个格就消失 ✗ ⇒ 只在顶端发的话，半空以下全是空的 ✗。
     */
    private static void puffBox(ServerLevel level, ParticleOptions type, Vec3 at, int count,
                                double sx, double sy, double sz, double speed) {
        if (count <= 0) return;
        level.sendParticles(type, at.x, at.y, at.z, count, sx, sy, sz, speed);
    }

    /** 命中面浪冠：一圈水滴沿着被打中的面翻回来 ✓ —— 比单纯"炸一团"更像水 ✗。 */
    private static void crown(ServerLevel level, Vec3 at, Vec3 normal, int count, double radius, double speed) {
        Vec3 axis = safeAxis(normal);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            Vec3 offset = radial(axis, a, radius);
            aimed(level, ParticleTypes.SPLASH, at.add(offset),
                    offset.normalize().scale(speed).add(axis.scale(speed * .6)));
        }
    }

    /** 枪口喷水：法术出膛那一瞬 ✓。tier 越高越厚 ✗。 */
    public static void muzzle(ServerLevel level, Vec3 at, Vec3 dir, int tier) {
        Vec3 fwd = safeAxis(dir), mouth = at.add(fwd.scale(.35));
        boolean heavy = tier >= 2;
        puff(level, ParticleTypes.SPLASH, mouth, heavy ? 14 : 9, .22, .06);
        puff(level, ParticleTypes.BUBBLE, mouth, 6, .26, .05);
        puff(level, ParticleTypes.ELECTRIC_SPARK, mouth, heavy ? 8 : 4, .18, .12);
        if (heavy) puff(level, ParticleTypes.END_ROD, mouth, 4, .16, .14);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            aimed(level, ParticleTypes.SPLASH, mouth.add(radial(fwd, a, .18)),
                    fwd.scale(.40 + tier * .08).add(radial(fwd, a, .20)));
        }
    }

    /** 弹体命中：分档的水花 / 气泡 / 亮闪 ✓，外加朝向命中面的浪冠 ✗。 */
    public static void impact(ServerLevel level, Vec3 at, Vec3 normal, int tier) {
        switch (tier) {
            case 1 -> {
                puff(level, ParticleTypes.SPLASH, at, 16, .30, .14);
                puff(level, ParticleTypes.BUBBLE, at, 10, .32, .05);
                puff(level, ParticleTypes.ELECTRIC_SPARK, at, 6, .24, .10);
                crown(level, at, normal, 8, .28, .22);
            }
            case 2 -> {
                puff(level, ParticleTypes.SPLASH, at, 30, .42, .20);
                puff(level, ParticleTypes.BUBBLE, at, 18, .45, .06);
                puff(level, ParticleTypes.BUBBLE_POP, at, 10, .40, .08);
                puff(level, ParticleTypes.ELECTRIC_SPARK, at, 14, .35, .14);
                puff(level, ParticleTypes.END_ROD, at, 6, .30, .16);
                puff(level, ParticleTypes.NAUTILUS, at, 4, .28, .05);
                puff(level, ParticleTypes.FLASH, at, 1, 0, 0);
                crown(level, at, normal, 12, .50, .34);
            }
            default -> {
                // 龙王吼的弹幕一次 150 发 ✗：单发命中必须便宜 ✓，否则 10 秒里会刷爆粒子预算 ✗。
                puff(level, ParticleTypes.SPLASH, at, 10, .30, .16);
                puff(level, ParticleTypes.BUBBLE, at, 6, .30, .05);
                puff(level, ParticleTypes.ELECTRIC_SPARK, at, 4, .24, .12);
                crown(level, at, normal, 6, .30, .20);
            }
        }
    }

    /** 持续法阵（龙王吼在背后那个）每 4 tick 一次的环绕水花 + 亮星 ✓。 */
    public static void sigil(ServerLevel level, Vec3 center, Vec3 axis, double radius, int age) {
        Vec3 fwd = safeAxis(axis);
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = age * .06 + i * Math.PI * 2 / points;
            Vec3 at = center.add(radial(fwd, a, radius));
            if (i % 2 == 0) aimed(level, ParticleTypes.SPLASH, at, radial(fwd, a, .08).add(fwd.scale(.02)));
            else aimed(level, ParticleTypes.ELECTRIC_SPARK, at, fwd.scale(.06));
        }
    }

    /** 蓄力：水元素从远处螺旋收进法阵中心 ✓（t4/t5 才有蓄力 ✗）。 */
    public static void charge(ServerLevel level, Vec3 center, Vec3 axis, double radius, int age, int chargeTicks) {
        Vec3 fwd = safeAxis(axis);
        double t = Math.min(1, age / (double) Math.max(1, chargeTicks));
        double r = radius * (1.25 - .85 * t);
        for (int i = 0; i < 3; i++) {
            double a = age * .35 + i * (Math.PI * 2 / 3);
            Vec3 at = center.add(radial(fwd, a, r));
            aimed(level, i == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SPLASH,
                    at, center.subtract(at).normalize().scale(.35 + t * .75));
        }
        if (age % 6 == 0) puff(level, ParticleTypes.END_ROD, center, 3, radius * .25, .10);
        if (age % 4 == 0) puff(level, ParticleTypes.BUBBLE, center, 4, radius * .40, .08);
    }

    /** 法阵点火：整个阵面在同一 tick 向外抖一圈水花 ✓（龙滅那种 30 格大阵才看得出 ✗）。 */
    public static void aperture(ServerLevel level, Vec3 center, Vec3 axis, double radius) {
        Vec3 fwd = safeAxis(axis);
        int points = 24;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            Vec3 offset = radial(fwd, a, radius);
            aimed(level, ParticleTypes.SPLASH, center.add(offset),
                    offset.normalize().scale(.9).add(fwd.scale(.15)));
        }
        puff(level, ParticleTypes.ELECTRIC_SPARK, center, 12, radius * .35, .20);
        puff(level, ParticleTypes.END_ROD, center, 8, radius * .50, .15);
    }

    /** 持续水束（龙啸 / 龙滅）：沿光束表面流动 + 末端喷发 ✓，长度用服务端真实钻进长度 ✗。 */
    public static void beam(ServerLevel level, Vec3 origin, Vec3 dir, double length, double radius, int tier, int age) {
        if (length <= .5) return;
        Vec3 fwd = safeAxis(dir);
        // t5 龙滅：8 个采样点 → **4 个** ✓（与渲染器那边"线条量减半"同一个理由 ✗）
        int samples = tier == 5 ? 4 : 6;
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 at = origin.add(fwd.scale(length * t));
            Vec3 off = radial(fwd, age * .30 + i * 1.9, radius * (tier == 5 ? .55 : .80));
            if (i % 2 == 1) aimed(level, ParticleTypes.SPLASH, at.add(off), fwd.scale(.25).add(off.scale(.05)));
            else aimed(level, ParticleTypes.ELECTRIC_SPARK, at.add(off), fwd.scale(.20));
        }
        if (age % 2 == 0) {
            Vec3 end = origin.add(fwd.scale(length));
            puff(level, ParticleTypes.SPLASH, end, tier == 5 ? 14 : 8, radius * .50, .30);
            puff(level, ParticleTypes.BUBBLE, end, tier == 5 ? 8 : 5, radius * .60, .12);
            puff(level, ParticleTypes.ELECTRIC_SPARK, end, tier == 5 ? 8 : 5, radius * .40, .25);
            if (tier == 5) puff(level, ParticleTypes.CLOUD, end, 4, radius * .70, .06);
        }
    }

    /* ==================== 水纹链（第二条链 · 2026-10-10 第二批次）====================
       这一链的五招全部由 {@link TNWaterFieldEntity}（kind==1）驱动 ✗，所以粒子也从这里长 ✗。
       关键：粒子的位置**照抄客户端画的那条几何公式**（`WaterWaveVisuals` 的剖面 / `OceanWaveRules`
       的浪前剖面）✓ —— 粒子就长在浪上，而不是在浪旁边乱撒 ✗。 */

    /** 水纹（t1）：地面扩散环上的水滴 ✓，环半径与客户端那三层环同一条公式 ✗。 */
    public static void ripple(ServerLevel level, Vec3 center, double radius, int age) {
        int points = 12;
        double r = Math.min(radius, age * .2 + .3);
        for (int i = 0; i < points; i++) {
            double a = age * .05 + i * Math.PI * 2 / points;
            Vec3 at = center.add(radial(UP, a, r)).add(0, .12, 0);
            aimed(level, ParticleTypes.SPLASH, at, radial(UP, a, .20).add(0, .06, 0));
        }
        if (age % 4 == 0) puff(level, ParticleTypes.BUBBLE, center.add(0, .15, 0), 4, radius * .45, .04);
    }

    /** 推进浪头（t2 水波 / t4 海啸）：沿客户端那张"卷浪"弧线的浪唇撒水滴 ✓。 */
    public static void waveFront(ServerLevel level, Vec3 center, Vec3 dir, double radius, double height, int age, int tier) {
        Vec3 fwd = safeAxis(dir), right = WaterSpellRules.right(fwd);
        int points = tier >= 4 ? 12 : 8;
        for (int i = 0; i < points; i++) {
            double a = -Math.PI * .48 + i * Math.PI * .96 / (points - 1);
            double crest = height * Math.cos(a) * (1 + .04 * Math.sin(a * 9 + age * .15));
            Vec3 at = center.add(right.scale(Math.sin(a) * radius)).add(0, .05 + crest, 0)
                    .add(fwd.scale(Math.cos(a) * 1.6));
            aimed(level, ParticleTypes.SPLASH, at, fwd.scale(.22).add(0, .18, 0));
            if (i % 3 == 0) aimed(level, ParticleTypes.END_ROD, at.add(0, .25, 0), fwd.scale(.10).add(0, .10, 0));
        }
        if (age % 4 == 0) puff(level, ParticleTypes.CLOUD, center.add(0, .30, 0), 3, radius * .50, .04);
    }

    /** 海浪斩击（t3）的刃面粒子 ✓：与客户端刃面用**同一套滚转基** ✗，否则粒子和刃会错开 ✗。 */
    public static void slashFront(ServerLevel level, Vec3 center, Vec3 dir, double radius, int age, double roll) {
        Vec3 fwd = safeAxis(dir);
        Vec3[] basis = rolledBasis(fwd, roll);
        Vec3 right = basis[0], up = basis[1];
        for (int i = 0; i < 6; i++) {
            double a = -Math.PI * .55 + i * Math.PI * 1.1 / 5;
            Vec3 at = center.add(WaterSpellRules.slashArc(fwd,roll,a,radius));
            aimed(level, ParticleTypes.SPLASH, at, WaterFx.radial(right, up, a, .28).add(fwd.scale(.25)));
        }
        if (age % 6 == 0) aimed(level, ParticleTypes.ELECTRIC_SPARK, center, fwd.scale(.30));
    }

    /** 灭世之海（t5）：按 {@link OceanWaveRules} 的**真实**浪前剖面撒浪花＋泡沫 ✓。 */
    public static void seaFronts(ServerLevel level, Vec3 center, double limit, int age) {
        int crest = OceanWaveRules.profilePoints() - 1;
        int maxFronts = Math.min(OceanWaveRules.MAX_FRONTS, 4);
        for (int slot = 0; slot < maxFronts; slot++) {
            double front = OceanWaveRules.frontRadius(age, slot);
            if (front < 0) continue;
            double h = OceanWaveRules.frontHeight(front);
            int points = 12;
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2 / points + slot * .7;
                for (int j : new int[]{crest, crest - 3}) {
                    double r = OceanWaveRules.profileRadius(j, front, h, limit);
                    double y = OceanWaveRules.profileHeight(j, h) * OceanWaveRules.waveLift(a, age + r * .1);
                    aimed(level, j == crest ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                            center.add(radial(UP, a, r)).add(0, y, 0),
                            radial(UP, a, .32).add(0, .14, 0));
                }
                // 每隔几个角度再补一颗亮星 ✓：不同 slot 错开相位 ⇒ 六道浪各有各的闪法 ✗
                if (slot % 2 == 0 ? i % 3 == 0 : i % 4 == 0) {
                    double r = OceanWaveRules.profileRadius(crest, front, h, limit);
                    double y = OceanWaveRules.profileHeight(crest, h) * OceanWaveRules.waveLift(a, age + r * .1);
                    aimed(level, ParticleTypes.END_ROD, center.add(radial(UP, a, r)).add(0, y + .5, 0),
                            radial(UP, a, .18).add(0, .30, 0));
                }
            }
        }
        if (age % 2 == 0) {
            double r = Math.min(limit, 24);
            puff(level, ParticleTypes.SPLASH, center.add(0, .30, 0), 14, r * .55, .26);
            puff(level, ParticleTypes.CLOUD, center.add(0, .25, 0), 6, r * .65, .05);
        }
    }

    /** 施法那一下：朝正前方压出一面水墙 ✓（tier 越高越厚 ✗）。 */
    public static void waveCast(ServerLevel level, Vec3 at, Vec3 dir, int tier) {
        Vec3 fwd = safeAxis(dir);
        puff(level, ParticleTypes.SPLASH, at, 10 + tier * 5, .55, .35);
        puff(level, ParticleTypes.BUBBLE, at, 5 + tier * 3, .65, .12);
        if (tier >= 3) puff(level, ParticleTypes.ELECTRIC_SPARK, at, 5 + tier * 2, .55, .30);
        if (tier >= 4) {
            puff(level, ParticleTypes.END_ROD, at, 10, .65, .30);
            puff(level, ParticleTypes.FLASH, at, 1, 0, 0);
        }
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI * .5 + i * Math.PI / 9;
            Vec3 offset = radial(fwd, a, 1.2);
            aimed(level, ParticleTypes.SPLASH, at.add(offset), fwd.scale(.55).add(offset.scale(.40)));
        }
    }

    /** 命中那一下：被打中的目标身上炸一小圈 ✓（水纹链每个目标一次，不是每帧 ✗）。 */
    public static void hit(ServerLevel level, Vec3 at, Vec3 away, int tier) {
        puff(level, ParticleTypes.SPLASH, at, 6 + tier, .35, .18);
        puff(level, ParticleTypes.BUBBLE, at, 4, .40, .06);
        crown(level, at, away, 6, .30, .22);
    }

    /** 灭世之海正常结束、要转雷暴那一下 ✓ —— 整片海域最后一次向外炸开 ✗。 */
    public static void seaBurst(ServerLevel level, Vec3 center) {
        puff(level, ParticleTypes.SPLASH, center.add(0, 3, 0), 60, 14, .80);
        puff(level, ParticleTypes.BUBBLE, center.add(0, 2, 0), 30, 10, .30);
        puff(level, ParticleTypes.ELECTRIC_SPARK, center.add(0, 4, 0), 30, 12, .60);
        puff(level, ParticleTypes.CLOUD, center.add(0, 2, 0), 20, 12, .20);
        puff(level, ParticleTypes.FLASH, center.add(0, 4, 0), 2, 2, 0);
    }

    /* ==================== 水缚链（第三条链 · kind==2 · 2026-10-10 第五批次）====================
       这一链的五招同样由 {@link TNWaterFieldEntity} 驱动（kind==2）✗，但在本批之前
       **服务端一个粒子都没有** ✗ —— 只有客户端那个"三环 + 立柱 + 螺旋"的笼子 ✗。
       粒子的位置照抄 `TNWaterFieldRenderer` 的笼子几何 ✓：三环用 y=.12+h*i/2 ✓、
       立柱用同一条角度公式 ✓ —— 粒子长在笼子上，而不是在笼子旁边乱撒 ✗。 */

    /** 水缚链持续表现：t1 收拢 / t2 落水 / t3 上浮 / t4 深渊墨花 / t5 冥穴 ✓。 */
    public static void bindFx(ServerLevel level, Vec3 center, double radius, double height, int tier, int age) {
        if (age % 4 != 0) return;
        // 三道环：与客户端 y=.12+h*i/2 同一条公式 ✓。t1/t2 只是"绑住"⇒ 向内收 ✓；
        // t3 起服务端会真的把敌人往里拉 ✓ ⇒ 粒子也跟着向内 ✓，表现与机制同向 ✗。
        int points = tier >= 4 ? 12 : 8;
        for (int ring = 0; ring < 3; ring++) {
            double y = .12 + height * ring / 2;
            for (int i = 0; i < points; i++) {
                double a = age * .04 + i * Math.PI * 2 / points + ring * .7;
                Vec3 at = center.add(radial(UP, a, radius)).add(0, y, 0);
                Vec3 inward = radial(UP, a, tier >= 3 ? -.24 : -.14).add(0, .03, 0);
                if (tier >= 4 && i % 3 == 0) aimed(level, ParticleTypes.GLOW, at, inward);
                else aimed(level, i % 3 == 1 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SPLASH, at, inward);
            }
        }
        // 立柱上沿的水冠 + 沿柱身落下的水 ✓（t2 起才有"笼子"的感觉 ✗）
        int bars = 8 + tier * 2;
        for (int i = 0; i < bars; i += 2) {
            double a = i * Math.PI * 2 / bars + age * .003;
            Vec3 top = center.add(radial(UP, a, radius)).add(0, .12 + height, 0);
            aimed(level, ParticleTypes.BUBBLE, top, radial(UP, a, .10).add(0, .26, 0));
            if (tier >= 2) puff(level, ParticleTypes.RAIN, center.add(radial(UP, a, radius)).add(0, height * .6, 0), 1, .06, .02);
        }
        // 水葬起：底下往上冒泡 ✓（"淹"的感觉 ✗）；深渊起：贴地一圈深色墨花 ✓
        if (tier >= 3 && age % 4 == 0) puff(level, ParticleTypes.BUBBLE_COLUMN_UP, center.add(0, .3, 0), 2 + tier, radius * .55, .05);
        if (tier >= 4) {
            for (int i = 0; i < 4; i++) {
                double a = age * .06 + i * Math.PI / 2;
                Vec3 at = center.add(radial(UP, a, radius * .8)).add(0, .35, 0);
                aimed(level, ParticleTypes.SQUID_INK, at, radial(UP, a, -.30).add(0, .05, 0));
            }
        }
        if (tier == 5 && age % 4 == 0) puff(level, ParticleTypes.CURRENT_DOWN, center.add(0, height * .7, 0), 4, radius * .5, .04);
    }

    /** 冥穴（t5）落剑前那两下提示：出现 / 下坠 ✓ —— 由 {@link SeaGodSwordRules#cue} 保证一次一发 ✗。 */
    public static void cryptCue(ServerLevel level, Vec3 center, double radius, boolean drop) {
        double y = drop ? SeaGodSwordRules.DROP_HEIGHT : 0;
        // 剑是"从 64 格高处掉下来"的 ✓ ⇒ 出现时在 64 格处炸一圈 ✓、下坠时从 64 格往下淌 ✓
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            Vec3 at = center.add(radial(UP, a, radius * .55)).add(0, y, 0);
            aimed(level, i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, at,
                    drop ? new Vec3(0, -1.6, 0) : radial(UP, a, .35));
        }
        puff(level, drop ? ParticleTypes.CLOUD : ParticleTypes.FLASH, center.add(0, y, 0), drop ? 8 : 1, radius * .5, drop ? .10 : 0);
    }

    /**
     * 冥穴落剑**冲击**：每一剑砸地那一下 ✓ —— 贴地浪冠 + 水柱 + 深色墨花 ✗。
     *
     * <p>由 {@code lastSwordImpact} 保证一剑一次 ✓（不是每帧 ✗）—— 这一链原本连一次冲击表现都没有 ✗，
     * 是本次文档里点名的"最该补"的两处之一 ✓。
     */
    public static void cryptImpact(ServerLevel level, Vec3 center, double radius, int index) {
        Vec3 at = center.add(0, .35, 0);
        puff(level, ParticleTypes.SPLASH, at, 44, radius * .55, .72);
        puff(level, ParticleTypes.BUBBLE, at.add(0, .3, 0), 20, radius * .45, .30);
        puff(level, ParticleTypes.SQUID_INK, at.add(0, .8, 0), 16, radius * .35, .30);
        puff(level, ParticleTypes.CLOUD, at.add(0, .2, 0), 12, radius * .62, .18);
        puff(level, ParticleTypes.END_ROD, at.add(0, 1.2, 0), 10, radius * .40, .35);
        crown(level, at, UP, 18, radius * .55, .60);   // 贴地向外翻的浪冠 ✓
        if (index == 0) puff(level, ParticleTypes.FLASH, center.add(0, 2, 0), 1, 0, 0);
    }

    /** Batched ground splashes; the tracked client field generates the continuous rain. */
    public static void rainFx(ServerLevel level, Vec3 center, double radius, double height, int tier, int age) {
        // Rain is generated locally by the tracked field, without one packet per decorative drop.
        if (age % 8 != 0) return;
        puffBox(level, ParticleTypes.SPLASH, center.add(0,.15,0), 3+tier,
                radius*.45,.08,radius*.45,.025);
    }

    /** Healing feedback, once per target per second. */
    public static void heal(ServerLevel level, Vec3 at) {
        aimed(level, ParticleTypes.ENCHANTED_HIT, at, new Vec3(0, .20, 0));
        puff(level, ParticleTypes.SPLASH, at, 3, .28, .10);
        aimed(level, ParticleTypes.NAUTILUS, at, new Vec3(0, .12, 0));
    }
}
