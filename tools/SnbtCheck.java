import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 独立的 SNBT 解析 + FTB 章节语义校验 —— TN-C 任务系统专题的护栏。
 *
 * <h2>为什么要有它</h2>
 * FTB 的章节是手写 SNBT，**错一个字符就整章静默不加载**（进游戏只看到任务界面是空的，
 * 没有任何报错）。所以每次改完章节、同步进实例之前先跑一遍这个。
 *
 * <h2>怎么跑</h2>
 * <pre>
 *   set JAVA_HOME=D:\ModTest\.jdk17
 *   %JAVA_HOME%\bin\javac -encoding UTF-8 -d build\tmp\snbt tools\check-ftb-chapter.java
 *   %JAVA_HOME%\bin\java -Dfile.encoding=UTF-8 -cp build\tmp\snbt SnbtCheck ^
 *       "modpack\...\config\ftbquests\quests\chapters\tnc_ch1_main.snbt"
 * </pre>
 * 检查：SNBT 语法 → 章节根字段（id/filename/group/quests）→ 每个节点的
 * 标题/提示/任务类型/奖励 → **依赖链有没有悬空引用**（指向不存在的任务 id）。
 * 传 chapter_groups.snbt 时只看语法（"缺字段 id"那几条是误报，可忽略）。
 *
 * <h2>踩过的坑：SNBT 允许省略逗号</h2>
 * <code>{a: 1</code> 换行 <code>b: 2}</code> 是**合法**的（Mojang 的解析器按空白分隔）。
 * 第一版校验器把它当语法错，结果对自己的三个文件全报错 —— 别重蹈覆辙。
 */
public class SnbtCheck {

    // ---------------------------------------------------------------- 解析
    static String src; static int p;

    static Object parse(String text) {
        src = text; p = 0;
        skipWs();
        Object v = value();
        skipWs();
        if (p < src.length()) throw new IllegalStateException("尾部有多余内容 @" + p + ": " + ctx());
        return v;
    }

    static String ctx() { return src.substring(Math.max(0, p - 40), Math.min(src.length(), p + 40)).replace("\n", "\\n"); }

    static void skipWs() {
        while (p < src.length()) {
            char c = src.charAt(p);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { p++; continue; }
            break;
        }
    }

    static Object value() {
        skipWs();
        char c = src.charAt(p);
        if (c == '{') return compound();
        if (c == '[') return list();
        if (c == '"' || c == '\'') return quoted(c);
        return bare();
    }

    static Map<String, Object> compound() {
        expect('{');
        Map<String, Object> m = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { p++; return m; }
        while (true) {
            skipWs();
            String key = key();
            skipWs();
            expect(':');
            skipWs();
            m.put(key, value());
            skipWs();
            char c = src.charAt(p);
            // ★ SNBT 允许省略逗号（换行即分隔）：读到 , 就吃掉，读到 } 就结束，其它字符当下一项
            if (c == ',') { p++; continue; }
            if (c == '}') { p++; return m; }
        }
    }

    static List<Object> list() {
        expect('[');
        List<Object> l = new ArrayList<>();
        skipWs();
        if (peek() == ']') { p++; return l; }
        while (true) {
            l.add(value());
            skipWs();
            char c = src.charAt(p);
            if (c == ',') { p++; continue; }
            if (c == ']') { p++; return l; }
            // 同理：省略逗号时继续读下一项
        }
    }

    static String quoted(char q) {
        p++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (p >= src.length()) throw new IllegalStateException("字符串没有收尾引号");
            char c = src.charAt(p++);
            if (c == '\\') {
                char e = src.charAt(p++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(e);
                }
                continue;
            }
            if (c == q) return sb.toString();
            sb.append(c);
        }
    }

    static String key() {
        skipWs();
        char c = src.charAt(p);
        if (c == '"' || c == '\'') return quoted(c);
        int start = p;
        while (p < src.length()) {
            char d = src.charAt(p);
            if (d == ':' || d == ' ' || d == '\t' || d == '\n' || d == '\r') break;
            p++;
        }
        if (p == start) throw new IllegalStateException("空 key @" + p);
        return src.substring(start, p);
    }

    static Object bare() {
        int start = p;
        while (p < src.length()) {
            char d = src.charAt(p);
            if (d == ',' || d == '}' || d == ']' || d == ' ' || d == '\t' || d == '\n' || d == '\r') break;
            p++;
        }
        String t = src.substring(start, p);
        if (t.isEmpty()) throw new IllegalStateException("空值 @" + p + ": " + ctx());
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.endsWith("d") || lower.endsWith("f") || lower.endsWith("b") || lower.endsWith("l") || lower.endsWith("s")) {
            return t; // 带类型后缀，按原样保留
        }
        if (lower.equals("true")) return Boolean.TRUE;
        if (lower.equals("false")) return Boolean.FALSE;
        return t; // 数字或裸字符串都保留为文本
    }

    static char peek() { return src.charAt(p); }
    static void expect(char c) {
        if (src.charAt(p) != c) throw new IllegalStateException("期望 '" + c + "' 但遇到 '" + src.charAt(p) + "' @" + p + ": " + ctx());
        p++;
    }

    // ---------------------------------------------------------------- 语义校验
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int bad = 0;

        // 先扫一遍入参，把 chapter_groups.snbt 里的分组 id 收集起来 ——
        // 章节的 `group:` 必须指向一个真实存在的分组，否则章节挂不进去
        // （2026-09-22 真踩过：FTB 会**自己重写分组 id**，手写的 id 就变悬空了）。
        Set<String> knownGroups = new LinkedHashSet<>();
        for (String f : args) {
            Object r = null;
            try { r = parse(Files.readString(Path.of(f), StandardCharsets.UTF_8)); } catch (Exception ignored) { }
            if (r instanceof Map<?, ?> mm && mm.get("chapter_groups") instanceof List<?> gl) {
                for (Object g : gl) {
                    if (g instanceof Map<?, ?> gm && gm.get("id") != null) knownGroups.add(String.valueOf(gm.get("id")));
                }
            }
        }
        if (!knownGroups.isEmpty()) {
            System.out.println("已收集到 " + knownGroups.size() + " 个分组 id: " + knownGroups);
        }

        for (String f : args) {
            System.out.println("=== " + Path.of(f).getFileName() + " ===");
            String text = Files.readString(Path.of(f), StandardCharsets.UTF_8);
            Object root;
            try {
                root = parse(text);
            } catch (Exception e) {
                System.out.println("  [SYNTAX FAIL] " + e.getMessage());
                bad++;
                continue;
            }
            if (!(root instanceof Map)) { System.out.println("  [FAIL] 根不是 compound"); bad++; continue; }
            Map<String, Object> m = (Map<String, Object>) root;

            // chapter_groups.snbt 走另一套检查（它没有 quests）
            if (m.get("chapter_groups") instanceof List<?> gl) {
                System.out.println("  [ok] 语法通过，分组数=" + gl.size());
                for (Object g : gl) {
                    Map<String, Object> gm = (Map<String, Object>) g;
                    boolean ok = gm.get("id") != null && gm.get("title") != null;
                    System.out.println("    " + (ok ? "[ok]" : "[FAIL]") + " id=" + gm.get("id")
                            + " 标题=" + gm.get("title"));
                    if (!ok) bad++;
                }
                continue;
            }

            System.out.println("  [ok] 语法通过，顶层键: " + m.keySet());

            // 章节根字段。★ title 漏了**不报错**、只是任务界面显示"未命名"（2026-09-22 真踩过）
            //   —— 所以必须在这里拦住。
            for (String need : new String[]{"id", "filename", "group", "title", "quests"}) {
                if (!m.containsKey(need)) { System.out.println("  [FAIL] 缺字段 " + need); bad++; }
            }
            Object qs = m.get("quests");
            if (!(qs instanceof List<?> list)) { System.out.println("  [FAIL] quests 不是 list"); bad++; continue; }
            System.out.println("  章节 id=" + m.get("id") + "  标题=" + m.get("title")
                    + "  filename=" + m.get("filename")
                    + "  group=" + m.get("group") + "  节点数=" + list.size());

            Set<String> ids = new LinkedHashSet<>();
            Set<String> depTargets = new LinkedHashSet<>();
            List<String> titles = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> q = (Map<String, Object>) o;
                String id = str(q.get("id"));
                String title = str(q.get("title"));
                ids.add(id);
                titles.add(title);
                Object deps = q.get("dependencies");
                if (deps instanceof List<?> dl) for (Object d : dl) depTargets.add(str(d));
                Object tasks = q.get("tasks");
                Object rewards = q.get("rewards");
                Object desc = q.get("description");
                boolean ok = id != null && title != null && desc instanceof List && tasks instanceof List
                        && !((List<?>) tasks).isEmpty() && rewards instanceof List && !((List<?>) rewards).isEmpty();
                System.out.println("    " + (ok ? "[ok]" : "[FAIL]") + " id=" + id + " 标题=" + title
                        + " 坐标=" + q.get("x") + "," + q.get("y")
                        + " 提示=" + (desc instanceof List<?> d ? d : "缺")
                        + " 任务类型=" + taskTypes(tasks) + " 奖励=" + rewardItems(rewards));
                if (!ok) bad++;
            }
            // 依赖完整性
            Set<String> dangling = new LinkedHashSet<>(depTargets);
            dangling.removeAll(ids);
            if (dangling.isEmpty()) {
                System.out.println("  [ok] 依赖链完整（无悬空引用），顺序: " + titles);
            } else {
                System.out.println("  [FAIL] 悬空依赖: " + dangling);
                bad++;
            }

            // ★ 章节的 group 必须真的存在（跨文件）。FTB 会自己重写分组 id，
            //   手写的 id 一旦被换掉，这里就会悬空、章节挂不进任何分组。
            if (!knownGroups.isEmpty()) {
                String g = str(m.get("group"));
                if (g == null || g.isEmpty()) {
                    System.out.println("  [FAIL] 章节没有 group（不会出现在任何分组里）");
                    bad++;
                } else if (knownGroups.contains(g)) {
                    System.out.println("  [ok] group " + g + " 在 chapter_groups.snbt 里存在");
                } else {
                    System.out.println("  [FAIL] group " + g + " 不存在于 chapter_groups.snbt —— 章节挂不进去！"
                            + "\n         现有分组: " + knownGroups
                            + "\n         （FTB 会重写分组 id：改完分组后要 sync pull 再对齐章节的 group）");
                    bad++;
                }
            }

            // ★ 视觉顺序：★ **y 越小越靠上**（2026-09-22 由用户实机观察确定，两次纠正后定案）。
            //
            //   踩坑记录：我一开始按"y 越小越靠下"（Minecraft 屏幕坐标的直觉）排，
            //   结果第一个节点跑到了最下面。进游戏实测：`挥戈 y=0` 在最上、`两个天才 y=8` 在下面
            //   ⇒ 屏幕上 **y 越大越靠下**，所以"第一个节点要 y 最小"。
            //   （我一度读字节码以为反过来 —— 以用户屏幕为准，别跟实机较劲。）
            List<Object[]> byY = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> q = (Map<String, Object>) o;
                byY.add(new Object[]{num(q.get("y")), str(q.get("title"))});
            }
            byY.sort((a, b) -> Double.compare((double) a[0], (double) b[0])); // y 小的在前（= 视觉在上）
            List<String> visual = new ArrayList<>();
            for (Object[] pair : byY) visual.add((String) pair[1]);
            if (visual.equals(titles)) {
                System.out.println("  [ok] 自上而下的视觉顺序与剧情顺序一致: " + visual);
            } else {
                System.out.println("  [FAIL] 视觉顺序与剧情顺序不一致！"
                        + "\n         剧情顺序: " + titles + "\n         屏幕从上到下: " + visual
                        + "\n         （y 越小越靠上 → 第一个节点应该 y 最小）");
                bad++;
            }
        }
        System.out.println(bad == 0 ? "ALL OK" : (bad + " 处问题"));
    }

    /** 把 SNBT 的数值文本（可能是 "8.0d" / 8 / "true"）转成 double。 */
    static double num(Object o) {
        if (o == null) return 0;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return 0;
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) s = s.substring(0, s.length() - 1);
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    static String str(Object o) { return o == null ? null : String.valueOf(o); }

    static String taskTypes(Object tasks) {
        if (!(tasks instanceof List<?> l)) return "缺";
        List<String> out = new ArrayList<>();
        for (Object o : l) if (o instanceof Map<?, ?> mm) out.add(String.valueOf(mm.get("type")));
        return out.toString();
    }

    static String rewardItems(Object rewards) {
        if (!(rewards instanceof List<?> l)) return "缺";
        List<String> out = new ArrayList<>();
        for (Object o : l) if (o instanceof Map<?, ?> mm) out.add(String.valueOf(mm.get("item")));
        return out.toString();
    }
}
