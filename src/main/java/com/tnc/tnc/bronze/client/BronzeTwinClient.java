package com.tnc.tnc.bronze.client;

import com.tnc.tnc.bronze.BronzeTwinWeapon;
import com.tnc.tnc.mixin.BronzeHandInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class BronzeTwinClient {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hand(RenderHandEvent event){
        var mc=Minecraft.getInstance();
        if(event.getHand()!=InteractionHand.OFF_HAND||mc.player==null||!event.getItemStack().isEmpty()||!BronzeTwinWeapon.active(mc.player))return;
        event.setCanceled(true);
        BronzeTwinRenderer.withView(BronzeTwinRenderer.View.SECONDARY,()->((BronzeHandInvoker)mc.gameRenderer.itemInHandRenderer).bronze$renderArmWithItem(
            mc.player,event.getPartialTick(),event.getInterpolatedPitch(),InteractionHand.OFF_HAND,mc.player.getAttackAnim(event.getPartialTick()),mc.player.getMainHandItem(),event.getEquipProgress(),event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight()));
    }
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
            for(String skin:event.getSkins()){PlayerRenderer renderer=event.getSkin(skin);if(renderer!=null)renderer.addLayer(new BronzeTwinLayer<>(renderer));}
        }
        @SubscribeEvent public static void reload(ModelEvent.BakingCompleted event){BronzeTwinRenderer.clear();}
    }
}
