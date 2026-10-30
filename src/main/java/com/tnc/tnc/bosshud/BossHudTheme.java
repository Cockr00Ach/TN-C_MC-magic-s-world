package com.tnc.tnc.bosshud;

import java.util.*;

/** Stable entity identities, not localized names or nearest-entity guesses. */
public enum BossHudTheme {
    APOLLO("apollo",0xF3C769,0xFFF8D7,0x70431C,1,1,"日冕圣座","THRONE OF THE SUN"),
    DARK_GIANT("dark_giant",0xA27ADB,0xE2C9FF,0x38203F,1,2,"幽狱君临","SOVEREIGN OF THE ABYSS"),
    STONE_LANCER("stone_lancer",0xC69C57,0xF7DDA0,0x51402B,1,1,"不动山岳","THE UNYIELDING MOUNTAIN"),
    LEVIATHAN("water_leviathan",0x52CBD9,0xC8FAFF,0x153F65,1,2,"沧海王冠","CROWN OF THE DEEP"),
    TORTOISE("volcanic_tortoise",0xF47735,0xFFE6A5,0x571E1B,1,2,"焚世熔心","HEART OF THE INFERNO"),
    YAN("yan_dark",0xCE547D,0xFFD2E0,0x471F4A,1,2,"迷失之冕","CROWN OF THE FORSAKEN"),
    WIND_GENERAL("wind_general",0x80B7A6,0xE6F2D6,0x304E50,1,1,"御风旌旗","BANNER OF THE GALE"),
    WIND_WARDEN("wind_warden",0x62D59E,0xD8FFE9,0x1E5647,1,2,"翠羽天衡","THE JADE SKY WARDEN"),
    CANGLAN("canglan_warlord",0x9ADFD9,0xF1FCED,0x365A6A,1,2,"苍穹战誓","OATH OF THE SKY"),
    INK_DRAKE("ink_abyss_drake",0xA3ACB2,0xEAF1F2,0x30363F,1,1,"渊底游墨","INK BENEATH THE ABYSS"),
    INK_CRANE("ink_crane_boss",0xC7CCD0,0xFFFFFF,0x454850,1,3,"起笔 · 玄翎","FIRST STROKE · BLACK FEATHER"),
    INK_TIGER("ink_winged_tiger",0xADB6BD,0xF3F5F2,0x393F47,2,3,"破卷 · 裂空","TORN SCROLL · RIVEN SKY"),
    INK_DRAGON("ink_celestial_dragon",0xD3D8D9,0xFFFFFF,0x40444C,3,3,"归墟 · 万象","FINAL INK · RETURN TO THE VOID");

    public final String entity;public final int accent,light,shade,initialPhase,phases;public final String subtitleZh,subtitleEn;
    BossHudTheme(String entity,int accent,int light,int shade,int initialPhase,int phases,String zh,String en){this.entity=entity;this.accent=accent;this.light=light;this.shade=shade;this.initialPhase=initialPhase;this.phases=phases;subtitleZh=zh;subtitleEn=en;}
    public boolean ink(){return ordinal()>=INK_DRAKE.ordinal();}
    private static final Map<String,BossHudTheme> TYPES;
    static{Map<String,BossHudTheme> map=new LinkedHashMap<>();for(var value:values())map.put("tnc:"+value.entity,value);TYPES=Collections.unmodifiableMap(map);}
    public static BossHudTheme forType(String id){return TYPES.get(id);}
    public String type(){return "tnc:"+entity;}
}
