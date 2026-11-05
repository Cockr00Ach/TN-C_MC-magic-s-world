package com.tnc.tnc.warrior.forge;

/** First four silhouettes prove thrust, straight blade, wide blade and four-sided forging. */
public enum ForgePattern {
    JIAN("汉式铁剑", "han_iron_jian", 4, new int[]{0,2,1,0,2,0,2,1,0,2,1,1}),
    GUANDAO("偃月刀", "crescent_guandao", 6, new int[]{0,2,1,0,2,0,2,1,0,2,1,1}),
    SPEAR("红缨枪", "red_tassel_spear", 5, new int[]{0,2,1,1,0,2,1,1,0,2,1,1}),
    MACE("四棱铁锏", "iron_jian_mace", 6, new int[]{0,1,2,0,1,2,0,1,2,0,1,2});

    public final String title, item;
    public final int iron;
    private final int[] zones;
    ForgePattern(String title, String item, int iron, int[] zones) {
        this.title = title; this.item = item; this.iron = iron; this.zones = zones;
    }
    public int zone(int stroke) { return zones[Math.max(0, Math.min(11, stroke))]; }
    public boolean heavy(int stroke) {
        return stroke < 3 || this == GUANDAO && stroke < 8 || this == MACE && stroke % 3 == 0;
    }
    public String zoneName(int zone) {
        return this == MACE ? new String[]{"左棱", "中棱", "右棱"}[zone]
                : new String[]{"左刃／根", "脊线／尖", "右刃／根"}[zone];
    }
    public int pose() { return ordinal(); }
    public String poseName() { return new String[]{"斜刃递入", "宽刃平稳递入", "枪尖先入", "转棱冷却"}[ordinal()]; }
}
