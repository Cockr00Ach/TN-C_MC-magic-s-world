package com.tnc.tnc.life.routes.mech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Single floating aperture, five independently orbiting vanes and a pulsing spell core. */
public final class ZeroGunRenderer extends BlockEntityWithoutLevelRenderer {
    public static final java.util.List<String> PARTS=java.util.List.of("body","rail","fin","glow","core");
    public ZeroGunRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    private void part(String name,ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light,int overlay,boolean glow){
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","item/zero_spellgun_"+name));
        mc.getItemRenderer().renderModelLists(model,stack,glow?LightTexture.FULL_BRIGHT:light,overlay,pose,
                buffers.getBuffer(glow?RenderType.eyes(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS):Sheets.cutoutBlockSheet()));
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var mc=Minecraft.getInstance();double time=mc.level==null?0:mc.level.getGameTime()+mc.getFrameTime();
        boolean firing=mc.level!=null&&stack.hasTag()&&stack.getTag().contains("ZeroFiring")&&mc.level.getGameTime()-stack.getTag().getLong("ZeroFiring")<4;
        pose.pushPose();if(firing)pose.translate(0,0,.028*Math.abs(Math.sin(time*1.57)));
        part("body",stack,pose,buffers,light,overlay,false);part("rail",stack,pose,buffers,light,overlay,false);
        for(int i=0;i<5;i++){
            pose.pushPose();pose.translate(.5,.625,-.375);pose.mulPose(Axis.ZP.rotationDegrees((float)(i*72+time*(firing?4:.35))));
            pose.translate(0,(firing?.025:.008)*Math.sin(time*.22+i),0);pose.translate(-.5,-.625,.375);
            part("fin",stack,pose,buffers,light,overlay,false);part("glow",stack,pose,buffers,light,overlay,true);pose.popPose();
        }
        pose.pushPose();pose.translate(.5,.625,.25);pose.mulPose(Axis.ZP.rotationDegrees((float)(-time*(firing?9:1.2))));
        float pulse=(float)(1+(firing?.12:.035)*Math.sin(time*.38));pose.scale(pulse,pulse,pulse);pose.translate(-.5,-.625,-.25);
        part("core",stack,pose,buffers,light,overlay,true);pose.popPose();pose.popPose();
    }
}
