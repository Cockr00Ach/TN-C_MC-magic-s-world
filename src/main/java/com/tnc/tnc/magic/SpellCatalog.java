package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * TN-C 法术目录（魔法石界面里"可解锁的法术"从哪来）。
 *
 * <p>现在是<b>硬编码</b>的一小张表，和 `kubejs/data/tnc/spells/*.json` 里的法术一一对应。
 * 等界面稳定了再改成数据驱动（读 SpellEngine 的法术注册表，或者从 mod 自带 json 读）。
 *
 * <p>每条记录要知道：法术 id、属于哪个元素、第几级、中文名。
 */
public final class SpellCatalog {

    /** 一个可解锁法术。 */
    public record Entry(ResourceLocation id, Element element, int tier, String displayName) {

        /** 界面/提示里显示的完整名字，例如「小闪电（雷 · 冒险者级）」。 */
        public String fullName() {
            return displayName + "（" + element.cn() + " · " + Element.tierName(tier) + "）";
        }

        /** 施放一次消耗多少魔力（数值在配置里）。 */
        public int manaCost() {
            return com.tnc.tnc.Config.manaCostForTier(tier);
        }

        /** 解锁要投多少魔法点数。 */
        public int learnCost() {
            return com.tnc.tnc.Config.learnCostForTier(tier);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            entry("spark", Element.LIGHTNING, 1, "小闪电"),
            entry("lightning_field", Element.LIGHTNING, 2, "雷场"),
            entry("lightning_strike", Element.LIGHTNING, 3, "雷击"),
            entry("lightning_storm", Element.LIGHTNING, 4, "雷暴"),
            entry("heavenly_thunder", Element.LIGHTNING, 5, "天打五雷轰")
    );

    private SpellCatalog() {
    }

    private static Entry entry(String path, Element element, int tier, String name) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), element, tier, name);
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    /** 按元素筛选（界面以后要分页/分元素显示时用）。 */
    public static List<Entry> of(Element element) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.element() == element) {
                result.add(entry);
            }
        }
        return result;
    }

    public static Entry byId(ResourceLocation id) {
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
