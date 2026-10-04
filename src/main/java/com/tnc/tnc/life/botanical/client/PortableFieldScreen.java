package com.tnc.tnc.life.botanical.client;
import com.tnc.tnc.life.botanical.PortableFieldMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class PortableFieldScreen extends AbstractContainerScreen<PortableFieldMenu> {
    public PortableFieldScreen(PortableFieldMenu m,Inventory i,Component title){super(m,i,title);imageWidth=176;imageHeight=166;inventoryLabelY=73;}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos-2,topPos-2,leftPos+178,topPos+168,0xff514538);g.fill(leftPos,topPos,leftPos+176,topPos+166,0xffe4dac3);g.fill(leftPos+5,topPos+17,leftPos+171,topPos+72,menu.archive?0xffc8c5b5:0xffbac3a5);for(var s:menu.slots){g.fill(leftPos+s.x-1,topPos+s.y-1,leftPos+s.x+17,topPos+s.y+17,0xff7a7464);g.fill(leftPos+s.x,topPos+s.y,leftPos+s.x+16,topPos+s.y+16,0xffbdb7a5);}}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,menu.archive?"署名调查档案 · 十二份":"防雨种匣 · 每格16粒",8,6,0x413d31,false);g.drawString(font,playerInventoryTitle,8,73,0x413d31,false);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
