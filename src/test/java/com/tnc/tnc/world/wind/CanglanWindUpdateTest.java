package com.tnc.tnc.world.wind;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风系 Boss（苍岚）跟着玩家风法一起换代的闸门（2026-11-15 作者：
 * 「我们的风系魔法更新过了，那风系boss手里的魔法有更新过吗」）✓。
 *
 * <p>玩家侧这十几轮换了三代：龙卷风＝螺旋折线、风刃＝剑气光带、风球＝漩涡线 ✓。
 * Boss 侧原来只有「风刃」因为共用 {@code TNWindBladeEntity} 被动跟着换了；
 * <b>旋风</b>还在画烘焙模型（7 层薄板）✗、吸力也还是"轻推"✗。
 *
 * <p>这条闸门钉住三件事：
 * <ol>
 *   <li>Boss 旋风走<b>和玩家同一套</b> {@code WindTornadoLines}（不再是 {@code projectile/wind_tornado} 模型）✓</li>
 *   <li>Boss 旋风有<b>强力吸力</b>（走 {@code TornadoSwirlRules}，且比玩家收着点 0.9 倍）✓</li>
 *   <li>Boss 风刃和玩家统一成"顺着风向直射"（{@code setSpinRate(0.0F)}）✓</li>
 * </ol>
 */
class CanglanWindUpdateTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void theBossCycloneUsesTheSameLineArtAsThePlayer() throws Exception {
        String renderer = read("src/main/java/com/tnc/tnc/world/wind/client/CanglanTempestRenderer.java");
        assertTrue(renderer.contains("WindTornadoLines.draw("),
                "Boss 旋风没有走玩家的线龙卷风（还是模型薄板）✗");
        assertTrue(renderer.contains("if (e.tornado())"),
                "没有把「旋风」和「风刃波」分开处理 ✗");
        // 旋风分支里不该再出现 wind_tornado 模型
        int tornadoBranch = renderer.indexOf("renderLineCyclone");
        int modelId = renderer.indexOf("projectile/wind_tornado");
        assertTrue(tornadoBranch > 0, "没有旋风专用的线渲染方法 ✗");
        assertTrue(modelId < 0, "旋风分支里还留着 projectile/wind_tornado 模型 ✗");
        // 2026-11-15「改啊」：风刃波也换成玩家同款剑气 ✓
        assertTrue(renderer.contains("WindBladeQi.draw("),
                "风刃波没有换成剑气（还是模型刀刃）✗");
        assertFalse(renderer.contains("\"tnc\", \"projectile/windblade\"") || renderer.contains("\"projectile/windblade\""),
                "风刃波还在画烘焙模型 ✗");
    }

    @Test
    void theBossCycloneActuallySucksPlayersIn() throws Exception {
        String entity = read("src/main/java/com/tnc/tnc/world/wind/CanglanTempestEntity.java");
        assertTrue(entity.contains("swirlNearby("), "Boss 旋风没有强力吸力 ✗");
        assertTrue(entity.contains("TornadoSwirlRules.velocity("),
                "吸力没有走和玩家同一套规则 ✗");
        assertTrue(entity.contains("0.9D"), "Boss 吸力没有比玩家收着（0.9 倍）⇒ 会把玩家甩飞 ✗");
        assertTrue(entity.contains("fallDistance=0"), "被卷起来的人掉下来会摔死 ✗");
        assertTrue(entity.contains("suctionRadius(") && entity.contains("suctionHeight("),
                "吸力的半径/高度没有跟着 Boss 体型走 ✗");
    }

    /** 风系自身的光环：旧视觉保留 ＋ 叠一层漩涡线（作者 11-10 的"不要替换"规矩）✓ */
    @Test
    void theWindAuraKeepsTheOldModelAndAddsVortexLinesOnTop() throws Exception {
        String renderer = read("src/main/java/com/tnc/tnc/client/WindEarthVisualRenderer.java");
        assertTrue(renderer.contains("WindSolidModels.flight("), "旧的风翼/风场被删了 ✗");
        assertTrue(renderer.contains("WindBallLines.vortex("), "没有叠上新的漩涡线 ✗");
        int oldVisual = renderer.indexOf("WindSolidModels.flight(");
        int lines = renderer.indexOf("windAuraLines(entity,centre");
        assertTrue(oldVisual >= 0 && lines > oldVisual, "顺序反了：应该先画原有、再叠线条 ✗");
        // 「全部改完」：风系剩下两种（召唤小阵 / 狂风波）也叠了新线条 ✓
        assertTrue(renderer.contains("windKindLines("), "kind 1/2 的线条叠加没接上 ✗");
        assertTrue(renderer.contains("WindBladeQi.draw("), "狂风波没有弯月剑气 ✗");
        // 优化：48 格外不画（不然 27 根线在远处白烧）✓
        assertTrue(renderer.contains("48.0D*48.0D"), "没有距离剔除 ⇒ 远处的风系光环白画 ✗");
    }

    @Test
    void theBossBladeStopsSpinningLikeThePlayers() throws Exception {
        String combat = read("src/main/java/com/tnc/tnc/world/wind/WindWardenCombat.java");
        assertFalse(combat.contains("setSpinRate(26.0F)"),
                "Boss 风刃还在 26°/tick 打转（玩家已经改成直射）✗");
        int zero = combat.split("setSpinRate\\(0\\.0F\\)", -1).length - 1;
        assertEquals(2, zero, "Boss 的两发风刃（波＋投矛）应该都改成直射 ✗");
    }
}
