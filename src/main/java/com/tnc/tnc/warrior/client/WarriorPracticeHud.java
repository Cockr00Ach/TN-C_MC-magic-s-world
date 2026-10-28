package com.tnc.tnc.warrior.client;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WarriorPracticeHud {
    private WarriorPracticeHud(){}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||!TemperingClient.training())return;
        String text="守势反击 · "+WarriorPracticeScreen.trainingStatus();
        var g=event.getGuiGraphics();int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        int textWidth=mc.font.width(text),x=(width-textWidth)/2,y=height-88;
        g.pose().pushPose();g.pose().translate(0,0,75);
        g.fill(x-5,y-3,x+textWidth+5,y+12,0xa010151b);
        g.drawString(mc.font,text,x,y,0xffe8dcc2,true);g.pose().popPose();
    }
}
