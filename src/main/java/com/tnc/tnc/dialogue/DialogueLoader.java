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
 *
 *   旁白|                    说话人留空 = 旁白（那七处动作提示就这么写）
 *   Self|你在这儿坐了一整夜了，归。
 *   归|不用换。那杯不是我的。
 * </pre>
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
            int bar = line.indexOf('|');
            if (bar < 0) {
                throw new IllegalStateException("line " + lineNo
                        + ": expected 'speaker|text' but found no '|' -> " + line);
            }
            String speaker = line.substring(0, bar).trim();
            String text = line.substring(bar + 1).trim();
            lines.add(new DialogueScript.Line(speaker, text));
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("no dialogue lines found");
        }
        return new DialogueScript(id, next, List.copyOf(lines));
    }

    /** 剧本 id 的约定：{@code tnc:<npc>_<序号>}。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }
}
