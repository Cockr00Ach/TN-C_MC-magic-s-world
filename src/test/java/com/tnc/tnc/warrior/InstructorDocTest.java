package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 课程表文档必须和 {@link InstructorCourses} 一致。
 *
 * <p>为什么要测：课程表是"两处各写一遍"的东西——代码里一份、{@code docs/warrior/教官课程-*.md}
 * 里一份。2026-11-03 改课的时候，代码已经变成"第二课 · 振刀 2 次"，文档还写着旧的目标数，
 * 玩家照着文档练会觉得"我都打三下了怎么还不过"。这种错不会让任何测试变红，只能专门钉住。
 *
 * <p>只认表格行（第一格是教官、第二格是课名、第三格是"5 次"）：正文里还有大段说明，
 * 靠猜数字会一直误报；玩家真正照着做的是表格。
 */
class InstructorDocTest {
    private static final Path DOC = Path.of("docs", "warrior", "教官课程-20261103.md");
    /** 文档里写的是人话名字，代码里是角色 id。 */
    private static final Map<String, String> ROLE_BY_NAME = Map.of(
        "洛克", "guard", "莉娅", "blade", "岚", "wind", "陪练", "counter");
    private static final Pattern GOAL = Pattern.compile("(\\d+)\\s*次");

    @Test
    void courseTableInTheDocMatchesTheCatalog() throws IOException {
        assertTrue(Files.exists(DOC), DOC + " 不见了：文档改名要同步改这个测试");

        Map<String, List<String>> want = new LinkedHashMap<>();
        for (String role : InstructorCourses.roles()) {
            List<String> rows = new ArrayList<>();
            for (var course : InstructorCourses.of(role)) {
                rows.add(course.title() + "|" + course.goal());
            }
            want.put(role, rows);
        }
        assertFalse(want.isEmpty(), "课程表是空的");

        Map<String, List<String>> got = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        String role = null;
        List<String> lines = Files.readAllLines(DOC, StandardCharsets.UTF_8);
        for (int number = 0; number < lines.size(); number++) {
            String stripped = lines.get(number).strip();
            if (!stripped.startsWith("|")) {
                continue;
            }
            String[] cells = stripped.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
            if (cells.length < 3) {
                continue;
            }
            for (int index = 0; index < cells.length; index++) {
                cells[index] = cells[index].replace("**", "").replace("`", "").strip();
            }
            String head = cells[0];
            if (head.contains("教官") || head.startsWith("---")) {
                continue; // 表头 / 分隔行
            }
            if (!head.isEmpty()) {
                String matched = null;
                for (var entry : ROLE_BY_NAME.entrySet()) {
                    if (head.contains(entry.getKey())) {
                        matched = entry.getValue();
                    }
                }
                if (matched == null) {
                    continue;
                }
                role = matched;
            }
            Matcher goal = GOAL.matcher(cells[2]);
            if (role == null || !goal.find()) {
                continue;
            }
            got.computeIfAbsent(role, key -> new ArrayList<>()).add(cells[1] + "|" + goal.group(1));
        }

        for (String each : InstructorCourses.roles()) {
            List<String> expected = want.get(each);
            List<String> actual = got.getOrDefault(each, List.of());
            if (!expected.equals(actual)) {
                problems.add("「" + each + "」文档写的是 " + actual + "，代码里是 " + expected);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        // 11 节课是 2026-11-03 的实际节数；掉下来往往说明表格被重排成列表了，而不是"没问题"。
        int rows = got.values().stream().mapToInt(List::size).sum();
        assertTrue(rows >= 11, "只在文档里找到 " + rows + " 节课，太少，检查是不是换了写法");
    }
}
