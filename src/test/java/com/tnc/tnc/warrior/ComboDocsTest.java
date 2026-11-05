package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 文档里的连招表格必须和 {@link WarriorCombos} 一致。
 *
 * <p>为什么要测这个：连招的按键是"两处各写一遍"的东西——代码里一份、给玩家看的 markdown 里一份。
 * 2026-11-01 就把旋转斩击从 {@code QEZZZZ} 改成了 {@code QEQZZZ}，代码改了、文档没改，
 * 结果是玩家照着文档按不出来，只会觉得自己手慢。这种错不会让任何测试变红，只能专门钉住。
 *
 * <p>只认表格行（一格是连招名、另一格只有按键）：正文里还有 {@code 长剑EQ长E → 振刀}
 * 这种"动画名"写法，靠猜名字和按键的距离会一直误报；玩家真正照着按的就是表格。
 */
class ComboDocsTest {
    private static final Pattern KEYS = Pattern.compile("[QZE]{2,6}");
    private static final Path DOC_DIR = Path.of("docs", "warrior");

    private record Row(String doc, int line, String name, String keys) {}

    @Test
    void everyCombosTableInTheDocsMatchesTheCode() throws IOException {
        Map<String, String> truth = new LinkedHashMap<>();
        for (WarriorSkill skill : WarriorCombos.all()) {
            truth.put(skill.name(), skill.sequenceLabel());
        }
        assertFalse(truth.isEmpty(), "连招目录是空的");

        List<Row> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<Path> docs;
        try (Stream<Path> stream = Files.list(DOC_DIR)) {
            docs = stream.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }
        assertFalse(docs.isEmpty(), DOC_DIR + " 下没有 markdown，路径变了要同步改这个测试");

        for (Path doc : docs) {
            List<String> lines = Files.readAllLines(doc, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                Row row = row(lines.get(index), doc.getFileName().toString(), index + 1, truth);
                if (row == null) {
                    continue;
                }
                rows.add(row);
                String expected = truth.get(row.name());
                if (!expected.equals(row.keys())) {
                    problems.add(row.doc() + ":" + row.line() + " 「" + row.name() + "」写成 "
                        + row.keys() + "，代码里是 " + expected);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        // 27 行是 2026-11-03 的实际行数；行数掉下来往往说明文档被重排成列表了，而不是"没问题"。
        assertTrue(rows.size() >= 20, "只在文档里找到 " + rows.size() + " 行连招表格，太少，检查是不是换了写法");
    }

    private static Row row(String line, String doc, int number, Map<String, String> truth) {
        String stripped = line.strip();
        if (!stripped.startsWith("|")) {
            return null;
        }
        String[] cells = stripped.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
        String name = null;
        List<int[]> keyCells = new ArrayList<>();
        List<String> keyText = new ArrayList<>();
        for (int index = 0; index < cells.length; index++) {
            String cell = cells[index].replace("**", "").replace("`", "")
                .replace("「", "").replace("」", "").strip();
            if (truth.containsKey(cell)) {
                if (name != null) {
                    return null; // 一行里两个连招名：分不清哪一行算谁的，跳过
                }
                name = cell;
            } else if (KEYS.matcher(cell).matches()) {
                keyCells.add(new int[]{index});
                keyText.add(cell);
            }
        }
        if (name == null || keyCells.isEmpty()) {
            return null;
        }
        int nameIndex = -1;
        for (int index = 0; index < cells.length; index++) {
            if (truth.containsKey(cells[index].replace("**", "").replace("`", "").strip())) {
                nameIndex = index;
                break;
            }
        }
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < keyCells.size(); index++) {
            int distance = Math.abs(keyCells.get(index)[0] - nameIndex);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return new Row(doc, number, name, keyText.get(best));
    }
}
