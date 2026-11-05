package com.tnc.tnc.warrior;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * 「武器家族」判定的门面：旧代码只关心"是不是长剑"，新代码可以问"是哪一族"。
 *
 * <p>第一版用原版武器跑通三条链，<b>不新增物品</b>：长剑 = 任意 {@code SwordItem}
 * 或 {@code tnc:longswords} 标签；弓 = {@code BowItem}/{@code CrossbowItem} 或
 * {@code tnc:longbows}。重剑/锤先留标签口子（{@code tnc:greatswords} /
 * {@code tnc:hammers}），等对应物品做出来只要往标签里加一行，判定与连招不用改。
 */
public final class WarriorWeapons {

    /** 武器归类标签：默认只有原版六档剑。 */
    public static final TagKey<Item> LONGSWORDS = WarriorWeaponFamily.LONGSWORD.tag();

    private WarriorWeapons() {
    }

    /** 这件物品算不算"长剑"（含模组剑）。空手永远不算。 */
    public static boolean isLongsword(ItemStack stack) {
        return WarriorWeaponFamily.of(stack) == WarriorWeaponFamily.LONGSWORD;
    }

    /** 这件物品属于哪个武器家族；不是武器返回 {@code null}。 */
    public static WarriorWeaponFamily family(ItemStack stack) {
        return WarriorWeaponFamily.of(stack);
    }

    /**
     * 这一族是不是<b>双手武器</b>。
     *
     * <p>长槍、重剑、战锤在手里都是“一只手提不动”的东西，出招时另一只手
     * 要搭在武器上（见 {@code WarriorPose}）；否则一刀砍出去，副手直接
     * 垂回腿边，长槍瞬间变成“单手捡”，就很丑。
     */
    public static boolean twoHanded(WarriorWeaponFamily family) {
        return family != null && family.twoHanded();
    }

    /**
     * 这件物品"有没有刃"——决定要不要画常驻剑身辉光。
     *
     * <p>返回 true 的有：任何已归类的近战武器（长剑/重剑/锤），以及没有连招家族的剑、斧
     * （斧 `AxeItem` 不在四族里，但确实是把带刃的武器，拿在手上应该有光）。弓、法杖、
     * 木棍、方块一律 false，免得空手或拿根木棍也发光。
     */
    public static boolean bladed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        WarriorWeaponFamily family = WarriorWeaponFamily.of(stack);
        if (family != null) {
            return family != WarriorWeaponFamily.BOW;
        }
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
