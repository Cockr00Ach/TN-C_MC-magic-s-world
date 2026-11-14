package com.tnc.tnc.magic.thunderseal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「雷锲要有天雷砸地、六个就是六道」的离线闸门</b> ✓（作者 2026-11-07：
 * 「那个雷锲就不要从地底冒出来吧……一个天雷砸地，然后汇聚出一个雷锲如何」／「六个就是六道」）。
 *
 * <h2>这条测试守什么</h2>
 * <ol>
 *   <li><b>一楔一道雷</b>：雷数 = 楔数（t4 六个楔 = 六道雷、t5 九个楔 = 九道雷），
 *       不是写死"五道"✗ —— 以后调 {@link ThunderSealRules#count(int)} 雷数自动跟上 ✓；</li>
 *   <li><b>错开落地</b>：越靠后的楔越晚落（各差 2 tick），而且**全部在第一次脉冲之前成型**
 *       （不然雷还没聚完就开始掉血，看的人会懵 ✗）；</li>
 *   <li><b>模型不再从地底往上滑</b> ✗ —— 这条只能读源码把关（和 {@code YanDarkSpellAudioTest}
 *       同一套路：删掉一句渲染代码不会编译报错、不会崩，只有看画面才发现"怎么又冒出来了"✗）；</li>
 *   <li><b>天打五雷轰的次数</b>：作者嫌少 ⇒ 现在明显多于原来那 6 道，且衍 boss 那 12 道要分波。</li>
 * </ol>
 */
class ThunderSealBirthTest {

    private static final Path RENDERER =
            Path.of("src/main/java/com/tnc/tnc/magic/thunderseal/client/ThunderSealRenderer.java");
    private static final Path BOSS =
            Path.of("src/main/java/com/tnc/tnc/boss/YanDarkBossEntity.java");

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void oneWedgeMeansOneSkyBolt() {
        for (int tier = 1; tier <= 5; tier++) {
            int wedges = ThunderSealRules.count(tier);
            assertTrue(wedges > 0, "t" + tier + " 一个雷楔都没有 ✗");
            for (int slot = 0; slot < wedges; slot++) {
                double at = ThunderSealRules.strikeTick(slot);
                assertTrue(at >= 0.0D, "t" + tier + " 第 " + slot + " 个楔的天雷落点是负数 ✗");
                assertTrue(at + ThunderSealRules.birthTicks() <= ThunderSealRules.charge(tier),
                        "t" + tier + " 第 " + slot + " 个楔要等到 " + (at + ThunderSealRules.birthTicks())
                                + " tick 才成型，但 " + ThunderSealRules.charge(tier)
                                + " tick 就开始脉冲了 ✗ —— 雷还没聚完就掉血 ✓");
            }
        }
        // "六个就是六道" —— 这两档是作者原话点名的 ✓
        assertEquals(6, ThunderSealRules.count(4), "t4 应该是六个楔（六道雷）✗");
        assertEquals(9, ThunderSealRules.count(5), "t5 应该是九个楔（九道雷）✗");
    }

    @Test
    void skyBoltsLandInOrder() {
        for (int tier = 1; tier <= 5; tier++) {
            int wedges = ThunderSealRules.count(tier);
            double prev = -1.0D;
            for (int slot = 0; slot < wedges; slot++) {
                double at = ThunderSealRules.strikeTick(slot);
                assertTrue(at > prev, "t" + tier + " 第 " + slot + " 道天雷没有比上一道晚 ✗"
                        + " —— 同时落就是一团白闪，听不出「六道」✓");
                prev = at;
            }
        }
        assertEquals(2.0D, ThunderSealRules.strikeTick(0), "第一道天雷应该第 2 tick 就砸下来 ✗");
        assertEquals(2.0D, ThunderSealRules.strikeTick(1) - ThunderSealRules.strikeTick(0),
                "相邻两道天雷应该差 2 tick ✗");
        assertTrue(ThunderSealRules.birthTicks() > 0.0D, "「闪出/成型」的窗口不能是 0 ✗");
    }

    @Test
    void theWedgeFlashesInInsteadOfGrowing() throws Exception {
        String renderer = read(RENDERER);
        assertFalse(renderer.contains("node.y - (1.0D - growth) * height"),
                "模型又从地底往上滑了 ✗ —— 作者明确说「不要从地底冒出来」✓");
        assertTrue(renderer.contains("ThunderSealRules.strikeTick("),
                "渲染器没用统一的落地时刻表 ✗ ⇒ 雷数和楔数会对不上 ✓");
        // 天雷砸地那一下：天上的雷 ＋ 落点白闪
        assertTrue(renderer.contains("600L + slot"), "每个楔自己那道天雷不见了 ✗");
        assertTrue(renderer.contains("groundFlash"), "天雷砸地没有落点白闪 ✗");
        // 2026-11-08 作者：「雷锲的出现直接闪出就行，你不要雷劈了之后还从地里长出来」⇒
        // 任何"长"的痕迹都不许回来：缩放式长大、按出生进度淡入、往楔轴上收的短雷
        assertFalse(renderer.contains("float pop ="), "楔子又开始 pop 式长大了 ✗");
        assertFalse(renderer.contains("Math.max(0.2F, born)"),
                "楔子还在按 born 淡入（＝一边淡一边长大）✗");
        assertFalse(renderer.contains("800L + slot"),
                "「往楔轴上收」的短雷又回来了 ✗ —— 那一下就是「从地里长出来」的读法");
        assertTrue(renderer.contains("float scale = (float) (height / 1.9D);"),
                "楔子本体应该是满尺寸直接闪出，不能带缩放 ✗");
        assertTrue(renderer.contains("900L + slot"),
                "向外炸开的雷环不见了 ✗ —— 现在是「炸开」，不再是「收拢」");
    }

    @Test
    void heavenlyThunderStrikesFarMoreOften() throws Exception {
        int bolts = com.tnc.tnc.magic.TnSpellMechanics.HEAVENLY_THUNDER_BOLTS;
        assertTrue(bolts >= 15, "天打五雷轰只有 " + bolts + " 道 ✗ —— 作者嫌少，现在是 19 道 ✓");
        // 衍 boss 那 12 道：分波（每 4 道一波）才听得出"轰轰轰"✓
        String boss = read(BOSS);
        assertTrue(boss.contains("queueHeavenlyVolley(level, at, 12)"),
                "衍 boss 的天打五雷轰没有走分波入口 ✗");
        assertTrue(boss.contains("(i / 4) * 4L"),
                "衍 boss 的 12 道雷又挤回同一 tick 了 ✗ —— 那样只有一声「轰」✓");
        assertTrue(boss.contains("tickPendingStrikes(server)"),
                "分波的雷排好了却没人每 tick 清 ✗（＝永远不会落 ✓）");
    }
}
