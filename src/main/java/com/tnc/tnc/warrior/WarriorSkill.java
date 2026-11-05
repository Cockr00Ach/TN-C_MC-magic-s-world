package com.tnc.tnc.warrior;

import java.util.List;

/**
 * 一个武器连招终结技（"连招达成后自动衔接的那一招"）。
 *
 * <p><b>纯数据，不碰 Minecraft 类型</b>：服务端拿它结算伤害，客户端拿它画 HUD，单元测试
 * 直接构造它验证规则 —— 三边读的是同一份对象，不会出现"界面写 6 段、实际判定 5 段"这种错位。
 *
 * <p>解锁条件不在这里：它由 {@link #codexId()} 指向武器图鉴里的条目，
 * 判定统一走 {@link WarriorCodex.Entry#met(WarriorCodex.Progress)}。
 *
 * <p><b>链不是代码分支</b>：链只是默认值的来源，真正结算读的是
 * {@code dash / ranged / interrupt / staggerTicks / evadeTicks / backstep} 这几个字段，
 * 所以重剑的突击链可以 {@code dash=0}、弓的闪避链可以 {@code evadeTicks=10, backstep=2.5}，
 * 结算逻辑一行都不用改。每把武器至少要有<b>防御链或闪避链之一</b>（保命链）。
 *
 * @param id               稳定 id（网络包 / 测试用，<b>不要改</b>）
 * @param codexId          武器图鉴条目 id（门槛与说明的唯一来源）
 * @param name             显示名
 * @param sequence         要求的输入顺序，例如 {@code qeqzzz}
 * @param slot             技能槽位（0..17，与法术的 2 页 × 9 槽同一套位置）
 * @param cooldownTicks    终结技冷却（tick）
 * @param damageMultiplier 相对玩家攻击力的倍率
 * @param radius           作用半径（格）
 * @param arcDegrees       扇形角度；360 = 原地一圈
 * @param knockback        击退强度
 * @param durabilityCost   释放一次的武器耐久消耗
 * @param finisherText     一句话动作描述（面板/HUD 用）
 * @param family           这把武器属于哪一族（长剑/重剑/锤/弓）
 * @param chain            这条连招属于哪条链（突击/攻击/防御/闪避）
 * @param dash             释放时向前位移的格数；0 = 不位移
 * @param ranged           是不是远程招式（弓：射箭而不是近身挥砍）
 * @param interrupt        是否打断目标的施法/连招
 * @param staggerTicks     命中后目标的僵直时长（tick）；0 = 不僵直
 * @param animation        播放的玩家全身动作
 * @param holds            每一位是否需要长按（与 {@code sequence} 等长）
 * @param guardTicks       释放后自身获得抗性的时长（tick）；0 = 无
 * @param evadeTicks       释放后自身无敌的时长（tick）；0 = 无
 * @param backstep         释放时向后位移的格数；0 = 不后撤
 */
public record WarriorSkill(String id, String codexId, String name, List<WarriorMove> sequence, int slot,
                           int cooldownTicks, float damageMultiplier, double radius, int arcDegrees,
                           double knockback, int durabilityCost, String finisherText,
                           WarriorWeaponFamily family, WarriorChain chain, double dash, boolean ranged,
                           boolean interrupt, int staggerTicks, WarriorAnimation animation,
                           List<Boolean> holds, int guardTicks, int evadeTicks, double backstep) {

    /** 与法术配装同一个版面：2 页 × 9 槽。 */
    public static final int SLOTS_PER_PAGE = 9;
    public static final int PAGE_COUNT = 2;
    public static final int LOADOUT_SLOTS = SLOTS_PER_PAGE * PAGE_COUNT;

    public WarriorSkill {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("连招技能必须有 id");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(id + " 必须有名字");
        }
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException(id + " 没有连招顺序");
        }
        sequence = List.copyOf(sequence);
        if (slot < 0 || slot >= LOADOUT_SLOTS) {
            throw new IllegalArgumentException(id + " 的槽位越界：" + slot);
        }
        if (cooldownTicks <= 0) {
            throw new IllegalArgumentException(id + " 的冷却必须为正");
        }
        if (!(damageMultiplier > 0)) {
            throw new IllegalArgumentException(id + " 的伤害倍率必须为正");
        }
        if (!(radius > 0)) {
            throw new IllegalArgumentException(id + " 的作用半径必须为正");
        }
        if (arcDegrees <= 0 || arcDegrees > 360) {
            throw new IllegalArgumentException(id + " 的扇形角度必须在 1..360");
        }
        if (knockback < 0 || durabilityCost < 0) {
            throw new IllegalArgumentException(id + " 的击退 / 耐久消耗不能为负");
        }
        if (family == null || chain == null || animation == null) {
            throw new IllegalArgumentException(id + " 必须指定武器家族、链和动作");
        }
        if (!(dash >= 0) || dash > 8) {
            throw new IllegalArgumentException(id + " 的位移必须在 0..8 格");
        }
        if (staggerTicks < 0 || staggerTicks > 200) {
            throw new IllegalArgumentException(id + " 的僵直必须在 0..200 tick");
        }
        if (holds == null) {
            holds = java.util.Collections.nCopies(sequence.size(), false);
        }
        holds = List.copyOf(holds);
        if (holds.size() != sequence.size()) {
            throw new IllegalArgumentException(id + " 的长按标记数量必须和顺序一致");
        }
        if (guardTicks < 0 || guardTicks > 200) {
            throw new IllegalArgumentException(id + " 的架势时长必须在 0..200 tick");
        }
        if (evadeTicks < 0 || evadeTicks > 100) {
            throw new IllegalArgumentException(id + " 的无敌时长必须在 0..100 tick");
        }
        if (!(backstep >= 0) || backstep > 8) {
            throw new IllegalArgumentException(id + " 的后撤距离必须在 0..8 格");
        }
    }

    /** 第 {@code index} 段要不要长按。 */
    public boolean holdAt(int index) {
        return holds.get(index);
    }

    /** 有没有任何一段需要长按。 */
    public boolean anyHold() {
        return holds.contains(true);
    }

    /** 界面上的连招串，例如 {@code QEQZZZ}（纯字母，可被 {@link WarriorMove#parse} 反解）。 */
    public String sequenceLabel() {
        return WarriorMove.label(sequence);
    }

    /** 槽位里的紧凑写法：长按段后缀 {@code +}，例如 {@code QEZ+}。 */
    public String compactLabel() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sequence.size(); i++) {
            out.append(sequence.get(i).label());
            if (holds.get(i)) {
                out.append('+');
            }
        }
        return out.toString();
    }

    /** 提示文案：长按段写成"Z(长按)"。 */
    public String holdLabel() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sequence.size(); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(sequence.get(i).label());
            if (holds.get(i)) {
                out.append("(长按)");
            }
        }
        return out.toString();
    }

    /** 连招长度（要按几下）。 */
    public int length() {
        return sequence.size();
    }

    /** 360 度 = 原地一圈，不做朝向判定。 */
    public boolean fullCircle() {
        return arcDegrees >= 360;
    }

    /** 会向前位移吗？ */
    public boolean displaces() {
        return dash > 0;
    }

    /** 会向后拉开距离吗？ */
    public boolean backsteps() {
        return backstep > 0;
    }

    /** 释放时是否短暂无敌（闪避链）。 */
    public boolean evades() {
        return evadeTicks > 0;
    }

    /** 槽位所在页（0 基）。 */
    public int page() {
        return slot / SLOTS_PER_PAGE;
    }

    /** 槽位在本页里的列（0..8）。 */
    public int column() {
        return slot % SLOTS_PER_PAGE;
    }

    /** 冷却秒数（保留一位小数，界面用）。 */
    public String cooldownSeconds() {
        return String.format(java.util.Locale.ROOT, "%.1f", cooldownTicks / 20.0);
    }

    /** 适合放 HUD 的一句话：链 + 顺序。 */
    public String chainLabel() {
        return (slot<3 ? WarriorUnifiedSlots.purpose(slot) : chain.cn()) + " · " + compactLabel();
    }

    /**
     * 这一招在对拼里的定位（{@link WarriorComboRole}）。
     *
     * <p>四把武器现在都有<b>架势</b>：长剑振刀、重剑壁立、战锤沉渊；近战的攻击链/突击链都是
     * 进攻；其余防御链（逆鳞斩、铁壁、断招）都是"破架"；弓全部是远程，不参与对拼。
     * 判定按 id 写死，是因为"架势"是这三招独有的性质，而不是防御链的默认值 ——
     * 铁壁/断招也给抗性，但它们没有"架住别人"的资格，只有"打散别人的架势"。
     */
    public WarriorComboRole role() {
        if (slot == 1) return WarriorComboRole.STANCE;
        if (slot == 2) return WarriorComboRole.BREAKER;
        if (ranged) return WarriorComboRole.NEUTRAL;
        return switch (chain) {
            case ATTACK, ASSAULT -> WarriorComboRole.OFFENSE;
            case DEFENSE -> WarriorComboRole.BREAKER;
            case EVADE -> WarriorComboRole.NEUTRAL;
        };
    }
}
