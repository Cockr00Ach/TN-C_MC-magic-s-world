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
    @Test void partnerReversesTheLongBladeWhileKeepingItsGripFixedInEitherHand() throws Exception {
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
            assertEquals(-1,tip.normalize().dot(correctedTip.normalize()),.00001,context+": reverse the long blade and pommel spike about the handle");
            var facing=new Vector3f(.75f,.5f,.5f).mulPosition(pose.last().pose()).sub(grip);
            assertEquals(-1,edge.normalize().dot(facing.normalize()),.00001,context+": turn the inward-facing cutting edge outwards");
        }
    }
    @Test void primaryBladeAndCombinedInventoryPresentationKeepTheirAuthoredOrientation(){
        var pose=new PoseStack();pose.translate(1,2,3);pose.mulPose(Axis.ZP.rotationDegrees(30));
        var before=new org.joml.Matrix4f(pose.last().pose());
        BronzeTwinGrip.apply(false,pose);
        assertEquals(before,pose.last().pose());
    }
}
