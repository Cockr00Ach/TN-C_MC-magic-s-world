package com.tnc.tnc.life.client;
import com.tnc.tnc.life.TravelBagMenu;
import com.tnc.tnc.adventure.AdventureRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TravelBagScreen extends com.tnc.tnc.client.ui.ArcaneContainerScreen<TravelBagMenu> {
    public TravelBagScreen(TravelBagMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=198;imageHeight=205;inventoryLabelY=110;}
    @Override protected void init(){super.init();
        for(int i=0;i<5;i++){final int food=i;addRenderableWidget(Button.builder(Component.literal("吃"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,food)).bounds(leftPos+65+i*22,topPos+83,20,17).build());}
        String[] names={"取铜","取银","取金","存入"};for(int i=0;i<4;i++){final int action=i+10;addRenderableWidget(Button.builder(Component.literal(names[i]),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+65+i*31,topPos+29,30,20).build());}
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        com.tnc.tnc.client.ui.ArcaneGui.frame(g,leftPos-3,topPos-3,imageWidth+6,imageHeight+6);
        for(var slot:menu.slots){com.tnc.tnc.client.ui.ArcaneGui.slot(g,leftPos+slot.x,topPos+slot.y);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,"行囊 · "+AdventureRules.money(menu.balance()),8,8,0xe5d6ba,false);g.drawString(font,"钱袋",24,19,0xb5aa98,false);g.drawString(font,"食物袋 / 五格旅途补给",24,51,0xb5aa98,false);g.drawString(font,playerInventoryTitle,8,110,0xb5aa98,false);}

}
