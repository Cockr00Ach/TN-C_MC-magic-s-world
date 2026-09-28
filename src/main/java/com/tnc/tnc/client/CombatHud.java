package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.CombatRules;
import com.tnc.tnc.magic.ChantRules;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.CombatCasting;
import com.tnc.tnc.network.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compact chant at top; downed status and server-authoritative rescue progress below center. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class CombatHud {
    private static CombatStatePacket state=new CombatStatePacket(0,0,"",-1);
    private static int age,inputTicks;
    private static boolean held;
    private CombatHud() {}
    public static void accept(CombatStatePacket packet) {state=packet;age=0;}
    public static boolean downed() {return age<40&&(state.mode()==1||state.mode()==2);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();age++;
        if(mc.player==null||mc.level==null) {state=new CombatStatePacket(0,0,"",-1);held=false;return;}
        boolean pressed=mc.screen==null&&MagicStoneKeys.RESCUE.isDown();
        if(pressed!=held||pressed&&++inputTicks%3==0)MagicStoneNetwork.CHANNEL.sendToServer(new RescueInputPacket(pressed));
        held=pressed;
        if(downed()) {
            mc.player.input.forwardImpulse=0;mc.player.input.leftImpulse=0;mc.player.input.jumping=false;
            mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        var g=e.getGuiGraphics();int cx=mc.getWindow().getGuiScaledWidth()/2;
        g.pose().pushPose();g.pose().translate(0,0,200);
        if(age<40&&state.mode()!=0) {
            int y=mc.getWindow().getGuiScaledHeight()*2/3;
            String key=MagicStoneKeys.RESCUE.getTranslatedKeyMessage().getString();
            String title=state.mode()==1?"你已倒地 · 按住 ["+key+"] 自救":state.mode()==2?"你已倒地 · 等待队友救援":state.mode()==3?"救援中 · 保持 ["+key+"]":state.text().replace("救援键","["+key+"]");
            int width=Math.min(mc.getWindow().getGuiScaledWidth()-12,Math.max(180,mc.font.width(title)+20));
            g.fill(cx-width/2,y-8,cx+width/2,y+29,0xB5101025);
            g.drawCenteredString(mc.font,title,cx,y,0xFFD4C8FF);
            int filled=(int)((width-20)*Math.min(1,state.progress()/(float)CombatRules.RESCUE_TICKS));
            g.fill(cx-width/2+10,y+17,cx+width/2-10,y+20,0xFF35304C);
            g.fill(cx-width/2+10,y+17,cx-width/2+10+filled,y+20,0xFF8568ED);
        }
        var cast=downed()?null:CombatCasting.current(mc.player);
        if(cast!=null) {
            var entry=SpellCatalog.byId(cast.id());
            if(ChantRules.chants(entry)) {
                int y=Math.max(22,mc.getWindow().getGuiScaledHeight()/9);
                String text=ChantRules.line(entry,cast.progress());int width=mc.font.width(text);
                float scale=Math.min(1,(mc.getWindow().getGuiScaledWidth()-24)/(float)Math.max(1,width));
                g.pose().pushPose();g.pose().translate(cx,y,0);g.pose().scale(scale,scale,1);
                g.drawCenteredString(mc.font,text,0,0,0xFFC8B5FF);
                g.drawCenteredString(mc.font,entry.displayName(),0,13,0xFF7F8DCC);g.pose().popPose();
            }
        }
        g.pose().popPose();
    }
}
