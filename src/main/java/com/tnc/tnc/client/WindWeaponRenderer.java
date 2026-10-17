package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public final class WindWeaponRenderer extends EntityRenderer<WindWeaponEntity> {
    public WindWeaponRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(WindWeaponEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(WindWeaponEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(e.getYRot()));p.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
        p.mulPose(Axis.ZP.rotationDegrees((float)e.visualAge(partial)*(e.form()==0||e.form()==3?18:3)));
        float size=(float)WindArsenalRules.scale(e.tier());p.scale(size,size,size);
        WindConstructModels.weapon(e.form(),p,b,.78F,true);p.popPose();
    }
}
