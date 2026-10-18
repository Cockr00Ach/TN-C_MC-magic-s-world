package com.tnc.tnc.life.routes.mech;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
public final class MechGunRenderer extends BlockEntityWithoutLevelRenderer {
    public MechGunRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    private void part(String name,ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light,int overlay){var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","item/mech_spellgun_"+name));mc.getItemRenderer().renderModelLists(model,stack,light,overlay,pose,buffers.getBuffer(name.equals("glow")?RenderType.eyes(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS):Sheets.cutoutBlockSheet()));}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var mc=Minecraft.getInstance();part("body",stack,pose,buffers,light,overlay);double time=mc.level==null?0:mc.level.getGameTime()+mc.getFrameTime();boolean firing=stack.hasTag()&&mc.level!=null&&mc.level.getGameTime()-stack.getTag().getLong("MechFiring")<5;
        pose.pushPose();pose.translate(.5,.6125,.125);pose.mulPose(Axis.ZP.rotation((float)(time*(firing?.55:.015))));pose.translate(-.5,-.6125,-.125);part("barrels",stack,pose,buffers,light,overlay);part("glow",stack,pose,buffers,LightTexture.FULL_BRIGHT,overlay);pose.popPose();
    }
}
