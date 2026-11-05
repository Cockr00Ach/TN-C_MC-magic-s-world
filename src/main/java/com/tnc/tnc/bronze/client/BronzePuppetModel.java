package com.tnc.tnc.bronze.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.bronze.*;
import com.tnc.tnc.warrior.client.*;
import java.io.InputStreamReader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Game and offline preview share exactly the same authored cuboids. */
public final class BronzePuppetModel extends HumanoidModel<BronzePuppetEntity> {
    private float fade=1;
    public BronzePuppetModel(ModelPart root){super(root);hat.visible=false;}
    public static LayerDefinition layer(){
        MeshDefinition mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        try(var in=BronzePuppetModel.class.getResourceAsStream("/assets/tnc/geo/entity/bronze_puppet_parts.json")){
            if(in==null)throw new IllegalStateException("Missing bronze puppet geometry");
            var parts=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            for(var raw:parts){var part=raw.getAsJsonObject();var cubes=CubeListBuilder.create();
                for(var box:part.getAsJsonArray("boxes")){var b=box.getAsJsonObject();var a=b.getAsJsonArray("box");var uv=b.getAsJsonArray("uv");cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat(),a.get(3).getAsFloat(),a.get(4).getAsFloat(),a.get(5).getAsFloat());}
                var p=part.getAsJsonArray("pivot");root.addOrReplaceChild(part.get("name").getAsString(),cubes,PartPose.offset(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(BronzePuppetEntity entity,float limb,float amount,float age,float yaw,float pitch){
        // Native warrior curves are offsets from the authored pose, never last frame's offsets.
        head.resetPose();body.resetPose();rightArm.resetPose();leftArm.resetPose();rightLeg.resetPose();leftLeg.resetPose();hat.resetPose();
        super.setupAnim(entity,limb,amount,age,yaw,pitch);fade=1;
        var motion=entity.motion();float progress=entity.progress(age-entity.tickCount);
        if(motion!=null&&progress<1){
            var p=WarriorPoseMath.of(motion,progress,false);
            rightArm.xRot=p.rightArmX();rightArm.yRot=p.rightArmY();rightArm.zRot=p.rightArmZ();
            leftArm.xRot=p.leftArmX();leftArm.yRot=p.leftArmY();leftArm.zRot=p.leftArmZ();
            body.yRot+=p.bodyYaw();body.xRot+=p.bodyPitch();head.yRot+=p.headYaw();head.xRot+=p.headPitch();rightLeg.xRot+=p.rightLegX();leftLeg.xRot+=p.leftLegX();
            if(Math.abs(leftArm.xRot)<.2f){leftArm.xRot=rightArm.xRot+.15f;leftArm.yRot=-rightArm.yRot*.5f;}
        }else{rightArm.xRot=-.5f;leftArm.xRot=-.7f;leftArm.yRot=-.35f;}
        if(entity.fadeEnd()!=0){fade=Mth.clamp((entity.fadeEnd()-entity.level().getGameTime()-(age-entity.tickCount))/PuppetBondData.FADE,0,1);head.xRot+=(1-fade)*.6f;body.xRot+=(1-fade)*.12f;}
    }
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,float red,float green,float blue,float alpha){super.renderToBuffer(poses,vertices,light,overlay,red,green,blue,alpha*fade);}
}
