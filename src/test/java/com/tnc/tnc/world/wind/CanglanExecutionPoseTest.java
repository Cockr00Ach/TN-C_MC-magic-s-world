package com.tnc.tnc.world.wind;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CanglanExecutionPoseTest {
    @Test void independentClipHasOneContactAndOneRelease(){
        assertEquals("execution_impale",CanglanWarGodRules.animation(CanglanExecutionPose.MOVE));
        assertEquals(112,CanglanWarGodRules.duration(CanglanExecutionPose.MOVE));
        assertFalse(CanglanExecutionPose.pinned(21));assertTrue(CanglanExecutionPose.pinned(22));
        assertTrue(CanglanExecutionPose.pinned(71));assertFalse(CanglanExecutionPose.pinned(72));
        assertEquals("sweep",CanglanWarGodRules.animation(5));assertEquals(80,CanglanWarGodRules.duration(5));
    }
    @Test void actualSpearLiftsTheCaptiveWellAboveTheHelmet(){
        var contact=CanglanExecutionPose.offset(22,0,1);var raised=CanglanExecutionPose.offset(54,0,1);
        assertEquals(1.4,contact.y,.12);assertTrue(contact.z>5);
        assertTrue(raised.y>9&&raised.y-contact.y>7);
        for(int tick=0;tick<=112;tick++){
            var a=CanglanExecutionPose.offset(tick,0,1);var b=CanglanExecutionPose.offset(tick,0,5);
            assertEquals(0,a.scale(5).distanceTo(b),.0001);
            assertTrue(Double.isFinite(a.x)&&Double.isFinite(a.y)&&Double.isFinite(a.z));
            if(tick>0)assertTrue(a.distanceTo(CanglanExecutionPose.offset(tick-1,0,1))<2,"Discontinuous spear pose");
        }
    }
    @Test void launchIsForwardAndBoundedInBothPhases(){
        for(float yaw:new float[]{0,45,90,180,270})for(float scale:new float[]{1,5}){
            var v=CanglanExecutionPose.launch(yaw,scale);
            assertTrue(v.horizontalDistance()>2.5&&v.length()<4.2);assertEquals(1.15,v.y,.0001);
            assertTrue(v.dot(net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw))>0);
        }
    }
    @Test void editableClipAndAuthoritativeBoneKeysAreIdentical()throws Exception{
        var root=Path.of("src/main/resources/assets/tnc/animations/entity");
        var clips=JsonParser.parseString(Files.readString(root.resolve("canglan_warlord.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
        var clip=clips.getAsJsonObject("execution_impale");assertFalse(clip.get("loop").getAsBoolean());assertEquals(5.6,clip.get("animation_length").getAsDouble());
        var data=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/poses/canglan_execution.json"))).getAsJsonObject();
        for(var element:data.getAsJsonArray("nodes")){
            var node=element.getAsJsonObject();String name=node.get("name").getAsString();var keys=node.getAsJsonObject("keys");
            for(var entry:keys.entrySet()){
                String seconds=String.format(java.util.Locale.ROOT,"%.3f",Double.parseDouble(entry.getKey())/20);
                assertEquals(entry.getValue(),clip.getAsJsonObject("bones").getAsJsonObject(name).getAsJsonObject("rotation").get(seconds));
            }
        }
    }
}
