package com.tnc.tnc.warrior;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorWeaponModelTest {
    private static final String[] IDS = {"dragon_sword","frost_katana","star_rapier","crimson_warblade",
            "doom_scythe","earthshaker_hammer","sky_halberd"};
    private JsonObject model(String id) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/tnc/models/item/"+id+".json")) {
            assertNotNull(stream, id);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void allWeaponFacesHaveValidTextureReferencesAndMinecraftUvUnits() throws Exception {
        for (String id : IDS) {
            var model = model(id);
            for (var value : model.getAsJsonArray("elements")) {
                var element = value.getAsJsonObject();
                if (element.has("rotation")) {
                    double angle = element.getAsJsonObject("rotation").get("angle").getAsDouble();
                    assertTrue(angle == -45 || angle == -22.5 || angle == 0 || angle == 22.5 || angle == 45, id);
                }
                for (var face : element.getAsJsonObject("faces").entrySet()) {
                    var data = face.getValue().getAsJsonObject();
                    assertEquals("#0", data.get("texture").getAsString(), id);
                    for (var uv : data.getAsJsonArray("uv")) {
                        double v = uv.getAsDouble(); assertTrue(Double.isFinite(v) && v >= 0 && v <= 16, id);
                    }
                }
            }
            try(var stream = getClass().getResourceAsStream("/assets/tnc/textures/item/"+id+".png")) {
                var image = ImageIO.read(stream);
                assertEquals(model.getAsJsonArray("texture_size").get(0).getAsInt(), image.getWidth(), id);
                assertEquals(model.getAsJsonArray("texture_size").get(1).getAsInt(), image.getHeight(), id);
            }
        }
    }
    @Test void realAndEnlargedWeaponGripsMatchTheVanillaHandForBothArms() throws Exception {
        for (String id : IDS) for (boolean left : new boolean[]{false,true}) {
            var model = model(id); var grip = model.getAsJsonArray("tnc_grip");
            var display = model.getAsJsonObject("display").getAsJsonObject(left?"thirdperson_lefthand":"thirdperson_righthand");
            var r=display.getAsJsonArray("rotation");var t=display.getAsJsonArray("translation");var s=display.getAsJsonArray("scale");
            float sign=left?-1:1;
            Matrix4f transform=new Matrix4f().translation(sign*t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                    .rotateXYZ((float)Math.toRadians(r.get(0).getAsFloat()),(float)Math.toRadians(sign*r.get(1).getAsFloat()),
                            (float)Math.toRadians(sign*r.get(2).getAsFloat())).scale(s.get(0).getAsFloat(),s.get(1).getAsFloat(),s.get(2).getAsFloat());
            var anchor=WarriorGripGeometry.anchor(transform,grip.get(0).getAsFloat()/16,grip.get(1).getAsFloat()/16,grip.get(2).getAsFloat()/16);
            var reference=WarriorGripGeometry.anchor(new Matrix4f().translation(0,.25f,.5f/16)
                    .rotateXYZ(0,(float)Math.toRadians(-90),(float)Math.toRadians(55)).scale(.85f),3/16f,3/16f,.5f);
            assertEquals(0,anchor.distance(reference),2e-6,id+(left?" left":" right"));
            var enlarged=new Vector3f(anchor).mul(1.45f).add(WarriorGripGeometry.compensation(anchor,1.45));
            assertEquals(0,anchor.distance(enlarged),1e-6,id);
        }
    }
}
