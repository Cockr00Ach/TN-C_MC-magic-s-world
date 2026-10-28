package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.skyacademy.AcademyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;

/** Short, readable resident dialogue using the established old-brass grimoire frame. */
public final class AcademyScreen extends Screen {
    private final CompoundTag state;private double left,top,scale;
    public AcademyScreen(CompoundTag t){super(Component.literal(t.getString("Title")));state=t.copy();}
    @Override protected void init(){scale=Math.min(1.5,Math.min((width-16)/430.,(height-16)/260.));left=(width-430*scale)/2;top=(height-260*scale)/2;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);ArcaneGui.proportionalFrame(g,0,0,430,260);
        g.drawString(font,state.getString("Name"),25,30,ArcaneGui.GOLD,false);g.drawString(font,title,25,52,ArcaneGui.CYAN,false);
        int y=82;for(var line:font.split(Component.literal(state.getString("Text")),378)){g.drawString(font,line,25,y,ArcaneGui.TEXT,false);y+=15;}
        boolean ok=state.getBoolean("Allowed");String text=ok?"请演示给我看 → 然后我来试试":"先完成上一段现场研习，再来找我";
        g.fill(25,207,405,235,ok?0xff253d40:0xff28282a);g.renderOutline(25,207,380,28,ArcaneGui.GOLD);g.drawCenteredString(font,text,215,217,ok?ArcaneGui.TEXT:ArcaneGui.DIM);
        g.drawString(font,"ESC 暂别 · 可再次交谈",25,241,ArcaneGui.DIM,false);g.pose().popPose();super.render(g,mx,my,partial);
    }
    @Override public boolean mouseClicked(double x,double y,int button){double px=(x-left)/scale,py=(y-top)/scale;if(button==0&&state.getBoolean("Allowed")&&px>=25&&px<405&&py>=207&&py<235){AcademyNetwork.action(state.getLong("Token"),"begin");onClose();return true;}return super.mouseClicked(x,y,button);}
}
