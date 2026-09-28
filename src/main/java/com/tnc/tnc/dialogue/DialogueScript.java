package com.tnc.tnc.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 一段对话剧本 —— 已解析好的数据。
 *
 * <p>结构刻意做得很小：<b>一条剧本 = 若干行台词</b>，每行是"谁说的 + 说了什么"。
 * 真要分支时，{@link #next}（演完后接下一段）已经接好了：
 * 客户端播完回报服务端，服务端再推下一段（见 {@code DialoguePackets.DialogueDone}）。
 *
 * @param id      剧本 id（{@code tnc:self_first}）
 * @param next    台词放完之后跳去的下一条剧本（可空）
 * @param theme   对话框配色主题名（见 {@link DialogueTheme}）；{@code null} = 默认金色
 * @param lines   台词行，按顺序播放
 * @param quests  这段演完后要推进的 <b>WhisperingQuests 任务 id 列表</b>（可空 = 不推进任何任务）。
 *                来自 {@code @quest <任务id>}（可写多行）或 {@code @quests a b c}。
 *                ★ 允许<b>多条</b>是有意的：收尾对话常常要同时结掉"本段最后一环"和
 *                "下一段的引子"，否则那两条会一条卡住一条 ✓。
 * @param activate 这段演完后要<b>接取</b>的任务 id（可空 = 不接任务）。
 *                来自 {@code @activate <任务id>}。与 {@code quests}（完成）是两件事 ✓。
 * @param requires 前置门槛：<b>必须已完成</b>这些任务，这段才播（空 = 无门槛）。
 *                来自 {@code @requires <任务id>}，可写多行。见 {@code DialoguePicker}。
 * @param excludes 反向门槛：<b>已完成</b>其中任意一条，这段就<b>不再播</b>（空 = 不限）。
 *                来自 {@code @excludes <任务id>} —— 同一个 NPC 的"后来那次对话"就靠它退场 ✓。
 *                ★ 注意用"已完成"而不是"正在进行"：这样没接任务时也照样能对话，
 *                  剧本自己会推进任务，不会出现"必须先接任务才能说话"的死锁 ✗。
 *                ★★ {@code quests / activate / requires / excludes} 四个字段<b>只在服务端用</b>，
 *                <b>不走网络</b> ✗ —— 客户端不需要知道这段戏跟哪条任务有关。
 */
public record DialogueScript(ResourceLocation id,
                             ResourceLocation next,
                             String theme,
                             List<ResourceLocation> quests,
                             ResourceLocation activate,
                             List<ResourceLocation> requires,
                             List<ResourceLocation> excludes,
                             List<Line> lines) {

    /** 这段剧本现在能不能对这名玩家播？（只看门槛，不看 id） */
    public boolean gatesAllow(net.minecraft.server.level.ServerPlayer player) {
        for (ResourceLocation needed : requires) {
            if (!com.tnc.tnc.dialogue.compat.WhisperingQuestBridge.isQuestCompleted(player, needed)) {
                return false;
            }
        }
        for (ResourceLocation blocked : excludes) {
            if (com.tnc.tnc.dialogue.compat.WhisperingQuestBridge.isQuestCompleted(player, blocked)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一行台词。
     *
     * @param speaker 显示在名字栏的名字（如 {@code Self}、{@code 归}）。
     *                <b>空字符串 = 旁白</b>（那七处动作提示就这样写）。
     * @param text    台词正文。{@code §} 颜色码可直接写在文本里，客户端按原版解析。
     * @param action  这一行显示时要播的<b>动作名</b>（可空 = 不动）。
     *                来自剧本里的 {@code @act <名字>} 行，见 {@code DialogueLoader}；
     *                播放由服务端执行（见 {@code DialogueNetwork.action}）✓。
     */
    public record Line(String speaker, String text, String action) {

        /** 兼容老写法：没有动作的行。 */
        public Line(String speaker, String text) {
            this(speaker, text, null);
        }

        /** 这一行有动作要播吗？ */
        public boolean hasAction() {
            return action != null && !action.isBlank();
        }
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
        // 主题只传**名字**，调色板留在客户端 —— 改颜色不用动网络协议，也不用两个端同步色值
        buf.writeUtf(script.theme() == null ? "" : script.theme());
        buf.writeVarInt(script.lines().size());
        for (Line line : script.lines()) {
            buf.writeUtf(line.speaker());
            buf.writeUtf(line.text());
            // 动作名：空串 = 这一行不动（协议上不区分 null 与空串）
            buf.writeUtf(line.action() == null ? "" : line.action());
        }
    }

    public static DialogueScript read(net.minecraft.network.FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        ResourceLocation next = buf.readBoolean() ? buf.readResourceLocation() : null;
        String theme = buf.readUtf();
        int n = buf.readVarInt();
        java.util.List<Line> lines = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String speaker = buf.readUtf();
            String text = buf.readUtf();
            String action = buf.readUtf();
            lines.add(new Line(speaker, text, action.isEmpty() ? null : action));
        }
        // ★ quests/activate/requires/excludes 故意不从网络读也不写：它们只在服务端用
        //   （见 record 上的说明）。
        return new DialogueScript(id, next, theme.isEmpty() ? null : theme,
                List.of(), null, List.of(), List.of(), List.copyOf(lines));
    }

    /** 便于调试/日志：{@code 3 行} 这种摘要。 */
    public String summary() {
        long acts = lines.stream().filter(Line::hasAction).count();
        return id + " (" + lines.size() + " line(s)"
                + (acts > 0 ? ", " + acts + " act" : "")
                + (theme != null ? ", theme=" + theme : "")
                + (next != null ? ", next=" + next : "")
                + (quests.isEmpty() ? "" : ", quests=" + quests)
                + (activate != null ? ", activate=" + activate : "")
                + (requires.isEmpty() ? "" : ", requires=" + requires)
                + (excludes.isEmpty() ? "" : ", excludes=" + excludes) + ")";
    }
}
