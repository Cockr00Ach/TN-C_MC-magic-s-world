package com.tnc.tnc.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.bronze.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class BronzeDaggerRenderer extends EntityRenderer<BronzeDaggerEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public BronzeDaggerRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();}
    @Override public void render(BronzeDaggerEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
        pose.pushPose();var velocity=entity.getDeltaMovement();
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(velocity.x,velocity.z)));
        pose.mulPose(Axis.XP.rotation((float)Math.atan2(velocity.horizontalDistance(),velocity.y)));
        pose.scale(.7f,.7f,.7f);
        items.renderStatic(entity.getItem(),ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,pose,buffer,entity.level(),entity.getId());
        pose.popPose();super.render(entity,yaw,partial,pose,buffer,light);
    }
    @Override public ResourceLocation getTextureLocation(BronzeDaggerEntity entity){return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration{
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(BronzeWeapons.DAGGER.get(),BronzeDaggerRenderer::new);}
    }
}
