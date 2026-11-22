package com.tnc.tnc.adventure;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry-space checks use the same centered model and mirrored hand chain as ItemRenderer. */
final class WandHoldTransformTest {
    private JsonObject read(String path) throws IOException {
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/assets/tnc/" + path), path)) {
            return JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
        }
    }
    private List<String> names() {
        var result = new ArrayList<String>(); result.add("magic_wand");
        for (String e : List.of("water", "fire", "lightning", "wind", "earth", "light", "dark"))
            for (int tier = 1; tier <= 5; tier++) result.add(e + "_wand_" + tier);
        return result;
    }
    private Vector3f vector(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()); }
    private Vector3f pose(Vector3f point, JsonObject display, boolean left) {
        var r = vector(display.getAsJsonArray("rotation")); var s = vector(display.getAsJsonArray("scale"));
        var t = vector(display.getAsJsonArray("translation")); float sign = left ? -1 : 1;
        return new Vector3f(point).mul(s).rotate(new Quaternionf().rotationXYZ(
                (float)Math.toRadians(r.x), (float)Math.toRadians(sign*r.y), (float)Math.toRadians(sign*r.z)))
                .add(sign*t.x, t.y, t.z);
    }
    private JsonObject held(String id) throws IOException { return read("models/item/wands/held/" + id + ".json"); }

    /** The actual ItemInHandLayer path, in model pixels before the arm bone is applied. */
    private Vector3f handAttachment(Vector3f point, JsonObject display, boolean left) {
        return pose(point, display, left).add(left ? -1 : 1, 2, -10)
                .rotateY((float) Math.PI).rotateX(-(float) Math.PI / 2);
    }

    @Test void inventoriesKeepTheApprovedArtworkWhileHandsUseSolidGeometry() throws IOException {
        for (String id : names()) {
            var wrapper = read("models/item/" + id + ".json");
            assertEquals("forge:separate_transforms", wrapper.get("loader").getAsString());
            var icon = read("models/item/wands/icons/" + id + ".json");
            assertEquals("minecraft:item/generated", icon.get("parent").getAsString());
            assertEquals("tnc:item/" + (id.equals("magic_wand") ? id : "wands/"+id), icon.getAsJsonObject("textures").get("layer0").getAsString());
            var model = held(id);
            assertEquals("tnc:botanical_mesh",model.get("loader").getAsString());
            var surfaces=model.getAsJsonArray("surfaces");assertTrue(surfaces.size()>=90,id);
            float minHead=Float.POSITIVE_INFINITY,maxHead=Float.NEGATIVE_INFINITY;
            for(var value:surfaces){var face=value.getAsJsonObject();
                assertEquals(4,face.getAsJsonArray("vertices").size(),id);
                for(var vertex:face.getAsJsonArray("vertices")){
                    var v=vector(vertex.getAsJsonArray());
                    for(int axis=0;axis<3;axis++)assertTrue(Float.isFinite(v.get(axis))&&v.get(axis)>=(axis==1?-24:-16)&&v.get(axis)<=(axis==1?54:32),id+" actual mesh bounds");
                    if(v.y>15){minHead=Math.min(minHead,v.z);maxHead=Math.max(maxHead,v.z);}
                }
                for(var pair:face.getAsJsonArray("uv"))for(var uv:pair.getAsJsonArray())assertTrue(uv.getAsFloat()>=0&&uv.getAsFloat()<=16,id+" UV bounds");
            }
            assertTrue(maxHead-minHead>=1.8f,id+" head has real depth");
        }
    }

    @Test void thirdPersonRespectsTheSelectedStaffGripOnBothSides() throws IOException {
        var reference = read("models/item/qimei_staff.json");
        var referenceGrip = vector(reference.getAsJsonArray("tnc_grip")).sub(8, 8, 8);
        for (String id : names()) {
            var model = held(id); var grip = vector(model.getAsJsonArray("tnc_grip")).sub(8,8,8);
            var tip = vector(model.getAsJsonArray("tnc_tip")).sub(8,8,8);
            for (boolean left : new boolean[]{false, true}) {
                String context = "thirdperson_" + (left ? "lefthand" : "righthand");
                var d = model.getAsJsonObject("display").getAsJsonObject(context);
                var ref = reference.getAsJsonObject("display").getAsJsonObject(context);
                var palm = handAttachment(grip, d, left);
                var weaponPalm = handAttachment(referenceGrip, ref, left);
                var weaponAxis = handAttachment(new Vector3f(referenceGrip).add(0, 0, 1), ref, left)
                        .sub(weaponPalm).normalize();
                var shaft = handAttachment(tip, d, left).sub(palm).normalize();
                if (!id.equals("magic_wand")) {
                    Vector3f anchor = id.startsWith("fire_") ? new Vector3f(-1.75f,0,0)
                            : id.startsWith("lightning_") ? new Vector3f(.68f,-.19f,3.53f) : new Vector3f();
                    assertTrue(pose(grip, d, left).distance(anchor) < .04f, id + " elemental palm anchor");
                    assertTrue(-shaft.y > .99f, id + " elemental staff is upright");
                    continue;
                }
                assertTrue(palm.distance(weaponPalm) < .01, id + " must share the proven weapon's palm anchor");
                assertTrue(shaft.dot(weaponAxis) > .999f, id + " must follow the weapon's direction across the fist");
                var front = new Vector3f(palm).fma(3, shaft);
                var back = new Vector3f(palm).fma(-3, shaft);
                assertTrue(front.z < -2.8f && back.z > 2.8f,
                        id + " shaft must clear either side of the forearm immediately beyond the grip");
            }
        }
    }

    private Vector3f firstPersonPalm(String id, boolean left) {
        return switch (id.split("_")[0]) {
            case "magic" -> new Vector3f();
            case "fire" -> left ? new Vector3f(2.06f,-2.64f,.34f) : new Vector3f(-3.54f,-2.36f,-.34f);
            case "lightning" -> left ? new Vector3f(2.86f,-.80f,.07f) : new Vector3f(-2.29f,-.91f,.34f);
            case "wind" -> new Vector3f(left ? 3.17f : -3.17f, -2.35f, -.51f);
            case "earth" -> new Vector3f(left ? 2.8f : -2.8f, 1.08f, -2);
            default -> new Vector3f(left ? 2.8f : -2.8f, -2.5f, 0);
        };
    }

    @Test void firstPersonKeepsEachElementsGripAndFramesItsCrown() throws IOException {
        for (String id : names()) {
            var model = held(id); var grip = vector(model.getAsJsonArray("tnc_grip")).sub(8,8,8);
            var tip = vector(model.getAsJsonArray("tnc_tip")).sub(8,8,8);
            for (boolean left : new boolean[]{false,true}) {
                var d = model.getAsJsonObject("display").getAsJsonObject("firstperson_"+(left?"lefthand":"righthand"));
                var palm = pose(grip,d,left);
                assertTrue(palm.distance(firstPersonPalm(id, left)) < .04f, id + " grip remains at its palm anchor");
                var axis = pose(tip,d,left).sub(palm).rotateY((left ? -1 : 1) * (float) Math.PI / 4);
                assertTrue(axis.y > axis.length() * .93 && Math.abs(axis.z) < axis.length() * .2,
                        id + " staff crown stays within the restored first-person framing");
                if (id.equals("magic_wand")) assertEquals(14, axis.length(), .1, id);
                else assertTrue(axis.length() >= 7.9f && axis.length() <= 15.6f, id + " crown stays in frame");
            }
        }
    }
}
