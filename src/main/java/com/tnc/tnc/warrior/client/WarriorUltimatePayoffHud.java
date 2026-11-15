package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorWeaponFamily;
import com.tnc.tnc.warrior.WarriorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local, finite reward readout for the elemental bow's post-ultimate seeking volley. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorUltimatePayoffHud {
    private static final int WIDTH=114,HEIGHT=29;
    private static ClientLevel world;
    private static int owner=-1,duration;
    private static long grantedAt,until;

    private WarriorUltimatePayoffHud() {}

    /** Called on the client main thread by the execution-effect packet. */
    public static void grant(int entityId,int ticks){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.getId()!=entityId||!mc.player.isAlive()||ticks<=0)return;
        world=mc.level;owner=entityId;duration=Math.min(600,ticks);grantedAt=world.getGameTime();until=grantedAt+duration;
    }

    private static void clear(){world=null;owner=-1;duration=0;grantedAt=0;until=0;}
    private static boolean valid(Minecraft mc){
        return world!=null&&mc.level==world&&mc.player!=null&&mc.player.getId()==owner&&mc.player.isAlive()
                &&!mc.player.isSpectator()&&world.getGameTime()<until;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase==TickEvent.Phase.END&&world!=null&&!valid(Minecraft.getInstance()))clear();
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!valid(mc)){if(world!=null)clear();return;}
        if(mc.options.hideGui||mc.screen!=null||WarriorWeapons.family(mc.player.getMainHandItem())!=WarriorWeaponFamily.BOW)return;
        int screenWidth=mc.getWindow().getGuiScaledWidth(),screenHeight=mc.getWindow().getGuiScaledHeight();
        if(screenWidth<180||screenHeight<130)return;
        double remaining=Math.max(0,until-world.getGameTime()-mc.getFrameTime());
        float alpha=(float)Math.min(1,remaining/16);if(alpha<.03f)return;
        double age=world.getGameTime()+mc.getFrameTime()-grantedAt;
        boolean lesson=TemperingClient.training()||!TemperingClient.courseNow().isEmpty();
        // Combo slots occupy the lower left; armor/mana and hotbar occupy the center below this row.
        // A training lesson uses height-88, so move this badge upward while that lesson is present.
        int x=screenWidth-WIDTH-10,y=Math.max(8,screenHeight-108-(lesson?20:0));
        GuiGraphics g=e.getGuiGraphics();g.pose().pushPose();g.pose().translate(0,0,85);
        g.fill(x,y,x+WIDTH,y+HEIGHT,color(0x11191B,.86f*alpha));
        int border=color(age<20?0xA99563:0x486361,(age<20?.80f:.60f)*alpha);
        g.fill(x,y,x+WIDTH,y+1,border);g.fill(x,y+HEIGHT-1,x+WIDTH,y+HEIGHT,border);
        g.fill(x,y,x+1,y+HEIGHT,border);g.fill(x+WIDTH-1,y,x+WIDTH,y+HEIGHT,border);
        for(int i=0;i<3;i++)arrow(g,x+7+i*7,y+6,color(0xB8D4C0,alpha*(i==1?1:.7f)));
        g.drawString(mc.font,"散射追猎",x+32,y+5,color(0xE4DDC7,alpha),false);
        String seconds=(int)Math.ceil(remaining/20)+"秒";
        g.drawString(mc.font,seconds,x+WIDTH-7-mc.font.width(seconds),y+5,color(0xC7D8C8,alpha),false);
        int trackX=x+7,trackY=y+20,trackWidth=WIDTH-14;
        g.fill(trackX,trackY,trackX+trackWidth,trackY+3,color(0x33413F,.85f*alpha));
        int filled=(int)Math.ceil(trackWidth*Math.min(1,remaining/Math.max(1,duration)));
        if(filled>0){g.fill(trackX,trackY,trackX+filled,trackY+3,color(0x829D8A,.9f*alpha));g.fill(trackX,trackY,trackX+filled,trackY+1,color(0xB8CBA9,.85f*alpha));}
        g.pose().popPose();
    }

    /** Three tiny fletched-arrow marks, drawn from pixels so the payoff needs no texture pack. */
    private static void arrow(GuiGraphics g,int x,int y,int color){
        g.fill(x+2,y,x+3,y+7,color);g.fill(x+1,y+1,x+4,y+2,color);g.fill(x,y+2,x+5,y+3,color);
        g.fill(x+1,y+5,x+2,y+6,color);g.fill(x+3,y+5,x+4,y+6,color);
    }
    private static int color(int rgb,float alpha){return Math.max(0,Math.min(255,Math.round(alpha*255)))<<24|rgb;}
}
