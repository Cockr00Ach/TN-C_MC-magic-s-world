package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthSpearLayoutTest {
    @Test void authoredPointStaysAtTheTargetAndEntireShaftExtendsUpward() {
        var pose=new PoseStack();EarthSpearLayout.apply(pose,25,70);
        var matrix=pose.last().pose();
        var tip=matrix.transformPosition(new Vector3f(.5F,.5F,2));
        var shaft=matrix.transformPosition(new Vector3f(.5F,.5F,0));
        assertEquals(0,tip.x,1E-5);assertEquals(0,tip.y,1E-5);assertEquals(0,tip.z,1E-5);
        assertEquals(0,shaft.x,1E-5);assertEquals(70,shaft.y,1E-5);assertEquals(0,shaft.z,1E-5);
    }
    @Test void everyAuthoredCornerRemainsAboveTheVisiblePointWithoutLateralDrift() throws Exception {
        var pose=new PoseStack();EarthSpearLayout.apply(pose,25,70);
        var path=java.nio.file.Path.of("src/main/resources/assets/tnc/models/projectile/earth_spear_sharp.json");
        var elements=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().getAsJsonArray("elements");
        float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(var entry:elements) {
            var e=entry.getAsJsonObject();
            for(int i=0;i<8;i++) {
                float[] p=new float[3];
                for(int axis=0;axis<3;axis++)p[axis]=e.getAsJsonArray((i&(1<<axis))==0?"from":"to").get(axis).getAsFloat()/16;
                var v=pose.last().pose().transformPosition(new Vector3f(p[0],p[1],p[2]));
                assertTrue(Math.abs(v.x)<5.5&&Math.abs(v.z)<5.5,"Spear must stay centered above its target");
                minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);
            }
        }
        assertEquals(0,minY,1E-5);assertEquals(70,maxY,1E-5);
    }
}
