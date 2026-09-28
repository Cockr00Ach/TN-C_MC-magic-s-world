package com.tnc.tnc.dialogue;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <b>按任务进度挑剧本</b> —— 同一个 NPC 可以有多段对话，播哪一段由玩家的任务状态决定。
 *
 * <p>这是"做完第一个任务之后，才能跟对应的人说上那句话"的实现 ✓。
 *
 * <h2>怎么用（编剧视角）</h2>
 * 给同一个 NPC 写多个文件，序号越大越"靠后"，门槛写在文件里：
 * <pre>
 *   data/tnc/dialogues/self_first.txt   ← 第一次见面（无门槛，兜底）
 *   data/tnc/dialogues/self_02.txt      ← @requires tnc:main/self_talk    （他答完第一段之后）
 *   data/tnc/dialogues/self_03.txt      ← @requires tnc:main/xxx          （更后面）
 * </pre>
 * 选段规则（见 {@link #pickFor}）：
 * <ol>
 *   <li>按<b>序号从大到小</b>试，第一段"门槛全部满足"的就是要播的 ✓；</li>
 *   <li>所以新加一段＝序号填最大的那个，再写上门槛，<b>不用改 Java</b> ✓；</li>
 *   <li>序号大的都没过门槛 → 自动回落序号小的，最终回落到 {@code <npc>_first} ✓。</li>
 * </ol>
 *
 * <h2>为什么门槛用"已完成"而不是"正在进行"</h2>
 * 用"正在进行"会造出一个死锁：任务没接取 → 剧本选不出来 → 玩家没法通过对话接任务 ✗。
 * 用"已完成"就没有这个问题：该说的话说完了，下一段自然出现 ✓。
 * 需要"拒绝重播"时用 {@code @excludes}（同样是"已完成就退场"）✓。
 *
 * <h2>序号怎么排</h2>
 * {@code first} 当作 <b>0</b>，{@code _02} 当作 <b>2</b>、{@code _03} 当作 <b>3</b>……
 * 只用 id 末尾的数字段（要求是纯粹的数字，{@code self_02} ✓ / {@code self_02b} ✗）。
 */
public final class DialoguePicker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.Map<String, List<ResourceLocation>> CACHE = new java.util.HashMap<>();

    private DialoguePicker() {
    }

    /**
     * 给这名玩家挑这个 NPC 该播的剧本。
     *
     * @param npcPrefix 剧本 id 的前缀（= NPC 皮肤名，如 {@code self}）
     * @return 选中的剧本；一条都加载不出来时返回空（调用方负责提示）
     */
    public static Optional<DialogueScript> pickFor(ServerPlayer player, String npcPrefix) {
        ResourceManager resources = player.server.getResourceManager();
        List<ResourceLocation> candidates = candidates(resources, npcPrefix);
        if (candidates.isEmpty()) {
            LOGGER.error("TN-C dialogue: no script at all for npc '{}'", npcPrefix);
            return Optional.empty();
        }

        // 序号大的先试。列表已在 candidates() 里排好序，这里顺着走即可。
        DialogueScript fallback = null;
        for (ResourceLocation id : candidates) {
            Optional<DialogueScript> loaded = DialogueLoader.get(resources, id);
            if (loaded.isEmpty()) {
                continue;                       // 加载失败的原因已在 DialogueLoader 里报过 ✓
            }
            DialogueScript script = loaded.get();
            if (script.gatesAllow(player)) {
                if (!id.equals(candidates.get(0))) {
                    LOGGER.info("TN-C dialogue: picked {} for npc '{}' (gates passed)",
                            id, npcPrefix);
                }
                return Optional.of(script);
            }
            if (fallback == null) {
                fallback = script;               // 序号最小的那个＝兜底
            }
        }

        // 走到这儿说明连兜底那段都没过门槛（编剧给每段都写了 @requires 却没留无门槛的一段）。
        // 这不是崩溃点，但要**明确报出来** ⚠️ —— 否则表现就是"右键没反应"，最难查 ✓。
        LOGGER.warn("TN-C dialogue: every script for npc '{}' failed its gates; "
                + "playing the lowest-numbered one anyway. Give '{}_first' no @requires "
                + "to make this go away", npcPrefix, npcPrefix);
        return Optional.ofNullable(fallback);
    }

    /** 资源重载后要清缓存，否则改了文件名/加了新段游戏里看不到（服务器 /reload 时会调用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 找出这个 NPC 的所有剧本 id，<b>序号从大到小</b>排好。
     *
     * <p>用资源管理器扫 {@code data/tnc/dialogues/} —— 所以<b>新加一个文件就能被发现</b> ✓，
     * 不需要在任何地方登记。
     */
    private static List<ResourceLocation> candidates(ResourceManager resources, String npcPrefix) {
        List<ResourceLocation> cached = CACHE.get(npcPrefix);
        if (cached != null) {
            return cached;
        }

        Map<ResourceLocation, Resource> found = resources.listResources(
                "dialogues", path -> path.getNamespace().equals(TNMod.MODID)
                        && path.getPath().endsWith(".txt"));

        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation file : found.keySet()) {
            String path = file.getPath();                      // dialogues/self_02.txt
            String name = path.substring("dialogues/".length(), path.length() - ".txt".length());
            if (!name.equals(npcPrefix) && !name.startsWith(npcPrefix + "_")) {
                continue;                                      // 不是这个 NPC 的
            }
            result.add(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, name));
        }

        result.sort(Comparator.comparingInt(DialoguePicker::sequenceOf).reversed());
        List<ResourceLocation> immutable = List.copyOf(result);
        CACHE.put(npcPrefix, immutable);
        if (!immutable.isEmpty()) {
            LOGGER.info("TN-C dialogue: npc '{}' has {} script(s), newest first: {}",
                    npcPrefix, immutable.size(), immutable);
        }
        return immutable;
    }

    /**
     * 从剧本名取序号，决定"先试哪一段"。
     *
     * <p>约定：<b>{@code _first} 与不带后缀都算第 0 段</b> ✓ ——
     * 两种写法在项目里都用过（{@code self_first} / {@code self}），
     * 所以这里**故意把它们视为同序**，不会一个压住另一个 ✗。
     *
     * <pre>
     *   self          -> 0      self_first -> 0
     *   self_02       -> 2      self_13    -> 13
     *   self_02b      -> 0      （末尾不是纯数字 = 没法排序，当 0）
     * </pre>
     */
    private static int sequenceOf(ResourceLocation id) {
        String name = id.getPath();
        int cut = name.lastIndexOf('_');
        if (cut < 0 || cut == name.length() - 1) {
            return 0;
        }
        String tail = name.substring(cut + 1);
        if ("first".equals(tail)) {
            return 0;                                          // 与不带后缀同序 ✓
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return 0;                                      // 不是纯数字（如 _02b）=> 0
            }
        }
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException tooBig) {
            return 0;
        }
    }

    /** 调试用：把某个 NPC 的候选顺序与门槛打成可读文本。 */
    public static String describe(ResourceManager resources, String npcPrefix) {
        List<ResourceLocation> ids = candidates(resources, npcPrefix);
        if (ids.isEmpty()) {
            return npcPrefix + ": (no script)";
        }
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation id : ids) {
            sb.append('\n').append("  ").append(id);
            DialogueLoader.get(resources, id).ifPresent(s ->
                    sb.append("  seq=").append(sequenceOf(id))
                            .append(s.requires().isEmpty() ? "" : "  requires=" + s.requires())
                            .append(s.excludes().isEmpty() ? "" : "  excludes=" + s.excludes())
                            .append(s.quests().isEmpty() ? "" : "  quests=" + s.quests())
                            .append(s.activate() == null ? "" : "  activate=" + s.activate()));
        }
        return npcPrefix + ":" + sb;
    }
}
