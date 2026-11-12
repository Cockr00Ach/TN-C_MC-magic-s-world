package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风系"前戏"（施法前摇法阵）的守门测试，2026-11-07。
 *
 * <p>风系以前是唯一没有前戏的元素：读条时玩家脚下什么都没有，法阵只在技能已经打出去
 * 之后才随 {@code WindEarthVisualEntity} 出现 ✗。这次补上 {@code WindCastFx}（客户端
 * 即时几何，不生成任何实体）+ {@code WindSpiritGhost}（复用真模型的风灵虚影）。
 *
 * <p>这里钉住四件事，防止以后被"顺手优化"掉：
 * <ol>
 *   <li>前戏法阵和落地法阵必须是<b>同一份几何</b>（{@code WindEarthGeometry.windSeal}）——
 *       否则读条时是一种花纹、打出去变成另一种，玩家会觉得"两个法术"；</li>
 *   <li>前戏<b>不许生成实体</b>（服务端不同步、会留在存档里、会被清除指令带走）；</li>
 *   <li>风灵虚影必须用<b>真模型</b>（geo + 贴图 + 高阶形态），不能用几个四边形糊一个替身；</li>
 *   <li>风灵模型必须保留 idle 动画引用的骨骼名，否则 GeckoLib 会静默不动（或直接崩）。</li>
 * </ol>
 */
class WindCastFxTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tnc/tnc/" + path));
    }

    private static String asset(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/assets/tnc/" + path));
    }

    @Test
    void windPreludeReusesTheSameSealGeometryAsTheField() throws Exception {
        String fx = source("client/WindCastFx.java");
        assertTrue(fx.contains("WindEarthGeometry.windSeal("),
                "风系前戏必须画 WindEarthGeometry.windSeal —— 和落地法阵同一份几何");
        String solid = source("client/WindSolidModels.java");
        assertTrue(solid.contains("WindEarthGeometry.windSeal("),
                "WindSolidModels.seal 也必须走同一份几何，前戏和落地才会读成「同一个阵在长」");
        assertTrue(fx.contains("chargeGlow(info.ratio())"),
                "阵的大小要跟着读条比例长，不能一上来就是满尺寸");
    }

    @Test
    void preludeNeverSpawnsEntitiesOnTheClient() throws Exception {
        String fx = source("client/WindCastFx.java");
        for (String forbidden : new String[]{"addFreshEntity", "new WindEarthVisualEntity",
                "new TNWindSpiritEntity", "EntityType.create"}) {
            assertFalse(fx.contains(forbidden),
                    "前戏是纯客户端渲染，不该出现 " + forbidden + "（会在存档里留下幽灵实体）");
        }
        assertTrue(fx.contains("WindSpiritGhost.render("),
                "风灵虚影要走 WindSpiritGhost，不要自己拼四边形");
    }

    @Test
    void spiritGhostBorrowsTheRealGeoModelAndItsHighTierForm() throws Exception {
        String ghost = source("client/WindSpiritGhost.java");
        assertTrue(ghost.contains("geo/entity/wind_sprite.geo.json") && ghost.contains("wind_sprite_bedrock.png"),
                "风灵虚影必须用真的 geo/贴图，不然虚影和召唤出来的不是同一只风灵");
        assertTrue(ghost.contains("previewTier(5)"),
                "1/2 级风灵在游戏里渲染成一颗风球；前戏要的是「风灵本体」，所以固定高阶形态");
        assertTrue(ghost.contains("getRenderer(") && ghost.contains("renderRecursively("),
                "虚影是借真模型渲染，走 GeckoLib 的 renderRecursively");
    }

    @Test
    void spriteModelKeepsEveryBoneTheIdleClipDrives() throws Exception {
        String geo = asset("geo/entity/wind_sprite.geo.json");
        for (String bone : new String[]{"\"body\"", "\"wing_left\"", "\"wing_right\"",
                "\"crest\"", "\"tail\"", "\"tail_left\"", "\"tail_right\""}) {
            assertTrue(geo.contains(bone), "wind_sprite 少了骨骼 " + bone + "，idle 动画会失效");
        }
        assertTrue(Files.size(Path.of("src/main/resources/assets/tnc/textures/entity/wind_sprite_bedrock.png")) > 256,
                "风灵贴图不能是空文件（旧版就是一张 64x64 的纯色图）");
    }
}
