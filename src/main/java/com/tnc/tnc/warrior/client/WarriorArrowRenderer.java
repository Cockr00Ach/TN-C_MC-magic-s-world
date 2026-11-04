package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorArrowEntity;
import com.tnc.tnc.warrior.WarriorWeaponOrnaments.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Actual voxel arrow model in flight, with a short, continuous constructed tail. */
public final class WarriorArrowRenderer extends EntityRenderer<WarriorArrowEntity> {
    public WarriorArrowRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(WarriorArrowEntity arrow){return ResourceLocation.parse("tnc:textures/item/"+id(arrow)+".png");}
    private static String id(WarriorArrowEntity a){return a.style()==1?"moon_arrow":a.style()==2?"ember_arrow":"storm_arrow";}
    @Override public void render(WarriorArrowEntity arrow,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,arrow.yRotO,arrow.getYRot())));
            pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial,arrow.xRotO,arrow.getXRot())));
            float shake=arrow.shakeTime-partial;
            if(shake>0)pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake*3)*shake));
            var item=arrow.style()==1?TNMod.MOON_ARROW.get():arrow.style()==2?TNMod.EMBER_ARROW.get():TNMod.STORM_ARROW.get();
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(item),ItemDisplayContext.NONE,light,
                    OverlayTexture.NO_OVERLAY,pose,buffers,arrow.level(),arrow.getId());
            if(arrow.getDeltaMovement().lengthSqr()>.03 && !arrow.onGround()) {
                var out=buffers.getBuffer(WarriorGeometryRenderType.TYPE);
                int c=arrow.style()==1?0xD5BDFF:arrow.style()==2?0xFFAE66:0x9FE8FF;
                pose.translate(-.5,-.5,-.5);
                // Connected tapering blocks, not spawned particles. No accumulated history.
                for(int i=0;i<9;i++){
                    double w=.2*(1-i/12.0),z=-5.2-i*.55;
                    WarriorWeaponOrnamentRenderer.cube(out,pose,new Box(8-w/2,8-w/2,z,w,w,.6,c));
                }
                for(int i=0;i<4;i++){
                    double a=(arrow.tickCount+partial)*.12+i*Math.PI/2;
                    WarriorWeaponOrnamentRenderer.cube(out,pose,new Box(7.9+Math.cos(a)*.6,7.9+Math.sin(a)*.6,7.4,.18,.18,.35,c));
                }
            }
        } finally {pose.popPose();}
        super.render(arrow,yaw,partial,pose,buffers,light);
    }
}
