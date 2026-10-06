package com.tnc.tnc.life.routes;
public final class RouteNodeRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<RouteNodeEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer renderer;
    public RouteNodeRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx){renderer=ctx.getItemRenderer();}
    public void render(RouteNodeEntity be,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffer,int light,int overlay){if(be.kind()!=RouteKind.INFUSER&&be.kind()!=RouteKind.CHARGER)return;var stack=be.inventory.getItem(0);if(stack.isEmpty())return;pose.pushPose();pose.translate(.5,be.kind()==RouteKind.INFUSER?.93:.77,.5);pose.scale(.45f,.45f,.45f);renderer.renderStatic(stack,net.minecraft.world.item.ItemDisplayContext.GROUND,light,overlay,pose,buffer,be.getLevel(),0);pose.popPose();}
}
