package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>资源里一个 BOM 都不许有</b> ✗ —— 作者 2026-10-04："黑夜之手在魔法石界面显示<b>尚未实装</b>" ✗。
 *
 * <h2>真因</h2>
 * 那 5 个法术 json 开头带了 <b>UTF-8 BOM</b>（{@code EF BB BF} ✓）—— 原版/Forge 读大多数资源时
 * 用的是"容忍 BOM"的读取器 ✓，但 <b>SpellEngine 用 Gson 直接解析法术 json</b> ✗：
 * <pre>
 * Spell Engine: Failed to parse spell: tnc:spells/night_hand.json
 *   | Reason: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $
 * </pre>
 * ⇒ 引擎注册表里**根本没有这个法术** ✗ ⇒ 魔法石界面走
 * {@code MagicStoneLearning.check()} 的防呆闸门（{@code SpellEngineBridge.hasSpell()} ✗）
 * ⇒ 显示"尚未实装"、学不了、也放不出 ✗。
 *
 * <p>★ 这种毛病**编译、启动、看日志都很难注意到** ✗（json 本身完全合法 ✓，只是头上有 3 个字节 ✗），
 * 所以拿一条测试钉死 ✓：整个 {@code src/main/resources} 里任何一个 {@code .json} 带 BOM 就红 ✓。
 */
class ResourceBomTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void noResourceJsonStartsWithAByteOrderMark() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                scanned++;
                byte[] head = new byte[3];
                try (var in = Files.newInputStream(file)) {
                    if (in.readNBytes(head, 0, 3) == 3
                            && head[0] == BOM[0] && head[1] == BOM[1] && head[2] == BOM[2]) {
                        offenders.add(file.toString());
                    }
                }
            }
        }
        assertTrue(scanned > 100, "应该扫到几百个 json 才对（实际 " + scanned + " 个 ✗）");
        assertTrue(offenders.isEmpty(),
                "这些 json 带 UTF-8 BOM ✗ ⇒ SpellEngine 之类的 Gson 解析器会整个拒收 ✗："
                        + offenders + "（跑 tools/strip_bom.ps1 清掉 ✓）");
    }
}
