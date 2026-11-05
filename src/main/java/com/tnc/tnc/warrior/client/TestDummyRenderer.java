package com.tnc.tnc.warrior.client;
import com.tnc.tnc.warrior.TestDummyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class TestDummyRenderer extends HumanoidMobRenderer<TestDummyEntity,HumanoidModel<TestDummyEntity>> {
    public TestDummyRenderer(EntityRendererProvider.Context context){super(context,new HumanoidModel<TestDummyEntity>(context.bakeLayer(ModelLayers.PLAYER)){
        @Override public void setupAnim(TestDummyEntity e,float swing,float amount,float age,float yaw,float pitch){
            super.setupAnim(e,0,0,age,0,0);rightArm.xRot=leftArm.xRot=0;rightArm.zRot=(float)Math.PI/2;leftArm.zRot=-(float)Math.PI/2;
            rightLeg.x=0;rightLeg.xRot=rightLeg.zRot=0;leftLeg.visible=false;hat.visible=false;
            body.xRot=(float)Math.sin(e.hurtTime*.6)*e.hurtTime*.004f;
        }
    },.35f);}
    @Override public ResourceLocation getTextureLocation(TestDummyEntity e){return ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/test_dummy.png");}
}
