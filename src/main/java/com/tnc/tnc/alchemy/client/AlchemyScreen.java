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
    public AlchemyScreen(AlchemyMenu m,Inventory inv,Component t){super(m,inv,t);imageWidth=360;imageHeight=260;}
    @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("‹"),b->selected=Math.floorMod(selected-1,Elixir.values().length)).bounds(leftPos+210,topPos+218,40,20).build());addRenderableWidget(Button.builder(Component.literal("›"),b->selected=(selected+1)%Elixir.values().length).bounds(leftPos+292,topPos+218,40,20).build());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        ArcaneGui.proportionalFrame(g,leftPos,topPos,imageWidth,imageHeight);g.pose().pushPose();g.pose().translate(leftPos,topPos,0);
        g.drawString(font,"魔焰药典",24,21,ArcaneGui.GOLD,false);g.drawString(font,"在锅边炼制 · 书页只供查阅",24,37,ArcaneGui.DIM,false);
        g.fill(193,56,194,235,0x886f644c);
        String[] names={"01  倒入清水","02  投放药材","03  火法点燃","04  观察融合","05  封盖续煮"};
        String[] help={"水桶右键，一桶可炼三剂。","三味药材各一份，右键或丢进锅口。","火球等火系法术命中釜体。燧石与普通火无效。","魔力视下漩涡自动靠拢。锅周翠绿适温，蓝冷红热。","同心星光时盖锅锁定药性。保持绿温再煮六秒，用空瓶盛药。"};
        int y=60;for(int i=0;i<names.length;i++){g.drawString(font,names[i],24,y,ArcaneGui.CYAN,false);int z=y+12;for(var line:font.split(Component.literal(help[i]),155)){g.drawString(font,line,24,z,ArcaneGui.DIM,false);z+=10;}y+=35;}
        var e=Elixir.values()[selected];g.pose().pushPose();g.pose().translate(246,55,0);g.pose().scale(3,3,1);g.renderItem(new ItemStack(AlchemyContent.POTIONS.get(e)),0,0);g.pose().popPose();
        g.drawCenteredString(font,Component.literal(e.name),268,109,ArcaneGui.TEXT);int z=125;
        for(var line:font.split(Component.literal(e.help),124)){g.drawString(font,line,207,z,ArcaneGui.CYAN,false);z+=10;}
        z=151;for(String id:e.ingredients){var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(id));if(item!=null){g.renderItem(new ItemStack(item),209,z);g.drawString(font,font.plainSubstrByWidth(item.getDescription().getString(),100),230,z+4,ArcaneGui.TEXT,false);}z+=20;}
        g.drawCenteredString(font,Component.literal((selected+1)+" / "+Elixir.values().length),271,225,ArcaneGui.DIM);g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
}
