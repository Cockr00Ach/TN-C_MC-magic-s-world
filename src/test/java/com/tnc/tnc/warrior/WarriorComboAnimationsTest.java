package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.client.WarriorComboAnimations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 长剑连招的骨骼关键帧：真的能把美术做的 Blockbench 文件读进来、按时间采样。
 *
 * <p>这些测试盯着的是"文件 + 解析 + 采样"这条链：文件改名、少一根骨骼、关键帧顺序乱了，
 * 都会在这里先炸，而不是等进游戏看见人摆成 T 字。
 */
class WarriorComboAnimationsTest {

    private static final String CLIP_PATH = "/assets/tnc/animations/entity/战士连招/长剑连招.json";

    private static String json() {
        try (InputStream stream = WarriorComboAnimationsTest.class.getResourceAsStream(CLIP_PATH)) {
            assertNotNull(stream, "长剑连招的关键帧文件必须在 resources 里：" + CLIP_PATH);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new AssertionError("读不到 " + CLIP_PATH, error);
        }
    }

    @Test
    void everySwordComboHasAClip() {
        Map<String, WarriorComboAnimations.Clip> clips = WarriorComboAnimations.parse(json());
        assertEquals(4, clips.size(), "长剑四招各一条动画：" + clips.keySet());
        for (String name : new String[]{"长剑QZE", "长剑QEQZZZ", "长剑QE长Z", "长剑ZZQQEE"}) {
            assertTrue(clips.containsKey(name), "缺少动画 " + name);
        }
        assertEquals(0.875f, clips.get("长剑QZE").lengthSeconds(), 1e-4);
        assertEquals(0.75f, clips.get("长剑QEQZZZ").lengthSeconds(), 1e-4);
        assertEquals(1.0f, clips.get("长剑QE长Z").lengthSeconds(), 1e-4);
        assertEquals(1.25f, clips.get("长剑ZZQQEE").lengthSeconds(), 1e-4);
    }

    @Test
    void clipsAreWiredToTheAnimationEnum() {
        // 走 classpath 加载（build/resources/main 里就是这四份文件）
        for (WarriorAnimation animation : new WarriorAnimation[]{
                WarriorAnimation.SWORD_BREAKER, WarriorAnimation.SWORD_SPIN,
                WarriorAnimation.SWORD_STANCE, WarriorAnimation.SWORD_COUNTER,
                WarriorAnimation.GS_SWEEP, WarriorAnimation.GS_SMASH,
                WarriorAnimation.GS_WALL, WarriorAnimation.GS_STANCE,
                WarriorAnimation.HM_CRUSH, WarriorAnimation.HM_QUAKE,
                WarriorAnimation.HM_CUT, WarriorAnimation.HM_STANCE,
                WarriorAnimation.BOW_AIM, WarriorAnimation.BOW_DASH_SHOT,
                WarriorAnimation.BOW_BACK_SHOT}) {
            assertNotNull(WarriorComboAnimations.clip(animation), "动作没有对应关键帧：" + animation);
        }
        long catalogClips=WarriorComboAnimations.all().keySet().stream()
                .filter(name->WarriorMartialArts.ids().stream().noneMatch(id->name.startsWith(id+"_"))).count();
        assertEquals(40, catalogClips,
                "29 条连招、6 条镰刀变体和持续蓄弓：" + WarriorComboAnimations.all().keySet());
        // 单段挥砍仍然走程序化曲线
        assertEquals(null, WarriorComboAnimations.clip(WarriorAnimation.SLASH_LEFT));
    }

    @Test
    void clipLengthMatchesTheBroadcastDuration() {
        // 动画文件的 animation_length（秒）和枚举里的 durationTicks 必须对得上，
        // 否则服务端广播 18 tick、客户端放 22 tick，动作会卡在最后一帧。
        for (WarriorAnimation animation : WarriorAnimation.values()) {
            WarriorComboAnimations.Clip clip = WarriorComboAnimations.clip(animation);
            if (clip == null) {
                continue;
            }
            assertEquals(animation.durationTicks(), Math.round(clip.lengthSeconds() * 20f),
                    animation + " 的文件时长和 network 时长不一致：" + clip.lengthSeconds() + "s");
        }
    }

    @Test
    void firstFrameIsRestPose() {
        WarriorComboAnimations.Clip clip = WarriorComboAnimations.parse(json()).get("长剑QZE");
        for (String bone : new String[]{"bone", "armLeft", "armRight", "legLeft", "legRight", "head"}) {
            float[] rotation = clip.rotation(bone, 0f);
            assertEquals(0f, rotation[0], 1e-3, bone + " 起手该是静止姿势");
            assertEquals(0f, rotation[1], 1e-3, bone + " 起手该是静止姿势");
            assertEquals(0f, rotation[2], 1e-3, bone + " 起手该是静止姿势");
        }
    }

    @Test
    void samplingHitsKeyframesAndInterpolatesBetween() {
        WarriorComboAnimations.Clip clip = WarriorComboAnimations.parse(json()).get("长剑QZE");
        // 0.2083 秒是第二帧：右臂抬到 -90.2 度
        assertEquals(-90.2059f, clip.rotation("armRight", 0.2083f)[0], 1e-2);
        // 取两帧正中间，应落在两个关键帧的平均值上（线性插值）
        float middle = (0f + 0.2083f) / 2f;
        assertEquals(-45.10295f, clip.rotation("armRight", middle)[0], 0.5f);
        // 超出末尾取最后一帧，不会跑飞（armRight 末帧是 -77.82 度）
        assertEquals(-77.8191f, clip.rotation("armRight", 99f)[0], 1e-2);
        // 没动过的骨骼恒为 0
        assertEquals(0f, clip.rotation("hat", 0.3f)[0], 1e-6);
    }

    @Test
    void spinClipReallyTurnsTheWholeBody() {
        WarriorComboAnimations.Clip clip = WarriorComboAnimations.parse(json()).get("长剑QEQZZZ");
        // 旋身斩的根骨骼要转过 360 度（687.5 度是关键帧终值）
        assertTrue(clip.moves("bone"), "旋身斩必须动根骨骼，否则只有手臂在甩");
        assertTrue(clip.rotation("bone", 0.7083f)[1] > 360f, "根骨骼要真的转过一整圈");
        // 腿也跟着转，不然只有上半身扭
        assertTrue(clip.moves("legLeft") && clip.moves("legRight"));
    }

    /**
     * 根骨骼的 yaw 必须交给"整只模型"那一层（{@link WarriorPose#rootYaw}），
     * 不能只加在躯干上 —— 只加躯干的话，游戏里回旋斩看起来完全不转。
     */
    @Test
    void rootYawIsHandedToTheWholeModel() {
        WarriorComboAnimations.Clip spin = WarriorComboAnimations.parse(json()).get("长剑QEQZZZ");
        assertEquals(687.5f, com.tnc.tnc.warrior.client.WarriorPose.rootYaw(spin, 0.7083f, false), 0.5f,
                "旋身斩末帧根骨骼 687.5 度要原样交给整只模型");
        assertEquals(-687.5f, com.tnc.tnc.warrior.client.WarriorPose.rootYaw(spin, 0.7083f, true), 0.5f,
                "左手玩家是镜像，角度取反");
        // 根骨骼没动的那一帧不能误转（破锋式只有 -7.5 度的小转身，起手是 0）
        WarriorComboAnimations.Clip turn = WarriorComboAnimations.parse(json()).get("长剑QZE");
        assertEquals(0f, com.tnc.tnc.warrior.client.WarriorPose.rootYaw(turn, 0f, false), 1e-4f,
                "起手没转身就是 0，别把整只模型转歪");
        assertEquals(-7.5f, com.tnc.tnc.warrior.client.WarriorPose.rootYaw(turn, 0.3f, false), 0.6f,
                "小转身也要原样交出去");
        // 没有动作时一律 0
        assertEquals(0f, com.tnc.tnc.warrior.client.WarriorPose.rootYaw(null, 0.4f, false), 1e-6f);
    }
}
