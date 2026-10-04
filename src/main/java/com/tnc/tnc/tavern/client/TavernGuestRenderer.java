package com.tnc.tnc.tavern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.tavern.TavernGuestEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;

/** Vanilla clothing with a dedicated seated mesh: bent thighs, vertical shins, shortened coat. */
public final class TavernGuestRenderer extends MobRenderer<TavernGuestEntity,VillagerModel<TavernGuestEntity>> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("tnc","tavern_guest"),"main");
    public static LayerDefinition layer() {
        var mesh=VillagerModel.createBodyModel();var root=mesh.getRoot();
        root.getChild("body").addOrReplaceChild("jacket",CubeListBuilder.create().texOffs(0,38).addBox(-4,0,-3,8,12,6,new CubeDeformation(.5F)),PartPose.ZERO);
        for(String name:new String[]{"right_leg","left_leg"}) {
            float x=name.equals("right_leg")?-2:2;
            var thigh=root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,22).addBox(-2,0,-2,4,6,4),PartPose.offsetAndRotation(x,12,0,-(float)Math.PI/2,0,0));
            thigh.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(0,28).addBox(-2,0,-2,4,6,4),PartPose.offsetAndRotation(0,6,0,(float)Math.PI/2,0,0));
        }
        return LayerDefinition.create(mesh,64,64);
    }
    private static final class SeatedModel extends VillagerModel<TavernGuestEntity> {
        SeatedModel(ModelPart root){super(root);}
        @Override public void setupAnim(TavernGuestEntity guest,float walk,float amount,float age,float yaw,float pitch) {
            super.setupAnim(guest,0,0,age,0,0);
            root().getChild("right_leg").xRot=-(float)Math.PI/2;
            root().getChild("left_leg").xRot=-(float)Math.PI/2;
            float shin=guest.lowSeat()?0:(float)Math.PI/2;
            root().getChild("right_leg").getChild("shin").xRot=shin;
            root().getChild("left_leg").getChild("shin").xRot=shin;
        }
    }
    public TavernGuestRenderer(EntityRendererProvider.Context context) {
        super(context,new SeatedModel(context.bakeLayer(LAYER)),.25F);
        addLayer(new VillagerProfessionLayer<>(this,context.getResourceManager(),"villager"));
    }
    @Override public ResourceLocation getTextureLocation(TavernGuestEntity guest){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/villager/villager.png");}
    @Override public void render(TavernGuestEntity guest,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light) {
        pose.pushPose();pose.translate(0,-.704,0);super.render(guest,yaw,partial,pose,buffer,light);pose.popPose();
    }
    @Override protected void scale(TavernGuestEntity guest,PoseStack pose,float partial){pose.scale(.9375F,.9375F,.9375F);}
}
