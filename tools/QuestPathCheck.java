import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 校验 KubeJS 数据包里的 whisperingquests 任务/章节是否放在框架真正会扫的路径下。
 *
 * 框架（whisperingquests）用 SimpleJsonResourceReloadListener 扫这两个目录：
 *   whisperingquests/tasks/<group>/<name>.json
 *   whisperingquests/chapters/<name>.json
 * ⇒ 完整路径必须是  data/<命名空间>/whisperingquests/tasks/<group>/<name>.json
 *
 * 踩过的坑：把文件放在 data/tnc/main/self_talk.json（少了 whisperingquests/tasks 两级），
 * 结果框架根本扫不到 —— 任务书里什么都不显示，而且**没有任何报错**。
 */
public class QuestPathCheck {
    public static void main(String[] args) throws Exception {
        Path dataRoot = Path.of(args[0]);
        int ok = 0, bad = 0;
        try (var walk = Files.walk(dataRoot)) {
            for (Path p : walk.filter(Files::isRegularFile).filter(x -> x.toString().endsWith(".json")).toList()) {
                String rel = dataRoot.relativize(p).toString().replace('\\', '/');
                // 只看像任务/章节的文件（顶层含 data 之后是 <ns>/...）
                boolean looksLikeQuest = rel.contains("whisperingquests")
                        || rel.matches("[^/]+/(main|side|daily|chapters)/[^/]+\\.json");
                if (!looksLikeQuest) continue;

                boolean good = rel.matches("[^/]+/whisperingquests/tasks/(main|side|daily|[^/]+)/[^/]+\\.json")
                        || rel.matches("[^/]+/whisperingquests/chapters/[^/]+\\.json");
                String content = Files.readString(p, StandardCharsets.UTF_8);
                String id = firstMatch(content, "\"id\"\\s*:\\s*\"([^\"]+)\"");
                String enabled = firstMatch(content, "\"enabled\"\\s*:\\s*(true|false)");
                String chapter = firstMatch(content, "\"chapter\"\\s*:\\s*\"([^\"]+)\"");
                String type = firstMatch(content, "\"type\"\\s*:\\s*\"(dialogue)\"");

                if (good) { ok++; System.out.printf("  [ok]   %-64s id=%s enabled=%s%s%n", rel, id, enabled,
                        chapter != null ? " chapter=" + chapter : ""); }
                else { bad++; System.out.printf("  [BAD]  %-64s  <- 路径不对，框架扫不到！%n", rel); }
            }
        }
        System.out.println(bad == 0 ? ("路径检查通过（" + ok + " 个任务/章节）") : (bad + " 个文件路径有问题"));
    }

    static String firstMatch(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        return m.find() ? m.group(m.groupCount() >= 1 ? 1 : 0) : null;
    }
}
