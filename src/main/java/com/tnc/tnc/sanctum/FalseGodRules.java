package com.tnc.tnc.sanctum;

/** Measurements are blocks and server ticks. Telegraphs and hit tests use these same values. */
final class FalseGodRules {
    static final int WARNING = 30, EXHAUSTION = 80;
    static final double POOL_RADIUS = 2.65, JUMP_CLEARANCE = .52, METEOR_RADIUS = 24;
    enum Move {
        PILLARS("pillars","神罚之柱 · 撤出金阵",44),
        PRAYER("prayer","肃穆祈祷 · 石像霸体，跳过三道光环",107),
        WINGS("wings","羽翼裁决 · 绕后或退开，再穿矛隙",85),
        GAZE("gaze","深渊凝视 · 立即背对双眼",46),
        TEARS("tears","血泪倾泻 · 离开滴血处",47),
        SCREAM("scream","裂口尖啸 · 远离血池和口部",50),
        COMBO("combo","错乱连斩 · 刀印锁身时举盾，重掌落下前撤开",SanctumTimings.COMBO),
        FIRE("fire","堕天业火 · 引开五柱，跳过余波",145),
        GRAB("grab","血肉献祭 · 横移避开爪线",67),
        FUNERAL("funeral","千翼葬礼 · 穿过弹雨，退至外圈",147),
        DANCE("dance","终末狂舞 · 五次交叉斩后提防重劈",142),
        WINGCUT("wingcut","掠翼侧切 · 从红色翼径的侧边撤出",SanctumTimings.WINGCUT),
        HOOK("hook","恶魔重掌 · 离开掌印，落掌后反打",SanctumTimings.HOOK);
        final String action, cue;
        final int duration;
        Move(String action, String cue, int duration) { this.action=action; this.cue=cue; this.duration=duration; }
    }
    static Move choose(int phase, int cycle, boolean frenzy) {
        return switch(phase) {
            case 1 -> switch (cycle % 3) { case 0 -> Move.PILLARS; case 1 -> Move.PRAYER; default -> Move.WINGS; };
            case 2 -> switch(cycle % (frenzy ? 5 : 4)) {
                case 0,2 -> Move.TEARS; case 1 -> Move.GAZE; case 3 -> Move.SCREAM; default -> Move.COMBO;
            };
            default -> switch(cycle % (frenzy ? 6 : 5)) {
                case 0 -> Move.WINGCUT; case 1 -> Move.FIRE; case 2 -> Move.HOOK;
                case 3 -> Move.GRAB; case 4 -> Move.FUNERAL; default -> Move.DANCE;
            };
        };
    }
    static int health(int phase, int size) {
        return (int)((phase == 1 ? 120 : phase == 2 ? 145 : 160) * (1 + .50 * (size-1)));
    }
    static double incoming(int phase, boolean exhausted) {
        return exhausted ? 1.35 : phase == 1 ? .62 : phase == 2 ? .82 : 1.0;
    }
    static boolean backTurned(double lookX,double lookZ,double bossX,double bossZ) {
        double length=Math.hypot(lookX,lookZ)*Math.hypot(bossX,bossZ);
        return length > 1.e-8 && (lookX*bossX+lookZ*bossZ) / length <= -.20;
    }
    static boolean wave(double distance, double height, double previous, double current) {
        return height < JUMP_CLEARANCE && distance >= Math.max(0,previous-.46) && distance <= current+.46;
    }
    static double segment(double px,double pz,double ax,double az,double bx,double bz) {
        double x=bx-ax,z=bz-az, den=x*x+z*z;
        double t=den<1.e-8 ? 0 : Math.max(0, Math.min(1, ((px-ax)*x+(pz-az)*z)/den));
        return Math.hypot(px-ax-t*x,pz-az-t*z);
    }
    static int danceDash(int t) {
        return t>=16 && t<=64 && (t-16)%12==0 ? (t-16)/12 : -1;
    }
    private FalseGodRules(){}
}
