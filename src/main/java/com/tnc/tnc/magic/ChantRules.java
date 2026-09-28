package com.tnc.tnc.magic;

/** Readable short clauses, advanced spells only. Shared rules are easy to test without rendering. */
public final class ChantRules {
    private ChantRules() {}
    public static boolean chants(SpellCatalog.Entry entry) {return entry!=null&&!entry.independent()&&entry.tier()>=4;}
    public static String[] words(SpellCatalog.Entry entry) {
        return switch(entry.id().getPath()) {
            case "dragon_howl" -> new String[]{"深海之门，为我而开","万流汇聚，贯穿长夜","听见龙啸，潮光降临"};
            case "dragon_ruin" -> new String[]{"以无尽深渊为契","万海归一，冲破天地","龙滅——让一切归于洪流"};
            case "tsunami" -> new String[]{"沉睡的巨潮，回应我","以海为刃，以浪为锋","海啸——吞没前方"};
            case "world_ending_sea" -> new String[]{"大地曾自海中升起","今夜，四海重归于此","灭世之海——无岸，无涯"};
            case "abyss" -> new String[]{"深海封锁，万流成牢","光明止步，潮汐收紧","深渊——不再归还猎物"};
            case "sea_god_crypt" -> new String[]{"以海神之名，划定冥穴","苍穹铸剑，万潮俯首","落下吧——深海的审判"};
            case "downpour" -> new String[]{"聚散的云，听我呼唤","生命随雨而归","暴雨落——庇佑此地"};
            case "flood_of_heaven" -> new String[]{"打开苍天的水门","以天河为冠，万流为誓","天洪——生息永续"};
            case "lightning_storm" -> new String[]{"乌云为帐，雷霆为鼓","万钧之怒，汇聚此地","雷暴——回应我的意志"};
            case "heavenly_thunder" -> new String[]{"苍天列阵，五雷听令","斩断黑夜，审判降临","天打五雷轰——敕"};
            case "cataclysm_thunder_orb" -> new String[]{"万千电流，在掌中合一","雷霆凝星，毁灭为核","释放——轰鸣的灾厄"};
            case "divine_shot" -> new String[]{"借我神明的一瞬","天穹为场，雷星为球","神在投篮——此击必达"};
            case "lightning_recharge" -> new String[]{"雷脉轮转，时序逆行","解开束缚我的刻度","闪电——再度听令"};
            case "lightning_ascension" -> new String[]{"肉身为引，雷霆为魂","跨过凡人的边界","闪电登神——吾即天威"};
            default -> new String[]{entry.element().cn()+"之元素，回应召唤","以魔力为契，以意志为引",entry.displayName()+"——降临"};
        };
    }
    public static String line(SpellCatalog.Entry entry,float progress) {
        var lines=words(entry);return lines[Math.min(lines.length-1,Math.max(0,(int)(progress*lines.length)))];
    }
}
