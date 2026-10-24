package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Closed gold-banded coffer; opening unfolds articulated walking legs and two crystal pincers. */
public final class CrystalCrabModel extends EcologyModel {
    public CrystalCrabModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var r=mesh.getRoot();
        part(r,"coffer",0,0,-7,0,-5,14,8,10,0,16,0);
        var lid=part(r,"lid",0,24,-7,-4,-10,14,4,10,0,16,5);part(lid,"gold_strap_l",64,0,-.6f,-4.2f,-10.2f,1.2f,4.5f,10.4f,-5,0,0);part(lid,"gold_strap_r",64,0,-.6f,-4.2f,-10.2f,1.2f,4.5f,10.4f,5,0,0);
        part(r,"latch",64,0,-1,-2,-.8f,2,3,1,0,17,-5);
        for(int side:new int[]{-1,1})for(int i=0;i<3;i++){var leg=part(r,"leg_"+side+"_"+i,0,48,side<0?-7:0,-.7f,-.7f,7,1.4f,1.4f,side*5,20,-3+i*3);part(leg,"tip",32,48,-.6f,0,-.6f,1.2f,4,1.2f,side*6.5f,0,0);}
        for(int side:new int[]{-1,1}){var claw=part(r,"claw_"+side,0,48,-2,-1,-5,4,2,5,side*5,19,-5);part(claw,"finger",64,24,-1,-1,-4,2,2,4,side*2,0,-3);}
        part(r,"core",80,80,-2.5f,-2.5f,-2.5f,5,5,5,0,17,0);return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(EcologyMob mob,float walk,float amount,float age,float yaw,float pitch){
        reset();boolean awake=mob instanceof MimicCrystalCrab crab&&crab.awakened();float attack=Mth.sin(mob.getAttackAnim(age-mob.tickCount)*Mth.PI);
        root.getChild("lid").xRot=awake?-.8f-attack*.6f:0;
        for(int side:new int[]{-1,1}){root.getChild("claw_"+side).visible=awake;root.getChild("claw_"+side).yRot=side*(.2f+attack*.7f);for(int i=0;i<3;i++){var leg=root.getChild("leg_"+side+"_"+i);leg.visible=awake;leg.yRot=Mth.sin(walk*.9f+i*1.8f+side)*amount*.6f;leg.zRot=side*(.15f+Mth.cos(walk*.9f+i)*amount*.18f);}}
        float pulse=1+Mth.sin(age*.23f)*.15f;core.xScale=core.yScale=core.zScale=pulse;
    }
}
