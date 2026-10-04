package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;
import java.util.UUID;

/** Approved A health gauge. Does not handle mana, armor or hunger. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class HealthGaugeHud {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"textures/gui/health_bar/health_a_frame.png");
    private static final ResourceLocation HEALTH=ResourceLocation.withDefaultNamespace("player_health");
    private static HealthGaugeState state=new HealthGaugeState();
    private static UUID owner;
    // The original generated PNG is kept intact. Sample its measured alpha bounds.
    private static final int TEX_W=2172,TEX_H=724,U=26,V=85,SW=2128,SH=550;
    private static final int WIDTH=104,HEIGHT=27;
    private HealthGaugeHud() {}
    private static boolean visible(Minecraft mc) {
        return mc.player!=null && mc.gameMode!=null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui;
    }
    @SubscribeEvent public static void claim(RenderGuiOverlayEvent.Pre event) {
        if(HEALTH.equals(event.getOverlay().id()) && visible(Minecraft.getInstance()))event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null){owner=null;state=new HealthGaugeState();return;}
        if(!mc.player.getUUID().equals(owner)){owner=mc.player.getUUID();state=new HealthGaugeState();}
        if(!mc.isPaused())state.tick(mc.player.getHealth(),mc.player.getMaxHealth());
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(!visible(mc))return;
        draw(mc,event.getGuiGraphics(),mc.getWindow().getGuiScaledWidth()/2-110,mc.getWindow().getGuiScaledHeight()-76);
    }
    public static void draw(Minecraft mc,GuiGraphics g,int x,int y) {
        var player=mc.player;if(player==null)return;
        g.pose().pushPose();g.pose().translate(0,0,75);
        g.blit(FRAME,x,y,WIDTH,HEIGHT,U,V,SW,SH,TEX_W,TEX_H);
        // Safe rectangular area strictly inside the ornamental trough.
        int tx=x+25,ty=y+11,tw=67,th=5;
        int trail=Math.round(tw*state.trailFraction()),fill=Math.round(tw*state.fraction());
        g.fill(tx,ty,tx+trail,ty+th,0xFFB36830);
        boolean poison=player.hasEffect(MobEffects.POISON),wither=player.hasEffect(MobEffects.WITHER);
        int top=wither?0xFF989589:poison?0xFFBDCB45:0xFF73F2A0;
        int bottom=wither?0xFF44413F:poison?0xFF667521:0xFF117239;
        if(fill>0)g.fillGradient(tx,ty,tx+fill,ty+th,top,bottom);
        float absorption=player.getAbsorptionAmount();
        if(absorption>0)g.fill(tx,ty+th,tx+Math.round(tw*Math.min(1,absorption/Math.max(1,player.getMaxHealth()))),ty+th+1,0xFFF8D77D);
        String text=number(player.getHealth())+"/"+number(player.getMaxHealth())+(absorption>0?" +"+number(absorption):"");
        g.pose().pushPose();g.pose().translate(tx+tw/2F,ty-.5F,1);g.pose().scale(.75F,.75F,1);
        g.drawString(mc.font,text,-mc.font.width(text)/2,0,0xFFF6F3E5,true);g.pose().popPose();g.pose().popPose();
    }
    private static String number(float value){return value==Math.floor(value)?Integer.toString((int)value):String.format(Locale.ROOT,"%.1f",value);}
}
