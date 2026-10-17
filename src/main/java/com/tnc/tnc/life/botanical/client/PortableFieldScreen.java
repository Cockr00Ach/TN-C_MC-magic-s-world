package com.tnc.tnc.life.botanical.client;
import com.tnc.tnc.life.botanical.PortableFieldMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class PortableFieldScreen extends com.tnc.tnc.client.ui.ArcaneContainerScreen<PortableFieldMenu> {
    public PortableFieldScreen(PortableFieldMenu m,Inventory i,Component title){super(m,i,title);imageWidth=176;imageHeight=166;inventoryLabelY=73;}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){com.tnc.tnc.client.ui.ArcaneGui.frame(g,leftPos-3,topPos-3,182,172);g.fill(leftPos+5,topPos+17,leftPos+171,topPos+72,menu.archive?0xff20252b:0xff20252b);for(var s:menu.slots){com.tnc.tnc.client.ui.ArcaneGui.slot(g,leftPos+s.x,topPos+s.y);}}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,menu.archive?"署名调查档案 · 十二份":"防雨种匣 · 每格16粒",8,6,0xd6c7ae,false);g.drawString(font,playerInventoryTitle,8,73,0xd6c7ae,false);}

}
