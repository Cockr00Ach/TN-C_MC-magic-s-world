package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水系 Boss（利维坦）与光系 Boss（阿波罗）的读条前戏闸门（2026-11-06）。
 *
 * <p>两个 Boss 都不走 SpellEngine 的 {@code SpellCast.Process}：利维坦用
 * {@code actionTicks} 蓄力，阿波罗用 {@code pendingTicks} 等动画。这里把“服务端同步到客户端”
 * 和“客户端复用同一套潮汐/日轮几何”钉住，避免以后只保留了玩家法术的前戏。
 */
class BossCastFxStyleTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tnc/tnc/" + path));
    }

    @Test
    void leviathanPublishesItsWindupAndGetsABossScaleTideRitual() throws Exception {
        String entity = source("boss/TNWaterLeviathanEntity.java");
        assertTrue(entity.contains("CAST_REMAIN") && entity.contains("CAST_TOTAL")
                        && entity.contains("CAST_VISUAL_TIER") && entity.contains("CAST_VISUAL_FAMILY"),
                "利维坦必须同步剩余/总蓄力、潮汐档位和法术家族");
        assertTrue(entity.contains("beginVisualWindup(") && entity.contains("clearVisualWindup("),
                "蓄力开始/结束必须成对清理，不能留下永久法阵");
        assertTrue(entity.contains("castPose()"), "保留原有嘴部姿态接口");

        String fx = source("client/WaterCastFx.java");
        assertTrue(fx.contains("TNWaterLeviathanEntity") && fx.contains("scanBoss(")
                        && fx.contains("bossRitual("),
                "水法前戏必须识别利维坦并绘制 Boss 尺寸的主阵");
        assertTrue(fx.contains("MOUTH_FORWARD") && fx.contains("MOUTH_HEIGHT"),
                "Boss 的竖直阵应贴在真实嘴部，而不是脚边的小水环");
        assertTrue(fx.contains("castVisualFamily()"), "水缚 Boss 招必须进入 BIND 前戏分支");
    }

    @Test
    void apolloPublishesItsWindupAndUsesTheLightRitual() throws Exception {
        String entity = source("boss/TNApolloEntity.java");
        assertTrue(entity.contains("DATA_CAST_SPELL") && entity.contains("DATA_CAST_TICKS"),
                "阿波罗必须把当前法术和剩余读条同步给客户端");
        assertTrue(entity.contains("castDuration()") && entity.contains("clearCastSync("),
                "客户端要能算读条比例，且释放/取消/死亡都要清理");
        assertTrue(entity.contains("STYLE_LIGHT_GRAND"),
                "召唤读条仍应有 Boss 级的旧光阵，不能退回最低档小阵");

        String fx = source("client/LightCastFx.java");
        assertTrue(fx.contains("TNApolloEntity") && fx.contains("scanApollo(")
                        && fx.contains("apolloRitual("),
                "光法前戏必须识别阿波罗并复用日轮主阵");
        assertTrue(fx.contains("castRemainingTicks()") && fx.contains("castDuration()"),
                "阿波罗的前戏必须跟着真实读条倒计时，而不是固定循环");
    }
}
