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
        int textureWidth=128,textureHeight=128;
        try(var in=BronzePuppetModel.class.getResourceAsStream("/assets/tnc/geo/entity/bronze_puppet_parts.json")){
            if(in==null)throw new IllegalStateException("Missing bronze puppet geometry");
            var document=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
            var parts=document.isJsonArray()?document.getAsJsonArray():document.getAsJsonObject().getAsJsonArray("parts");
            float uvScale=1;
            if(document.isJsonObject()){
                var metadata=document.getAsJsonObject();textureWidth=metadata.get("texture_width").getAsInt();textureHeight=metadata.get("texture_height").getAsInt();
                // CubeDefinition scales UV denominators only; physical bone proportions stay unchanged.
                uvScale=1f/metadata.get("texel_density").getAsFloat();
            }
            for(var raw:parts){var part=raw.getAsJsonObject();var cubes=CubeListBuilder.create();
                for(var box:part.getAsJsonArray("boxes"))if(!box.getAsJsonObject().has("rotation"))addCube(cubes,box.getAsJsonObject(),uvScale,false);
                var p=part.getAsJsonArray("pivot");var bone=root.addOrReplaceChild(part.get("name").getAsString(),cubes,PartPose.offset(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
                int index=0;
                for(var box:part.getAsJsonArray("boxes")){
                    var b=box.getAsJsonObject();if(!b.has("rotation"))continue;
                    var a=b.getAsJsonArray("box");var rotation=b.getAsJsonArray("rotation");var child=CubeListBuilder.create();addCube(child,b,uvScale,true);
                    bone.addOrReplaceChild("detail_"+index++,child,PartPose.offsetAndRotation(
                        a.get(0).getAsFloat()+a.get(3).getAsFloat()/2,a.get(1).getAsFloat()+a.get(4).getAsFloat()/2,a.get(2).getAsFloat()+a.get(5).getAsFloat()/2,
                        rotation.get(0).getAsFloat()*Mth.DEG_TO_RAD,rotation.get(1).getAsFloat()*Mth.DEG_TO_RAD,rotation.get(2).getAsFloat()*Mth.DEG_TO_RAD));
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        return LayerDefinition.create(mesh,textureWidth,textureHeight);
    }
    private static void addCube(CubeListBuilder cubes,JsonObject box,float uvScale,boolean centered){
        var a=box.getAsJsonArray("box");var uv=box.getAsJsonArray("uv");float w=a.get(3).getAsFloat(),h=a.get(4).getAsFloat(),d=a.get(5).getAsFloat();
        cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(
            centered?-w/2:a.get(0).getAsFloat(),centered?-h/2:a.get(1).getAsFloat(),centered?-d/2:a.get(2).getAsFloat(),w,h,d,CubeDeformation.NONE,uvScale,uvScale);
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
