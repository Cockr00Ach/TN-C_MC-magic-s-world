package com.tnc.tnc.dungeon.leyline;
/** Stable persisted identifiers; never serialize ordinal. */
public enum LeylineKind {
 NONE("魔脉母巢"),BRIDGE("无形之桥"),DUAL_BRIDGE("双界交错"),FALSE_VAULT("幻镜宝库"),TIDE("魔力潮汐"),ECHO("时间回廊"),LENS("透镜寻珍"),SUNDIAL("影针日晷"),FACTIONS("双域斗场"),STOMACH("沉睡胃袋"),VINES("饥饿魔藤"),PUPPETS("提线剧场"),CHESS("镜中棋局"),REFRACTION("折光门扉"),ALCHEMY("失控药室"),MEAL("五味封印"),MUSIC("回声晶厅"),POLLUTION("逆污净室"),GRAVITY("颠倒风暴"),RAPIDS("魔流急湍"),SCALE("贪婪天平"),CAULDRON("饥饿坩埚"),EXHIBITION("琥珀展馆"),PAWN("地精典当与深泉");
 public final String title;public final String hint;
 LeylineKind(String title){this.title=title;this.hint="切换 F8，观察环境中的魔力痕迹。";}
}
