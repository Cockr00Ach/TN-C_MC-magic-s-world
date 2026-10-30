package com.tnc.tnc.warrior;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tnc.tnc.warrior.client.InstructorHeldItemLayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 握姿的一致性：渲染器角度 ↔ Blockbench 作者模型里的方块旋转。
 *
 * <p>这里守的是踩过一次的坑：多轴欧拉角在 Blockbench（three.js，按 Rx·Ry·Rz 合成）和
 * Bedrock/GeckoLib（按 Rz·Ry·Rx 合成）里会摆出<b>不同</b>的姿势——同一组数字，
 * 游戏里剑朝前、编辑器里剑朝后。所以握姿只用一个轴，并且让测试把"单轴"这件事钉住：
 * 谁哪天把它改成多轴，这里就会红。
 */
class InstructorGripModelTest {
    private static final float DEG = (float) (Math.PI / 180.0);

    @Test
    void gripRotationIsSingleAxisSoBothEulerConventionsAgree() {
        Quaternionf xyz = new Quaternionf().rotationXYZ(
            InstructorHeldItemLayer.GRIP_ROT_X * DEG, 0.0F, 0.0F);
        Quaternionf zyx = new Quaternionf().rotationZYX(
            0.0F, 0.0F, InstructorHeldItemLayer.GRIP_ROT_X * DEG);
        assertTrue(xyz.equals(zyx, 1.0e-5F), "单轴旋转在 XYZ 与 ZYX 两种合成顺序下必须相同");

        Vector3f blade = xyz.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertEquals(0.0F, blade.x(), 1.0e-4F, "刀身不该往左右歪");
        assertTrue(blade.y() > 0.5F && blade.z() < -0.7F,
            "刀身应朝上偏前（-Z 是角色正面），实际 " + blade);

        Vector3f guard = xyz.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertTrue(Math.abs(guard.x()) > 0.99F, "护手应横在刀身两侧，实际 " + guard);
    }

    @Test
    void authoringModelSwordMatchesTheRenderer() throws Exception {
        Path geo = Path.of("model-source", "instructor", "instructor-blockbench.geo.json");
        assertTrue(Files.exists(geo), "先在项目根目录跑 python tools/build_instructor_authoring.py");
        JsonObject root = JsonParser.parseString(Files.readString(geo, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonArray bones = geometry.getAsJsonArray("bones");

        JsonObject right = bone(bones, "forearm_r");
        JsonObject left = bone(bones, "forearm_l");
        assertEquals(4, right.getAsJsonArray("cubes").size(), "右前臂 = 1 个手臂方块 + 3 个剑方块");
        assertEquals(2, left.getAsJsonArray("cubes").size(), "左前臂 = 1 个手臂方块 + 1 面盾");

        JsonObject blade = right.getAsJsonArray("cubes").get(1).getAsJsonObject();
        JsonArray rotation = blade.getAsJsonArray("rotation");
        assertEquals(InstructorHeldItemLayer.GRIP_ROT_X, rotation.get(0).getAsFloat(), 1.0e-3F);
        assertEquals(0.0F, rotation.get(1).getAsFloat(), 1.0e-3F, "只允许绕 X 一个轴");
        assertEquals(0.0F, rotation.get(2).getAsFloat(), 1.0e-3F, "只允许绕 X 一个轴");

        JsonArray pivot = blade.getAsJsonArray("pivot");
        assertEquals(-6.0F, pivot.get(0).getAsFloat(), 1.0e-3F, "握点在右手（模型 x=-6）");
        assertEquals(12.0F, pivot.get(1).getAsFloat(), 1.0e-3F, "握点在前臂末端（模型 y=12）");
        assertEquals(0.0F, pivot.get(2).getAsFloat(), 1.0e-3F);
    }

    private static JsonObject bone(JsonArray bones, String name) {
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) return bone;
        }
        throw new AssertionError("missing bone " + name);
    }
}
