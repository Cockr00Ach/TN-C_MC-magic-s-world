package com.tnc.tnc.production.client;
import com.tnc.tnc.production.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** Work area, recipe list and status each have their own fixed rectangle. */
public final class MagicForgeScreen extends AbstractContainerScreen<MagicForgeMenu>{
    private static final int RX=146,RY=58,RW=146,RH=20,INK=0xff302b29;
    private static final String[] FILTERS={"all","materials","utility","workshop"},NAMES={"全部","材料","用品","工坊"};
    private int filter,scroll;
    public MagicForgeScreen(MagicForgeMenu m,Inventory i,Component t){super(m,i,t);imageWidth=300;imageHeight=240;inventoryLabelY=149;}
    @Override protected void init(){super.init();
        addRenderableWidget(Button.builder(Component.literal("搭建说明"),b->ForgeGuideScreen.open(this)).bounds(leftPos+240,topPos+4,52,16).build());
        addRenderableWidget(Button.builder(Component.literal("注魔 +25"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+17,topPos+132,112,16).build());
        for(int i=0;i<4;i++){final int chosen=i;addRenderableWidget(Button.builder(Component.literal(NAMES[i]),b->{filter=chosen;scroll=0;}).bounds(leftPos+RX+i*37,topPos+40,35,16).build());}
    }
    private List<Integer> visible(){List<Integer> r=new ArrayList<>();var all=menu.recipes();for(int i=0;i<all.size();i++)if(filter==0||all.get(i).category().equals(FILTERS[filter]))r.add(i);scroll=Math.max(0,Math.min(scroll,Math.max(0,r.size()-3)));return r;}
    private String issue(){if(menu.formed())return "结构完整 · 可以锻造";int i=Math.max(0,Math.min(26,menu.firstBad()));return switch(menu.cell(i)){
        case ForgeStructure.UNLOADED->"区块未加载 · 炉台暂停";case ForgeStructure.WRONG_DIRECTION->"炉口需要朝外";case ForgeStructure.SHARED_PART->"两座炉不能共用部件";
        default->ForgeStructure.cellName(i)+" 缺 "+ForgeStructure.expectedName(ForgeStructure.expected(i));};}
    private void fill(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(leftPos+x,topPos+y,leftPos+x+w,topPos+y+h,c);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        fill(g,-2,-2,304,244,INK);fill(g,0,0,300,240,0xffe4d9c4);fill(g,0,0,300,24,0xff34393d);
        fill(g,8,26,284,12,0xfff1e9d9);fill(g,8,40,129,90,0xff57504a);fill(g,140,40,1,116,0xffbbab92);
        for(int i=0;i<menu.slots.size();i++){var s=menu.slots.get(i);fill(g,s.x-1,s.y-1,18,18,0xff756a5a);fill(g,s.x,s.y,16,16,i<6?0xffbdaa87:0xffc7bda8);}
        fill(g,73,71,28,5,0xff2d2c2a);fill(g,74,72,26*Math.min(menu.progress(),menu.workTicks())/menu.workTicks(),3,0xffb77f48);
        fill(g,15,124,114,4,INK);fill(g,16,125,112*menu.charge()/200,2,0xff68c6a5);
        var list=visible();for(int row=0;row<3&&scroll+row<list.size();row++)fill(g,RX,RY+row*RH,RW,RH-1,list.get(scroll+row)==menu.selectedIndex()?0xffceb184:0xfff1e8d7);
        for(int l=0;l<3;l++)for(int r=0;r<3;r++)for(int c=0;c<3;c++){int code=menu.cell(ForgeStructure.index(l,r,c));fill(g,156+l*47+c*6,137+r*6,5,5,code==0?0xff5baf84:code==2?0xff8f98a5:0xffce755f);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,"炼金炉",10,8,0xfff7e7c9,false);g.drawString(font,font.plainSubstrByWidth(issue(),275),12,28,menu.formed()?0xff377d5a:0xff985340,false);
        g.drawString(font,"投料",19,46,0xfff4e7d1,false);g.drawString(font,"成品",108,57,0xfff4e7d1,false);g.drawString(font,"升级",108,93,0xfff4e7d1,false);
        g.drawString(font,"魔力 "+menu.charge()+"/200",16,112,0xffa8efd6,false);
        var list=visible();for(int row=0;row<3&&scroll+row<list.size();row++){var out=menu.recipes().get(list.get(scroll+row)).getResultItem(minecraft.level.registryAccess());g.renderItem(out,RX+2,RY+row*RH+1);g.drawString(font,font.plainSubstrByWidth(out.getHoverName().getString(),120),RX+21,RY+row*RH+6,INK,false);}
        var selected=menu.selectedIndex()>=0&&menu.selectedIndex()<menu.recipes().size()?menu.recipes().get(menu.selectedIndex()):null;
        g.drawString(font,selected==null?"选择工艺后显示所需材料":"本次需要 "+selected.mana()+" 魔力",RX,119,0xff4a6156,false);
        String[] labels={"底层","中层","顶层"};for(int i=0;i<3;i++)g.drawString(font,labels[i],153+i*47,128,0xff675540,false);
        if(selected!=null)for(int i=0;i<selected.inputs().size();i++){if(menu.slots.get(i).hasItem())continue;var sample=selected.inputs().get(i).ingredient().getItems();if(sample.length==0)continue;var s=menu.slots.get(i);g.renderItem(sample[0],s.x,s.y);g.fill(s.x,s.y,s.x+16,s.y+16,0x770c1013);g.drawString(font,String.valueOf(selected.inputs().get(i).count()),s.x+10,s.y+8,0xffffffff,true);}
        g.drawString(font,"背包",79,inventoryLabelY,INK,false);
    }
    private int row(double mx,double my){int x=(int)mx-leftPos,y=(int)my-topPos;return x>=RX&&x<RX+RW&&y>=RY&&y<RY+3*RH?(y-RY)/RH:-1;}
    @Override public boolean mouseClicked(double mx,double my,int b){int row=row(mx,my);var list=visible();if(b==0&&row>=0&&scroll+row<list.size()){ForgeRecipeNetwork.select(menu.containerId,menu.recipes().get(list.get(scroll+row)).getId());return true;}return super.mouseClicked(mx,my,b);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(row(mx,my)>=0){scroll=Math.max(0,Math.min(Math.max(0,visible().size()-3),scroll-(int)Math.signum(amount)));return true;}return super.mouseScrolled(mx,my,amount);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);int r=row(mx,my);var list=visible();
        if(r>=0&&scroll+r<list.size())g.renderTooltip(font,menu.recipes().get(list.get(scroll+r)).getResultItem(minecraft.level.registryAccess()),mx,my);
        else if(my>=topPos+26&&my<topPos+38)g.renderTooltip(font,Component.literal(issue()),mx,my);
        else if(mx>=leftPos+107&&mx<leftPos+127&&my>=topPos+103&&my<topPos+123)g.renderTooltip(font,Component.literal("预热油10次 / 冷凝壳20次 · 加速20% · 剩余"+menu.upgradeUses()+"次"),mx,my);
        else if(mx>=leftPos+156&&mx<leftPos+268&&my>=topPos+137&&my<topPos+155){int l=(mx-leftPos-156)/47,c=((mx-leftPos-156)%47)/6,y=(my-topPos-137)/6;if(l<3&&c<3){int i=ForgeStructure.index(l,y,c);g.renderTooltip(font,Component.literal(ForgeStructure.cellName(i)+" · "+ForgeStructure.expectedName(ForgeStructure.expected(i))),mx,my);}}
        else renderTooltip(g,mx,my);
    }
}
