package com.tnc.tnc.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindSolidModels {
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc","wind/"+name);}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e){e.register(id("feather_wing"));e.register(id("orbit_ribbon"));e.register(id("seal"));}
    private static void draw(String name,PoseStack p,MultiBufferSource b){
        p.pushPose();p.translate(-.5,0,-.5);
        var mc=Minecraft.getInstance();mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(id(name)),ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,b.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)));
        p.popPose();
    }
    static void flight(int tier,double age,float envelope,PoseStack p,MultiBufferSource b){
        float scale=Math.min(1,envelope*4);
        p.pushPose();p.scale(scale,scale,scale);p.translate(0,.12,0);p.mulPose(Axis.YP.rotationDegrees((float)(age*2.3)));
        p.scale(.95F,1.15F,.95F);draw("orbit_ribbon",p,b);p.popPose();
        if(tier>=3)for(int side:new int[]{-1,1}) {
            p.pushPose();p.translate(side*.23,.9,.25);
            p.mulPose(Axis.ZP.rotationDegrees(-side*(26+(float)Math.sin(age*.10)*5)));
            p.scale(side*(1.15F+tier*.12F)*scale,scale,scale);draw("feather_wing",p,b);p.popPose();
        }
    }
    static void seal(double radius,double age,float alpha,PoseStack p,MultiBufferSource b){
        if(alpha<=0)return;
        p.pushPose();p.translate(0,.08,0);p.mulPose(Axis.YP.rotationDegrees((float)(age*.55)));
        float size=(float)(radius*2*Math.min(1,alpha*4));p.scale(size,1.2F,size);
        draw("seal",p,b);p.popPose();
    }
}
