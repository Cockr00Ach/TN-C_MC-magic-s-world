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
            if (line.startsWith("@act")) {
                // ★ 动作名必须挂在**下一行**台词上：写错了（比如挂在另一条 @act 后面、
                //   或者挂在文件末尾）就直接报错，不做静默忽略 —— 静默失效是这个项目最大的坑。
                if (pendingAction != null) {
                    throw new IllegalStateException("line " + lineNo
                            + ": two @act in a row (the first one at line " + pendingActionLine
                            + " has no dialogue line to attach to)");
                }
                String action = line.substring(4).trim();
                if (action.isEmpty()) {
                    throw new IllegalStateException("line " + lineNo + ": @act needs an action name");
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
        return new DialogueScript(id, next, theme, List.copyOf(lines));
    }

    /** 剧本 id 的约定：{@code tnc:<npc>_<序号>}。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }
}
