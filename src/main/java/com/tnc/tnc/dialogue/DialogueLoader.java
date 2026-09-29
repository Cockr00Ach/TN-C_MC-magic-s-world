package com.tnc.tnc.dialogue;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 剧本的加载与缓存：从数据包路径 {@code data/<ns>/dialogues/*.txt} 读取。
 *
 * <h2>文件格式（给编剧看的，直接改文本就行，不用重新编译）</h2>
 * <pre>
 *   # 井号开头是注释，可以整行
 *   @id   tnc:self_first      剧本 id（必须与文件名一致）
 *   @next                     下一条剧本（可选）—— 本行之后的行是台词
 *   @quest tnc:main/self_talk 可选：本段演完**完成**这条任务（可写多行）
 *   @quests a b c             可选：一行完成多条（空格分隔）
 *   @activate tnc:main/xxx    可选：本段演完**接取**哪条任务（与 @quest 是两件事）
 *   @requires tnc:main/xxx    可选：**必须先完成**这条任务，本段才会被选中（可写多行）
 *   @excludes tnc:main/xxx    可选：**完成**这条任务后本段就退场（可写多行）
 *   @act  pointcup            可选：给**紧接着的下一行台词**挂一个动作
 *
 *   旁白|                    说话人留空 = 旁白（那七处动作提示就这么写）
 *   Self|你在这儿坐了一整夜了，归。
 *   归|不用换。那杯不是我的。
 *
 *   @act wipehand
 *   Self|他跟你说过「看清楚了就回来」……
 * </pre>
 *
 * <p>★ <b>{@code @act} 的语义</b>（2026-09-22，动画系统MMM）：<b>只作用于紧接的那一行</b>，
 * 那行一显示出来就播这个动作（名字必须与 {@code assets/tnc/animations/entity/*.json}
 * 里的动画名一致 ✗，写错不会崩，但游戏里什么都不动）。
 * 动作的"怎么播"在 {@code DialogueNetwork.action} 那一侧（服务端触发）✓。
 *
 * <p>用竖线分隔"说话人 | 台词"。之所以不用 JSON：台词里全是中文标点和引号，
 * 方括号/转义在 JSON 里极易出错，而这种一行一句的格式编剧能直接读、直接改。
 *
 * <p>放在 {@code data/} 下而不是 {@code assets/} 下，是因为<b>服务端</b>也要读它
 * （交互判定在服务端，剧本由服务端通过 {@code OpenDialogue} 包发给客户端）。
 */
public final class DialogueLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "dialogues";
    private static final java.util.Map<ResourceLocation, DialogueScript> CACHE = new java.util.HashMap<>();

    private DialogueLoader() {
    }

    /** 加载（或取缓存）。失败会**明确报错**，不静默返回空 —— 静默失效是这个项目最大的坑。 */
    public static Optional<DialogueScript> get(ResourceManager resources, ResourceLocation id) {
        DialogueScript cached = CACHE.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), DIR + "/" + id.getPath() + ".txt");
        Optional<Resource> res = resources.getResource(file);
        if (res.isEmpty()) {
            LOGGER.error("TN-C dialogue: script file missing: {}", file);
            return Optional.empty();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
            DialogueScript script = parse(id, reader);
            CACHE.put(id, script);
            LOGGER.info("TN-C dialogue: loaded {} -> {}", file, script.summary());
            return Optional.of(script);
        } catch (Exception e) {
            LOGGER.error("TN-C dialogue: failed to parse {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** 资源重载后要清缓存，否则改了台词游戏里看不到（服务器 /reload 时会调用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static DialogueScript parse(ResourceLocation id, BufferedReader reader) throws Exception {
        ResourceLocation next = null;
        ResourceLocation activate = null;
        List<ResourceLocation> quests = new ArrayList<>();
        List<ResourceLocation> requires = new ArrayList<>();
        List<ResourceLocation> excludes = new ArrayList<>();
        List<DialogueScript.Line> lines = new ArrayList<>();
        // 待挂到"下一行台词"上的动作名（见类注释里的 @act 说明）
        String pendingAction = null;
        int pendingActionLine = 0;
        String raw;
        int lineNo = 0;
        while ((raw = reader.readLine()) != null) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("@id")) {
                String declared = line.substring(3).trim();
                if (!declared.isEmpty() && !declared.equals(id.toString())) {
                    throw new IllegalStateException("line " + lineNo + ": @id '" + declared
                            + "' does not match file id '" + id + "'");
                }
                continue;
            }
            if (line.startsWith("@next")) {
                String target = line.substring(5).trim();
                if (!target.isEmpty()) {
                    next = ResourceLocation.parse(target);
                }
                continue;
            }
            // ★ 顺序要紧：这些 @指令 必须排在"说话人|台词"之前判断，
            //   否则 "@quest tnc:main/xxx" 里没有竖线，会被下面当成格式错误 ✗。
            if (line.startsWith("@quests")) {
                // 一行完成多条（空格分隔）：收尾对话常常同时结掉本段和下一段的引子 ✓
                for (String one : line.substring(7).trim().split("\\s+")) {
                    if (!one.isEmpty()) {
                        quests.add(ResourceLocation.parse(one));
                    }
                }
                if (quests.isEmpty()) {
                    throw new IllegalStateException("line " + lineNo
                            + ": @quests needs at least one quest id");
                }
                continue;
            }
            if (line.startsWith("@quest")) {
                quests.add(parseIdDirective(line, 6, lineNo, "@quest"));
                continue;
            }
            // ★★ 指令判断顺序的铁律：**长关键字必须排在它的前缀之前** ✗
            //   出过事：`@activate` 也以 `@act` 开头，而 @act 分支写在前面 ⇒
            //   它被当成动作名、取 substring(4) 得到 `ivate tnc:main/xxx`
            //   ⇒ 所有带 @activate 的剧本**整条解析失败**（2026-09-29，5/12 剧本死掉，
            //   玩家表现就是"跟这个 NPC 没法对话"）。改结构时务必保持这个顺序：
            //     @requires / @excludes / @activate / @quests / @quest  先
            //     @next / @id                                         中
            //     @act                                                最后
            //   （`@quests` 也要排在 `@quest` 前面，同理。）
            if (line.startsWith("@activate")) {
                activate = parseIdDirective(line, 9, lineNo, "@activate");
                continue;
            }
            if (line.startsWith("@requires")) {
                requires.add(parseIdDirective(line, 9, lineNo, "@requires"));
                continue;
            }
            if (line.startsWith("@excludes")) {
                excludes.add(parseIdDirective(line, 9, lineNo, "@excludes"));
                continue;
            }
            // ★★ 顺序铁律：**@act 必须排在所有以它开头的指令之后** ✗
            //   出过事（2026-09-29，两次）：`@activate` 也以 `@act` 开头。
            //   当 @act 写在前面时，`@activate tnc:main/s1_leave` 会被当成动作名、
            //   取 substring(4) 得到 `ivate tnc:main/s1_leave` ⇒ **整条剧本解析失败**
            //   ⇒ 玩家表现是"跟这个 NPC 没法对话"（huai / cava / 周坐望大半都中招）。
            //   所以保持这个顺序：
            //     先： @quests / @quest / @activate / @requires / @excludes
            //     中： @id / @next
            //     最后：@act
            //   下面的防呆会在顺序再次被弄乱时**立刻抛错**，不静默吞掉。
            if (line.equals("@act") || line.startsWith("@act ") || line.startsWith("@act\t")) {
                // 动作名必须挂在**下一行**台词上：写在末尾或连着两条就直接报错，
                // 不做静默忽略 —— 静默失效是这个项目最大的坑。
                if (pendingAction != null) {
                    throw new IllegalStateException("line " + lineNo
                            + ": two @act in a row (the first one at line " + pendingActionLine
                            + " has no dialogue line to attach to)");
                }
                String action = line.substring(4).trim();
                if (action.isEmpty()) {
                    throw new IllegalStateException("line " + lineNo + ": @act needs an action name");
                }
                // 防呆：动作名是**一个词**，永远不含 ':' 或 '/'。
                // 出现了就说明有别的前缀指令（@activate 之类）掉进了这个分支 ⇒ 立刻报错。
                if (action.contains(":") || action.contains("/")) {
                    throw new IllegalStateException("line " + lineNo
                            + ": looks like another @directive fell into the @act branch -> '"
                            + action + "'. @act must be tested LAST "
                            + "(see the ordering rule above).");
                }
                if (action.indexOf('|') >= 0 || action.indexOf(' ') >= 0) {
                    throw new IllegalStateException("line " + lineNo
                            + ": action name must be one word (no spaces / '|') -> " + action);
                }
                pendingAction = action;
                pendingActionLine = lineNo;
                continue;
            }
            int bar = line.indexOf('|');
            if (bar < 0) {
                throw new IllegalStateException("line " + lineNo
                        + ": expected 'speaker|text' but found no '|' -> " + line);
            }
            String speaker = line.substring(0, bar).trim();
            String text = line.substring(bar + 1).trim();
            lines.add(new DialogueScript.Line(speaker, text, pendingAction));
            pendingAction = null;
        }
        if (pendingAction != null) {
            throw new IllegalStateException("line " + pendingActionLine + ": @act " + pendingAction
                    + " is not followed by any dialogue line");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("no dialogue lines found");
        }
        // 配色主题：按剧本 id 的前缀自动定（zhuangquerang_second -> zhuangquerang），
        // 所以**加戏不用在 Java 里登记任何东西**，剧本文件一放就有对应颜色。
        String theme = DialogueTheme.paletteOf(id.getPath()).key();
        return new DialogueScript(id, next, theme, List.copyOf(quests), activate,
                List.copyOf(requires), List.copyOf(excludes), List.copyOf(lines));
    }

    /**
     * 解析 {@code @指令 <资源id>} 形式的一行。
     *
     * @param keyword 指令名（只用于报错）
     * @param at      资源 id 在这一行里的起始下标（= 指令名长度 + 1）
     */
    private static ResourceLocation parseIdDirective(String line, int at, int lineNo, String keyword) {
        String target = line.substring(at).trim();
        if (target.isEmpty()) {
            throw new IllegalStateException("line " + lineNo + ": " + keyword
                    + " needs a quest id (e.g. " + keyword + " tnc:main/self_talk)");
        }
        return ResourceLocation.parse(target);
    }

    /** 剧本 id 的约定：{@code tnc:<npc>_<序号>}。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }
}
