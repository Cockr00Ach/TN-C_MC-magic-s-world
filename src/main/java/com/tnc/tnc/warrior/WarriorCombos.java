package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器连招目录。
 *
 * <p>每把武器三条链：<b>突击</b>（带位移）、<b>攻击</b>（高伤害）、<b>防御</b>（打断+僵直）。
 * 顺序、槽位、冷却、伤害、位移、动作全在这里；解锁门槛写在 {@link WarriorCodex} 的同 id 条目里。
 *
 * <p><b>目录不变量</b>（{@code WarriorCombosTest} 会盯着）：
 *
 * <ul>
 *   <li>id / 槽位唯一；</li>
 *   <li>同一把武器内，任何一条顺序都<b>不是</b>另一条的前缀或后缀 —— 否则同一串输入可能
 *       命中两招，"到底放哪招"就要靠更复杂的延迟判定；</li>
 *   <li>每条连招都必须在图鉴里有已实装条目，否则玩家永远不知道该怎么练；</li>
 *   <li>每条连招都要有武器家族和链 —— 判定、HUD、图鉴都靠这两个字段分组。</li>
 * </ul>
 */
public final class WarriorCombos {

    private WarriorCombos() {
    }

    private static final List<WarriorSkill> SKILLS = List.of(
            // 长剑 · 攻击链：三段剑势，站桩换血
            new WarriorSkill("sword_combo_1", "sword_combo_1", "破锋式",
                    WarriorMove.parse("qze"), 0, 80, 1.50f, 3.0, 80, .55, 2,
                    "左斩开势、横斩破架、右斩收锋，向前斩出三重剑势",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.SWORD_BREAKER,
                    List.of(false, false, false), 0, 0, 0),
            // 长剑 · 突击链：旋身突进，把近身敌人扫开
            new WarriorSkill("sword_combo_2", "sword_combo_2", "旋转斩击",
                    WarriorMove.parse("qeqzzz"), 1, 140, .90f, 3.6, 360, .70, 3,
                    "向前突进半步后旋身一周，把近身的敌人全部扫开",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.ASSAULT, 1.35, false, false, 0,
                    WarriorAnimation.SWORD_SPIN,
                    List.of(false, false, false, false, false, false), 0, 0, 0),
            // 长剑 · 防御链（长连）：横斩起手、压身、回身重劈，打断并压出长僵直
            new WarriorSkill("sword_combo_3", "sword_combo_3", "逆鳞斩",
                    WarriorMove.parse("zzqqee"), 2, 200, 1.10f, 3.2, 100, 1.00, 4,
                    "横斩两次诱敌、双左斩压身、双右斩回身重劈；命中会打断施法并造成僵直",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.DEFENSE, 0.35, false, true, 40,
                    WarriorAnimation.SWORD_COUNTER,
                    List.of(false, false, false, false, false, false), 0, 0, 0),
            // 长剑 · 防御链（短连 + 长按）：E Q 后按住 E 半秒，摆出架势振刀
            //   （用户 2026-11-02：结尾不能再是长按 Z，会和别的链抢输入，改成"长按 E"）
            new WarriorSkill("sword_combo_4", "sword_combo_4", "架势·振刀",
                    WarriorMove.parse("eqe"), 3, 160, .60f, 3.0, 120, .40, 2,
                    "右斩开势、左斩压身，再按住右斩半秒架刀：震开身前的攻击，打断施法并压出僵直",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.DEFENSE, 0.35, false, true, 50,
                    WarriorAnimation.SWORD_STANCE,
                    List.of(false, false, true), 40, 0, 0),
            // 重剑 · 突击链：不位移，用大范围横扫代替突进（用户指定"重剑可以不做位移"）
            new WarriorSkill("greatsword_combo_1", "greatsword_combo_1", "破阵斩",
                    WarriorMove.parse("zqe"), 0, 180, 1.10f, 4.0, 140, .90, 3,
                    "横斩起手、左斩压上、右斩收势，大范围横扫身前",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.ASSAULT, 0.60, false, false, 0,
                    WarriorAnimation.GS_SWEEP,
                    List.of(false, false, false), 0, 0, 0),
            // 重剑 · 攻击链：过顶重劈
            new WarriorSkill("greatsword_combo_2", "greatsword_combo_2", "山崩",
                    WarriorMove.parse("qqz"), 1, 220, 2.00f, 3.2, 90, 1.10, 4,
                    "双左斩蓄势后过顶重劈，伤害最高、前摇也最长",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.GS_SMASH,
                    List.of(false, false, false), 0, 0, 0),
            // 重剑 · 防御链：架剑前顶（破振刀）
            new WarriorSkill("greatsword_combo_3", "greatsword_combo_3", "铁壁",
                    WarriorMove.parse("eqz"), 2, 240, .80f, 3.0, 100, .40, 2,
                    "右斩拨开、左斩压进、横斩前顶：打断施法/连招并压出僵直，也专门破振刀",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.DEFENSE, 0.35, false, true, 60,
                    WarriorAnimation.GS_WALL,
                    List.of(false, false, false), 30, 0, 0),
            // 重剑 · 防御链（架势）：竖剑当门，架住攻击后把对手顶开
            new WarriorSkill("greatsword_combo_4", "greatsword_combo_4", "架势·壁立",
                    WarriorMove.parse("qee"), 3, 200, .70f, 3.0, 180, .90, 3,
                    "双左斩提剑、右斩翻刃成架势：两秒内架住身前的攻击，破防者反被震开",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.DEFENSE, 0.35, false, true, 40,
                    WarriorAnimation.GS_STANCE,
                    List.of(false, false, false), 36, 0, 0),
            // 战锤 · 突击链：不位移，靠范围压制
            new WarriorSkill("hammer_combo_1", "hammer_combo_1", "碎骨",
                    WarriorMove.parse("qzz"), 0, 200, 1.20f, 3.4, 120, .80, 3,
                    "左斩起手、两记横斩压上，过顶砸击范围更宽",
                    WarriorWeaponFamily.HAMMER, WarriorChain.ASSAULT, 0.60, false, false, 0,
                    WarriorAnimation.HM_CRUSH,
                    List.of(false, false, false), 0, 0, 0),
            // 战锤 · 攻击链：砸地一圈
            new WarriorSkill("hammer_combo_2", "hammer_combo_2", "震地",
                    WarriorMove.parse("eez"), 1, 240, 1.90f, 4.0, 360, 1.20, 4,
                    "双右斩蓄力后砸地，震波扫开一圈敌人",
                    WarriorWeaponFamily.HAMMER, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.HM_QUAKE,
                    List.of(false, false, false), 0, 0, 0),
            // 战锤 · 防御链：短距离震击断招（破振刀）
            new WarriorSkill("hammer_combo_3", "hammer_combo_3", "断招",
                    WarriorMove.parse("zqq"), 2, 260, .70f, 3.6, 100, .50, 2,
                    "横斩诱敌、双左斩压身后一记短震：打断施法/连招并压出长僵直，也专门破振刀",
                    WarriorWeaponFamily.HAMMER, WarriorChain.DEFENSE, 0.35, false, true, 70,
                    WarriorAnimation.HM_CUT,
                    List.of(false, false, false), 0, 0, 0),
            // 战锤 · 防御链（架势）：横锤沉腰，架住攻击再把对手顶出去
            new WarriorSkill("hammer_combo_4", "hammer_combo_4", "架势·沉渊",
                    WarriorMove.parse("qeq"), 3, 220, .80f, 3.4, 120, 1.00, 4,
                    "左斩、右斩压身后横斩收锤：锤横胸前沉腰成架势，架住攻击并顶出僵直",
                    WarriorWeaponFamily.HAMMER, WarriorChain.DEFENSE, 0.35, false, true, 40,
                    WarriorAnimation.HM_STANCE,
                    List.of(false, false, false), 40, 0, 0),
            // 长弓 · 攻击链：射箭
            new WarriorSkill("bow_combo_1", "bow_combo_1", "穿云箭",
                    WarriorMove.parse("qz"), 0, 100, 1.60f, 1.0, 20, 0, 1,
                    "左斩开弓、横斩放箭：射出一支高伤害箭，不消耗背包里的箭",
                    WarriorWeaponFamily.BOW, WarriorChain.ATTACK, 0.55, true, false, 0,
                    WarriorAnimation.BOW_AIM,
                    List.of(false, false), 0, 0, 0),
            // 长弓 · 突击链：突进半步射击
            new WarriorSkill("bow_combo_2", "bow_combo_2", "疾影射",
                    WarriorMove.parse("eq"), 1, 120, 1.00f, 1.0, 20, 0, 1,
                    "右斩拉弓、左斩突进半步并放箭",
                    WarriorWeaponFamily.BOW, WarriorChain.ASSAULT, 1.0, true, false, 0,
                    WarriorAnimation.BOW_DASH_SHOT,
                    List.of(false, false), 0, 0, 0),
            // 长弓 · 闪避链：免疫伤害 + 后跃射击
            new WarriorSkill("bow_combo_3", "bow_combo_3", "后跃射",
                    WarriorMove.parse("ze"), 2, 160, .80f, 1.0, 20, 0, 1,
                    "横斩诱敌、右斩后跃：短时无敌并向后拉开距离，落地前放箭",
                    WarriorWeaponFamily.BOW, WarriorChain.EVADE, 0, true, false, 0,
                    WarriorAnimation.BOW_BACK_SHOT,
                    List.of(false, false), 0, 10, 2.5),
            // ------------------------------------------------------------------
            //  2026-11-02 扩写：每把武器再补两招，并加入长枪（风遗物长枪）
            //  规则：同一家族内不能有"一招是另一招的前缀/后缀"，所以新链都做成 3~4 段
            // ------------------------------------------------------------------
            // 长剑 · 闪避链：后撤半步反手回斩，保命用的
            new WarriorSkill("sword_combo_5", "sword_combo_5", "退步斩",
                    WarriorMove.parse("zqez"), 4, 150, .85f, 3.0, 130, .50, 2,
                    "横斩虚晃、左斩压身、右斩收势时向后撤半步，再横斩回身：短时无敌并拉开距离",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.EVADE, 0, false, false, 0,
                    WarriorAnimation.EVADE,
                    List.of(false, false, false, false), 0, 9, 1.8),
            // 长剑 · 攻击链（长连）：四段燕返，伤害最高
            new WarriorSkill("sword_combo_6", "sword_combo_6", "燕返",
                    WarriorMove.parse("ezzq"), 5, 130, 1.65f, 3.2, 150, .65, 2,
                    "右斩起手、两记横斩连压、最后一记左斩回身切：四段全中伤害最高",
                    WarriorWeaponFamily.LONGSWORD, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.SWORD_COUNTER,
                    List.of(false, false, false, false), 0, 0, 0),
            // 重剑 · 攻击链（长连）：裂地，比山崩多一段前摇，伤害更高
            new WarriorSkill("greatsword_combo_5", "greatsword_combo_5", "裂地",
                    WarriorMove.parse("qzzq"), 4, 280, 2.20f, 3.8, 220, 1.20, 5,
                    "左斩提剑、两记横斩扫开身前、最后一记左斩砸落：重剑伤害天花板",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.GS_SMASH,
                    List.of(false, false, false, false), 0, 0, 0),
            // 重剑 · 突击链：冲阵，唯一带位移的重剑招式
            new WarriorSkill("greatsword_combo_6", "greatsword_combo_6", "扫阵",
                    WarriorMove.parse("zzqq"), 5, 200, 1.15f, 3.8, 180, .95, 3,
                    "两记横斩开路、双左斩压上：往前趟半步扫一大片，靠范围把人罩住",
                    WarriorWeaponFamily.GREATSWORD, WarriorChain.ASSAULT, 0.60, false, false, 0,
                    WarriorAnimation.GS_SWEEP,
                    List.of(false, false, false, false), 0, 0, 0),
            // 战锤 · 攻击链（长连）：崩山，三连横砸
            new WarriorSkill("hammer_combo_5", "hammer_combo_5", "崩山",
                    WarriorMove.parse("zzz"), 4, 300, 2.10f, 4.2, 360, 1.40, 5,
                    "三记横砸连着抡下来，原地一圈全被震开：锤的最强一击",
                    WarriorWeaponFamily.HAMMER, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.HM_QUAKE,
                    List.of(false, false, false), 0, 0, 0),
            // 战锤 · 突击链：撞锤，短距离顶进去
            new WarriorSkill("hammer_combo_6", "hammer_combo_6", "顶锤",
                    WarriorMove.parse("qeeq"), 5, 220, 1.05f, 3.8, 150, .95, 3,
                    "左斩抢身、双右斩把锤头甩出去、最后一记左斩顶进去：往前顶半步，靠大范围把人砸退",
                    WarriorWeaponFamily.HAMMER, WarriorChain.ASSAULT, 0.60, false, false, 0,
                    WarriorAnimation.HM_CRUSH,
                    List.of(false, false, false, false), 0, 0, 0),
            // 长弓 · 攻击链（长连）：裂空箭，三段式蓄力，伤害最高
            new WarriorSkill("bow_combo_4", "bow_combo_4", "裂空箭",
                    WarriorMove.parse("zqq"), 3, 150, 2.10f, 1.0, 20, 0, 1,
                    "横斩虚引、两次左斩把弓拉满再放：一支贯穿的裂空箭，伤害最高",
                    WarriorWeaponFamily.BOW, WarriorChain.ATTACK, 0.55, true, false, 0,
                    WarriorAnimation.BOW_AIM,
                    List.of(false, false, false), 0, 0, 0),
            // 长弓 · 突击链：追风射，向左半步再放箭
            new WarriorSkill("bow_combo_5", "bow_combo_5", "追风射",
                    WarriorMove.parse("qqq"), 4, 130, 1.15f, 1.0, 20, 0, 1,
                    "三次左斩错步引弓、最后一记放箭：向左滑半步射出一箭，用来追侧身的敌人",
                    WarriorWeaponFamily.BOW, WarriorChain.ASSAULT, 1.2, true, false, 0,
                    WarriorAnimation.BOW_DASH_SHOT,
                    List.of(false, false, false), 0, 0, 0),
            // 长弓 · 突击链（速射）：连珠箭，三段手速越快箭越密
            new WarriorSkill("bow_combo_6", "bow_combo_6", "连珠箭",
                    WarriorMove.parse("qez"), 5, 120, 1.30f, 1.0, 20, 0, 1,
                    "左斩拉弦、右斩扣弦、横斩放箭：三下手速越快箭越密，不位移的连射",
                    WarriorWeaponFamily.BOW, WarriorChain.ASSAULT, 0.60, true, false, 0,
                    WarriorAnimation.BOW_DASH_SHOT,
                    List.of(false, false, false), 0, 0, 0),
            // 长枪 · 攻击链：破军刺，直线突刺，窄扇形但很长
            new WarriorSkill("spear_combo_1", "spear_combo_1", "破军刺",
                    WarriorMove.parse("qqz"), 0, 110, 1.60f, 3.9, 90, .55, 2,
                    "双左斩压枪、横斩送出去：一记直线突刺，扇形窄但枪长、打得更远",
                    WarriorWeaponFamily.SPEAR, WarriorChain.ATTACK, 0.55, false, false, 0,
                    WarriorAnimation.THRUST_DASH,
                    List.of(false, false, false), 0, 0, 0),
            // 长枪 · 突击链：龙牙突，长距离突进
            new WarriorSkill("spear_combo_2", "spear_combo_2", "龙牙突",
                    WarriorMove.parse("qzq"), 1, 160, 1.05f, 3.2, 80, .60, 2,
                    "左斩压枪、横斩起势、左斩送枪：向前突进一大步，枪尖先到",
                    WarriorWeaponFamily.SPEAR, WarriorChain.ASSAULT, 1.9, false, false, 0,
                    WarriorAnimation.THRUST_DASH,
                    List.of(false, false, false), 0, 0, 0),
            // 长枪 · 防御链：回马枪，扫开并打断
            new WarriorSkill("spear_combo_3", "spear_combo_3", "回马枪",
                    WarriorMove.parse("qqqe"), 2, 210, .95f, 3.5, 170, .85, 3,
                    "三连压枪诱敌、最后一记右斩回身横扫：打断施法/连招并压出僵直",
                    WarriorWeaponFamily.SPEAR, WarriorChain.DEFENSE, 0.35, false, true, 45,
                    WarriorAnimation.GUARD_BASH,
                    List.of(false, false, false, false), 0, 0, 0),
            // 长枪 · 架势：横栏，长按右斩把枪横在身前
            new WarriorSkill("spear_combo_4", "spear_combo_4", "架势·横栏",
                    WarriorMove.parse("qzeq"), 3, 170, .65f, 3.6, 150, .80, 3,
                    "左斩、横斩、右斩压枪，最后按住右斩半秒把枪横在身前：架住身前的攻击",
                    WarriorWeaponFamily.SPEAR, WarriorChain.DEFENSE, 0.35, false, true, 45,
                    WarriorAnimation.GS_STANCE,
                    List.of(false, false, false, true), 42, 0, 0),
            // 长枪 · 闪避链：游龙步，向后滑步再点一枪
            new WarriorSkill("spear_combo_5", "spear_combo_5", "游龙步",
                    WarriorMove.parse("ezq"), 4, 150, .70f, 3.2, 110, .35, 2,
                    "右斩点枪、横斩借力、左斩收枪向后滑步：短时无敌并拉开距离",
                    WarriorWeaponFamily.SPEAR, WarriorChain.EVADE, 0, false, false, 0,
                    WarriorAnimation.EVADE,
                    List.of(false, false, false), 0, 10, 2.2)).stream().map(WarriorPerformance::catalog).map(WarriorUnifiedSlots::apply).toList();

    /** 全部连招（按槽位顺序）。 */
    public static List<WarriorSkill> all() {
        return SKILLS;
    }

    /** 某个武器家族的全部连招。 */
    public static List<WarriorSkill> forFamily(WarriorWeaponFamily family) {
        List<WarriorSkill> out = new ArrayList<>();
        for (WarriorSkill skill : SKILLS) {
            if (skill.family() == family) {
                out.add(skill);
            }
        }
        out.sort(java.util.Comparator.comparingInt(WarriorSkill::slot));
        return out;
    }

    public static WarriorSkill byId(String id) {
        for (WarriorSkill skill : SKILLS) {
            if (skill.id().equals(id)) {
                return skill;
            }
        }
        return null;
    }

    public static WarriorSkill byCodexId(String codexId) {
        for (WarriorSkill skill : SKILLS) {
            if (skill.codexId().equals(codexId)) {
                return skill;
            }
        }
        return null;
    }

    /** 某个槽位上的连招；空槽返回 {@code null}。 */
    public static WarriorSkill bySlot(int slot) {
        for (WarriorSkill skill : SKILLS) {
            if (skill.slot() == slot) {
                return skill;
            }
        }
        return null;
    }

    /** 某族武器某个槽位上的连招；空槽返回 {@code null}。 */
    public static WarriorSkill bySlot(WarriorWeaponFamily family, int slot) {
        for (WarriorSkill skill : SKILLS) {
            if (skill.family() == family && skill.slot() == slot) {
                return skill;
            }
        }
        return null;
    }

    /** 某一页（0 基）的 9 个槽位，空槽是 {@code null}（默认按长剑算，兼容旧调用）。 */
    public static List<WarriorSkill> page(int page) {
        return page(WarriorWeaponFamily.LONGSWORD, page);
    }

    /** 某族武器某一页的 9 个槽位。 */
    public static List<WarriorSkill> page(WarriorWeaponFamily family, int page) {
        int base = Math.max(0, Math.min(WarriorSkill.PAGE_COUNT - 1, page)) * WarriorSkill.SLOTS_PER_PAGE;
        List<WarriorSkill> out = new ArrayList<>(WarriorSkill.SLOTS_PER_PAGE);
        for (int i = 0; i < WarriorSkill.SLOTS_PER_PAGE; i++) {
            out.add(bySlot(family, base + i));
        }
        return out;
    }

    /** 解锁判定：唯一口径是图鉴条目，和服务端面板读的是同一段代码。 */
    public static boolean unlocked(WarriorSkill skill, WarriorCodex.Progress progress) {
        if (skill == null || progress == null) {
            return false;
        }
        WarriorCodex.Entry entry = WarriorCodex.byId(skill.codexId());
        return entry != null && entry.implemented() && entry.met(progress);
    }

    /** 还没解锁时，面板/提示里要写"还差多少磨砺"；没有磨砺条件返回 -1。 */
    public static int temperRequirement(WarriorSkill skill) {
        WarriorCodex.Entry entry = skill == null ? null : WarriorCodex.byId(skill.codexId());
        if (entry == null) {
            return -1;
        }
        for (WarriorCodex.Clause clause : entry.gates()) {
            if (clause.gate() == WarriorCodex.Gate.TEMPER) {
                return clause.value();
            }
        }
        return -1;
    }

    /** 已解锁的连招（按槽位顺序），HUD 只画这些。 */
    public static List<WarriorSkill> unlocked(WarriorCodex.Progress progress) {
        return unlocked(WarriorWeaponFamily.LONGSWORD, progress);
    }

    /** 某族武器已解锁的连招。 */
    public static List<WarriorSkill> unlocked(WarriorWeaponFamily family, WarriorCodex.Progress progress) {
        List<WarriorSkill> out = new ArrayList<>();
        for (WarriorSkill skill : forFamily(family)) {
            if (unlocked(skill, progress)) {
                out.add(skill);
            }
        }
        return out;
    }
}
