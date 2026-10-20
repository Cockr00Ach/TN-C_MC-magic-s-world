package com.tnc.tnc.alchemy.client;
import com.tnc.tnc.alchemy.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import com.mojang.blaze3d.vertex.PoseStack;
public final class HiddenCacheRenderer implements BlockEntityRenderer<HiddenCacheEntity>{
    public HiddenCacheRenderer(BlockEntityRendererProvider.Context c){}
    @Override public void render(HiddenCacheEntity be,float partial,PoseStack p,MultiBufferSource b,int light,int overlay){var mc=Minecraft.getInstance();if(!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()||mc.player==null||be.getLevel()==null)return;double time=be.getLevel().getGameTime()+partial;double range=mc.player.hasEffect(AlchemyContent.ECHO)?52:24;if(be.getBlockPos().distToCenterSqr(mc.player.position())>range*range)return;
        p.pushPose();p.translate(.5,.41+Math.sin(time*.05)*.025,.5);p.scale(.9f,.9f,.9f);mc.getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(AlchemyContent.HIDDEN_CACHE),net.minecraft.world.item.ItemDisplayContext.FIXED,0xf000f0,0,p,b,be.getLevel(),0);p.popPose();AlchemyRenderer.ring(p,b,time,0xdd8ce5e0,.08f,.63f);AlchemyRenderer.ring(p,b,-time,0xffe6c58a,1.02f,.32f);
    }
}
