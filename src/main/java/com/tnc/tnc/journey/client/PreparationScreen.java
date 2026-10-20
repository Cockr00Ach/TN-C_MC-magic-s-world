package com.tnc.tnc.journey.client;
import com.tnc.tnc.journey.PreparationMenu;
import com.tnc.tnc.client.ui.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class PreparationScreen extends ArcaneContainerScreen<PreparationMenu>{
    public PreparationScreen(PreparationMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=340;imageHeight=288;}
    @Override protected void init(){super.init();button("收纳主背包",22,128,82,0);button("收回暂存",112,128,82,1);button("确认备战",211,251,106,2);}
    private void button(String label,int x,int y,int w,int id){addRenderableWidget(Button.builder(Component.literal(label),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,w,20).build());}
    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){ArcaneGui.proportionalFrame(g,leftPos,topPos,imageWidth,imageHeight);g.pose().pushPose();g.pose().translate(leftPos,topPos,0);g.drawString(font,"远征备战 · 第"+menu.floor+"—"+(menu.floor+4)+"层",22,24,ArcaneGui.TEXT,false);g.drawString(font,"私人暂存 · 离开或重登仍保留",22,44,ArcaneGui.DIM,false);g.drawString(font,"魔药携行 · 按H饮用",211,48,ArcaneGui.CYAN,false);g.drawString(font,"帽子",213,104,ArcaneGui.DIM,false);g.drawString(font,"衣甲",248,104,ArcaneGui.DIM,false);
        for(var s:menu.slots)ArcaneGui.slot(g,s.x,s.y);g.drawString(font,"原背包",22,180,ArcaneGui.GOLD,false);var lines=font.split(Component.literal("收纳只搬主背包，保留快捷栏和穿戴；把要用的物品放回背包或携行栏。"),110);for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),211,152+i*12,ArcaneGui.DIM,false);g.drawString(font,"全员确认后，由队长选择该段启程。",22,159,ArcaneGui.CYAN,false);g.pose().popPose();}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
}
