package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** User-approved C woodland-fruit hunger gauge, separate from the existing mana HUD. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class HungerGaugeHud {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"textures/gui/hunger_bar/hunger_c_frame.png");
    private static final ResourceLocation FOOD=ResourceLocation.withDefaultNamespace("food_level");
    private HungerGaugeHud() {}
    private static boolean visible(Minecraft mc) {
        return mc.player!=null && mc.gameMode!=null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui;
    }
    @SubscribeEvent public static void claim(RenderGuiOverlayEvent.Pre event) {
        if(FOOD.equals(event.getOverlay().id())&&visible(Minecraft.getInstance()))event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(!visible(mc))return;
        draw(mc,event.getGuiGraphics(),mc.getWindow().getGuiScaledWidth()/2-110,mc.getWindow().getGuiScaledHeight()-48);
    }
    public static void draw(Minecraft mc,GuiGraphics g,int x,int y) {
        if(mc.player==null)return;
        g.pose().pushPose();g.pose().translate(0,0,75);
        // Sample the opaque sprite bounds; preserve the original PNG alpha and dimensions.
        g.blit(FRAME,x,y,104,20,26,170,2110,402,2172,724);
        int level=Math.max(0,Math.min(20,mc.player.getFoodData().getFoodLevel()));
        int tx=x+26,ty=y+8,tw=64,fill=Math.round(tw*level/20F);
        boolean hungry=mc.player.hasEffect(MobEffects.HUNGER);
        if(fill>0)g.fillGradient(tx,ty,tx+fill,ty+5,hungry?0xFFA3AB68:0xFFE2F593,hungry?0xFF687442:0xFF8FAA42);
        String text=level+"/20";
        g.pose().pushPose();g.pose().translate(tx+tw/2F,ty-.5F,1);g.pose().scale(.75F,.75F,1);
        g.drawString(mc.font,text,-mc.font.width(text)/2,0,0xFFF6F3E5,true);g.pose().popPose();g.pose().popPose();
    }
}
