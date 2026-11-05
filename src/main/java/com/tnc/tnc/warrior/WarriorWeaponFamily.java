package com.tnc.tnc.warrior;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * 武器家族：同一套连招只对同一家族生效。
 *
 * <p>第一版先用<b>原版武器</b>跑通链与动画，不新增物品：
 *
 * <ul>
 *   <li>{@link #LONGSWORD}：任意 {@link SwordItem}（原版六档剑 + 大部分模组剑）；</li>
 *   <li>{@link #GREATSWORD}：数据包标签 {@code tnc:greatswords}（以后给重剑留的口子）；</li>
 *   <li>{@link #HAMMER}：数据包标签 {@code tnc:hammers}；</li>
 *   <li>{@link #BOW}：{@link BowItem} / {@link CrossbowItem} 或 {@code tnc:longbows}。</li>
 * </ul>
 *
 * <p>标签优先于父类判定：一把剑如果同时写进 {@code tnc:greatswords}，它算重剑而不是长剑。
 * 这样以后加重剑物品不用改判定代码。
 */
public enum WarriorWeaponFamily {

    LONGSWORD("长剑", "tnc:longswords", true),
    GREATSWORD("重剑", "tnc:greatswords", false),
    HAMMER("锤", "tnc:hammers", false),
    // 长枪：靠长度和突刺吃饭，命中框更长（WarriorBladeGeometry.length 也给到最长）
    SPEAR("长枪", "tnc:spears", true),
    BOW("弓", "tnc:longbows", true);

    private final String cn;
    private final ResourceLocation tagId;
    /** 这个家族的突击链默认带位移吗？（重剑/锤用范围代替位移） */
    private final boolean assaultDisplacement;

    WarriorWeaponFamily(String cn, String tagId, boolean assaultDisplacement) {
        this.cn = cn;
        // 这里只存 ResourceLocation：TagKey 要碰 Registries，单元测试没有 Bootstrap 会炸，
        // 所以延迟到 tag() 里创建。
        this.tagId = ResourceLocation.fromNamespaceAndPath("tnc", tagId.substring(tagId.indexOf(':') + 1));
        this.assaultDisplacement = assaultDisplacement;
    }

    public String cn() {
        return cn;
    }

    public TagKey<Item> tag() {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
    }

    public boolean assaultDisplacement() {
        return assaultDisplacement;
    }

    /**
     * 双手武器：副手也要搭在武器上（见 {@code WarriorPose}）。
     *
     * <p>放在 enum 上而不是 {@code WarriorWeapons} 上：这里不碰任何注册表，
     * 单元测试可以直接钉。
     */
    public boolean twoHanded() {
        return this == GREATSWORD || this == HAMMER || this == SPEAR;
    }

    /** 这个家族靠近身挥砍吗？弓不算（它的攻击链是射箭）。 */
    public boolean melee() {
        return this != BOW;
    }

    /** 拿着这件物品算哪个家族；空手/非武器返回 {@code null}。 */
    public static WarriorWeaponFamily of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() instanceof com.tnc.tnc.bronze.BronzeWeaponItem && stack.hasTag() && stack.getTag().contains(com.tnc.tnc.bronze.BronzeWeaponItem.FLIGHT)) {
            return null;
        }
        // 标签先判：重剑/锤也可能是 SwordItem
        for (WarriorWeaponFamily family : values()) {
            if (family != LONGSWORD && stack.is(family.tag())) {
                return family;
            }
        }
        Item item = stack.getItem();
        if (item instanceof SwordItem || stack.is(LONGSWORD.tag())) {
            return LONGSWORD;
        }
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return BOW;
        }
        return null;
    }
}
