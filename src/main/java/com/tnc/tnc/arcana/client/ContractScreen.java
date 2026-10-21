package com.tnc.tnc.arcana.client;
import com.tnc.tnc.arcana.*;
import com.tnc.tnc.client.ui.ArcaneGui;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ContractScreen extends Screen {
    private final int entity;private int x,y;public ContractScreen(int id){super(Component.literal("高维契约"));entity=id;}
    @Override protected void init(){x=(width-330)/2;y=(height-238)/2;String[] offers={"当前90%生命 → 高维魔力棱镜","3级经验 → 禁忌古卷","献祭普通武器 → 虚空寄生种","100%大师共鸣酊 → 星界杖芯"};for(int i=0;i<4;i++){final int k=i;addRenderableWidget(Button.builder(Component.literal(offers[i]),b->{ArcanaNetwork.pact(entity,k);onClose();}).bounds(x+22,y+66+i*33,286,24).build());}}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){renderBackground(g);ArcaneGui.proportionalFrame(g,x,y,330,238);g.drawCenteredString(font,title,x+165,y+20,ArcaneGui.GOLD);g.drawCenteredString(font,Component.literal("祂不收金币，只收概念与代价"),x+165,y+41,ArcaneGui.TEXT);g.drawString(font,"同一契约每世界日一次 · 请先留出背包空位",x+23,y+208,ArcaneGui.DIM,false);super.render(g,mx,my,delta);}
    @Override public boolean isPauseScreen(){return false;}
}
