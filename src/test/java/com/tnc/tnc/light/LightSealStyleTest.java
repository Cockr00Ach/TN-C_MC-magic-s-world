package com.tnc.tnc.light;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 光法阵的<b>样式规范</b>（作者 2026-11-05：“统一一下光法里的法阵”）。
 *
 * <p>光系只允许四种光阵样式：
 * <ul>
 *   <li>{@code STYLE_LIGHT}：中阶（t3）地面 / 竖立 / 小阵 / 光龙 / Apollo；</li>
 *   <li>{@code STYLE_LIGHT_MINOR}：t1/t2 的低阶简阵（一圈符，没有八角星）；</li>
 *   <li>{@code STYLE_LIGHT_GRAND}：t4/t5 的高阶重阵（多一圈外环与刻痕）；</li>
 *   <li>{@code STYLE_LIGHT_SKY}：悬在落点上方的天阵（同一套几何，但不往上长柱、改往下垂光幕）。</li>
 * </ul>
 * 不允许再借 {@code STYLE_STORM}（雷法线条型）—— 那是暗系召唤与暗龙的样式。
 */
class LightSealStyleTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test void lightBeamSealsUseTheLightStylesOnly() throws Exception {
        String beam = source("src/main/java/com/tnc/tnc/light/TNLightBeamMechanics.java");
        assertTrue(beam.contains("TNMagicCircleEntity.STYLE_LIGHT_SKY"),
                "天上的光阵必须用 STYLE_LIGHT_SKY（同一套白金光阵，往下垂光幕）");
        assertTrue(beam.contains("TNMagicCircleEntity.STYLE_LIGHT_GRAND"),
                "t4/t5 的落点阵必须用高阶阵 STYLE_LIGHT_GRAND");
        assertTrue(beam.contains("TNMagicCircleEntity.STYLE_LIGHT_MINOR"),
                "低阶落点阵应当用简阵 STYLE_LIGHT_MINOR");
        assertFalse(beam.contains("TNMagicCircleEntity.STYLE_STORM"),
                "光系不能再借雷法的 STYLE_STORM");
    }

    @Test void lightChainsAndDragonUseTheLightStyle() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/tnc/tnc/light/TNLightChainMechanics.java",
                "src/main/java/com/tnc/tnc/light/TNLightDragonChain.java"}) {
            String src = source(path);
            assertTrue(src.contains("TNMagicCircleEntity.STYLE_LIGHT"), path + " 必须用光系阵");
            assertFalse(src.contains("TNMagicCircleEntity.STYLE_STORM"), path + " 不能借 STYLE_STORM");
        }
    }

    @Test void lightChainPicksSealDetailByTier() throws Exception {
        String chain = source("src/main/java/com/tnc/tnc/light/TNLightChainMechanics.java");
        assertTrue(chain.contains("LightRitualSequence.rite(tier)"),
                "主阵样式必须由档位仪式（INSCRIPTION/SANCTUARY/DESCENT）决定");
        assertTrue(chain.contains("STYLE_LIGHT_MINOR") && chain.contains("STYLE_LIGHT_GRAND"),
                "低阶/高阶必须落到不同的阵样式，而不是所有档位都用 STYLE_LIGHT");
    }
}
