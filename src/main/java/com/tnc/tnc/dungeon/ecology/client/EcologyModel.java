package com.tnc.tnc.dungeon.ecology.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.dungeon.ecology.EcologyMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Drawing plumbing only. Each species supplies its own anatomy and animation. */
public abstract class EcologyModel extends EntityModel<EcologyMob> {
    protected final ModelPart root,core;
    protected EcologyModel(ModelPart root){this.root=root;this.core=root.getChild("core");}
    protected void reset(){root.getAllParts().forEach(p->{p.resetPose();p.visible=true;});core.visible=false;}
    protected static PartDefinition part(PartDefinition parent,String name,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz){return parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.offset(px,py,pz));}
    public void renderCore(PoseStack pose,VertexConsumer buffer,float red,float green,float blue,float alpha){core.visible=true;core.render(pose,buffer,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,red,green,blue,alpha);core.visible=false;}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha){root.render(pose,buffer,light,overlay,red,green,blue,alpha);}
}
