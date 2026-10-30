package com.tnc.tnc.magic;

import com.tnc.tnc.npc.compat.SelfCastActorSupport;
import net.minecraft.world.entity.Entity;

/**
 * 玩家施法动作的<b>唯一名字表</b> —— 三条动作在
 * {@code assets/tnc/animations/entity/player_magic-animation.json} 里的键，
 * 以及"什么法术播哪条"的约定（用户 2026-10-30 定的）：
 *
 * <pre>
 *   召唤类      → magicsummon   （双手开合、杖头翻上）
 *   绝大多数    → wavingstaff   （举杖连续挥动）"主要都用 waving"
 *   强力/大招   → playmagic_1   （抬杖前推 + 转杖）"强力的可以用 play1"
 * </pre>
 *
 * <p>为什么单独一个枚举：动作名要是散在命令、实体、以后的施法流程里三份，
 * 改一个名字就会"编译过、静默不播" ✗ —— 本工程的老坑。这里只放字符串，
 * <b>不引用 GeckoLib 的任何类型</b> ✓，没装 GeckoLib 的环境也能安全加载。
 *
 * <p>真正播动作要经过 {@link SelfCastActorSupport}（那里才碰 GeckoLib）。
 */
public enum PlayerCastAnimations {

    /** 召唤：{@code magicsummon}。 */
    SUMMON("magicsummon", "召唤"),

    /** 常规施法：{@code wavingstaff}。 */
    STANDARD("wavingstaff", "常规"),

    /** 强力法术：{@code playmagic_1}。 */
    POWERFUL("playmagic_1", "强力");

    private final String animation;
    private final String label;

    PlayerCastAnimations(String animation, String label) {
        this.animation = animation;
        this.label = label;
    }

    /** JSON 里的动作名。 */
    public String animation() {
        return this.animation;
    }

    /** 中文短名（命令回显用）。 */
    public String label() {
        return this.label;
    }

    /** 全部动作名（命令补全用）。 */
    public static String[] animationNames() {
        PlayerCastAnimations[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].animation;
        }
        return names;
    }

    /** 按 JSON 动作名找回枚举；不认识 → {@code null}。 */
    public static PlayerCastAnimations byAnimation(String animation) {
        if (animation == null) {
            return null;
        }
        for (PlayerCastAnimations kind : values()) {
            if (kind.animation.equals(animation)) {
                return kind;
            }
        }
        return null;
    }

    /** 按命令里的短名（summon / standard / powerful，大小写随意）找回枚举；不认识 → {@code null}。 */
    public static PlayerCastAnimations byKey(String key) {
        if (key == null) {
            return null;
        }
        for (PlayerCastAnimations kind : values()) {
            if (kind.name().equalsIgnoreCase(key)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * 在 {@code actor} 身上播这条动作。
     *
     * <p>★ 以后接真正的施法流程就调这里：{@code PlayerCastAnimations.SUMMON.play(player, false)}。
     * 现在 {@code actor} 只接受持杖版 Self（演示角色）；换成玩家本人时只需改
     * {@link SelfCastActorSupport}，调用点不用动 ✓。
     *
     * @param loop true = 循环（观察用）
     * @return 真的播了没有
     */
    public boolean play(Entity actor, boolean loop) {
        return SelfCastActorSupport.play(actor, this.animation, loop);
    }
}
