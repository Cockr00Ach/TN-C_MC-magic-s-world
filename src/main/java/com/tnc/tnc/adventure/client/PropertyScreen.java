package com.tnc.tnc.adventure.client;
import com.tnc.tnc.adventure.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Milo's readable property catalogue, separate from money transfers. */
public final class PropertyScreen extends Screen {
    private CompoundTag data;private int left,top,w,h,group,page,ticks,scroll,limit;private String chosen="",notice="",confirmation="";private AdventurePackets.Action action;
    public PropertyScreen(CompoundTag data){super(Component.literal("RouchNao · 房源与土地"));this.data=data;}
    public void update(CompoundTag tag){data=tag;if(!tag.getString("Message").isEmpty()){notice=tag.getString("Message");confirmation="";}rebuildWidgets();}
    private List<CompoundTag> plots(){return data.getCompound("Housing").getList("Catalog",10).stream().map(t->(CompoundTag)t).filter(t->group==0?t.getString("Kind").equals("HOME"):!t.getString("Kind").equals("HOME")).toList();}
    private CompoundTag selected(){return data.getCompound("Housing").getList("Catalog",10).stream().map(t->(CompoundTag)t).filter(t->t.getString("Id").equals(chosen)).findFirst().orElse(new CompoundTag());}
    private void button(String text,int x,int y,int bw,Runnable r,boolean active){var b=Button.builder(Component.literal(text),v->r.run()).bounds(x,y,bw,20).build();b.active=active;addRenderableWidget(b);}
    private void send(AdventurePackets.Action a,String id){AdventurePackets.send(a,id);}
    private void prepare(AdventurePackets.Action a,String terms){action=a;confirmation=terms;scroll=0;rebuildWidgets();}
    private static String installmentAmount(long total){long base=total/10;return total%10==0?base+"":base+"或"+(base+1);}
    @Override protected void init(){w=Math.min(440,width-16);h=Math.min(330,height-16);left=(width-w)/2;top=(height-h)/2;boolean bank=data.getBoolean("AtBroker");
        button(data.getBoolean("AtShop")?"返回商行":"返回银行",left+w-81,top+8,72,()->Minecraft.getInstance().setScreen(data.getBoolean("AtShop")?new ShopScreen(data):new BankScreen(data)),true);
        if(chosen.isEmpty()){
            button("住宅（5套）",left+16,top+40,(w-36)/2,()->{group=0;page=0;rebuildWidgets();},group!=0);button("农田与牧场",left+20+(w-36)/2,top+40,(w-36)/2,()->{group=1;page=0;rebuildWidgets();},group!=1);
            var list=plots();int rows=Math.max(1,(h-112)/43);page=Math.min(page,Math.max(0,(list.size()-1)/rows));
            for(int i=page*rows;i<Math.min(list.size(),(page+1)*rows);i++){var p=list.get(i);int y=top+70+(i-page*rows)*43;button("详情",left+w-61,y+4,44,()->{chosen=p.getString("Id");confirmation="";scroll=0;rebuildWidgets();},true);}
            button("上一页",left+16,top+h-47,65,()->{page--;rebuildWidgets();},page>0);button("下一页",left+86,top+h-47,65,()->{page++;rebuildWidgets();},(page+1)*rows<list.size());
        }else{
            var p=selected();boolean home=p.getString("Kind").equals("HOME"),sold=p.hasUUID("Owner");var profile=AdventureSavedData.readProfile(data);
            if(!confirmation.isEmpty()){button("确认办理",left+16,top+h-48,(w-36)/2,()->send(action,chosen),bank);button("返回详情",left+20+(w-36)/2,top+h-48,(w-36)/2,()->{confirmation="";scroll=0;rebuildWidgets();},true);return;}
            int bw=(w-40)/3;button("去看"+(home?"房":"地"),left+16,top+h-48,bw,()->{send(AdventurePackets.Action.PROPERTY_LOOK,chosen);Minecraft.getInstance().setScreen(null);},bank||data.getBoolean("AtShop"));
            boolean viewed=!home||p.getBoolean("Viewed");boolean otherOwned=data.getCompound("Housing").getList("Catalog",10).stream().map(t->(CompoundTag)t).anyMatch(a->a.getBoolean("Mine")&&a.getString("Kind").equals(p.getString("Kind")));
            button("全款购买",left+20+bw,top+h-48,bw,()->prepare(AdventurePackets.Action.BUY_HOME,"购买"+p.getString("Number")+"号"+p.getString("Name")+"，支付"+p.getLong("Price")+"铜。"+(home?"空屋交付，自己的边界内可挖可放；不开放公共道路。":"原状土地交付，可以自己整地和布置。")),bank&&!sold&&!otherOwned&&viewed&&profile.coins()+profile.bank.savings>=p.getLong("Price"));
            button(home?"分期购买":"返回目录",left+24+bw*2,top+h-48,bw,()->{if(!home){chosen="";rebuildWidgets();}else prepare(AdventurePackets.Action.BUY_HOME_INSTALLMENT,"首付"+p.getLong("Down")+"铜，剩余本金加一次10%费用，之后每游玩40分钟还"+installmentAmount(p.getLong("MortgageTotal"))+"铜（整数均摊），共10次，合计"+p.getLong("Total")+"铜。离线不催款，不足不拆房。钱袋优先，存款补足；可提前结清免未来费用。");},!home||bank&&!sold&&!otherOwned&&viewed&&profile.bank.canMortgage(profile)&&profile.coins()+profile.bank.savings>=p.getLong("Down"));
            button("目录",left+16,top+40,48,()->{chosen="";confirmation="";rebuildWidgets();},true);
        }
    }
    private void body(GuiGraphics g,String text,int y){var lines=font.split(Component.literal(text),w-32);int end=top+h-66;limit=Math.max(0,lines.size()*12-(end-y));scroll=Math.min(scroll,limit);g.enableScissor(left+16,y,left+w-16,end);for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),left+16,y+i*12-scroll,0xFF544737,false);g.disableScissor();if(limit>0)g.drawString(font,"滚轮查看更多",left+w-92,top+h-61,0xFF246E78,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.fill(left-2,top-2,left+w+2,top+h+2,0xFFA48254);g.fill(left,top,left+w,top+h,0xFFF1E7D5);g.fill(left,top,left+w,top+32,0xFF182C42);g.drawString(font,title,left+14,top+12,0xFFF5E6CB,false);
        if(chosen.isEmpty()){var list=plots();int rows=Math.max(1,(h-112)/43);for(int i=page*rows;i<Math.min(list.size(),(page+1)*rows);i++){var p=list.get(i);int y=top+70+(i-page*rows)*43;g.drawString(font,p.getString("Number")+" · "+p.getString("Name"),left+16,y,0xFF182C42,false);g.drawString(font,p.getLong("Price")+"铜 · "+(p.getBoolean("Mine")?"你的产权":p.hasUUID("Owner")?"已售出":"待售"),left+16,y+14,0xFF246E78,false);}}
        else{var p=selected();if(!confirmation.isEmpty())body(g,confirmation,top+44);else{g.drawString(font,p.getString("Number")+" · "+p.getString("Name"),left+74,top+45,0xFF182C42,false);String text=p.getString("Detail")+"\n\n位置："+p.getString("Entry")+"\n全款："+p.getLong("Price")+"铜。"+(p.getString("Kind").equals("HOME")?"分期首付"+p.getLong("Down")+"铜，10期共还"+p.getLong("MortgageTotal")+"铜，总支出"+p.getLong("Total")+"铜。\n"+(p.getBoolean("Viewed")?"已完成实地看房。":"先去实地看房，再回来决定。")+"\n可购买条件：全款有足够余额；分期需协会登记、Lv10、无欠付账单。":"一次购买，无租金；同类土地每人一块。");if(p.hasUUID("Owner"))text+="\n"+(p.getBoolean("Mine")?"这是你的产权。":"这处已售出，请挑另一处。");body(g,text,top+69);}}
        if(!notice.isEmpty()){g.drawString(font,font.plainSubstrByWidth(notice,w-32),left+16,top+h-17,0xFF7B452B,false);if(my>top+h-24)g.renderTooltip(font,font.split(Component.literal(notice),Math.min(300,width-24)),mx,my);}super.render(g,mx,my,dt);
    }
    @Override public boolean mouseScrolled(double x,double y,double d){if(!chosen.isEmpty()&&limit>0){scroll=Math.max(0,Math.min(limit,scroll-(int)(d*18)));return true;}return super.mouseScrolled(x,y,d);}
    @Override public void tick(){if(++ticks%20==0)send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean isPauseScreen(){return false;}
}
