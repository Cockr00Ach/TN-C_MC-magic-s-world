package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「衍的法术有声音、打雷有雷声」的离线闸门</b> ✓（作者 2026-10-21：
 * "为衍的法术增加音效，增加雷声"）。
 *
 * <h2>为什么要有这条测试</h2>
 * 音效是**最容易悄悄丢**的一类东西 ✗：删掉一行 {@code playSound} 不会编译报错、
 * 不会崩、不会有日志 —— 只有打起来才发现"怎么没声了" ✗。而黑暗衍的四个技能是它
 * **自己实现**的（不走引擎的法术系统 ✓），引擎那边给的法术音效一个都用不上，
 * 全靠 {@code YanDarkBossEntity} 里那几句 ✓。
 *
 * <p>所以这里直接读源码把关（和 {@code OrbEntityRendererCoverageTest} 同一套路 ✓）：
 * <ol>
 *   <li>四个技能**各自**都要出声 ✓（不能四个共用一声"蓄能"就完事 ✗）；</li>
 *   <li>施法那一下要有"报点"声 ⇒ 不用看画面也能听出他要放哪一招 ✓；</li>
 *   <li>落雷实体要在**落地之后**补一声滚雷 ✓（"先见白闪、后闻轰隆"✓），
 *       而且要有节流（天打五雷轰 12 道齐落 ⇒ 不加闸就是 12 声叠在一起 ✗✗）；</li>
 *   <li>二阶段"万雷归体"那一下要有声 ✓。</li>
 * </ol>
 */
class YanDarkSpellAudioTest {

    private static final Path BOSS =
            Path.of("src/main/java/com/tnc/tnc/boss/YanDarkBossEntity.java");
    private static final Path STRIKE =
            Path.of("src/main/java/com/tnc/tnc/magic/TNLightningStrikeEntity.java");

    /** 四个技能方法名 ✓（和 {@code castRandomSpell} 的 switch 一一对应）。 */
    private static final String[] SPELLS = {"castStorm", "castHeavenlyThunder", "castGodDescent", "castBlink"};

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 取一个方法的完整方法体（按花括号配对；跳过注释、字符串、字符字面量 ✓）。 */
    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "找不到方法 " + signature + " ✗（名字改了？那这条测试也要跟着改 ✓）");
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int eol = source.indexOf('\n', i);
                i = eol < 0 ? source.length() - 1 : eol;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                for (i++; i < source.length(); i++) {
                    char d = source.charAt(i);
                    if (d == '\\') {
                        i++;
                    } else if (d == quote) {
                        break;
                    }
                }
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("方法 " + signature + " 的花括号没配对上 ✗");
    }

    /** 方法体里出现的 {@code SoundEvents.XXX} 事件名 ✓。 */
    private static Set<String> soundsIn(String body) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = Pattern.compile("SoundEvents\\.([A-Z][A-Z_0-9]*)").matcher(body);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    void everySpellMakesItsOwnNoise() throws Exception {
        String boss = read(BOSS);
        for (String spell : SPELLS) {
            String body = body(boss, "private void " + spell + "(");
            assertTrue(body.contains("playSound"),
                    spell + " 一点声音都没有 ✗（作者要「增加音效」✓）");
            assertFalse(soundsIn(body).isEmpty(),
                    spell + " 里没有 SoundEvents.XXX ✗");
        }
    }

    @Test
    void theBossThrowsRealThunder() throws Exception {
        String boss = read(BOSS);
        int thunder = 0;
        Matcher m = Pattern.compile("SoundEvents\\.LIGHTNING_BOLT_THUNDER").matcher(boss);
        while (m.find()) {
            thunder++;
        }
        assertTrue(thunder >= 3,
                "黑暗衍只有 " + thunder + " 处雷声 ✗ —— 雷暴/天打五雷轰/神在投篮 至少各要一处 ✓");
        assertTrue(boss.contains("SoundEvents.LIGHTNING_BOLT_IMPACT"),
                "少了那一下「啪」（IMPACT）✗ —— 只有「轰」没有「啪」听着不像打雷 ✓");
    }

    @Test
    void castingAnnouncesWhichSpellIsComing() throws Exception {
        String cast = body(read(BOSS), "private void castRandomSpell(");
        for (String tell : new String[]{"LIGHTNING_BOLT_IMPACT", "BEACON_ACTIVATE",
                "CONDUIT_ACTIVATE", "ENDERMAN_TELEPORT"}) {
            assertTrue(cast.contains("SoundEvents." + tell),
                    "施法那一下少了 " + tell + " 这个「报点」声 ✗ ⇒ 四个技能听起来一模一样 ✓");
        }
        assertTrue(cast.contains("WITHER_SHOOT"),
                "蓄能那一声（凋零发射骷髅头 ✓）被删了 ✗ —— 那是黑暗衍的招牌音 ✓");
    }

    @Test
    void phaseTwoBurstIsAudible() throws Exception {
        String burst = body(read(BOSS), "private void phaseTwoGatherBurst(");
        assertTrue(burst.contains("SoundEvents.LIGHTNING_BOLT_IMPACT"),
                "二阶段「万雷归体」没有干雷那一下 ✗");
        assertTrue(burst.contains("SoundEvents.LIGHTNING_BOLT_THUNDER"),
                "二阶段「万雷归体」没有滚雷 ✗ —— 变身那一下是全流程最该有雷声的地方 ✓");
    }

    @Test
    void theStrikeRumblesAfterItLands() throws Exception {
        String strike = read(STRIKE);
        String rumble = body(strike, "private void playRollingThunder(");
        assertTrue(rumble.contains("SoundEvents.LIGHTNING_BOLT_THUNDER"),
                "落雷落地之后没有雷声 ✗");
        assertTrue(body(strike, "public void tick(").contains("playRollingThunder("),
                "滚雷写好了却没在 tick 里调用 ✗（＝永远不会响 ✓）");
        assertTrue(strike.contains("this.thunderTick = this.tickCount + THUNDER_DELAY"),
                "滚雷没有「隔一拍」（先「啪」后「轰」✓）✗ —— 和 IMPACT 挤在同一 tick 会互相盖掉 ✓");
    }

    @Test
    void thunderDoesNotStackIntoNoise() throws Exception {
        String strike = read(STRIKE);
        String rumble = body(strike, "private void playRollingThunder(");
        assertTrue(rumble.contains("THUNDER_GAP") && rumble.contains("LAST_THUNDER"),
                "滚雷没有节流 ✗ —— 天打五雷轰 12 道同 tick 落地会叠成 12 声噪音 ✗✗");
        assertTrue(rumble.contains("THUNDER_MIN_SCALE") || rumble.contains("isDark()"),
                "滚雷没有「只给大雷/暗色雷」的门槛 ✗ —— 主链一轮十几道小雷会吵死 ✓");
        assertTrue(strike.contains("SoundSource.HOSTILE"),
                "黑暗衍的雷声应该走敌对音源 ✗（玩家自己的雷才是 PLAYERS ✓）");
    }

    @Test
    void theGodsAndTheFallingBallHaveTheirOwnVoices() throws Exception {
        String strike = read(STRIKE);
        assertTrue(strike.contains("SoundEvents.CONDUIT_AMBIENT"),
                "三尊神悬停时没有「神鸣」✗");
        assertTrue(strike.contains("SoundEvents.BEACON_AMBIENT"),
                "神在投篮那颗大雷球下落时没有低频轰鸣 ✗ —— 玩家听不出「有东西正压下来」✓");
    }
}
