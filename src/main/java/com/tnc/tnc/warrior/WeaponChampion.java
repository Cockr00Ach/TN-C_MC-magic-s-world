package com.tnc.tnc.warrior;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Arena encounters for each codex branch at each challenge tier. */
public enum WeaponChampion {
    LONGSWORD("han_iron_jian", BossEvent.BossBarColor.BLUE, 0x91d8ff, 88, Items.DIAMOND_CHESTPLATE,
        "青霄 · 守剑人", "追锋横斩可振刀。架剑时正面普攻会引出抢拍反斩，用破振刀拆架。"),
    GREATSWORD("greatsword", BossEvent.BossBarColor.RED, 0xff7740, 115, Items.NETHERITE_CHESTPLATE,
        "赤阙 · 镇关重剑", "横移让重劈砍空，剑嵌地时用蓄力或主攻追击。"),
    HAMMER("hammer", BossEvent.BossBarColor.YELLOW, 0xffdc67, 104, Items.GOLDEN_CHESTPLATE,
        "鸣铸 · 雷砧", "熊锤贴地传震，踩准落锤起跳就能越过；躲过末圈再追击。"),
    SPEAR("bronze_spear", BossEvent.BossBarColor.GREEN, 0x67edb1, 94, Items.IRON_CHESTPLATE,
        "回雁 · 走阵枪师", "猿师每枪都沿枪路扑进，回马枪冲得最远。横移放它扑空，转身追击。"),
    HALBERD("single_crescent_halberd", BossEvent.BossBarColor.WHITE, 0xeecb85, 106, Items.GOLDEN_CHESTPLATE,
        "断岳 · 月戟守将", "戟将正面有甲，沿十字空隙绕到侧翼，收招时横扫破甲。"),
    SCYTHE("iron_crescent_scythe", BossEvent.BossBarColor.PURPLE, 0xd18aff, 96, Items.CHAINMAIL_CHESTPLATE,
        "蚀月 · 鬼镰", "外圈镰刃命中会把你勾向圈心；主动贴进内圈，用周身斩截住收割。"),
    BOW("storm_bow", BossEvent.BossBarColor.PINK, 0xf898aa, 84, Items.LEATHER_CHESTPLATE,
        "逐霞 · 雁翎射手", "游隼近身疾退。它举弓时追进两格半，可逼它弃射失衡。"),
    LONGSWORD_ELITE(LONGSWORD, "willow_leaf_dao", 0xd8db92, 138,
        "柳藏锋 · 折枝刀师", "正面普攻会诱使双回锋变架，接破振刀拆架；也可等它出刀再振。", Strike.TWIN_CUT),
    GREATSWORD_ELITE(GREATSWORD, "monk_spade", 0xe7a960, 162,
        "行峦 · 护院禅师", "禅铲第一落把人挑开，第二落横切退路。侧闪第二落，趁铲头陷地追击。", Strike.SPADE_FORK),
    HAMMER_ELITE(HAMMER, "earthshaker_hammer", 0xf0c778, 154,
        "石砺 · 镇窑锤师", "背鳞能挡重击。绕到面前，趁它蓄震以破振刀掀开护鳞。", Strike.ROLLING_QUAKE),
    SPEAR_ELITE(SPEAR, "red_tassel_spear", 0xf08a75, 141,
        "韩追风 · 红缨教头", "白鹤盘空避刃，收翼落地才连刺。近身振刀使它坠地长晕。", Strike.TURNING_THRUST),
    HALBERD_ELITE(HALBERD, "fangtian_ji", 0xe9c893, 158,
        "卫承岳 · 方天戟督", "第一重十字后开始转戟。第二重落下前松开蓄力，就能震开转轴。", Strike.PIVOT_CROSS),
    SCYTHE_ELITE(SCYTHE, "doom_scythe", 0xcdacee, 145,
        "沈乌 · 照夜镰客", "夜枭绕侧合镰。末钩落下时回头盯住它，能识破伏击，让它失衡。", Strike.CLOSING_REAP),
    BOW_ELITE(BOW, "moon_bow", 0xa9d9fc, 128,
        "乌弦 · 望月弓师", "三轮斜箭锁住最初落点。走出当前箭路，回射一箭可以断弦。", Strike.ARROW_CURTAIN),
    LONGSWORD_MASTER(LONGSWORD, "cloudridge_jian", 0xb5f0df, 197, 4,
        "陆听云 · 云门剑宗", "鹿师叠锋时连续侧踏。它踏出第一步后，从侧面命中能让它失足。", Strike.CLOUD_EDGES),
    GREATSWORD_MASTER(GREATSWORD, "crescent_guandao", 0xffac71, 235, 4,
        "纪横山 · 镇江刀侯", "虎师举刀力劈华山，挨上一刀会损失大量生命。横移躲锋，趁拔刀长后摇追击。", Strike.MOON_DIVIDE),
    HAMMER_MASTER(HAMMER, "ring_flange_hammer", 0xffd291, 224, 4,
        "洪铸岳 · 九环宗匠", "钟震内圈会把人推向下一轮外震。被推出要折返，末响落空可追击。", Strike.BELL_STEPS),
    SPEAR_MASTER(SPEAR, "silver_hook_spear", 0xdceaff, 205, 4,
        "许照川 · 银钩枪王", "银钩命中会把你拖向双枪中线。穿过中缝后留力躲末扫，钩空即破势。", Strike.SILVER_GATE),
    HALBERD_MASTER(HALBERD, "black_iron_halberd", 0xfed29a, 228, 4,
        "秦镇野 · 玄铁都督", "井字戟阵护住正面。两次正面蓄力可永久击碎本场胸甲，也可绕后追击。", Strike.IRON_GRID),
    SCYTHE_MASTER(SCYTHE, "white_bone_scythe", 0xd3bdff, 212, 4,
        "白无声 · 骨镰门主", "骨镰旋得越来越快，每次命中回 5 点生命。贴进内圈截断吸血。", Strike.BONE_ORBIT),
    BOW_MASTER(BOW, "laminated_horn_bow", 0xa8e8d0, 192, 4,
        "拓跋霜 · 穿云射师", "外环束足箭会短暂减速。先避外环，再离开网心；末箭落空时追射。", Strike.HORN_CAGE),
    LONGSWORD_LEGEND(LONGSWORD, "dragon_sword", 0xaee9e3, 265, 5,
        "绥白 · 雪渡剑君", "雪狐折步重新瞄准。首轮分锋后，从背面命中可破雪踪；正面可振刀。", Strike.FOX_TRAIL),
    GREATSWORD_LEGEND(GREATSWORD, "crimson_warblade", 0xff936b, 314, 5,
        "彭饮霞 · 丹炉刀祖", "前两道横切每命中一次就蓄一层炉火，末刀随层数加重。侧闪末刀追击。", Strike.FURNACE_CUT),
    HAMMER_LEGEND(HAMMER, "thunder_bronze_hammer", 0x8bd8be, 326, 5,
        "负岳 · 铜雷玄龟", "合甲时刀枪难入。趁蓄雷贴身破振刀，或收锤时蓄力开甲。", Strike.SHELL_RESONANCE),
    SPEAR_LEGEND(SPEAR, "coiled_dragon_spear", 0x8bd1ec, 272, 5,
        "解流泉 · 盘龙枪圣", "曲枪先向一侧、再向反侧卷人，末枪变直。近身振住末枪即可破势。", Strike.DRAGON_WEAVE),
    HALBERD_LEGEND(HALBERD, "sky_halberd", 0xf3d498, 302, 5,
        "燕玄圭 · 司天戟正", "先避斜十字。正十字亮起时踏回发光阵眼，站稳 0.3 秒即可破阵。", Strike.STAR_GATE),
    SCYTHE_LEGEND(SCYTHE, "eclipse_scythe", 0xbbdf83, 268, 5,
        "碧裁 · 翠螳镰隐", "螳螂侧钩虚晃，合钳那一瞬才露出双臂。振住合钳让它长时间失衡。", Strike.MANTIS_HOOK),
    BOW_LEGEND(BOW, "ember_bow", 0xffa878, 258, 5,
        "丹翎 · 朱羽神射", "火羽纵横封路，命中会点燃三秒。两轮全部落空，朱羽就要收翼换羽。", Strike.CINDER_PLUMES);

    final String weapon;
    final BossEvent.BossBarColor barColor;
    final int color;
    final int health;
    final Item armor;
    public final String title;
    public final String hint;
    private final WeaponChampion base;
    private final Strike signature;
    private final int rank;

    WeaponChampion(String weapon, BossEvent.BossBarColor barColor, int color, int health, Item armor,
                   String title, String hint) {
        this.weapon = weapon;
        this.barColor = barColor;
        this.color = color;
        this.health = health;
        this.armor = armor;
        this.title = title;
        this.hint = hint;
        this.base = this;
        this.signature = null;
        this.rank = 2;
    }

    WeaponChampion(WeaponChampion base, String weapon, int color, int health, String title, String hint, Strike signature) {
        this(base, weapon, color, health, 3, title, hint, signature);
    }

    WeaponChampion(WeaponChampion base, String weapon, int color, int health, int rank, String title, String hint, Strike signature) {
        this.weapon = weapon;
        this.barColor = base.barColor;
        this.color = color;
        this.health = health;
        this.armor = base.armor;
        this.title = title;
        this.hint = hint;
        this.base = base;
        this.signature = signature;
        this.rank = rank;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_champion";
    }

    public WarriorWeaponRanks.Branch branch() {
        return WarriorWeaponRanks.Branch.valueOf(base.name());
    }

    public int rank() {
        return rank;
    }

    public float height() {
        return switch (this) {
            case HAMMER_LEGEND -> 3.2f;
            case HAMMER_MASTER -> 3.05f;
            case HAMMER -> 2.9f;
            case GREATSWORD_MASTER -> 2.65f;
            case SPEAR_ELITE -> 2.45f;
            case LONGSWORD_MASTER, HALBERD_MASTER, GREATSWORD_LEGEND -> 2.3f;
            case GREATSWORD, GREATSWORD_ELITE, HALBERD_ELITE, HALBERD_LEGEND -> 2.15f;
            case SCYTHE_LEGEND -> 2.05f;
            case HAMMER_ELITE, SPEAR -> 1.95f;
            case BOW, SCYTHE_ELITE -> 1.65f;
            case LONGSWORD_LEGEND, BOW_MASTER -> 1.8f;
            case BOW_LEGEND -> 2.25f;
            default -> 1.9f;
        };
    }

    public float width() {
        return switch (this) {
            case HAMMER_LEGEND -> 2.05f;
            case HAMMER, HAMMER_MASTER -> 1.55f;
            case GREATSWORD_MASTER, HAMMER_ELITE -> 1.15f;
            case SPEAR_ELITE -> .72f;
            default -> height() * .39f;
        };
    }

    public float scale() { return height() / 2f; }

    double attackScale(Strike move) {
        double weapon = switch (branch()) {
            case SPEAR, HALBERD -> 1.18;
            case GREATSWORD, HAMMER, SCYTHE -> 1.10;
            default -> 1;
        };
        return weapon * (1 + .25 * (rank - 2)) * (move.isUltimate() ? 1.20 : 1);
    }

    public static WeaponChampion forBranch(WarriorWeaponRanks.Branch branch, int rank) {
        return valueOf(branch.name() + switch (rank) {
            case 2 -> "";
            case 3 -> "_ELITE";
            case 4 -> "_MASTER";
            case 5 -> "_LEGEND";
            default -> throw new IllegalArgumentException("Unknown champion rank " + rank);
        });
    }

    public String selectionKey() {
        return switch (rank) {
            case 4 -> base.name() + "_M";
            case 5 -> base.name() + "_L";
            default -> name();
        };
    }

    public static WeaponChampion fromSelection(String key) {
        for (var champion : values()) {
            if (champion.selectionKey().equals(key)) return champion;
        }
        return null;
    }

    public Component displayName() {
        return Component.translatable("entity.tnc." + id());
    }

    public Component weaponName() {
        return Component.translatable("item.tnc." + weapon);
    }

    public int color() {
        return color;
    }

    boolean opens(WarriorSkill skill, boolean casting, boolean recovery, boolean charged,
                  double distance, double facing) {
        if (skill == null) return false;
        if (this == SPEAR_ELITE || this == HAMMER_ELITE && armored(facing)) return false;
        if (this == HAMMER_LEGEND && recovery && charged) return true;
        if (this == HALBERD_MASTER && recovery && facing < -.55 && skill.slot() == 0) return true;
        boolean breaker = skill.role().breaksStance();
        return switch (base) {
            case LONGSWORD -> casting && breaker;
            case GREATSWORD -> recovery && (charged || skill.slot() == 0);
            case HAMMER -> casting && distance <= 3.2 && (breaker || skill.fullCircle());
            case SPEAR -> recovery && distance >= 2.5
                && (breaker || skill.slot() == 0 || skill.animation() == WarriorAnimation.SPEAR_DRAGON);
            case HALBERD -> recovery && Math.abs(facing) < .62 && (skill.arcDegrees() >= 100 || breaker);
            case SCYTHE -> casting && distance <= 2.6 && (skill.arcDegrees() >= 150 || breaker);
            case BOW -> (casting || recovery) && skill.ranged() && (distance >= 4 || skill.evadeTicks() > 0);
            default -> throw new IllegalStateException("Unexpected base champion " + base);
        };
    }

    public enum Strike {
        SWORD("追锋横斩", 25), SWORD_GUARD("回锋架势", 32),
        CLEAVE("镇关重劈", 36), BACKSWING("回身横扫", 22),
        QUAKE("三叠雷砧", 40), INNER_QUAKE("沉锤震心", 23),
        THRUST("探步突刺", 29), RETURN_THRUST("回马枪", 21),
        CROSS("十字断岳", 38), HALBERD_SWEEP("卷旗横扫", 24),
        REAP("蚀月收割", 34), SCYTHE_CORE("勾魂回镰", 24),
        VOLLEY("雁翎箭阵", 30), QUICK_VOLLEY("回身连发", 19),
        TWIN_CUT("柳枝双回锋", 28), SPADE_FORK("分山双落", 39),
        ROLLING_QUAKE("走地三震", 37), TURNING_THRUST("红缨三照", 33),
        PIVOT_CROSS("方天转阵", 37), CLOSING_REAP("闭月回镰", 34), ARROW_CURTAIN("斜月箭幕", 33),
        CLOUD_EDGES("云门叠锋", 35), MOON_DIVIDE("偃月分江", 43), BELL_STEPS("九环回震", 42),
        SILVER_GATE("银钩锁门", 38), IRON_GRID("玄铁井阵", 43),
        BONE_ORBIT("白骨环镰", 38), HORN_CAGE("角弓覆阵", 41),
        FOX_TRAIL("踏雪分锋", 36), FURNACE_CUT("炉火截江", 42), SHELL_RESONANCE("龟甲鸣雷", 42),
        DRAGON_WEAVE("盘龙折水", 36), STAR_GATE("斗转天门", 40), MANTIS_HOOK("双钩合璧", 37),
        CINDER_PLUMES("朱羽燎原", 40),
        SWORD_RAIN("万剑归鞘", "先躲两道斜剑，末段横跨中线。"),
        MOUNTAIN_FALL("天岳倾", "先横移躲刀脊，再钻进震环内侧。"),
        THUNDER_TIDE("三叠雷潮", "雷圈从外向内收，趁圈间空隙向外穿。"),
        DRAGON_LANCES("九龙贯阵", "左右枪列逐次封路，留意最后的中枪。"),
        SKY_LATTICE("八门镇天", "正十字、斜十字后封外环，末段回圈心。"),
        ECLIPSE_WHEEL("三轮蚀月", "镰轮依次收拢，离开内轮可避末斩。"),
        METEOR_VOLLEY("星落九霄", "箭雨先封两翼再封纵列，最后落在阵心。"),
        COMBO_FINISH("收式", 16);

        final String title;
        final int windup;
        final String ultimateHint;

        Strike(String title, int windup) {
            this.title = title;
            this.windup = windup;
            this.ultimateHint = null;
        }

        Strike(String title, String ultimateHint) {
            this.title = title;
            this.windup = 38;
            this.ultimateHint = ultimateHint;
        }

        public boolean isUltimate() {
            return ultimateHint != null;
        }

        boolean hits(Vec3 origin, Vec3 forward, Vec3 point) {
            return hits(origin, forward, point, 3);
        }

        boolean hits(Vec3 origin, Vec3 forward, Vec3 point, int wavesLeft) {
            Vec3 offset = point.subtract(origin).multiply(1, 0, 1);
            double distance = offset.length();
            double front = offset.dot(forward);
            double side = Math.abs(offset.x * forward.z - offset.z * forward.x);
            return switch (this) {
                case COMBO_FINISH -> distance <= 4; // Arc is selected by the equipped weapon in WeaponChampionCue.
                case SWORD -> distance <= 3.6 && front >= distance * .20;
                case SWORD_GUARD -> distance <= 3.6 && front >= distance * .40;
                case CLEAVE -> front >= -.8 && front <= 9 && side <= 1.55;
                case BACKSWING, HALBERD_SWEEP -> distance <= 4.8 && front >= -distance * .35;
                case QUAKE -> switch (wavesLeft) {
                    case 3 -> distance >= 3.65 && distance <= 6.8;
                    case 2 -> distance <= 3.2;
                    default -> distance >= 2.15 && distance <= 5.3;
                };
                case INNER_QUAKE, SCYTHE_CORE -> distance <= 3;
                case THRUST -> front >= 0 && front <= 9 && side <= .72;
                case RETURN_THRUST -> front >= 0 && front <= 8 && side <= 1.03;
                case CROSS -> distance <= 8 && (side <= 1.05 || Math.abs(front) <= 1.05);
                case REAP -> distance > 2.6 && distance <= 6.4;
                case VOLLEY, QUICK_VOLLEY -> distance <= 2.35;
                case TWIN_CUT -> distance <= 3.9 && front >= distance * (wavesLeft == 2 ? .2 : -.28);
                case SPADE_FORK -> front >= 0 && front <= 8.15 && side <= (wavesLeft == 2 ? .95 : 1.35);
                case ROLLING_QUAKE -> {
                    double inner = (3 - wavesLeft) * 2.05;
                    yield distance >= inner && distance <= inner + 2.35;
                }
                case TURNING_THRUST -> front >= 0 && front <= 9.3 && side <= (wavesLeft == 1 ? .88 : .64);
                case PIVOT_CROSS -> distance <= 8 && (side <= .86 || Math.abs(front) <= .86);
                case CLOSING_REAP -> distance > (wavesLeft == 2 ? 3.1 : 2.35) && distance <= 6.5
                    && front < distance * .84;
                case ARROW_CURTAIN -> Math.abs(front) <= (wavesLeft == 1 ? 1.38 : 1.04) && side <= 5.7;
                case CLOUD_EDGES -> distance <= 5.5 && front >= distance * (wavesLeft == 1 ? -.18 : .27);
                case MOON_DIVIDE -> wavesLeft == 2 ? distance <= 6.1 && front >= -distance * .24
                    : front >= -.7 && front <= 10 && side <= 1.45;
                case BELL_STEPS -> switch (wavesLeft) {
                    case 4, 2 -> distance <= 3.5;
                    case 3 -> distance >= 4 && distance <= 7.7;
                    default -> distance >= 3.9 && distance <= 6.5;
                };
                case SILVER_GATE -> wavesLeft == 1 ? distance <= 5.6 && front >= -distance * .18
                    : front >= 0 && front <= 9.5 && Math.abs(side - 1.55) <= .73;
                case IRON_GRID -> distance <= 8.5 && switch (wavesLeft) {
                    case 3 -> Math.abs(side - 2.6) < 1;
                    case 2 -> Math.abs(Math.abs(front) - 2.6) < 1;
                    default -> distance <= 3.4;
                };
                case BONE_ORBIT -> distance >= 3 && distance <= 7.6 && front <= distance * .69;
                case HORN_CAGE -> wavesLeft == 1 ? distance <= 2.55 : distance >= 3.35 && distance <= 6.3;
                case FOX_TRAIL -> wavesLeft == 1 ? distance <= 6.4 && front >= 0
                    : front >= 0 && front <= 9 && Math.abs(side - (wavesLeft == 3 ? front : 9 - front) * .42) <= .82;
                case FURNACE_CUT -> wavesLeft == 1 ? front >= 0 && front <= 9 && side <= 1.3
                    : side <= 6.2 && Math.abs(front - (wavesLeft == 3 ? 5.6 : 2.6)) <= 1;
                case SHELL_RESONANCE -> Math.abs(Math.abs(front) + side - (4 - wavesLeft) * 2.2) <= 1.1;
                case DRAGON_WEAVE -> {
                    double across = offset.x * forward.z - offset.z * forward.x;
                    double curve = switch (wavesLeft) {
                        case 3 -> 2.1 * Math.sin(front * .63);
                        case 2 -> -2.1 * Math.sin(front * .63);
                        default -> 0;
                    };
                    yield front >= 0 && front <= 10 && Math.abs(across - curve) < .92;
                }
                case STAR_GATE -> distance <= 8 && (wavesLeft == 2 ? Math.abs(Math.abs(front) - side) <= 1.3
                    : Math.abs(front) <= .9 || side <= .9);
                case MANTIS_HOOK -> wavesLeft == 1 ? front >= 0 && front <= 6.3 && side <= 1.25
                    : Math.hypot(front - 3.7, side - 3.4) < 2.15;
                case CINDER_PLUMES -> {
                    double lane = wavesLeft == 2 ? side : Math.abs(front);
                    yield distance <= 6.2 && (lane <= .68 || Math.abs(lane - 3.7) <= .68);
                }
                case SWORD_RAIN -> {
                    double signedSide = offset.x * forward.z - offset.z * forward.x;
                    double slope = wavesLeft == 3 ? .64 : wavesLeft == 2 ? -.64 : 0;
                    yield Math.abs(front) <= 8 && Math.abs(signedSide - slope * front) <= (wavesLeft == 1 ? 1.55 : .95);
                }
                case MOUNTAIN_FALL -> wavesLeft == 2 ? front >= -1 && front <= 10 && side <= 1.8
                    : distance >= 3 && distance <= 7.7;
                case THUNDER_TIDE -> Math.abs(distance - (wavesLeft - 1) * 3.2) <= (wavesLeft == 1 ? 2.2 : 1.25);
                case DRAGON_LANCES -> {
                    double signedSide = offset.x * forward.z - offset.z * forward.x;
                    yield front >= -.8 && front <= 10 && Math.abs(signedSide - (wavesLeft == 3 ? 2.8 : wavesLeft == 2 ? -2.8 : 0)) <= 1.05;
                }
                case SKY_LATTICE -> distance <= 8 && switch (wavesLeft) {
                    case 3 -> Math.min(side, Math.abs(front)) <= .95;
                    case 2 -> Math.abs(Math.abs(front) - side) <= 1.15;
                    default -> distance >= 3.1;
                };
                case ECLIPSE_WHEEL -> Math.abs(distance - wavesLeft * 2.1) <= 1.0;
                case METEOR_VOLLEY -> distance <= 7.8 && switch (wavesLeft) {
                    case 3 -> Math.abs(Math.abs(front) - 3.6) <= 1.2;
                    case 2 -> Math.abs(side - 2.4) <= 1.15;
                    default -> distance <= 3;
                };
            };
        }

        int waves() {
            if (isUltimate()) return this == MOUNTAIN_FALL ? 2 : 3;
            return switch (this) {
                case BELL_STEPS -> 4;
                case QUAKE, VOLLEY, ROLLING_QUAKE, TURNING_THRUST, ARROW_CURTAIN,
                    CLOUD_EDGES, SILVER_GATE, IRON_GRID, BONE_ORBIT, HORN_CAGE -> 3;
                case FOX_TRAIL, FURNACE_CUT, SHELL_RESONANCE, DRAGON_WEAVE -> 3;
                case QUICK_VOLLEY, TWIN_CUT, SPADE_FORK, PIVOT_CROSS, CLOSING_REAP, MOON_DIVIDE,
                    STAR_GATE, MANTIS_HOOK, CINDER_PLUMES -> 2;
                default -> 1;
            };
        }
    }

    Strike strike(boolean followUp) {
        return switch (base) {
            case LONGSWORD -> followUp ? Strike.SWORD_GUARD : Strike.SWORD;
            case GREATSWORD -> followUp ? Strike.BACKSWING : Strike.CLEAVE;
            case HAMMER -> followUp ? Strike.INNER_QUAKE : Strike.QUAKE;
            case SPEAR -> followUp ? Strike.RETURN_THRUST : Strike.THRUST;
            case HALBERD -> followUp ? Strike.HALBERD_SWEEP : Strike.CROSS;
            case SCYTHE -> followUp ? Strike.SCYTHE_CORE : Strike.REAP;
            case BOW -> followUp ? Strike.QUICK_VOLLEY : Strike.VOLLEY;
            default -> throw new IllegalStateException("Unexpected base champion " + base);
        };
    }

    Strike strikeForRound(int round, boolean late) {
        if (this == GREATSWORD_MASTER) return round % 3 == 2 || late && round % 3 == 0 ? signature : Strike.CLEAVE;
        if (signature == null) return strike(late ? round % 2 == 1 : round % 4 == 3);
        if (round == 0) return signature;
        return switch (round % 3) {
            case 0 -> late ? signature : strike(false);
            case 1 -> strike(true);
            default -> signature;
        };
    }

    boolean canInterrupt(Strike strike, int wave) {
        if (strike == Strike.MANTIS_HOOK) return wave == 1;
        return switch (branch()) {
            case LONGSWORD -> strike == Strike.SWORD_GUARD;
            case BOW -> true;
            default -> strike == signature || strike == strike(false);
        };
    }

    Strike ultimate() {
        return switch (branch()) {
            case LONGSWORD -> Strike.SWORD_RAIN;
            case GREATSWORD -> Strike.MOUNTAIN_FALL;
            case HAMMER -> Strike.THUNDER_TIDE;
            case SPEAR -> Strike.DRAGON_LANCES;
            case HALBERD -> Strike.SKY_LATTICE;
            case SCYTHE -> Strike.ECLIPSE_WHEEL;
            case BOW -> Strike.METEOR_VOLLEY;
        };
    }

    public String ultimateTitle() {
        return rank >= 4 ? ultimate().title : "";
    }

    boolean ultimateReady(int normalAttacks, boolean late) {
        return rank >= 4 && normalAttacks >= 7 - rank - (late ? 1 : 0);
    }

    int approachTicks(boolean late) {
        return (late ? 11 : 16) - (rank - 2) * 2;
    }

    int windupTicks(Strike move) {
        return move.isUltimate() ? move.windup : Math.max(10, move.windup - 5 - (rank - 2) * 2);
    }

    boolean heavyBlow(Strike strike, int wave) {
        return switch (this) {
            case GREATSWORD_MASTER -> true;
            case GREATSWORD_LEGEND -> strike == Strike.FURNACE_CUT && wave == 1;
            case HAMMER_MASTER -> strike == Strike.BELL_STEPS && wave == 1;
            default -> false;
        };
    }

    int recoveryTicks() {
        if (this == GREATSWORD_MASTER) return 98;
        if (this == SPEAR_ELITE) return 19;
        return 24 - (rank - 2) * 5;
    }

    int waveInterval(Strike strike, int wavesLeft) {
        if (strike.isUltimate()) return rank == 5 ? 16 : 20;
        return switch (strike) {
            case TWIN_CUT -> 12;
            case BONE_ORBIT -> wavesLeft == 2 ? 17 : 11;
            case MOON_DIVIDE -> 30;
            case MANTIS_HOOK -> 14;
            case STAR_GATE -> 20; // Leave time to step back into the eye and hold it for six ticks.
            default -> Math.max(11, (strike == Strike.QUAKE ? 18 : 17) - (rank - 2) * 2);
        };
    }

    boolean exposedOnMiss(Strike strike) {
        return switch (this) {
            case GREATSWORD -> strike == Strike.CLEAVE;
            case HAMMER -> strike == Strike.QUAKE;
            case SPEAR -> strike == Strike.RETURN_THRUST;
            case GREATSWORD_ELITE -> strike == Strike.SPADE_FORK;
            case HALBERD_ELITE -> strike == Strike.PIVOT_CROSS;
            case SCYTHE_ELITE -> strike == Strike.CLOSING_REAP;
            case HAMMER_MASTER -> strike == Strike.BELL_STEPS;
            case SPEAR_MASTER -> strike == Strike.SILVER_GATE;
            case BOW_MASTER -> strike == Strike.HORN_CAGE;
            case GREATSWORD_LEGEND -> strike == Strike.FURNACE_CUT;
            case BOW_LEGEND -> strike == Strike.CINDER_PLUMES;
            default -> false;
        };
    }

    boolean parryOpens(Strike strike, int wave) {
        if (this == SPEAR_ELITE) return true;
        if (this == SPEAR_LEGEND) return strike == Strike.DRAGON_WEAVE && wave == 1;
        if (this == SCYTHE_LEGEND) return strike == Strike.MANTIS_HOOK && wave == 1;
        return branch() == WarriorWeaponRanks.Branch.LONGSWORD && strike != Strike.SWORD_GUARD;
    }

    boolean armored(double facing) {
        return switch (this) {
            case HAMMER_LEGEND -> true;
            case HAMMER_ELITE -> facing < -.30;
            case HALBERD, HALBERD_MASTER -> facing > .55;
            default -> false;
        };
    }
}
