package com.tnc.tnc.magic;

/**
 * TN-C 七大元素（设计文档第一节）。
 *
 * 注意：<b>冰是水的衍生</b>，不是独立元素 —— 所以这里只有 7 个。
 * 元素与 SpellEngine 的学派（SpellSchool）不是一对一关系，映射见 {@link #spellSchoolHint()}。
 */
public enum Element {

    WATER("water", "水", "spell_power:water"),
    FIRE("fire", "火", "spell_power:fire"),
    LIGHTNING("lightning", "雷", "spell_power:lightning"),
    WIND("wind", "风", "spell_power:air"),
    EARTH("earth", "土", "spell_power:earth"),
    LIGHT("light", "光", "spell_power:healing"),
    DARK("dark", "dark", "spell_power:soul");

    private final String id;
    private final String cn;
    private final String spellSchoolHint;

    Element(String id, String cn, String spellSchoolHint) {
        this.id = id;
        this.cn = cn;
        this.spellSchoolHint = spellSchoolHint;
    }

    /** 小写英文 id，用于命令、NBT、法术 id 前缀。 */
    public String id() {
        return id;
    }

    /** 中文名，用于界面与提示。 */
    public String cn() {
        return cn;
    }

    /**
     * 该元素大致对应的 SpellEngine 学派（只作提示，真正换算等做施法时再定）。
     * 现阶段法术数据文件里写的仍然是 SpellEngine 的学派名，例如 tnc:spark 用的是 LIGHTNING。
     */
    public String spellSchoolHint() {
        return spellSchoolHint;
    }

    /** 五等级名称（设计文档第四节）：索引 = 等级，1 起。 */
    public static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "冒险者级";
            case 2 -> "勇者级";   // 设计文档里的二级叫"勇者"
            case 3 -> "王级";
            case 4 -> "传说级";
            case 5 -> "神级";
            default -> "未知";
        };
    }

    public static Element byId(String id) {
        for (Element e : values()) {
            if (e.id.equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (Element e : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.id);
        }
        return sb.toString();
    }
}
