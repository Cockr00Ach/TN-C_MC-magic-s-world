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
        WindWeaponTrails.draw(e.trail(),e.position(),e.visualAge(partial),.12+e.tier()*.06,p,b,1);
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(e.getYRot()));p.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
        if(e.form()==0||e.form()==3)p.mulPose(Axis.ZP.rotationDegrees((float)e.visualAge(partial)*18));
        float size=(float)WindArsenalRules.scale(e.tier());p.scale(size,size,size);
        WindConstructModels.phantomWeapon(e.form(),e.visualAge(partial),p,b,1,true);p.popPose();
    }
}
