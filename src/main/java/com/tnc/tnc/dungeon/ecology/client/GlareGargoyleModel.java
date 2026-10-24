package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.EcologyMob;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Crouched stone gargoyle with ribbed angular wings and a shuttered breast crystal. */
public final class GlareGargoyleModel extends EcologyModel {
    public GlareGargoyleModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var r=mesh.getRoot();part(r,"torso",0,0,-4,0,-2.5f,8,10,5,0,9,0);var head=part(r,"head",0,24,-3,-5,-3,6,6,6,0,8,-.5f);part(head,"snout",32,24,-2,-1,-2,4,2,2,0,-1,-3);
        for(int side:new int[]{-1,1}){part(head,"horn_"+side,64,0,-.7f,-4,-.7f,1.4f,4,1.4f,side*2,-4,0);part(r,"arm_"+side,0,48,-1.2f,0,-1.2f,2.4f,9,2.4f,side*5,10,0);part(r,"leg_"+side,0,48,-1.6f,0,-1.6f,3.2f,6,3.2f,side*2.5f,18,0);part(r,"talon_"+side,64,0,-1.8f,0,-3,3.6f,1.5f,5,side*2.5f,22.5f,-1);
            var wing=part(r,"wing_"+side,64,24,side<0?-10:0,-2,-.5f,10,13,1,side*3.5f,10,2);part(wing,"rib",64,0,side<0?-11:0,-2,-.9f,11,1.2f,1.8f,0,0,0);part(wing,"tip",64,0,-.7f,0,-.7f,1.4f,8,1.4f,side*9,3,0);}
        part(r,"tail",0,48,-1,-1,0,2,2,8,0,18,2);part(r,"core",80,80,-2,-2,-1,4,4,2,0,13,-2.8f);return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(EcologyMob mob,float walk,float amount,float age,float yaw,float pitch){reset();float attack=Mth.sin(mob.getAttackAnim(age-mob.tickCount)*Mth.PI);var head=root.getChild("head");head.yRot=yaw*Mth.DEG_TO_RAD*.6f;head.xRot=pitch*Mth.DEG_TO_RAD*.5f;for(int side:new int[]{-1,1}){root.getChild("leg_"+side).xRot=Mth.cos(walk*.8f+(side>0?0:Mth.PI))*amount*.6f;root.getChild("arm_"+side).xRot=-attack*1.4f;root.getChild("wing_"+side).yRot=side*(mob.action()>0?-.15f:.85f+Mth.sin(age*.06f)*.08f);}float glow=mob.action()>0?1.6f:1+Mth.sin(age*.1f)*.04f;core.xScale=core.yScale=glow;}
}
