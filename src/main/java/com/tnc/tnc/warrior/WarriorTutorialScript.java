package com.tnc.tnc.warrior;

import java.util.List;

/** Shared dialogue timing for the instructor and the learner's camera. */
public final class WarriorTutorialScript {
    public static final int INVITE=0, DEMO=1, TERMINAL=2, ATTACK=3, PARRY=4, BREAKER=5, SUMMARY=6, COMPLETE=7;
    public enum Shot { MENTOR, WIDE, TERMINAL, HANDS, DUMMY, PLAYER }
    public record Line(String text,int ticks,Shot shot) {}

    public static List<Line> lines(int stage) {
        return switch(stage) {
            case DEMO -> List.of(
                new Line("先看这一剑。看清起势，也别漏了最后的收剑。",50,Shot.MENTOR),
                new Line("剑影穿阵——",135,Shot.WIDE),
                new Line("九道穿刺连成一式，收剑时，余势才从目标脚下爆发。",100,Shot.MENTOR),
                new Line("这里是演武终端。它能召出木桩，也能安排教官陪你练习。",110,Shot.TERMINAL),
                new Line("先别急着学大招。每次只练一件事，主攻、振刀，再到破振刀。",105,Shot.HANDS),
                new Line("去右键台中央的终端。准备好后，我陪你走完前三招。",95,Shot.PLAYER));
            case ATTACK -> List.of(
                new Line("主攻要稳。连续三次 {Z}，也可以连续三次左键。",90,Shot.HANDS),
                new Line("面向木桩，保持距离，把最后一斩完整打出来。",80,Shot.DUMMY));
            case PARRY -> List.of(
                new Line("接下来是振刀。先按 {E}、{Q}，再按住 {E}，把架势留住。",110,Shot.MENTOR),
                new Line("举好武器，我会送来一记轻击。成功架开后，会顺势回身横扫。",95,Shot.HANDS));
            case BREAKER -> List.of(
                new Line("对方若一直架着，就用破振刀。按 {E}、{Q}、{Z}。",95,Shot.MENTOR),
                new Line("这招重在向前压进、打断架势。对准木桩，亲手把它打出来。",85,Shot.DUMMY));
            case SUMMARY -> List.of(
                new Line("三招记住了，其他兵器也认这三个基础槽位。剑轻灵，重剑重压，枪重穿刺，锤重震击。",125,Shot.WIDE),
                new Line("武器分一到五级。一级可用前三招，每升一级再开一招，五级共有七招。",115,Shot.HANDS),
                new Line("大招要三级或以上武器，并且磨砺严格超过三千。三千整，还差一步。",115,Shot.MENTOR),
                new Line("练习不授予天赋。磨砺上限由你的战士天赋决定，去人物档案里查看。",100,Shot.PLAYER),
                new Line("长按 {USE} 蓄力，松开出手。面对首领，先用简短连招抓住破绽。",110,Shot.HANDS),
                new Line("按 {K} 能查武器名录和连招。以后想复习，再来找我。",100,Shot.MENTOR));
            default -> List.of();
        };
    }

    public static int duration(int stage) { return lines(stage).stream().mapToInt(Line::ticks).sum(); }
    public static int lineAt(int stage,int elapsed) {
        var lines=lines(stage);
        int end=0;
        for(int i=0;i<lines.size();i++) { end+=lines.get(i).ticks();if(elapsed<end)return i; }
        return Math.max(0,lines.size()-1);
    }
    public static boolean cinematic(int stage,int elapsed) {
        return elapsed>=0 && elapsed<duration(stage);
    }
    private WarriorTutorialScript() {}
}
