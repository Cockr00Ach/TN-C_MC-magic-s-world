package com.tnc.tnc.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 一段对话剧本 —— 已解析好的数据。
 *
 * <p>结构刻意做得很小：<b>一条剧本 = 若干行台词</b>，每行是"谁说的 + 说了什么"。
 * 第一场（Self 的酒馆）只有一条直线，所以暂时不需要分支；
 * 真要分支时，{@link #next}（对白选项→下一条剧本）已经预留好了。
 *
 * @param id      剧本 id（{@code tnc:self_first}）
 * @param next    台词放完之后跳去的下一条剧本（可空）
 * @param lines   台词行，按顺序播放
 */
public record DialogueScript(ResourceLocation id,
                             ResourceLocation next,
                             List<Line> lines) {

    /**
     * 一行台词。
     *
     * @param speaker 显示在名字栏的名字（如 {@code Self}、{@code 归}）。
     *                <b>空字符串 = 旁白</b>（那七处动作提示就这样写）。
     * @param text    台词正文。{@code §} 颜色码可直接写在文本里，客户端按原版解析。
     */
    public record Line(String speaker, String text) {
    }

    // ------------------------------------------------------------------
    //  网络序列化
    //  为什么手写而不是把 DialogueScript 丢进网络：它是 record，且含 Map/List，
    //  用 FriendlyByteBuf 逐字段写最直白、也最容易核对（不依赖任何序列化库的行为）。
    // ------------------------------------------------------------------

    public static void write(net.minecraft.network.FriendlyByteBuf buf, DialogueScript script) {
        buf.writeResourceLocation(script.id());
        buf.writeBoolean(script.next() != null);
        if (script.next() != null) {
            buf.writeResourceLocation(script.next());
        }
        buf.writeVarInt(script.lines().size());
        for (Line line : script.lines()) {
            buf.writeUtf(line.speaker());
            buf.writeUtf(line.text());
        }
    }

    public static DialogueScript read(net.minecraft.network.FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        ResourceLocation next = buf.readBoolean() ? buf.readResourceLocation() : null;
        int n = buf.readVarInt();
        java.util.List<Line> lines = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(new Line(buf.readUtf(), buf.readUtf()));
        }
        return new DialogueScript(id, next, List.copyOf(lines));
    }

    /** 便于调试/日志：{@code 3 行} 这种摘要。 */
    public String summary() {
        return id + " (" + lines.size() + " line(s)" + (next != null ? ", next=" + next : "") + ")";
    }
}
