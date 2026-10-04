package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「实体有类型、客户端没渲染器 ⇒ 一进世界就卡退」的离线闸门</b> ✓。
 *
 * <h2>为什么要有这条测试（同一个坑已经崩了三次 ✗）</h2>
 * <ol>
 *   <li>2026-09-30 {@code yan_dark}：渲染器那一行被塞进 if/else 的 else 里 ✗
 *       ⇒ 本包装了 GeckoLib（只走 if ✓）⇒ 永远没有渲染器 ✗（crash-2026-09-30_18.37.25-client.txt ✓）；</li>
 *   <li>光龙（GeckoLib 版）：以为"原版 Display 会自己画" ✗ ⇒ 同样 null ✗；</li>
 *   <li>2026-10-04 作者："<b>释放龙法术会卡退</b>" ✓ —— 龙换成 {@code tnc:dragon} 之后，
 *       客户端那一行**没进那次构建** ✗ ⇒ 崩报里
 *       {@code NullPointerException: ... because "entityrenderer" is null}
 *       @ {@code EntityRenderDispatcher.render:127} ✓。</li>
 * </ol>
 *
 * <p>原版那几处渲染路径**不做 null 检查** ✗（尤其 Oculus/Iris 的影子阶段会主动调
 * {@code shouldRender}），所以只要漏一个渲染器，玩家就是**直接崩游戏** ✗，不是"看不见" ✗。
 *
 * <h2>这条测试怎么把关</h2>
 * 直接读源码 ✓：把 {@code TNOrbEntities} 里注册的**每一个**实体类型字段列出来，
 * 再去全仓扫 {@code registerEntityRenderer(TNOrbEntities.XXX...)} ✓，两边对不上就红 ✓。
 * 纯文本检查 ⇒ 不需要起客户端、不需要进游戏 ✓（这种事进游戏才知道就太晚了 ✗）。
 */
class OrbEntityRendererCoverageTest {

    /** {@code public static final RegistryObject<EntityType<...>> FIELD = ...} 的字段名 ✓。 */
    private static final Pattern DECLARED =
            Pattern.compile("RegistryObject<EntityType<[^;=]*>>\\s+([A-Z][A-Z_0-9]*)\\s*=");

    /** {@code registerEntityRenderer( ... TNOrbEntities.FIELD} 的字段名 ✓。 */
    private static final Pattern RENDERED =
            Pattern.compile("registerEntityRenderer\\(\\s*(?:[a-zA-Z0-9_.]+\\.)?TNOrbEntities\\.([A-Z][A-Z_0-9]*)");

    @Test
    void everySpellEntityTypeHasAClientRenderer() throws Exception {
        Set<String> declared = new LinkedHashSet<>();
        for (String line : Files.readAllLines(
                Path.of("src/main/java/com/tnc/tnc/magic/TNOrbEntities.java"), StandardCharsets.UTF_8)) {
            Matcher m = DECLARED.matcher(line);
            if (m.find()) {
                declared.add(m.group(1));
            }
        }
        assertFalse(declared.isEmpty(), "没读到任何实体类型字段 ✗（TNOrbEntities 的结构变了？）");

        Set<String> rendered = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/tnc/tnc"))) {
            List<Path> java = files.filter(f -> f.toString().endsWith(".java")).toList();
            for (Path file : java) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = RENDERED.matcher(text);
                while (m.find()) {
                    rendered.add(m.group(1));
                }
            }
        }

        Set<String> missing = new LinkedHashSet<>(declared);
        missing.removeAll(rendered);
        assertTrue(missing.isEmpty(),
                "这些实体类型没有客户端渲染器 ✗ ⇒ 一生成就 NullPointerException 卡退 ✗：" + missing);
    }
}
