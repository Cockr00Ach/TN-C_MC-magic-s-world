package com.tnc.tnc.life.routes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

/** Real rooted world geometry, with a restrained mature sway. */
public final class RoutePlantRenderer implements BlockEntityRenderer<RoutePlantEntity> {
    public RoutePlantRenderer(BlockEntityRendererProvider.Context context){}
    @Override public int getViewDistance(){return 32;}
    @Override public void render(RoutePlantEntity plant,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(plant.getLevel()==null)return;
        boolean mature=plant.getBlockState().getValue(RoutePlantBlock.AGE)==3;
        float sway=mature?(float)Math.sin((plant.getLevel().getGameTime()+partial)*.035+plant.getBlockPos().getX())*.7f:0;
        float size=plant.getPersistentData().contains("ArcanaScale")?plant.getPersistentData().getFloat("ArcanaScale"):1;
        if(plant.getPersistentData().getLong("OverdraftUntil")>plant.getLevel().getGameTime())sway=(float)Math.sin((plant.getLevel().getGameTime()+partial)*.32)*5;
        pose.pushPose();pose.translate(.5,0,.5);pose.scale(size,size,size);pose.translate(-.5,0,-.5);pose.translate(.5,.12,.5);pose.mulPose(Axis.ZP.rotationDegrees(sway));pose.translate(-.5,-.12,-.5);
        com.tnc.tnc.life.botanical.client.BotanicalPlantRenderer.drawPlant(plant.getBlockState(),pose,buffers,light);pose.popPose();
    }
}
