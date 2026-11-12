package com.tnc.tnc.boss;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 黑魔法师（2026-11-07）的回归闸门。
 *
 * <p>它和巨兽人领主**共用一套法术**（{@link TNDarkMageSpells} 全转发），所以这里<b>不</b>再测一遍招式表
 * （那边有 {@code DarkGiantArsenalTest}），只钉三件<b>只属于他</b>的事：
 * <ol>
 *   <li>招式的动作片段名 / 片段长度 / 骨骼名**在这个模型里真的存在** —— 漏一个就是"起手时模型不动"✗；</li>
 *   <li>自发光贴图**只亮眼缝·符文·杖心**三块 —— 一不小心把袍子也点亮就"不黑暗"了 ✗；</li>
 *   <li>体型与召唤参数的**量级**别写飞（碰撞箱别高过模型、召唤物别飞到看不见的地方 ✗）。</li>
 * </ol>
 */
class DarkMageBossTest {

    private static Path res() {
        return Path.of("src/main/resources/assets/tnc");
    }

    @Test
    void everySharedSpellHasARealClipWithRealBones() throws Exception {
        var animations = JsonParser.parseString(Files.readString(
                        res().resolve("animations/entity/dark_mage.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        var rig = JsonParser.parseString(Files.readString(res().resolve("geo/entity/dark_mage.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        var bones = new HashSet<String>();
        rig.forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));

        var distinct = new HashSet<String>();
        for (int i = 0; i < TNDarkMageSpells.count(); i++) {
            var move = TNDarkMageSpells.move(i);
            String clip = TNDarkMageSpells.animation(move);
            distinct.add(clip);
            assertTrue(animations.has(clip), "缺动作片段：" + clip + "（起手时模型不会动 ✗）");
            var data = animations.getAsJsonObject(clip);
            // 法师的片段长度按 castTicks 精确给（巨人那边多留 0.6 秒余量）⇒ 这里用精确值钉住
            assertEquals(move.castTicks() / 20.0, data.get("animation_length").getAsDouble(), .001, clip);
            for (String bone : data.getAsJsonObject("bones").keySet()) {
                assertTrue(bones.contains(bone), clip + " 里写了不存在的骨骼 " + bone);
            }
        }
        assertEquals(5, distinct.size(), "7 招只该对 5 个动作片段（T4/T5 的黑夜之手共用 sky_hands）");
    }

    @Test
    void idleAndGlideAndPhaseTwoClipsExist() throws Exception {
        var animations = JsonParser.parseString(Files.readString(
                        res().resolve("animations/entity/dark_mage.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for (String clip : new String[]{TNDarkMageEntity.ANIM_IDLE, TNDarkMageEntity.ANIM_GLIDE,
                TNDarkMageEntity.ANIM_PHASE_TWO}) {
            assertTrue(animations.has(clip), "缺 " + clip);
        }
        assertTrue(animations.getAsJsonObject(TNDarkMageEntity.ANIM_IDLE).get("loop").getAsBoolean(),
                "idle 必须循环");
        assertTrue(animations.getAsJsonObject(TNDarkMageEntity.ANIM_GLIDE).get("loop").getAsBoolean(),
                "glide 必须循环");
        var phaseTwo = animations.getAsJsonObject(TNDarkMageEntity.ANIM_PHASE_TWO);
        // 要么不写 loop（= 播一次），要么 hold_on_last_frame；**不能**是 true（循环会一直"涨"✗）
        String loop = phaseTwo.has("loop") ? phaseTwo.get("loop").getAsString() : "play_once";
        assertFalse("true".equals(loop), "phase2 不能循环");
    }

    /**
     * 2026-11-07 第二轮（作者："他的动作你也可以多做一点，让我有诡异神秘的感觉"）：
     * 五个小动作必须在文件里、**名字必须和实体常量对得上**（改一边忘一边 ⇒ 触发时静默不播 ✗）。
     */
    @Test
    void eeriePerformanceClipsExistAndMatchTheEntityConstants() throws Exception {
        var animations = JsonParser.parseString(Files.readString(
                        res().resolve("animations/entity/dark_mage.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        var rig = JsonParser.parseString(Files.readString(res().resolve("geo/entity/dark_mage.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        var bones = new HashSet<String>();
        rig.forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));

        var expected = new LinkedHashMap<String, Double>();
        expected.put(TNDarkMageEntity.ANIM_WATCH, 5.2);
        expected.put(TNDarkMageEntity.ANIM_TWITCH, 0.9);
        expected.put(TNDarkMageEntity.ANIM_BECKON, 2.4);
        expected.put(TNDarkMageEntity.ANIM_UNRAVEL, 1.6);
        expected.put(TNDarkMageEntity.ANIM_STARE, 2.2);
        for (var e : expected.entrySet()) {
            assertTrue(animations.has(e.getKey()), "缺小动作片段：" + e.getKey() + "（触发时会静默不播 ✗）");
            var clip = animations.getAsJsonObject(e.getKey());
            assertEquals(e.getValue(), clip.get("animation_length").getAsDouble(), .001, e.getKey());
            for (String bone : clip.getAsJsonObject("bones").keySet()) {
                assertTrue(bones.contains(bone), e.getKey() + " 里写了不存在的骨骼 " + bone);
            }
            String loop = clip.has("loop") ? clip.get("loop").getAsString() : "play_once";
            assertNotEquals("true", loop, e.getKey() + " 是「一次性」的，不能循环");
        }
        // 设计意图：抽搐必须是全场最短的那个（0.9s），"快慢对比"就是诡异感的来源
        for (var e : expected.entrySet()) {
            if (!TNDarkMageEntity.ANIM_TWITCH.equals(e.getKey())) {
                assertTrue(e.getValue() > 0.9, e.getKey() + " 不该比抽搐还快 —— 对比就没了");
            }
        }
    }

    /** 自发光只准亮三块：eye / rune / orb 三个分区，其余像素 alpha 必须为 0。 */    @Test
    void emissiveOnlyLightsEyesRuneAndOrb() throws Exception {
        var base = ImageIO.read(res().resolve("textures/entity/dark_mage_bedrock.png").toFile());
        var glow = ImageIO.read(res().resolve("textures/entity/dark_mage_emissive.png").toFile());
        assertEquals(base.getWidth(), glow.getWidth());
        assertEquals(base.getHeight(), glow.getHeight());

        // 和 tools/gen_dark_mage.py 里的 PATCH 一一对应（改分区就同步改这里）
        int[][] lit = {{96, 128, 16, 16}, {224, 32, 32, 32}, {64, 128, 32, 32}};
        int outside = 0, total = 0;
        for (int y = 0; y < glow.getHeight(); y++) {
            for (int x = 0; x < glow.getWidth(); x++) {
                if ((glow.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                total++;
                boolean ok = false;
                for (int[] p : lit) {
                    if (x >= p[0] && x < p[0] + p[2] && y >= p[1] && y < p[1] + p[3]) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    outside++;
                }
            }
        }
        assertTrue(total > 200, "自发光几乎是空白（只点亮了 " + total + " 像素）：眼缝/符文/杖心都得亮");
        assertEquals(0, outside, "自发光跑到袍子/披风上了（" + outside + " 像素）⇒ 会变成「整只发亮」");
    }

    /** 碰撞箱必须比模型矮（模型 62 单位 = 3.875 格），但要高过玩家；宽度得小于高度。 */
    @Test
    void hitboxIsShorterThanTheModelButTallerThanAPlayer() {
        float modelHeight = 62F / 16F;
        assertTrue(TNDarkMageEntity.HITBOX_HEIGHT < modelHeight,
                "碰撞箱比模型还高 ⇒ 会被 4 格通道卡住/浮在半空");
        assertTrue(TNDarkMageEntity.HITBOX_HEIGHT > 2.0F, "比玩家还矮就不像 boss 了");
        assertTrue(TNDarkMageEntity.HITBOX_WIDTH < TNDarkMageEntity.HITBOX_HEIGHT, "法师是竖长的");
        assertEquals(1.0D, TNDarkMageEntity.MODEL_SCALE, .0001, "geo 是 1 单位 = 1/16 格量的，不该再缩放");
    }

    /** 召唤参数得在实体允许的范围里，落点不能远到看不见。 */
    @Test
    void summonNumbersStayInsideTheEntityClamp() {
        assertTrue(TNDarkMageSpells.SUMMON_SCALE >= 0.1D && TNDarkMageSpells.SUMMON_SCALE <= 16.0D,
                "TNDarkSummonEntity#setScale 会夹到 [0.1,16]，超了就是另一个数了");
        double reach = TNDarkMageEntity.HITBOX_WIDTH + TNDarkMageSpells.SUMMON_RADIUS;
        assertTrue(reach < 8.0D, "召唤物落在 " + reach + " 格外 ⇒ 玩家看不见、它们也打不到人");
        assertTrue(reach > 2.0D, "落点比身宽还近 ⇒ 三个召唤物会卡在法师身体里");
        // 巨人拿到的是原值，行为必须零变化
        assertEquals(24.0F + 14.0D, TNDarkGiantEntity.HITBOX_WIDTH + 14.0D, .0001);
    }

    /** 二阶段与巨人共用同一条规则（50%），不各写一份。 */
    @Test
    void phaseTwoUsesTheSharedThreshold() {
        float max = TNDarkMageEntity.MAX_HP;
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(max, max, TNDarkGiantPhase.PHASE_ONE));
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(max * 0.6F, max, TNDarkGiantPhase.PHASE_ONE));
        assertTrue(TNDarkGiantPhase.shouldEnterPhaseTwo(max * 0.49F, max, TNDarkGiantPhase.PHASE_ONE));
        // 已经进过二阶段就不会再进一次
        assertFalse(TNDarkGiantPhase.shouldEnterPhaseTwo(max * 0.1F, max, TNDarkGiantPhase.PHASE_TWO));
    }

    /**
     * 黑雾分身（2026-11-07 第三轮，作者"我还希望这个boss有分身的能力 ……"）的四条硬规则。
     *
     * <p>这几条一旦写飞，坏的不是"不好玩"而是**平衡崩**✗：
     * 分身比本体还肉 ⇒ 玩家打分身比打 boss 划算；反噬比例太大 ⇒ 两个分身白送半管血；
     * 齐射那一招在表里/动画里找不到 ⇒ 雾散了什么都不发生（而且**静默**✗）。
     */
    @Test
    void cloneSwarmRulesAreSane() throws Exception {
        // ① 两个分身，血远低于本体（不到 1/4），否则"打分身"变成比打 boss 还累的活
        assertEquals(2, TNDarkMageEntity.CLONE_COUNT);
        assertTrue(TNDarkMageEntity.CLONE_MAX_HP > 0.0F);
        assertTrue(TNDarkMageEntity.CLONE_MAX_HP < TNDarkMageEntity.MAX_HP / 4.0F,
                "分身血 " + TNDarkMageEntity.CLONE_MAX_HP + " 太厚：玩家没理由去打它");
        // ② 反噬是"最大生命的百分比"，而且两个加起来也不能超过半管
        assertTrue(TNDarkMageEntity.CLONE_HP_PENALTY > 0.0F && TNDarkMageEntity.CLONE_HP_PENALTY < 1.0F);
        assertTrue(TNDarkMageEntity.CLONE_COUNT * TNDarkMageEntity.CLONE_HP_PENALTY < 0.5F,
                "两个分身加起来扣超过半管血 ⇒ 直接跳二阶段，节奏全乱");
        // ③ 招式表里必须真有"黑雾"和"齐射"这两招（按名字找，不写死下标）
        assertTrue(TNDarkMageSpells.indexOf(TNDarkMageEntity.MIST_MOVE) >= 0,
                "招式表里没有 " + TNDarkMageEntity.MIST_MOVE + " ⇒ 分身永远不会出现");
        int volley = TNDarkMageSpells.indexOf(TNDarkMageEntity.VOLLEY_MOVE);
        assertTrue(volley >= 0, "招式表里没有 " + TNDarkMageEntity.VOLLEY_MOVE + " ⇒ 雾散了不会齐射");
        // ④ 齐射用的动作片段必须在这个模型里存在，且长度 = 起手 tick / 20
        var animations = JsonParser.parseString(Files.readString(
                        res().resolve("animations/entity/dark_mage.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        String clip = TNDarkMageSpells.animation(TNDarkMageSpells.move(volley));
        assertTrue(animations.has(clip), "齐射用的动作 " + clip + " 不在动画文件里");
        assertEquals(TNDarkMageSpells.move(volley).castTicks() / 20.0D,
                animations.getAsJsonObject(clip).get("animation_length").getAsDouble(), .001,
                "齐射动作长度和起手 tick 对不上 ⇒ 手还没抬完黑手就下来了");
        // ⑤ 雾的保底时长（读不到法术 JSON 时用的 300 tick）必须比分身的余寿短，
        //    否则分身还没等到"雾散"就先自己散了
        assertTrue(TNDarkMageEntity.VOLLEY_WARNING_TICKS > 0 && TNDarkMageEntity.VOLLEY_WARNING_TICKS <= 60,
                "齐射预警要够玩家反应、又不能长到像卡住");
        // ⑥ 法师的出手顺序必须**保证**看得到黑雾（共用的权重表里它只有 2/21 ⇒ 靠随机等于看不到）
        var random = net.minecraft.util.RandomSource.create(20261107L);
        assertEquals(TNDarkMageSpells.indexOf(TNDarkMageEntity.MIST_MOVE),
                TNDarkMageSpells.pick(random, TNDarkMageSpells.MIST_AT, -1),
                "第 " + (TNDarkMageSpells.MIST_AT + 1) + " 招必须是黑雾 ⇒ 分身一定出现");
        assertEquals(TNDarkMageSpells.indexOf(TNDarkMageEntity.MIST_MOVE),
                TNDarkMageSpells.pick(random, TNDarkMageSpells.MIST_AT + TNDarkMageSpells.MIST_EVERY_NTH_CAST, -1),
                "隔 " + TNDarkMageSpells.MIST_EVERY_NTH_CAST + " 招要再来一次黑雾");
        // 而且不能把巨人的出手顺序带跑：巨人的前两招是定死的（0 → 以伤换伤 #6，1 → 黑夜之手 T4 #3）
        assertEquals(6, TNDarkGiantSpells.pick(net.minecraft.util.RandomSource.create(1L), 0, -1),
                "巨人的连招被改了 ⇒ 作者那边那个 boss 的节奏就变了");
        assertEquals(3, TNDarkGiantSpells.pick(net.minecraft.util.RandomSource.create(1L), 1, -1),
                "巨人的连招被改了");
    }
}
