package com.tnc.tnc.warrior;

import java.util.Locale;

/** Cosmetic palettes; ice remains a water visual variant rather than a new affinity. */
public enum WarriorEffectStyle {
    STEEL("steel","钢",0xb8cbd3),FIRE("fire","火",0xff702b),WATER("water","水",0x32b8ef),
    ICE("ice","冰",0xa5f3ff),LIGHTNING("lightning","雷",0xc0a1ff),WIND("wind","风",0x8be9c0),
    EARTH("earth","土",0xc9a06a),LIGHT("light","光",0xffe995),DARK("dark","暗",0x8c59cf);
    public final String id,label;public final int color;
    WarriorEffectStyle(String id,String label,int color){this.id=id;this.label=label;this.color=color;}
    public static WarriorEffectStyle find(String id){if(id==null)return null;for(var s:values())if(s.id.equals(id.toLowerCase(Locale.ROOT)))return s;return null;}
    public static int budget(String kind){return switch(kind){case "slash"->27;case "block"->24;default->12;};}
}
