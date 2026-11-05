package com.tnc.tnc.warrior.forge.client;

import com.mojang.math.Axis;
import com.tnc.tnc.warrior.forge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorForgeClient {
    @SubscribeEvent public static void hand(RenderHandEvent e){
        var p=Minecraft.getInstance().player;
        if(p==null||!p.getMainHandItem().is(WarriorForgeContent.HAMMER)||!p.isUsingItem())return;
        float t=Math.min(1,(p.getTicksUsingItem()+e.getPartialTick())/16f);
        e.getPoseStack().translate(0,-t*.08,0);
        e.getPoseStack().mulPose(Axis.ZP.rotationDegrees(-25*t));
        e.getPoseStack().mulPose(Axis.XP.rotationDegrees(-30*t));
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Post e){
        if(!e.getOverlay().id().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","crosshair")))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!(mc.hitResult instanceof BlockHitResult hit)
                ||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof WarriorForgeEntity be)||be.session==null)return;
        var s=be.session;String line;
        if(s.stage==ForgeSession.Stage.SHAPING)line=s.strokes<12?be.hint():"定形完成 · 潜行持锤修偏 / 空手取去淬火";
        else if(s.stage==ForgeSession.Stage.QUENCHING){
            int ticks=mc.player.isUsingItem()?mc.player.getTicksUsingItem():0;
            line="姿态："+ForgePattern.values()[be.pose].poseName()+" · "+(ticks>=68&&ticks<=92?"水纹收束 · 提起！":"按住入水，听水声由粗转细");
        }else if(s.stage==ForgeSession.Stage.ASSEMBLY)line="回百锻砧装柄 · 对准中间按住约1秒压合";
        else if(s.stage==ForgeSession.Stage.HEATING)line="火候 "+s.heat+" · 橙红700～800 · 空手夹出";
        else line="【"+s.quality().title+"】 空手领取 · "+be.summary();
        e.getGuiGraphics().drawCenteredString(mc.font,line,e.getWindow().getGuiScaledWidth()/2,e.getWindow().getGuiScaledHeight()/2+30,0xffdec38b);
    }
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(WarriorForgeContent.ENTITY,WarriorForgeRenderer::new);}
    }
}
