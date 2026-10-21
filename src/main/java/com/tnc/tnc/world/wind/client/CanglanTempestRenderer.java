package com.tnc.tnc.world.wind.client;
import com.tnc.tnc.world.wind.CanglanTempestEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
public final class CanglanTempestRenderer extends EntityRenderer<CanglanTempestEntity>{
 public CanglanTempestRenderer(EntityRendererProvider.Context c){super(c);}
 public ResourceLocation getTextureLocation(CanglanTempestEntity e){return TextureAtlas.LOCATION_BLOCKS;}
 public void render(CanglanTempestEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
  p.pushPose();p.mulPose(Axis.YP.rotationDegrees(e.tornado()?(e.tickCount+partial)*28:e.getYRot()));float s=e.visualScale()*(e.tornado()?2:4);p.scale(s,s,s);p.translate(-.5,0,-.5);
  var mc=Minecraft.getInstance();var id=ResourceLocation.fromNamespaceAndPath("tnc",e.tornado()?"projectile/wind_tornado":"projectile/canglan_gale_wave");
  mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(id),ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,b.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)));p.popPose();
 }
}
