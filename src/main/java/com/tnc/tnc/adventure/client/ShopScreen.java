package com.tnc.tnc.adventure.client;
import com.tnc.tnc.adventure.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import java.util.List;

public final class ShopScreen extends Screen {
    private CompoundTag data;private int left,top,w,h,page,tab,ticks;private String notice="",selected="";
    public ShopScreen(CompoundTag data){super(Component.literal("朝夕商行 · 织夏"));this.data=data;}
    public void update(CompoundTag tag){data=tag;if(!tag.getString("Message").isEmpty()){notice=tag.getString("Message");selected="";}rebuildWidgets();}
    private List<ShopCatalog.Goods> list(){return tab==0?ShopCatalog.FURNITURE:ShopCatalog.PRODUCE;}
    private int rows(){return Math.max(1,(h-142)/33);}
    private void button(String text,int x,int y,int width,Runnable run,boolean active){var b=Button.builder(Component.literal(text),v->run.run()).bounds(x,y,width,20).build();b.active=active;addRenderableWidget(b);}
    @Override protected void init(){
        w=Math.min(480,width-16);h=Math.min(338,height-16);left=(width-w)/2;top=(height-h)/2;boolean near=data.getBoolean("AtShop");
        button("家具与家饰",left+16,top+45,112,()->{tab=0;page=0;selected="";rebuildWidgets();},tab!=0);button("出售农畜产品",left+134,top+45,126,()->{tab=1;page=0;selected="";rebuildWidgets();},tab!=1);
        if(w>=360)button("问问房源",left+w-100,top+45,84,()->net.minecraft.client.Minecraft.getInstance().setScreen(new PropertyScreen(data)),near);
        page=Math.max(0,Math.min(page,(list().size()-1)/rows()));
        for(int i=page*rows();i<Math.min(list().size(),(page+1)*rows());i++){var g=list().get(i);boolean available=tab!=0||data.getCompound("Shop").getList("Available",8).stream().anyMatch(t->t.getAsString().equals(g.id()));int y=top+78+(i-page*rows())*33;button(selected.equals(g.id())?"确认"+(tab==0?"购买":"出售"):tab==0?"购买":"出售",left+w-86,y,68,()->{if(!selected.equals(g.id())){selected=g.id();rebuildWidgets();}else AdventurePackets.send(tab==0?AdventurePackets.Action.SHOP_BUY:AdventurePackets.Action.SHOP_SELL,g.id());},near&&available);}
        button("上一页",left+16,top+h-59,66,()->{page--;selected="";rebuildWidgets();},page>0);button("下一页",left+88,top+h-59,66,()->{page++;selected="";rebuildWidgets();},(page+1)*rows()<list().size());button("离开柜台",left+w-100,top+h-59,84,this::onClose,true);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.fill(left,top,left+w,top+h,0xf51c252b);g.fill(left,top,left+w,top+35,0xff2a3c40);g.drawString(font,title,left+16,top+11,0xffffd997,false);var p=AdventureSavedData.readProfile(data);g.drawString(font,"钱袋 "+p.coins()+"铜 · 存款 "+p.bank.savings+"铜",left+16,top+69,0xffa6d5cb,false);
        for(int i=page*rows();i<Math.min(list().size(),(page+1)*rows());i++){var item=list().get(i);int y=top+80+(i-page*rows())*33;g.drawString(font,item.name()+" · "+item.price()+"铜",left+18,y,0xfff1e5cc,false);g.drawString(font,font.plainSubstrByWidth(item.detail(),w-114),left+18,y+12,0xff9eafb5,false);}
        String tip=tab==0?"购买从钱袋优先扣款，再使用存款。确认后交付背包。":"本轮额度 "+Math.max(0,data.getCompound("Shop").getInt("Quota")-data.getCompound("Shop").getLong("Sold"))+"铜 · "+((data.getCompound("Shop").getLong("Next")+1199)/1200)+"分钟有效在线时间后恢复";
        g.drawString(font,font.plainSubstrByWidth(tip,w-32),left+16,top+h-76,0xffc8b789,false);g.drawString(font,font.plainSubstrByWidth(notice,w-32),left+16,top+h-29,0xffffd997,false);super.render(g,mx,my,partial);}
    @Override public void tick(){if(++ticks%20==0)AdventurePackets.send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean isPauseScreen(){return false;}
}
