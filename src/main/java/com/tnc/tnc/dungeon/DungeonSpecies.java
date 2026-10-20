package com.tnc.tnc.dungeon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 12 只地下城小怪的**唯一数值契约** —— 血量 / 伤害 / 体型 / 攻击方式全部在这张表里。
 *
 * <p>为什么做成枚举而不是 12 个实体类 ✗：
 * <ul>
 *   <li>12 个类会有 12 份 {@code createAttributes}、12 份 registerGoals，改一次平衡要动 12 个文件；</li>
 *   <li>模型/贴图/动画文件名就是 {@link #id}，枚举一份数据同时喂给 Java 与
 *       {@code tools/gen_dungeon_mobs.py}，两边不会漂移；</li>
 *   <li>刷怪一点名（{@code tnc:lantern_boar}）就能反查到这里，日志好读。</li>
 * </ul>
 *
 * <p>★ 攻击方式（{@link Kind}）决定三件事：视线/距离门槛、前摇与后摇的 tick 数、以及
 * {@code TNDungeonMob#performImpact} 里真正发生什么。**动画的关键帧按同一组 tick 生成**
 * （见生成器里 {@code anim_for}），所以"动画打到一半就出伤害"这种错位不会发生 ✓。
 */
public enum DungeonSpecies {

    // id                      攻击方式             血    伤害   速度   护甲  宽    高    经验
    // 宽/高按**新模型的实际包围盒**给（tools/audit_dungeon_mob_rigs.py 会打印），
    // 不然打起来刀会砍在空气上、或者还没碰到模型就掉血。
    LANTERN_BOAR        ("lantern_boar",        Kind.CHARGE,   62.0, 7.0, 0.30, 2.0, 0.95F, 1.45F,  8),
    RUST_HOOK_BUTCHER   ("rust_hook_butcher",   Kind.HOOK,     88.0, 9.0, 0.27, 5.0, 1.30F, 2.10F, 12),
    MOSS_CARAPACE_SPIDER("moss_carapace_spider",Kind.POUNCE,   46.0, 6.0, 0.34, 3.0, 1.80F, 0.85F,  9),
    BONE_CROSSBOWMAN    ("bone_crossbowman",    Kind.VOLLEY,   38.0, 4.0, 0.28, 1.0, 1.15F, 2.10F, 10),
    ASH_FLINGER         ("ash_flinger",         Kind.POT,      44.0, 5.0, 0.26, 2.0, 1.05F, 2.00F, 10),
    COPPER_FUSILIER     ("copper_fusilier",     Kind.BEAM,     52.0, 6.0, 0.25, 4.0, 1.05F, 2.00F, 13),
    SEWER_CRAWLER       ("sewer_crawler",       Kind.BURROW,   34.0, 5.0, 0.38, 1.0, 1.80F, 0.80F,  8),
    UMBRAL_BINDER       ("umbral_binder",       Kind.BIND,     48.0, 3.0, 0.26, 2.0, 1.15F, 2.45F, 14),
    FROST_WEAVER        ("frost_weaver",        Kind.FROST,    42.0, 5.0, 0.32, 1.0, 1.80F, 0.80F, 11),
    ASH_MONK            ("ash_monk",            Kind.SUMMON,   82.0, 6.0, 0.24, 3.0, 1.10F, 2.45F, 18),
    STEAM_SENTINEL      ("steam_sentinel",      Kind.OVERHEAT,124.0,10.0, 0.22, 8.0, 1.60F, 2.10F, 20),
    GRAVE_GRUB          ("grave_grub",          Kind.DETONATE, 30.0, 4.0, 0.30, 0.0, 1.25F, 0.80F,  7),
    // ---- 经典地城怪：玩家一眼就认得的那几种 ----
    GOBLIN_SKIRMISHER   ("goblin_skirmisher",   Kind.POUNCE,   28.0, 5.0, 0.35, 0.0, 1.05F, 1.65F,  8),
    KOBOLD_MINER        ("kobold_miner",        Kind.POT,      32.0, 5.0, 0.32, 1.0, 1.10F, 1.85F,  9),
    ORC_BRUTE           ("orc_brute",           Kind.WHIRL,    96.0,11.0, 0.25, 6.0, 1.65F, 2.45F, 16),
    MINOTAUR_WARDEN     ("minotaur_warden",     Kind.CHARGE,  112.0,12.0, 0.26, 6.0, 1.65F, 2.40F, 18),
    TENTACLE_HORROR     ("tentacle_horror",     Kind.GRAB,     64.0, 6.0, 0.29, 2.0, 1.25F, 1.60F, 12);

    /** 攻击方式：门槛 / 前摇 / 后摇 / 冷却全在这里，动画与它对齐。 */
    public enum Kind {
        /** 低头蓄力 → 直线冲撞；撞墙自晕（{@code TNDungeonMob#stun}）。 */
        CHARGE  (false,  6.0, 22, 12,  70, "attack"),
        /** 甩钩把目标拽过来。 */
        HOOK    (false,  8.0, 12, 14,  60, "attack"),
        /** 跃击：起跳扑脸。 */
        POUNCE  (false,  6.0, 10, 12,  50, "attack"),
        /** 三连弩：第三发穿透。 */
        VOLLEY  (true,  18.0, 14, 12,  55, "attack"),
        /** 抛物线火罐：落地炸开一片火。 */
        POT     (true,  14.0, 16, 14,  70, "attack"),
        /** 直线蓄能射线：命中留灼痕。 */
        BEAM    (true,  16.0, 20, 10,  80, "attack"),
        /** 钻地 → 从目标脚边冒出。 */
        BURROW  (false, 12.0, 10, 20,  90, "attack"),
        /** 暗影锁链：定身 + 减速。 */
        BIND    (false, 10.0, 14, 12,  80, "attack"),
        /** 冰霜喷吐：锥形减速 + 冻结。 */
        FROST   (false,  7.0, 14, 12,  60, "attack"),
        /** 召唤两只傀儡（有存续时间）。 */
        SUMMON  (false,  8.0, 20, 16, 200, "attack"),
        /** 活塞重拳 + 积热；4 层过热停机（弱点）。 */
        OVERHEAT(false,  4.0, 14, 10,  40, "attack"),
        /** 自爆：膨胀后炸开，自己也没了。 */
        DETONATE(false,  3.5, 24,  6, 200, "attack"),
        /** 横扫：沉身蓄力后原地转一圈，360° 扫开身边所有人。 */
        WHIRL   (false,  4.5, 16, 14,  80, "attack"),
        /** 触手攻击：六条触手前伸（约 2.9 格）缠绕，把碰到的目标拖近并定身。 */
        GRAB    (false,  3.2, 16, 16,  90, "attack");

        public final boolean ranged;
        /** 起手距离（格）。 */
        public final double reach;
        /** 前摇 tick —— 到点才出伤害，和动画关键帧对齐。 */
        public final int windup;
        /** 后摇 tick —— 这段时间不能动，给玩家反打窗口。 */
        public final int recover;
        /** 冷却 tick。 */
        public final int cooldown;
        public final String anim;

        Kind(boolean ranged, double reach, int windup, int recover, int cooldown, String anim) {
            this.ranged = ranged;
            this.reach = reach;
            this.windup = windup;
            this.recover = recover;
            this.cooldown = cooldown;
            this.anim = anim;
        }

        public double reachSqr() {
            return reach * reach;
        }
    }

    public final String id;
    public final Kind kind;
    public final double health;
    public final double attackDamage;
    public final double speed;
    public final double armor;
    public final float width;
    public final float height;
    public final int xp;

    /** 实体类型在 {@code DungeonMobRegistration} 注册时回填（枚举自己不能注册，会循环初始化）。 */
    private RegistryObject<EntityType<TNDungeonMob>> type;

    DungeonSpecies(String id, Kind kind, double health, double attackDamage, double speed,
                   double armor, float width, float height, int xp) {
        this.id = id;
        this.kind = kind;
        this.health = health;
        this.attackDamage = attackDamage;
        this.speed = speed;
        this.armor = armor;
        this.width = width;
        this.height = height;
        this.xp = xp;
    }

    public RegistryObject<EntityType<TNDungeonMob>> type() {
        return type;
    }

    /** 由注册类调用一次：把实体类型绑回来。**这里只存引用**，不提前 {@code .get()} ✗。 */
    void bind(RegistryObject<EntityType<TNDungeonMob>> registered) {
        this.type = registered;
    }

    /**
     * 实体 → 物种。
     *
     * <p>用"逐个比较"而不是 {@code HashMap}：注册期 {@code RegistryObject#get()} 会抛
     * "registry not frozen"，所以反查表只能**运行时**才建。12 个元素，遍历的开销可以忽略。
     */
    public static DungeonSpecies of(EntityType<?> entityType) {
        if (entityType == null) {
            return null;
        }
        for (DungeonSpecies s : values()) {
            if (s.type != null && s.type.get() == entityType) {
                return s;
            }
        }
        return null;
    }

    public static DungeonSpecies byId(String id) {
        for (DungeonSpecies s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static List<DungeonSpecies> all() {
        List<DungeonSpecies> list = new ArrayList<>();
        for (DungeonSpecies s : values()) {
            list.add(s);
        }
        return list;
    }
}
