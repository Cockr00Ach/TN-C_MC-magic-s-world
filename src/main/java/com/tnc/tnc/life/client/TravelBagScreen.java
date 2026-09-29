package com.tnc.tnc.life.client;
import com.tnc.tnc.life.TravelBagMenu;
import com.tnc.tnc.adventure.AdventureRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TravelBagScreen extends AbstractContainerScreen<TravelBagMenu> {
    public TravelBagScreen(TravelBagMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=198;imageHeight=205;inventoryLabelY=110;}
    @Override protected void init(){super.init();
        for(int i=0;i<5;i++){final int food=i;addRenderableWidget(Button.builder(Component.literal("吃"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,food)).bounds(leftPos+65+i*22,topPos+83,20,17).build());}
        String[] names={"取铜","取银","取金","存入"};for(int i=0;i<4;i++){final int action=i+10;addRenderableWidget(Button.builder(Component.literal(names[i]),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+65+i*31,topPos+29,30,20).build());}
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFFA48257);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFFF1E7D5);
        for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF998A76);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFFD6CBB9);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,"行囊 · "+AdventureRules.money(menu.balance()),8,8,0x182C42,false);g.drawString(font,"钱袋",24,19,0x635A4A,false);g.drawString(font,"食物袋 / 五格旅途补给",24,51,0x635A4A,false);g.drawString(font,playerInventoryTitle,8,110,0x635A4A,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
