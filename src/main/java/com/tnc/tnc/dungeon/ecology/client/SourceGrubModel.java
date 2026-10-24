package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.EcologyMob;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Chitin collar, split mandibles, four pairs of stub feet and an oversized liquid reservoir. */
public final class SourceGrubModel extends EcologyModel {
    public SourceGrubModel(ModelPart root){super(root);}
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var r=mesh.getRoot();part(r,"head",0,0,-4,-3,-3,8,6,6,0,19,-7);part(r,"collar",0,24,-4.5f,-3.5f,-2,9,7,4,0,19,-3);part(r,"belly",0,40,-5.5f,-4,-5.5f,11,8,11,0,18.5f,4);part(r,"tail",0,0,-3,-2.5f,-2,6,5,4,0,19.5f,10);
        for(int side:new int[]{-1,1}){part(r,"mandible_"+side,64,0,-.8f,-.8f,-3,1.6f,1.6f,4,side*2,20,-9);for(int i=0;i<4;i++)part(r,"foot_"+side+"_"+i,0,60,-1,-.7f,-1.3f,2,2.5f,2.6f,side*(i>1?5:3.5f),21,-5+i*4);}
        for(int i=0;i<3;i++)part(r,"crystal_"+i,64,0,-1,-3,-1,2,3,2,0,14.5f,1+i*3);
        part(r,"core",80,80,-4,-3,-4.5f,8,6,9,0,18.5f,4);return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(EcologyMob mob,float walk,float amount,float age,float yaw,float pitch){reset();float pulse=Mth.sin(age*.16f);var belly=root.getChild("belly");belly.xScale=1+pulse*.055f;belly.yScale=1-pulse*.045f;for(int side:new int[]{-1,1}){root.getChild("mandible_"+side).yRot=side*(.15f+Mth.sin(age*.3f)*.16f);for(int i=0;i<4;i++)root.getChild("foot_"+side+"_"+i).xRot=Mth.sin(walk*.9f+i*1.4f+side)*amount*.6f;}core.yScale=1+pulse*.1f;core.zRot=pulse*.035f;}
}
