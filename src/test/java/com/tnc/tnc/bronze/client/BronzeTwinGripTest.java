package com.tnc.tnc.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.nio.file.*;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BronzeTwinGripTest {
    @Test void partnerKeepsTheLongBladeForwardAndTheGripFixedInEitherHand() throws Exception {
        BlockModel item;
        try(var input=Files.newBufferedReader(Path.of("src/main/resources/assets/tnc/models/item/bronze_halberd.json"))){item=BlockModel.fromStream(input);}
        for(var context:new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND}){
            boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            var pose=new PoseStack();pose.translate(2,1,-4);pose.mulPose(Axis.XP.rotationDegrees(-70));
            item.getTransforms().getTransform(context).apply(left,pose);pose.translate(-.5,-.5,-.5);
            var grip=new Vector3f(.5f,.5f,.5f).mulPosition(pose.last().pose());
            var tip=new Vector3f(.5f,1.8f,.5f).mulPosition(pose.last().pose()).sub(grip);
            var edge=new Vector3f(.75f,.5f,.5f).mulPosition(pose.last().pose()).sub(grip);
            BronzeTwinGrip.apply(true,pose);
            assertEquals(0,grip.distance(new Vector3f(.5f,.5f,.5f).mulPosition(pose.last().pose())),.00001,context+": keep the hand on the handle");
            var correctedTip=new Vector3f(.5f,1.8f,.5f).mulPosition(pose.last().pose()).sub(grip);
            assertEquals(1,tip.normalize().dot(correctedTip.normalize()),.00001,context+": keep the long blade pointing along the native forward grip");
            var facing=new Vector3f(.75f,.5f,.5f).mulPosition(pose.last().pose()).sub(grip);
            assertEquals(-1,edge.normalize().dot(facing.normalize()),.00001,context+": turn the inward-facing cutting edge outwards");
        }
    }
    @Test void actualLongTipsFaceTheFrontOnPlayerAndPuppetThirdPersonMounts() throws Exception {
        BlockModel display;
        try(var input=Files.newBufferedReader(Path.of("src/main/resources/assets/tnc/models/item/bronze_halberd.json"))){display=BlockModel.fromStream(input);}
        // Player ITEM pose, followed by the puppet's authored right/left idle arm rotations.
        for(boolean puppet:new boolean[]{false,true})for(boolean partner:new boolean[]{false,true}){
            var pose=new PoseStack();
            pose.mulPose(Axis.YP.rotation(puppet&&partner?-.35f:0));
            pose.mulPose(Axis.XP.rotation(puppet?(partner?-.7f:-.5f):-.314159f));
            // The actual ItemInHandLayer/BronzeTwinLayer mount; model-space front is -Z.
            pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate((partner?-1:1)/16d,.125,-.625);
            display.getTransforms().getTransform(partner?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(partner,pose);
            pose.translate(-.5,-.5,-.5);
            BronzeTwinGrip.apply(partner,pose);
            var grip=new Vector3f(.5f,.5f,.5f).mulPosition(pose.last().pose());
            var tip=actualLongTip(partner).mulPosition(pose.last().pose()).sub(grip).normalize();
            assertTrue(tip.z<-.5f,(puppet?"Puppet":"Player")+(partner?" partner":" main")+": the long blade must face the front, not the pommel spike; direction="+tip);
        }
    }
    private static Vector3f actualLongTip(boolean partner) throws Exception {
        var path=Path.of("src/main/resources/assets/tnc/models/item/bronze_twin_"+(partner?"off":"main")+".json");
        var elements=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("elements");
        Vector3f tip=null;
        for(var raw:elements){
            var part=raw.getAsJsonObject();if(!part.get("name").getAsString().equals("forged_twin_bevel"))continue;
            var from=part.getAsJsonArray("from");var to=part.getAsJsonArray("to");
            var candidate=new Vector3f((from.get(0).getAsFloat()+to.get(0).getAsFloat())/32,to.get(1).getAsFloat()/16,(from.get(2).getAsFloat()+to.get(2).getAsFloat())/32);
            if(tip==null||candidate.y>tip.y)tip=candidate;
        }
        assertNotNull(tip,"Expected the actual authored long blade, not its short pommel spike");return tip;
    }
    @Test void primaryBladeAndCombinedInventoryPresentationKeepTheirAuthoredOrientation(){
        var pose=new PoseStack();pose.translate(1,2,3);pose.mulPose(Axis.ZP.rotationDegrees(30));
        var before=new org.joml.Matrix4f(pose.last().pose());
        BronzeTwinGrip.apply(false,pose);
        assertEquals(before,pose.last().pose());
    }
}
