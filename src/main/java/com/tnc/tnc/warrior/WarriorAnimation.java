package com.tnc.tnc.warrior;

/**
 * 玩家全身动作（纯数据，客户端按 id 摆姿势）。
 *
 * <p>动作<b>不绑定武器模型</b>：它转的是玩家模型的骨骼（右臂/左臂/身体/腿），
 * 所以 Steve 和 Alex 两种体型、以及任何拿在手上的武器都会跟着动。时长写在这里，
 * 服务端广播时带上同一个枚举，客户端按 {@link #durationTicks()} 插值。
 *
 * <p>第一版全部是"单段"动作：起手 → 挥出 → 收势。不做循环、不做上层状态机，
 * 因为连招本身已经是节奏来源，动作只需要在每次输入/终结技时对得上。
 */
public enum WarriorAnimation {

    /** 左斩：右臂从右上往左下斜劈。 */
    SLASH_LEFT(8),
    /** 右斩：右臂反手从左上往右下斜劈。 */
    SLASH_RIGHT(8),
    /** 横斩：右臂平肩横扫。 */
    SLASH_HORIZONTAL(8),
    /** 突进刺：身体前倾、右臂前刺，配合位移。 */
    THRUST_DASH(12),
    /** 旋身一周：身体 rotY 转 360°，双臂展开。 */
    SPIN_SLASH(20),
    /** 双手过顶重劈：双臂抬起再砸下，重剑/锤用。 */
    OVERHEAD_SMASH(16),
    /** 盾/护身冲撞：左臂横挡、右臂收在身侧，防御链用。 */
    GUARD_BASH(14),
    /** 拉弓：左臂前推、右臂后拉，松手时前臂回弹。 */
    BOW_DRAW(16),
    /** 闪避：身体侧倾、双腿收拢，弓的突击链用。 */
    EVADE(12),
    /** 震地：锤的双手下砸 + 蹲身。 */
    GROUND_QUAKE(18),
    /**
     * 长剑 · 破锋式（QZE）：手臂先抬后斩的三段剑势。
     * 骨骼关键帧来自 {@code assets/tnc/animations/entity/战士连招/长剑连招.json}。
     */
    SWORD_BREAKER(18),
    /** 长剑 · 旋转斩击（QEQZZZ）：身体整转一周的旋身斩。 */
    SWORD_SPIN(15),
    /** 长剑 · 架势·振刀（QE + 长按 Z）：架刀震开的短促架势。 */
    SWORD_STANCE(20),
    /** 长剑 · 逆鳞斩（ZZQQEE）：横斩压身再回身重劈。 */
    SWORD_COUNTER(25),
    /** 重剑 · 破阵斩（ZQE）：双手横握、由左向右的大范围横扫。 */
    GS_SWEEP(18),
    /** 重剑 · 山崩（QQZ）：双手过顶、前倾下砸的重劈。 */
    GS_SMASH(22),
    /** 重剑 · 铁壁（EQZ）：翻刃架住再向前顶出的短促守势。 */
    GS_WALL(17),
    /** 重剑 · 架势·壁立（QEE）：竖剑当门的长架势。 */
    GS_STANCE(20),
    /** 战锤 · 碎骨（QZZ）：单臂抡起、从右肩砸落。 */
    HM_CRUSH(19),
    /** 战锤 · 震地（EEZ）：双手高举、蹲身砸地。 */
    HM_QUAKE(23),
    /** 战锤 · 断招（ZQQ）：短距离前顶震击。 */
    HM_CUT(16),
    /** 战锤 · 架势·沉渊（QEQ）：锤横胸前、重心下沉。 */
    HM_STANCE(20),
    /** 长弓 · 穿云箭（QZ）：满弦定射。 */
    BOW_AIM(18),
    /** 长弓 · 疾影射（EQ）：侧身快拉快放。 */
    BOW_DASH_SHOT(14),
    /** 长弓 · 后跃射（ZE）：后仰收腿、空中放箭。 */
    BOW_BACK_SHOT(17),
    SWORD_RETREAT(22),
    SWORD_SWALLOW(24),
    GS_RIFT(28),
    GS_ADVANCE(24),
    HM_RUIN(30),
    HM_DRIVE(24),
    BOW_PIERCE(26),
    BOW_STRAFE(24),
    BOW_VOLLEY(28),
    SPEAR_BREAK(24),
    SPEAR_DRAGON(26),
    SPEAR_RETURN(26),
    SPEAR_GUARD(24),
    SPEAR_FLOW(24),
    SCYTHE_POUNCE(24),
    SCYTHE_REAP(28),
    SCYTHE_HOOK(24),
    SCYTHE_GUARD(24),
    SCYTHE_EXECUTE(30),
    SCYTHE_WHIRL(28),
    BOW_CHARGE(20),
    SWORD_CHARGE(20),
    GS_CHARGE(26),
    HM_CHARGE(26),
    SPEAR_CHARGE(20);

    private final int durationTicks;

    WarriorAnimation(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    /** 动作总时长（tick）。 */
    public int durationTicks() {
        return durationTicks;
    }

    /**
     * 动作的"分量"：数值越大越不容易被顶掉。
     *
     * <p>基础挥砍（Q/E/左键单段）是 0，所有终结技是 1。客户端收到新动作时，如果同一个人
     * 正在演分量更高的动作、而且还没演完，就<b>丢掉</b>低分量的那一个 —— 这样"放完连招
     * 再按 Q/E"不会把自己刚放出的动作打断，只有新连招（同为 1 或更高）才能接管。
     */
    public int priority() {
        return switch (this) {
            case SLASH_LEFT, SLASH_RIGHT, SLASH_HORIZONTAL -> 0;
            default -> 1;
        };
    }

    /** 网络序号（0 基，和枚举顺序一致；不要重排）。 */
    public int id() {
        return ordinal();
    }

    /**
     * 这一条是不是"打出去"的终结技（不是架势、不是拉弓/闪避）。
     *
     * <p>客户端靠它决定要不要放龙：龙之剑拿到 {@link #finisher()} 的动作时，
     * {@code WarriorDragonRenderer} 就沿朝向画一条龙。这样不用为视觉表现新增网络包，
     * 协议号保持不动。
     */
    public boolean finisher() {
        return switch (this) {
            case THRUST_DASH, SPIN_SLASH, OVERHEAD_SMASH, GROUND_QUAKE,
                 SWORD_BREAKER, SWORD_SPIN, SWORD_COUNTER,
                 GS_SWEEP, GS_SMASH, HM_CRUSH, HM_QUAKE,
                 SWORD_RETREAT,SWORD_SWALLOW,GS_RIFT,GS_ADVANCE,HM_RUIN,HM_DRIVE,
                 SPEAR_BREAK,SPEAR_DRAGON,SPEAR_RETURN,SPEAR_FLOW,
                 SCYTHE_POUNCE,SCYTHE_REAP,SCYTHE_HOOK,SCYTHE_EXECUTE,SCYTHE_WHIRL -> true;
            default -> false;
        };
    }

    /** 图鉴/提示里的中文动作名。 */
    public String cn() {
        return switch (this) {
            case SLASH_LEFT -> "左斩";
            case SLASH_RIGHT -> "右斩";
            case SLASH_HORIZONTAL -> "横斩";
            case THRUST_DASH -> "突进刺";
            case SPIN_SLASH -> "旋身斩";
            case OVERHEAD_SMASH -> "过顶重劈";
            case GUARD_BASH -> "守势顶撞";
            case BOW_DRAW -> "拉弓";
            case EVADE -> "闪避";
            case GROUND_QUAKE -> "震地";
            case SWORD_BREAKER -> "破锋式";
            case SWORD_SPIN -> "旋转斩击";
            case SWORD_STANCE -> "振刀";
            case SWORD_COUNTER -> "逆鳞斩";
            case GS_SWEEP -> "破阵斩";
            case GS_SMASH -> "山崩";
            case GS_WALL -> "铁壁";
            case GS_STANCE -> "壁立";
            case HM_CRUSH -> "碎骨";
            case HM_QUAKE -> "震地";
            case HM_CUT -> "断招";
            case HM_STANCE -> "沉渊";
            case BOW_AIM -> "穿云箭";
            case BOW_DASH_SHOT -> "疾影射";
            case BOW_BACK_SHOT -> "后跃射";
            case BOW_CHARGE -> "聚能满弦";
            case SWORD_CHARGE -> "藏锋蓄势";
            case GS_CHARGE -> "重剑蓄势";
            case HM_CHARGE -> "战锤蓄势";
            case SPEAR_CHARGE -> "压枪蓄势";
            case SWORD_RETREAT -> "退步回斩";
            case SWORD_SWALLOW -> "翻身燕返";
            case GS_RIFT -> "裂地重劈";
            case GS_ADVANCE -> "踏步扫阵";
            case HM_RUIN -> "崩山轮砸";
            case HM_DRIVE -> "沉肩顶锤";
            case BOW_PIERCE -> "滑步穿云";
            case BOW_STRAFE -> "侧滑追风";
            case BOW_VOLLEY -> "腾挪连射";
            case SPEAR_BREAK -> "弓步破军";
            case SPEAR_DRAGON -> "游龙送枪";
            case SPEAR_RETURN -> "回身横扫";
            case SPEAR_GUARD -> "横枪盘架";
            case SPEAR_FLOW -> "撤步反刺";
            case SCYTHE_POUNCE -> "扑身钩斩";
            case SCYTHE_REAP -> "低身收割";
            case SCYTHE_HOOK -> "倒钩锁喉";
            case SCYTHE_GUARD -> "横镰护身";
            case SCYTHE_EXECUTE -> "断头重斩";
            case SCYTHE_WHIRL -> "旋身猎杀";

        };
    }

    public static WarriorAnimation byId(int id) {
        WarriorAnimation[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
