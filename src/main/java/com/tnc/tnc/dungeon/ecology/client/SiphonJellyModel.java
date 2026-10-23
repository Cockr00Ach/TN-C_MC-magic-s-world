package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Bell, radial skirt, six two-joint tentacles and a distinct central suction cup. */
public final class SiphonJellyModel extends EcologyModel {
    public SiphonJellyModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var r=mesh.getRoot();var bell=part(r,"bell",0,0,-4,-5,-4,8,5,8,0,13,0);part(bell,"crown",0,24,-2.5f,-2,-2.5f,5,2,5,0,-5,0);part(r,"skirt",0,40,-5,-.5f,-5,10,1,10,0,13,0);
        part(r,"sucker",64,0,-2,-.5f,-2,4,1,4,0,17,0);part(r,"mouth",64,16,-1,-.5f,-1,2,1,2,0,17.8f,0);
        for(int i=0;i<6;i++){double a=i*Math.PI/3;var arm=part(r,"tentacle_"+i,32,48,-.6f,0,-.6f,1.2f,5,1.2f,(float)Math.cos(a)*3.4f,13,(float)Math.sin(a)*3.4f);var tip=part(arm,"tip",32,48,-.45f,0,-.45f,.9f,4,.9f,0,4.5f,0);part(tip,"cup",64,0,-.9f,0,-.9f,1.8f,.7f,1.8f,0,3.7f,0);}
        part(r,"core",80,80,-1.5f,-1.5f,-1.5f,3,3,3,0,11,0);return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(EcologyMob mob,float walk,float amount,float age,float yaw,float pitch){reset();float pulse=Mth.sin(age*.17f);var bell=root.getChild("bell");bell.xScale=bell.zScale=1+pulse*.08f;bell.yScale=1-pulse*.12f;for(int i=0;i<6;i++){var arm=root.getChild("tentacle_"+i);arm.xRot=Mth.sin(age*.12f+i)*.23f;arm.zRot=Mth.cos(age*.12f+i)*.23f;arm.getChild("tip").xRot=Mth.sin(age*.16f+i)*.35f;}if(mob instanceof ManaSiphonJelly jelly&&jelly.attached()){root.getChild("sucker").y=21;root.getChild("mouth").y=21.7f;}core.yScale=1+pulse*.2f;}
}
