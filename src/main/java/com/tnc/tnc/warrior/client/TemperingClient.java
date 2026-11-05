package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.InstructorEntity;
import com.tnc.tnc.warrior.TemperingPacket;
import com.tnc.tnc.warrior.TemperingRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TemperingClient {
    private static boolean enrolled;
    private static int points;
    private static int talent;
    private static boolean graduate, training, ground;
    private static int blocks, counterTicks, windTicks;
    private static int mode, completions, bestScore, bestReaction;
    /** 当前召出的教官（服务端下发）。 */
    private static String activeRole = "";
    /** 面板上选中的教官（纯客户端选择，点开始才发出去）。 */
    private static String selectedRole = "counter";
    /** 四位教官的出师进度：角色 → {已出师, 总课数}。 */
    private static final java.util.Map<String, int[]> courseProgress = new java.util.HashMap<>();
    /** 正在上的那一节（服务端拼好的短句）；没在上课就是空串。 */
    private static String courseNow = "";
    private static long receivedAt;
    private static long displayTicks;

    private TemperingClient() {
    }

    public static boolean ground() {
        return ground;
    }

    public static int mode() {
        return ground ? mode : 0;
    }

    public static int selectedMode() {
        return mode;
    }

    public static int completions() {
        return completions;
    }

    public static int bestScore() {
        return bestScore;
    }

    public static int bestReaction() {
        return bestReaction;
    }

    public static int cap() {
        return com.tnc.tnc.warrior.WarriorTalentRules.cap(talent);
    }

    public static int talent() {
        return talent;
    }

    public static boolean graduate() {
        return graduate;
    }

    public static boolean training() {
        return training;
    }

    public static int blocks() {
        return blocks;
    }

    /** 服务端记录的在场教官；没有则空串。 */
    public static String activeRole() {
        return activeRole;
    }

    /** 这位教官出师了几节，形如 {@code 1/3}；没有课程表就返回空串。 */
    public static String courseBrief(String role) {
        int[] pair = courseProgress.get(role);
        return pair == null || pair[1] <= 0 ? "" : pair[0] + "/" + pair[1];
    }

    /** 这位教官是不是整套课都出师了。 */
    public static boolean courseComplete(String role) {
        int[] pair = courseProgress.get(role);
        return pair != null && pair[1] > 0 && pair[0] >= pair[1];
    }

    /** 正在上的那一节，例如「第二课 · 振刀 1/2」；没在上课就是空串。 */
    public static String courseNow() {
        return courseNow;
    }

    /**
     * 四位教官加起来出师了几节，形如 {@code 5/11}；没有数据就返回空串。
     *
     * <p>单看一位教官的 {@code 1/4} 感觉不出"整套武学学了多少"，而这个总数正好是
     * 玩家最想知道的"还有多少课没上"。
     */
    public static String courseTotals() {
        int done = 0, total = 0;
        for (int[] pair : courseProgress.values()) {
            if (pair[1] <= 0) continue;
            done += Math.min(pair[0], pair[1]);
            total += pair[1];
        }
        return total <= 0 ? "" : done + "/" + total;
    }

    /**
     * 下一次 {@code open=true} 的同步要开哪个界面。
     *
     * <p>为什么用客户端标志而不是新增网络动作：按下快捷键时客户端<b>立刻</b>打开图鉴，
     * 同时发一个既有的 {@code VIEW} 请求最新数据；回包到达时按这个标志决定"重建图鉴"
     * 还是"打开训练面板"。这样协议一个字都不用改（改枚举会让 {@code PROTOCOL_VERSION}
     * 无谓地跳一版，而服务端本来就只需要回同一份快照）。
     */
    private static boolean codexHint;

    /** 记住"接下来这次同步是图鉴要的"，然后由 {@link #accept} 分派。 */
    public static void hintCodex() {
        codexHint = true;
    }

    /** 图鉴用的进度快照：与训练面板读的是同一份镜像。 */
    public static com.tnc.tnc.warrior.WarriorCodex.Progress progress() {
        return new com.tnc.tnc.warrior.WarriorCodex.Progress(talent, points, completions, bestScore, graduate);
    }

    /** 面板选中的教官；点「开始练习」才发出去。 */
    public static String selectedRole() {
        return selectedRole;
    }

    public static void selectRole(String role) {
        if (role == null) return;
        for (String name : InstructorEntity.VARIANTS) {
            if (name.equals(role)) {
                selectedRole = name;
                return;
            }
        }
    }

    private static long now() {
        return displayTicks;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) displayTicks++;
    }

    private static int remaining(int ticks) {
        return (int) Math.max(0, ticks - Math.max(0, now() - receivedAt));
    }

    public static int counterTicks() {
        return remaining(counterTicks);
    }

    public static int windTicks() {
        return remaining(windTicks);
    }

    public static boolean enrolled() {
        return enrolled;
    }

    public static int points() {
        return points;
    }

    public static void accept(TemperingPacket packet) {
        talent = packet.talent();
        enrolled = packet.enrolled() && talent > 0;
        points = Math.min(cap(), TemperingRules.clamp(packet.points()));
        graduate = packet.graduate();
        training = packet.training() && enrolled;
        ground = packet.ground();
        mode = packet.mode();
        completions = packet.completions();
        bestScore = packet.bestScore();
        bestReaction = packet.bestReaction();
        blocks = packet.blocks();
        counterTicks = packet.counterTicks();
        windTicks = packet.windTicks();
        activeRole = packet.role() == null ? "" : packet.role();
        courseProgress.clear();
        if (packet.courses() != null) {
            for (String part : packet.courses().split("\\|")) {
                int colon = part.indexOf(':');
                int slash = part.indexOf('/');
                if (colon <= 0 || slash <= colon) continue;
                try {
                    courseProgress.put(part.substring(0, colon), new int[]{
                        Math.max(0, Integer.parseInt(part.substring(colon + 1, slash))),
                        Math.max(0, Integer.parseInt(part.substring(slash + 1)))});
                } catch (NumberFormatException ignored) {
                    // 服务端格式变了也不会让界面崩：认不出来的那段直接忽略。
                }
            }
        }
        courseNow = packet.course() == null ? "" : packet.course();
        receivedAt = now();
        if (packet.open() && Minecraft.getInstance().player != null) {
            openRequestedScreen();
        }
    }

    /**
     * 服务端说"该开面板了"，到底开哪个由客户端决定：
     * 按快捷键来的开武器图鉴，右键终端、{@code /tempering panel} 来的开训练面板。
     */
    private static void openRequestedScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean codex = codexHint;
        codexHint = false;
        if (codex) {
            // 已经开着图鉴时只重建控件，别把玩家正在看的页签/选中条目顶掉
            if (minecraft.screen instanceof WarriorCodexScreen codexScreen) {
                codexScreen.refreshFromServer();
            } else {
                minecraft.setScreen(new WarriorCodexScreen());
            }
        } else if (minecraft.screen instanceof WarriorPracticeScreen practiceScreen) {
            practiceScreen.refreshFromServer();
        } else {
            minecraft.setScreen(new WarriorPracticeScreen());
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enrolled = false;
        points = 0;
        talent = 0;
        graduate = training = ground = false;
        blocks = counterTicks = windTicks = 0;
        receivedAt = displayTicks = 0;
        mode = completions = bestScore = bestReaction = 0;
        activeRole = "";
        courseProgress.clear();
        courseNow = "";
        codexHint = false;
    }
}
