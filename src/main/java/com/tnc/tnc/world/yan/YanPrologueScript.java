package com.tnc.tnc.world.yan;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The six on-screen cards, in the order approved for the prologue. */
public final class YanPrologueScript {
    public static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("tnc", "yan_prologue");
    public static final List<String> CARDS = List.of(
            "你是吴归衡，魔法协会最年轻的天才之一。公孙衍是你的挚友。你们一同长大，也一同在协会学习魔法。直到有一天清晨，他独自离开了城，只托酒馆老板带给你一句话。",
            "「看清楚了，我就回来。」",
            "于是，你踏上了寻找挚友的旅程。你沿着他留下的线索追寻多年，最后在晦雷祭界的祭坛上，再一次见到了他。",
            "你下意识地喊了一声「衍」。他闻声回过头，黑紫色的雷光缠着他的手臂。那是你再熟悉不过的起手式。过去你们切磋时，他总会等你先举起法杖。这一次，他没有等。",
            "你想把衍带回来，也想弄清那股黑暗究竟从何而来。魔法石的秘密，只能靠你亲手揭开。",
            "你握紧法杖，踏上了祭坛。"
    );
    static final List<String> REWIND_CARDS = List.of(
            "你败了，输给了挚友，但身体中的无尽元素不会让你死亡",
            "时间倒流,一切回到酒馆前的那句话，你要再次寻找真相...."
    );
    static final int CORPSE_HOLD = 32;
    static final int CLOCK_START = 84;
    static final int DEFEAT_TICKS = 126;
    static final int BLACK_HOLD = 11;
    public static final int FADE = 13;
    static final int REWIND_TICKS = BLACK_HOLD + REWIND_CARDS.stream().mapToInt(YanPrologueScript::ticks).sum();
    public static int ticks(String card) {
        return FADE * 2 + Math.max(34, card.length() * 2);
    }

    private YanPrologueScript() {}
}
