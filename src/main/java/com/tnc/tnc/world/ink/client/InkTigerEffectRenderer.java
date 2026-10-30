package com.tnc.tnc.world.ink.client;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
public final class InkTigerEffectRenderer extends GeoEntityRenderer<InkTigerEffectEntity>{
    private static ResourceLocation asset(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
    public InkTigerEffectRenderer(EntityRendererProvider.Context c){super(c,new GeoModel<>(){
        @Override public ResourceLocation getModelResource(InkTigerEffectEntity e){return asset("geo/entity/ink_winged_tiger.geo.json");}
        @Override public ResourceLocation getTextureResource(InkTigerEffectEntity e){return asset("textures/entity/ink_winged_tiger.png");}
        @Override public ResourceLocation getAnimationResource(InkTigerEffectEntity e){return asset("animations/entity/ink_winged_tiger.animation.json");}
        @Override public void setCustomAnimations(InkTigerEffectEntity e,long id,software.bernie.geckolib.core.animation.AnimationState<InkTigerEffectEntity> state){super.setCustomAnimations(e,id,state);InkTigerWingPose.apply(this,false);}
    });withScale((float)InkTigerAttackRules.GHOST_SCALE*InkTigerAttackRules.BODY_SCALE);shadowRadius=0;}
    @Override public RenderType getRenderType(InkTigerEffectEntity e,ResourceLocation t,MultiBufferSource b,float p){return RenderType.entityTranslucent(t);}
    @Override public Color getRenderColor(InkTigerEffectEntity e,float p,int l){return Color.ofRGBA(1F,1F,1F,(e.finished()?.08F:.34F)*(float)Math.min(1,(e.age()+p)/18));}
    @Override public void renderCubesOfBone(PoseStack p,GeoBone b,VertexConsumer o,int l,int v,float r,float g,float blue,float a){super.renderCubesOfBone(p,b,o,LightTexture.FULL_BRIGHT,v,r,g,blue,a*InkWingOpacity.alpha(b.getName()));}
    @Override public void render(InkTigerEffectEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource b,int light){
        if(e.mode()==InkTigerEffectEntity.GHOST){
            float formed=(float)Math.min(1,(e.age()+partial)/22);pose.pushPose();pose.scale(formed,formed,formed);
            super.render(e,yaw,partial,pose,b,LightTexture.FULL_BRIGHT);pose.popPose();
        }
        InkTigerSpellVisuals.effect(e,partial,pose,b);
    }
}
