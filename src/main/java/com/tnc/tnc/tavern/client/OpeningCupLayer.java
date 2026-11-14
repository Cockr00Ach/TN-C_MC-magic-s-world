package com.tnc.tnc.tavern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cosmetic film prop; never replaces, consumes or drops a player's inventory item. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class OpeningCupLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation MODEL=ResourceLocation.parse("tnc:cinematic/tavern_cup");
    public OpeningCupLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){event.register(MODEL);}
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){for(var skin:event.getSkins()){if(event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer)renderer.addLayer(new OpeningCupLayer(renderer));}}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch){
        var mc=Minecraft.getInstance();if(!TavernOpeningScene.active()||player!=mc.player)return;
        pose.pushPose();getParentModel().translateToHand(HumanoidArm.RIGHT,pose);
        pose.translate(-.0625,.55,-.10);pose.mulPose(com.mojang.math.Axis.XP.rotation(-getParentModel().rightArm.xRot));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));pose.scale(.65f,.65f,.65f);
        mc.getItemRenderer().render(new ItemStack(Items.STICK),ItemDisplayContext.NONE,false,pose,buffers,light,OverlayTexture.NO_OVERLAY,mc.getModelManager().getModel(MODEL));
        pose.popPose();
    }
}
