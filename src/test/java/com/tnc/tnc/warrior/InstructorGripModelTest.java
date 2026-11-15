package com.tnc.tnc.warrior;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tnc.tnc.warrior.client.InstructorHeldItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

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
    void animatedElbowKeepsTheGripAtTheActualFistWithOneRotation() {
        var bone = new GeoBone(null, "forearm_r", false, null, false, false);
        bone.setPivotX(6);
        bone.setPivotY(18);
        bone.setRotX(60 * DEG);
        bone.setScaleY(2);
        var pose = new PoseStack();
        // Enter the same frame GeoRenderer supplies to every render layer.
        RenderUtils.prepMatrixForBone(pose, bone);
        InstructorHeldItemLayer.moveToHand(pose, bone);
        var spriteGrip = new Vec3(3.0 / 16.0, 3.0 / 16.0, .5);
        InstructorHeldItemLayer.orientSword(pose, spriteGrip, false);
        pose.translate(-.5, -.5, -.5); // ItemRenderer's actual NONE-context model centering
        var at = pose.last().pose().transformPosition(new Vector3f((float)spriteGrip.x,
                (float)spriteGrip.y, (float)spriteGrip.z));
        // Elbow (6,18,0) + Rx(60deg) * scaled forearm (0,-12,0), in model pixels.
        assertEquals(6 / 16.0, at.x, 1e-5, "The grip must not gain a second elbow offset");
        assertEquals(12 / 16.0, at.y, 1e-5, "The animated elbow rotates only once");
        assertEquals(-Math.sqrt(3) * 6 / 16.0, at.z, 1e-5,
                "A doubled bone rotation would carry the grip behind the actor");
    }

    @Test
    void vanillaSpriteAndAuthoredDragonBladeShareOneGripAndPointForward() throws Exception {
        assertFistAndDirection(new Vec3(3.0 / 16.0, 3.0 / 16.0, .5),
                new Vec3(15.0 / 16.0, 15.0 / 16.0, .5), false);
        var root = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/models/item/dragon_sword.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        var raw = root.getAsJsonArray("tnc_grip");
        var grip = new Vec3(raw.get(0).getAsDouble() / 16, raw.get(1).getAsDouble() / 16,
                raw.get(2).getAsDouble() / 16);
        double tip = grip.z;
        for (var element : root.getAsJsonArray("elements"))
            tip = Math.max(tip, element.getAsJsonObject().getAsJsonArray("to").get(2).getAsDouble() / 16);
        assertTrue(tip - grip.z > .5, "Use the real longitudinal dragon blade, not a sprite surrogate");
        assertFistAndDirection(grip, new Vec3(grip.x, grip.y, tip), true);
    }

    private static void assertFistAndDirection(Vec3 grip, Vec3 tip, boolean longitudinal) {
        var pose = new PoseStack();
        InstructorHeldItemLayer.orientSword(pose, grip, longitudinal);
        pose.translate(-.5, -.5, -.5);
        var transform = pose.last().pose();
        var at = transform.transformPosition(new Vector3f((float)grip.x, (float)grip.y, (float)grip.z));
        assertEquals(0, at.x, 1e-5);
        assertEquals(0, at.y, 1e-5);
        assertEquals(0, at.z, 1e-5, "Model-centering must leave the authored grip at the fist");
        var blade = transform.transformPosition(new Vector3f((float)tip.x, (float)tip.y, (float)tip.z)).sub(at).normalize();
        assertEquals(0, blade.x, 1e-5);
        assertTrue(blade.y > .5 && blade.z < -.7, "The real blade must point above and in front of the actor: " + blade);
    }

    @Test
    void actualGeckoAttackAndThrustFramesExtendTheBladeFromTheFistInsteadOfBackAtTheBody() throws Exception {
        var resources=Path.of("src/main/resources/assets/tnc");
        var raw=software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(
                Files.readString(resources.resolve("geo/entity/instructor.geo.json")),software.bernie.geckolib.loading.json.raw.Model.class);
        for(String file:new String[]{"instructor.animation.json","instructor_tutorial.animation.json"}) {
            var json=JsonParser.parseString(Files.readString(resources.resolve("animations/entity/"+file))).getAsJsonObject();
            var animations=software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json.get("animations"),software.bernie.geckolib.loading.object.BakedAnimations.class);
            String[] clips=file.startsWith("instructor_tutorial")?new String[]{"idle","attack","tutorial_thrust"}:new String[]{"idle","attack"};
            for(String clip:clips) for(double seconds:clip.equals("attack")?new double[]{.35,.38,.40}:clip.equals("tutorial_thrust")?new double[]{.04,.08,.11}:new double[]{0}) {
                var model=new software.bernie.geckolib.loading.object.BakedModelFactory.Builtin().constructGeoModel(
                        software.bernie.geckolib.loading.object.GeometryTree.fromModel(raw));
                var animation=animations.getAnimation(clip);
                assertTrue(animation!=null,"The real GeckoLib decoder must load "+file+" / "+clip);
                for(var track:animation.boneAnimations()) {
                    var bone=model.getBone(track.boneName()).orElseThrow();var keys=track.rotationKeyFrames();
                    // Sample the actual decoded radians, including GeckoLib's X/Y sign conversion.
                    bone.setRotX(sample(keys.xKeyframes(),seconds*20));
                    bone.setRotY(sample(keys.yKeyframes(),seconds*20));
                    bone.setRotZ(sample(keys.zKeyframes(),seconds*20));
                }
                var forearm=model.getBone("forearm_r").orElseThrow();
                float wrist=InstructorHeldItemLayer.wristPitch(forearm);
                if(clip.equals("idle")) {assertEquals(InstructorHeldItemLayer.GRIP_ROT_X,wrist,1e-3,"Idle retains its relaxed grip");continue;}
                var chain=new java.util.ArrayDeque<GeoBone>();
                for(var bone=forearm;bone!=null;bone=bone.getParent())chain.addFirst(bone);
                var pose=new PoseStack();for(var bone:chain)RenderUtils.prepMatrixForBone(pose,bone);
                var reach=pose.last().pose().transformDirection(new Vector3f(0,-1,0)).normalize();
                InstructorHeldItemLayer.moveToHand(pose,forearm);
                var fist=pose.last().pose().transformPosition(new Vector3f());
                var grip=new Vec3(7.0/16,.5/16,2.05/16);
                InstructorHeldItemLayer.orientSword(pose,grip,true,wrist);pose.translate(-.5,-.5,-.5);
                var at=pose.last().pose().transformPosition(new Vector3f((float)grip.x,(float)grip.y,(float)grip.z));
                var tip=pose.last().pose().transformPosition(new Vector3f((float)grip.x,(float)grip.y,(float)grip.z+1));
                var blade=tip.sub(at).normalize();
                assertTrue(at.distance(fist)<1e-5,"Wrist correction must retain the same fist anchor");
                assertTrue(blade.dot(reach)>.995,"Blade must continue along the animated forearm at "+file+" / "+clip+" / "+seconds+"s; dot="+blade.dot(reach));
            }
        }
    }

    private static float sample(java.util.List<software.bernie.geckolib.core.keyframe.Keyframe<com.eliotlash.mclib.math.IValue>> keys,double tick) {
        if(keys.isEmpty())return 0;
        for(var key:keys) {
            if(tick<=key.length()) {
                double t=key.length()==0?1:tick/key.length();
                return (float)(key.startValue().get()+(key.endValue().get()-key.startValue().get())*t);
            }
            tick-=key.length();
        }
        return (float)keys.get(keys.size()-1).endValue().get();
    }

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
