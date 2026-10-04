package com.tnc.tnc.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 巨兽人领主「二阶段 + 背后虚影」的离线自检 ✓（作者 2026-10-03 ✓）—— 不用进游戏 ✓。
 *
 * <p>虚影是**渲染**出来的（把同一个已摆好动作的模型再画一遍 ✓），画面我在这边看不到 ✗；
 * 但"什么时候该有虚影"这件事完全在 {@link TNDarkGiantPhase} 里 ✓ ⇒ 那部分必须验死 ✓。
 * 另外顺手验一下作者的动画文件里**确实有**代码里引用的那三个片段 ✓
 * （他要是重新导出成别的名字，这里立刻报出来 ✓，而不是进游戏才发现巨人站着不动 ✗）。
 */
class DarkGiantPhaseTest {

    @Test
    void phaseTwoTriggersExactlyOnceAtHalfHealth() {
        float max = TNDarkGiantEntity.MAX_HP;
        // 3/4 血：还没到 ✓
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(max * 0.75F, max, TNDarkGiantPhase.PHASE_ONE));
        // 刚好一半 / 更低：进 ✓
        assertTrue(TNDarkGiantPhase.shouldEnterPhaseTwo(max * 0.5F, max, TNDarkGiantPhase.PHASE_ONE));
        assertTrue(TNDarkGiantPhase.shouldEnterPhaseTwo(1.0F, max, TNDarkGiantPhase.PHASE_ONE));
        // 已经在二阶段：再也不触发 ✓（不会来回横跳 ✗）
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(1.0F, max, TNDarkGiantPhase.PHASE_TWO));
        // 死透了 / 数据坏了：不触发 ✓
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(0.0F, max, TNDarkGiantPhase.PHASE_ONE));
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(10.0F, 0.0F, TNDarkGiantPhase.PHASE_ONE));
    }

    @Test
    void phantomOnlyExistsInPhaseTwo() {
        assertFalse(TNDarkGiantPhase.phantomVisible(TNDarkGiantPhase.PHASE_ONE));
        assertTrue(TNDarkGiantPhase.phantomVisible(TNDarkGiantPhase.PHASE_TWO));
        // 作者要的是"他自己的两倍高" ✓
        assertEquals(2.0D, TNDarkGiantPhase.PHANTOM_SCALE, 1.0E-6D);
        // 半透明（画成不透明就不叫虚影了 ✗）
        assertTrue(TNDarkGiantPhase.PHANTOM_ALPHA > 0.1F && TNDarkGiantPhase.PHANTOM_ALPHA < 0.7F,
                "虚影透明度要在 0.1~0.7 之间（太小看不见 ✗ 太大像实体 ✗）");
        // 呼吸透明度永远是个合法 alpha ✓
        for (int t = 0; t < 4000; t++) {
            float a = TNDarkGiantPhase.phantomAlpha(t);
            assertTrue(a > 0.05F && a < 0.95F, "tick=" + t + " 的虚影 alpha 越界：" + a);
        }
    }

    @Test
    void authorsAnimationClipsStillExist() throws Exception {
        JsonObject animations = JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for (String clip : new String[] {TNDarkGiantEntity.ANIM_WALK, TNDarkGiantEntity.ANIM_STOMP,
                TNDarkGiantEntity.ANIM_MAGIC, TNDarkGiantEntity.ANIM_SUMMON}) {
            assertTrue(animations.has(clip),
                    "动作文件里没有 '" + clip + "' ✗（作者换名字了？改 TNDarkGiantEntity 里的常量 ✓）");
        }
        // 模型也得在 ✓（骨头名变了没关系 —— 渲染器 crashIfBoneMissing=false ✓）
        assertTrue(Files.exists(Path.of("src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json")),
                "模型文件不见了 ✗");
        // ★ 作者 2026-10-03："谁让你把践踏当走路了" ✗ —— 这两件事必须是**两个**片段 ✓
        assertFalse(TNDarkGiantEntity.ANIM_WALK.equals(TNDarkGiantEntity.ANIM_STOMP),
                "走路又变回践踏了 ✗（walk 是 tools/gen_dark_giant_walk.ps1 生成的 ✓）");
        assertTrue(TNDarkGiantSpells.count() >= 5, "招式表至少要覆盖作者点名的五招 ✓");
    }

    /**
     * 招式表里的档位必须跟法术 json 的 {@code learn.tier} 对得上 ✓ ——
     * 作者要的是"暗龙 t5 · 召唤 t4/t5 · 手 t3/t4" ✓，而我一开始是**按模型大小猜**手链档位的 ✗：
     * 真相是手 t3 = {@code night_embrace} ✓、t4 = {@code black_ruin} ✓（{@code slay_light} 才是 t5 ✗）。
     * 这条测试就是防止以后再猜错 ✓。
     */
    @Test
    void movesMatchTheSpellJsonTiers() throws Exception {
        int checked = 0;
        for (String path : TNDarkGiantSpells.paths()) {
            Path json = Path.of("src/main/resources/data/tnc/spells", path + ".json");
            if (!Files.exists(json)) {
                continue;   // 召唤链的 dark_king / evil_god 还没有 json ✓（链自己认得这两个 path ✓）
            }
            JsonObject spell = JsonParser.parseString(Files.readString(json, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")).getAsJsonObject();
            int tier = spell.getAsJsonObject("learn").get("tier").getAsInt();
            assertEquals(TNDarkGiantSpells.tierOf(path), tier,
                    path + " 的档位跟 json 对不上 ✗（作者要的档位在 TNDarkGiantSpells.MOVES 里 ✓）");
            checked++;
        }
        assertTrue(checked >= 3, "至少该核对上手/暗龙那几招 ✓（现在只核对了 " + checked + " 招 ✗）");
    }
}
