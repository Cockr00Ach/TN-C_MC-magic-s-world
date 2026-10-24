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

    @Test void inventoriesKeepTheApprovedArtworkWhileHandsUseSolidGeometry() throws IOException {
        for (String id : names()) {
            var wrapper = read("models/item/" + id + ".json");
            assertEquals("forge:separate_transforms", wrapper.get("loader").getAsString());
            var icon = read("models/item/wands/icons/" + id + ".json");
            assertEquals("minecraft:item/generated", icon.get("parent").getAsString());
            assertEquals("tnc:item/" + (id.equals("magic_wand") ? id : "wands/"+id), icon.getAsJsonObject("textures").get("layer0").getAsString());
            var model = held(id); var elements = model.getAsJsonArray("elements");
            assertTrue(elements.size() >= 15, id);
            boolean deepHead = false;
            for (var value : elements) {
                var b = value.getAsJsonObject(); var lo = vector(b.getAsJsonArray("from")); var hi = vector(b.getAsJsonArray("to"));
                assertTrue(hi.x > lo.x && hi.y > lo.y && hi.z > lo.z, id + " has volume");
                for (int axis = 0; axis < 3; axis++) assertTrue(lo.get(axis) >= -16 && hi.get(axis) <= 32, id + " Java model bounds");
                if (lo.y > 15 && hi.z-lo.z >= 1.8f) deepHead = true;
                for (var f : b.getAsJsonObject("faces").entrySet()) for (var uv : f.getValue().getAsJsonObject().getAsJsonArray("uv"))
                    assertTrue(uv.getAsFloat() >= 0 && uv.getAsFloat() <= 16, id + " UV bounds");
            }
            assertTrue(deepHead, id + " must have a sculpted head, not extruded sprite pixels");
        }
    }

    @Test void thirdPersonAnchorsBothPalmsAndPointsTheStaffUpward() throws IOException {
        for (String id : names()) {
            var model = held(id); var grip = vector(model.getAsJsonArray("tnc_grip")).sub(8,8,8);
            var tip = vector(model.getAsJsonArray("tnc_tip")).sub(8,8,8);
            for (boolean left : new boolean[]{false, true}) {
                var d = model.getAsJsonObject("display").getAsJsonObject("thirdperson_"+(left?"lefthand":"righthand"));
                var palm = pose(grip, d, left); assertTrue(palm.length() < .01, id + " grip is centered on palm");
                var axis = pose(tip, d, left).sub(palm).rotateY((float)Math.PI).rotateX(-(float)Math.PI/2);
                assertTrue(-axis.y > axis.length()*.95, id + " held staff points up, hand="+left);
            }
        }
    }

    @Test void firstPersonKeepsTheLongAxisInTheScreenPlaneAndLimitsApparentHeight() throws IOException {
        for (String id : names()) {
            var model = held(id); var grip = vector(model.getAsJsonArray("tnc_grip")).sub(8,8,8);
            var tip = vector(model.getAsJsonArray("tnc_tip")).sub(8,8,8);
            for (boolean left : new boolean[]{false,true}) {
                var d = model.getAsJsonObject("display").getAsJsonObject("firstperson_"+(left?"lefthand":"righthand"));
                var palm = pose(grip,d,left); assertEquals(-.8, palm.y, .01);
                assertEquals(left?1.8:-1.8,palm.x,.01);
                var axis = pose(tip,d,left).sub(palm).rotateY((left?-1:1)*(float)Math.PI/4);
                assertTrue(axis.y > axis.length()*.95 && Math.abs(axis.z) < axis.length()*.2, id + " must not spear into camera depth");
                assertTrue(axis.length() <= 10.81, id + " staff height must fit a normal first-person view");
            }
        }
    }
}
