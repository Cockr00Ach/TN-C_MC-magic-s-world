package com.tnc.tnc.magic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 施法动作的"名字对不上就静默不播"防线（2026-10-30）。
 *
 * <p>本工程反复踩的坑：动作名写错一个字、或者骨骼被改名，编译照过、运行时什么都不动 ✗。
 * 这里把三件事钉成断言：
 * <ol>
 *   <li>枚举里的三个名字确实都在 {@code player_magic-animation.json} 里；</li>
 *   <li>每个动作动的骨骼都在 {@code self_wand.geo.json} 里（含根骨 {@code bone}）；</li>
 *   <li>三条动作都是 {@code loop: false}（点播语义；否则 {@code thenPlay} 会自己循环）。</li>
 * </ol>
 */
class PlayerCastAnimationsTest {

    private static final Path ANIMATION_FILE =
            Path.of("src/main/resources/assets/tnc/animations/entity/player_magic-animation.json");
    private static final Path WAND_MODEL =
            Path.of("src/main/resources/assets/tnc/geo/entity/self_wand.geo.json");

    private static JsonObject animations() throws Exception {
        return JsonParser.parseString(Files.readString(ANIMATION_FILE))
                .getAsJsonObject().getAsJsonObject("animations");
    }

    private static Map<String, JsonObject> bones() throws Exception {
        JsonObject geo = JsonParser.parseString(Files.readString(WAND_MODEL))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Map<String, JsonObject> bones = new HashMap<>();
        geo.getAsJsonArray("bones").forEach(bone ->
                bones.put(bone.getAsJsonObject().get("name").getAsString(), bone.getAsJsonObject()));
        return bones;
    }

    @Test
    void everyMappedAnimationExistsInTheAuthoredFile() throws Exception {
        JsonObject animations = animations();
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            assertTrue(animations.has(kind.animation()),
                    "PlayerCastAnimations." + kind.name() + " -> '" + kind.animation()
                            + "' is missing from " + ANIMATION_FILE);
        }
    }

    @Test
    void byAnimationAndByKeyRoundTrip() {
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            assertEquals(kind, PlayerCastAnimations.byAnimation(kind.animation()));
            assertEquals(kind, PlayerCastAnimations.byKey(kind.name().toLowerCase(java.util.Locale.ROOT)));
        }
        assertEquals(null, PlayerCastAnimations.byAnimation("nope"));
    }

    @Test
    void everyAnimatedBoneExistsInTheWandModel() throws Exception {
        Map<String, JsonObject> bones = bones();
        JsonObject animations = animations();
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            JsonObject moved = animations.getAsJsonObject(kind.animation()).getAsJsonObject("bones");
            assertNotNull(moved, kind.animation() + " moves no bones");
            for (String bone : moved.keySet()) {
                assertTrue(bones.containsKey(bone),
                        kind.animation() + " moves bone '" + bone
                                + "' which is NOT in self_wand.geo.json (it would silently do nothing)");
            }
        }
    }

    @Test
    void castingClipsAreOneShot() throws Exception {
        JsonObject animations = animations();
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            JsonObject clip = animations.getAsJsonObject(kind.animation());
            assertTrue(clip.has("loop"), kind.animation() + " must declare \"loop\"");
            assertFalse(clip.get("loop").getAsBoolean(),
                    kind.animation() + " must be \"loop\": false (it is a one-shot cast)");
        }
    }

    /** 根骨 + 杖骨：少了根骨，动画里 {@code bone} 的位移/转身会被静默丢掉。 */
    @Test
    void rootBoneAndWandBonesSurvive() throws Exception {
        Map<String, JsonObject> bones = bones();
        assertTrue(bones.containsKey("bone"), "self_wand.geo.json must keep the root bone 'bone'");
        assertEquals("armRight", bones.get("wand").get("parent").getAsString());
        for (String human : new String[]{"head", "body", "armRight", "armLeft", "legRight", "legLeft"}) {
            assertEquals("bone", bones.get(human).get("parent").getAsString(),
                    human + " must hang off the root bone so root motion moves the whole rig");
        }
    }

    /**
     * 握点 = 物品模型的握点（`tnc_grip`），拳头在杖身中段、杖尾垂到小腿 —— 这是"以前版本
     * 的握法"，用户 2026-10-31 确认它是对的（"你不要被前几天别的 Bai 做的误导了"）。
     *
     * <p>所以这里钉的是：杖尾在拳下明显一截（≈9.6 单位）、但又不拖地。谁再自作主张把
     * 拳头挪到杖尾（上一轮干过，把杖整体上移 7 单位），这条会红。
     */
    @Test
    void staffHangsBelowTheFistLikeTheItemModel() throws Exception {
        Map<String, JsonObject> bones = bones();
        JsonObject fist = bones.get("armRight").getAsJsonArray("cubes").get(0).getAsJsonObject();
        double fistBottom = fist.getAsJsonArray("origin").get(1).getAsDouble();
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (var cube : bones.get("wand").getAsJsonArray("cubes")) {
            JsonObject c = cube.getAsJsonObject();
            double y = c.getAsJsonArray("origin").get(1).getAsDouble();
            low = Math.min(low, y);
            high = Math.max(high, y + c.getAsJsonArray("size").get(1).getAsDouble());
        }
        double grip = bones.get("wand").getAsJsonArray("pivot").get(1).getAsDouble();
        assertTrue(low <= fistBottom - 5.0, String.format(
                "staff bottom is %.2f, only %.2f below the fist (%.2f): the shaft no longer "
                        + "hangs the way the item model holds it", low, fistBottom - low, fistBottom));
        assertTrue(low >= 1.5, String.format("staff bottom %.2f would drag on the ground", low));
        assertTrue(low < grip && grip < high, "the grip must sit inside the shaft");
        assertTrue(high > 32.0, "the staff must clear the head (32)");
    }

    /**
     * 手腕通道：手臂动多少，{@code wand} 就反向转多少，否则杖身跟着小臂翻成水平/倒立。
     *
     * <p>哪些动作该有这条通道，由 {@code tools/lock_staff_upright.py} 的 {@code PLUMB} 决定：
     * 抬手时手臂留在身体外侧的（wavingstaff / playmagic_1）锁竖直；magicsummon 的右手会横摆
     * 过身体中线，任何角度都穿模，那边保留刚性跟随、由那个工具打成 note。
     * 这里断言的是"有了就必须每个手臂关键帧都有"，数值由工具算。
     */
    @Test
    void everyLockedClipHasAWristKeyOnEveryArmKeyframe() throws Exception {
        JsonObject animations = animations();
        for (PlayerCastAnimations kind : PlayerCastAnimations.values()) {
            JsonObject moved = animations.getAsJsonObject(kind.animation()).getAsJsonObject("bones");
            JsonObject arm = moved.getAsJsonObject("armRight").getAsJsonObject("rotation");
            if (!moved.has("wand")) {
                continue;   // 没上锁的动作（见上面的注释），不在这里管
            }
            JsonObject wrist = moved.getAsJsonObject("wand").getAsJsonObject("rotation");
            for (String time : arm.keySet()) {
                assertTrue(wrist.has(time), kind.animation() + " has an armRight key at " + time
                        + " but no matching wand key (staff would tilt on that frame)");
            }
        }
        // 这两条是必须上锁的：手臂抬起时远离身体，杖身不竖直就会横着扫过胸口
        for (String clip : new String[]{"wavingstaff", "playmagic_1"}) {
            assertTrue(animations.getAsJsonObject(clip).getAsJsonObject("bones").has("wand"),
                    clip + " must carry a `wand` channel (run tools/lock_staff_upright.py)");
        }
    }
}
