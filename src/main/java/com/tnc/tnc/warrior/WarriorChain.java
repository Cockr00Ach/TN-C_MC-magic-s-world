package com.tnc.tnc.warrior;

/**
 * 一条武器线里的三条"链"：突击 / 攻击 / 防御。
 *
 * <p>链决定一招的<b>功能定位</b>，不是单纯换个名字：
 *
 * <ul>
 *   <li>{@link #ASSAULT}：带位移，用来贴身、追击或拉开距离；</li>
 *   <li>{@link #ATTACK}：伤害最高，站桩换血；</li>
 *   <li>{@link #DEFENSE}：打断敌人的施法/连招并造成僵直，伤害偏低。</li>
 * </ul>
 *
 * <p>武器特性允许改写链的表现：重剑的突击链<b>不做位移</b>（用范围更大的一击代替），
 * 弓的突击链是闪避、攻击链是射箭。判定逻辑读的是 {@link WarriorSkill} 上的
 * {@code dash / ranged / interrupt / staggerTicks}，所以"哪条链"只影响默认值，
 * 不影响结算代码——新武器只要填数据。
 */
public enum WarriorChain {

    ASSAULT("突击链", "带位移：贴身、追击或后撤"),
    ATTACK("攻击链", "伤害最高：站桩换血"),
    DEFENSE("防御链", "打断施法/连招并造成僵直"),
    EVADE("闪避链", "免疫伤害并迅速向后拉开距离");

    private final String cn;
    private final String note;

    WarriorChain(String cn, String note) {
        this.cn = cn;
        this.note = note;
    }

    /** 界面上的中文名（突击链 / 攻击链 / 防御链）。 */
    public String cn() {
        return cn;
    }

    /** 一句话定位。 */
    public String note() {
        return note;
    }

    /** 这条链默认带位移吗？具体招式可以在 {@link WarriorSkill#dash()} 里覆盖。 */
    public boolean defaultDash() {
        return this == ASSAULT;
    }

    /** 这条链默认要打断施法/连招吗？ */
    public boolean defaultInterrupt() {
        return this == DEFENSE;
    }

    /** 防御/闪避是每把武器<b>必须有</b>的一条保命链：二者占同一条生态位。 */
    public boolean isDefensive() {
        return this == DEFENSE || this == EVADE;
    }
}
