package com.tnc.tnc.life.routes;
public final class RouteNodeRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<RouteNodeEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer renderer;
    public RouteNodeRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx){renderer=ctx.getItemRenderer();}
    public void render(RouteNodeEntity be,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffer,int light,int overlay){com.tnc.tnc.arcana.client.ArcanaDeviceVisual.render(be,partial,pose,buffer,light,overlay);if(RouteMirrorLinks.mirror(be)){
        var state=be.getBlockState();var mc=net.minecraft.client.Minecraft.getInstance();String id=be.kind().id;
        var base=mc.getModelManager().getModel(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","block/"+id+"_base"));
        var head=mc.getModelManager().getModel(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","block/"+id+"_head"));
        var vertices=buffer.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());var dispatcher=mc.getBlockRenderer().getModelRenderer();
        dispatcher.renderModel(pose.last(),vertices,state,base,1,1,1,light,overlay);
        double yaw=Math.toRadians(180-state.getValue(RouteMirrorBlock.FACING).toYRot()),pitch=0;
        if(be.mirrorTarget!=null){var d=be.mirrorTarget.subtract(be.getBlockPos());yaw=Math.atan2(-d.getX(),-d.getZ());pitch=Math.atan2(d.getY(),Math.hypot(d.getX(),d.getZ()));}
        pose.pushPose();pose.translate(.5,.75,.5);pose.mulPose(com.mojang.math.Axis.YP.rotation((float)yaw));pose.mulPose(com.mojang.math.Axis.XP.rotation((float)pitch));pose.translate(-.5,-.75,-.5);
        dispatcher.renderModel(pose.last(),vertices,state,head,1,1,1,light,overlay);pose.popPose();return;
    }if(be.kind()!=RouteKind.INFUSER&&be.kind()!=RouteKind.CHARGER)return;var stack=be.inventory.getItem(0);if(stack.isEmpty())return;pose.pushPose();pose.translate(.5,be.kind()==RouteKind.INFUSER?.93:.77,.5);pose.scale(.45f,.45f,.45f);renderer.renderStatic(stack,net.minecraft.world.item.ItemDisplayContext.GROUND,light,overlay,pose,buffer,be.getLevel(),0);pose.popPose();}
}
