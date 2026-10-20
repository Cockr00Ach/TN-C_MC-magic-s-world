package com.tnc.tnc.alchemy.client;
import com.tnc.tnc.alchemy.*;
import com.tnc.tnc.client.ui.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
public final class AlchemyScreen extends ArcaneContainerScreen<AlchemyMenu> {
    private int selected;
    public AlchemyScreen(AlchemyMenu m,Inventory inv,Component t){super(m,inv,t);imageWidth=360;imageHeight=286;}
    @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("火魔法点燃 · 5M"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+92,topPos+164,135,20).build());addRenderableWidget(Button.builder(Component.literal("‹ 上一剂"),b->selected=Math.floorMod(selected-1,Elixir.values().length)).bounds(leftPos+242,topPos+247,48,20).build());addRenderableWidget(Button.builder(Component.literal("下一剂 ›"),b->selected=(selected+1)%Elixir.values().length).bounds(leftPos+297,topPos+247,48,20).build());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){ArcaneGui.proportionalFrame(g,leftPos,topPos,imageWidth,imageHeight);g.pose().pushPose();g.pose().translate(leftPos,topPos,0);
        g.fill(235,38,346,240,0x70101820);g.drawString(font,"魔焰炼药釜",23,23,ArcaneGui.TEXT,false);g.drawString(font,"药材",28,56,ArcaneGui.DIM,false);g.drawString(font,"空瓶",196,56,ArcaneGui.DIM,false);g.drawString(font,"成品",196,137,ArcaneGui.DIM,false);g.drawString(font,"焰蕊盐",139,148,ArcaneGui.DIM,false);
        for(int i=0;i<7;i++){var s=menu.slots.get(i);ArcaneGui.slot(g,s.x,s.y);}for(int i=7;i<menu.slots.size();i++){var s=menu.slots.get(i);ArcaneGui.slot(g,s.x,s.y);}
        double time=minecraft.level==null?0:minecraft.level.getGameTime()+partial;ArcaneGui.sigil(g,139,101,33,time,.7f,true);var active=menu.recipe();int color=active==null?0x72d6c9:active.color;int ticks=active==null?1:active.ticks*(menu.value(2)==2?1:3)/ (menu.value(2)==2?1:2);g.fill(97,138,179,141,0xff263336);g.fill(97,138,97+Math.min(82,82*menu.value(0)/Math.max(1,ticks)),141,0xff000000|color);
        String status=active==null?"按药典放入三种材料":menu.value(2)==0?"缺少火种 · 点燃后开工":menu.value(1)==0?"需要焰蕊盐和空瓶":active.name+" · 炼制中";g.drawString(font,font.plainSubstrByWidth(status,208),24,39,ArcaneGui.CYAN,false);
        g.drawString(font,"背包",23,182,ArcaneGui.DIM,false);var e=Elixir.values()[selected];g.drawString(font,"药典 "+(selected+1)+" / 16",244,48,ArcaneGui.GOLD,false);g.renderItem(new ItemStack(AlchemyContent.POTIONS.get(e)),285,68);g.drawCenteredString(font,Component.literal(e.name),291,93,ArcaneGui.TEXT);
        int y=112;for(var line:font.split(Component.literal(e.help),96)){g.drawString(font,line,244,y,ArcaneGui.CYAN,false);y+=11;}y=151;for(String id:e.ingredients){var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(id));if(item!=null){g.renderItem(new ItemStack(item),244,y);g.drawString(font,font.plainSubstrByWidth(item.getDescription().getString(),75),264,y+4,ArcaneGui.DIM,false);}y+=25;}
        g.pose().popPose();}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
}
