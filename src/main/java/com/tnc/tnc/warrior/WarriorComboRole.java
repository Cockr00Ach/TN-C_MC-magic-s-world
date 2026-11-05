package com.tnc.tnc.warrior;

/**
 * 一招连招在"对拼"里的定位：决定它和振刀/破架的克制关系。
 *
 * <p>这不是链的别名 —— 链是"功能分组"，定位是"结算规则"：
 *
 * <ul>
 *   <li>{@link #OFFENSE}：攻击链 / 突击链。伤害高，但<b>打不穿振刀</b>；</li>
 *   <li>{@link #STANCE}：振刀架势。架住正在进攻的人，让对方中断并僵直 1 秒；</li>
 *   <li>{@link #BREAKER}：防御链里的"破架"招式（长剑 · 逆鳞斩、重剑 · 铁壁、锤 · 断招）。
 *       专门破振刀：命中振刀中的人会打散架势并压 2 秒僵直；</li>
 *   <li>{@link #NEUTRAL}：远程（弓）和不参与对拼的招式，谁的架势都碰不到它。</li>
 * </ul>
 *
 * <p>克制是闭环：进攻 → 被振刀架住；振刀 → 被破架打散；破架 ↔ 进攻 互相中断
 * （谁先造成连招伤害谁赢，后手僵直 0.25 秒）。
 */
public enum WarriorComboRole {

    OFFENSE("进攻"),
    STANCE("振刀"),
    BREAKER("破架"),
    NEUTRAL("不参与");

    private final String cn;

    WarriorComboRole(String cn) {
        this.cn = cn;
    }

    /** 界面/提示里的中文定位。 */
    public String cn() {
        return cn;
    }

    /** 会不会被振刀架住。 */
    public boolean parriedByStance() {
        return this == OFFENSE;
    }

    /** 能不能破振刀。 */
    public boolean breaksStance() {
        return this == BREAKER;
    }

    /** 对拼里的对手：进攻 ↔ 破架，其余没有对手。 */
    public WarriorComboRole rival() {
        return switch (this) {
            case OFFENSE -> BREAKER;
            case BREAKER -> OFFENSE;
            default -> NEUTRAL;
        };
    }
}
