package com.tnc.tnc.warrior.forge.client;

import java.util.UUID;
import com.tnc.tnc.warrior.forge.ForgeNamingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional naming never holds the finished weapon hostage. */
public final class ForgeNamingScreen extends Screen {
    private final UUID weapon;
    private EditBox name;
    public ForgeNamingScreen(UUID weapon){super(Component.literal("原初·神铸 · 为你的兵器取名"));this.weapon=weapon;}
    public static void open(UUID weapon){Minecraft.getInstance().setScreen(new ForgeNamingScreen(weapon));}
    @Override protected void init(){
        name=new EditBox(font,width/2-110,height/2-12,220,22,Component.literal("兵器名字"));name.setMaxLength(32);addRenderableWidget(name);setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("铭刻此名"),b->{
            String s=name.getValue().strip();if(s.codePointCount(0,s.length())<2||s.codePointCount(0,s.length())>16)return;
            ForgeNamingNetwork.CHANNEL.sendToServer(new ForgeNamingNetwork.Name(weapon,s));onClose();
        }).bounds(width/2-110,height/2+24,106,20).build());
        addRenderableWidget(Button.builder(Component.literal("稍后再取名"),b->onClose()).bounds(width/2+4,height/2+24,106,20).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-62,0xffe8c6);
        g.drawCenteredString(font,"名字2～16字 · 品质与铸造者铭记不会被改写",width/2,height/2-39,0xbcc4ca);
        super.render(g,x,y,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
