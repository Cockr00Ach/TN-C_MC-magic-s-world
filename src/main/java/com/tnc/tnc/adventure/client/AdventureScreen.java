package com.tnc.tnc.adventure.client;

import com.tnc.tnc.adventure.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Transaction service; the handbook and tavern open this, FTB remains the questbook. */
public final class AdventureScreen extends Screen {
    private CompoundTag data;
    private int tab,page,left,top,panelWidth,panelHeight,ticks;
    private String notice="";
    private List<ContractCatalog.Contract> visible=List.of();
    public AdventureScreen(CompoundTag tag){super(Component.literal("冒险者协会"));data=tag;}
    public static void accept(CompoundTag tag,boolean open) {
        if(tag==null)return;var mc=Minecraft.getInstance();
        if(open)mc.setScreen(new AdventureScreen(tag));
        else if(mc.screen instanceof AdventureScreen screen) {
            screen.data=tag;if(!tag.getString("Message").isEmpty())screen.notice=tag.getString("Message");screen.rebuildWidgets();
        }
    }
    private AdventureProfile profile(){return AdventureSavedData.readProfile(data);}
    private void button(String title,int x,int y,int width,Runnable action,boolean active){
        var b=Button.builder(Component.literal(title),ignored->action.run()).bounds(x,y,width,20).build();b.active=active;addRenderableWidget(b);
    }
    private void send(AdventurePackets.Action action,String id){AdventurePackets.send(action,id);}
    @Override protected void init() {
        panelWidth=Math.min(500,width-20);panelHeight=Math.min(330,height-20);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int tabWidth=(panelWidth-28)/4;
        for(int i=0;i<4;i++){final int t=i;button(List.of("冒险档案","酒馆委托","驻馆匠人","住宅与邻里").get(i),left+14+i*tabWidth,top+44,tabWidth-5,()->{tab=t;page=0;rebuildWidgets();},tab!=i);}
        boolean local=data.getBoolean("AtService");var p=profile();
        if(tab==0) {
            button(p.registered()?"已登记":"登记冒险者",left+20,top+panelHeight-66,100,()->send(AdventurePackets.Action.REGISTER,""),local&&!p.registered());
            button("申请精锐晋升",left+126,top+panelHeight-66,110,()->send(AdventurePackets.Action.PROMOTE,""),local&&p.registered());
        } else if(tab==1) {
            var active=active();
            visible=ContractCatalog.ALL.stream().filter(c->!c.teaching()||!p.hasMilestone("teaching_paid")||active.contains(c.id()))
                    .sorted(Comparator.comparingInt(c->active.contains(c.id())?0:1)).toList();
            int rows=rows();page=Math.max(0,Math.min(page,(visible.size()-1)/rows));
            for(int i=page*rows;i<Math.min(visible.size(),(page+1)*rows);i++) {
                var c=visible.get(i);boolean accepted=active.contains(c.id());
                button(accepted?"提交结算":"接取",left+panelWidth-83,top+80+(i-page*rows)*36,65,()->send(accepted?AdventurePackets.Action.DELIVER:AdventurePackets.Action.ACCEPT,c.id()),local);
            }
            button("上一页",left+18,top+panelHeight-51,65,()->{page--;rebuildWidgets();},page>0);
            button("下一页",left+90,top+panelHeight-51,65,()->{page++;rebuildWidgets();},(page+1)*rows<visible.size());
        } else if(tab==2) {
            button("交材料下单",left+20,top+panelHeight-66,110,()->send(AdventurePackets.Action.ORDER,""),local&&data.getLong("SmithReady")<0);
            button("领取完成订单",left+138,top+panelHeight-66,110,()->send(AdventurePackets.Action.CLAIM,""),local&&data.getLong("SmithReady")>=0);
        } else {
            var house=data.getCompound("Housing");
            button("确认购买 · 5银",left+20,top+panelHeight-66,140,()->send(AdventurePackets.Action.BUY_HOME,""),(local||data.getBoolean("AtHomeSale"))&&!house.hasUUID("Owner")&&p.coins()>=500);
        }
        button("关闭",left+panelWidth-64,top+12,48,this::onClose,true);
    }
    private Set<String> active(){var ids=new HashSet<String>();for(var c:data.getList("Contracts",10))ids.add(((CompoundTag)c).getString("ID"));return ids;}
    private int rows(){return Math.max(1,(panelHeight-138)/36);}
    private String materialName(ContractCatalog.Material material){
        if(material.tag())return material.id().equals("minecraft:logs")?"任意原木":"匹配材料";
        var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(material.id()));
        return item==null?"所需材料":item.getDescription().getString();
    }
    private String enemyName(String id){
        var entity=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse(id));
        return entity==null?"目标敌人":entity.getDescription().getString();
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
    private int paragraph(GuiGraphics g,String s,int y,int color){for(var line:font.split(Component.literal(s),panelWidth-40)){g.drawString(font,line,left+20,y,color,false);y+=12;}return y;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);g.fill(left-2,top-2,left+panelWidth+2,top+panelHeight+2,0xFFA48257);g.fill(left,top,left+panelWidth,top+panelHeight,0xFFF1E7D5);
        g.fill(left,top,left+panelWidth,top+38,0xFF182C42);text(g,"TN-C  /  冒险者协会",left+16,top+14,0xF7E8CF);
        var p=profile();boolean local=data.getBoolean("AtService");
        if(tab==0) {
            int y=top+82;int lv=p.level();long next=lv>=100?0:AdventureRules.levelCost(lv),earned=p.xp()-AdventureRules.xpAtLevel(lv);
            text(g,"Lv."+lv+"   "+(data.getInt("Rank")==0?"初级":"精锐")+"冒险者",left+20,y,0x182C42);y+=20;
            text(g,lv>=100?"冒险成长已满级":"本级经验 "+earned+" / "+next+"   ·   声望 "+p.reputation(),left+20,y,0x544737);y+=18;
            int bar=panelWidth-40;g.fill(left+20,y,left+20+bar,y+5,0xFFD6CBB9);g.fill(left+20,y,left+20+(int)(bar*(lv>=100?1:earned/(double)next)),y+5,0xFF408B92);y+=17;
            text(g,"余额  "+AdventureRules.money(p.coins()),left+20,y,0x544737);y+=20;
            y=paragraph(g,local?"驻馆服务已连接。登记后接教学备料单；4木棍+2铜锭可请匠人打造第一根法杖。":"远行档案：可查看成长与委托。办理业务请返回酒馆，潜行右键Self；或靠近天空岛酒馆入口。",y,0x635A4A);
            if(!local&&!data.getString("Tavern").isEmpty())y=paragraph(g,"天空岛酒馆入口："+data.getString("Tavern"),y+8,0x635A4A);
            y+=10;for(var entry:data.getList("Ledger",8)){if(y>top+panelHeight-88)break;text(g,entry.getAsString(),left+20,y,0x635A4A);y+=12;}
        } else if(tab==1) {
            int rows=rows();var active=active();
            for(int i=page*rows;i<Math.min(visible.size(),(page+1)*rows);i++) {
                var c=visible.get(i);int y=top+81+(i-page*rows)*36;
                text(g,c.title(),left+20,y,0x182C42);
                int required=c.kills()>0?c.kills():c.materials().stream().mapToInt(ContractCatalog.Material::count).sum();
                String line=active.contains(c.id())?"进行中 "+data.getCompound("Counts").getInt(c.id())+" / "+required:"经验 "+c.xp()+" · 声望 "+c.reputation()+" · "+c.coins()+"铜";
                text(g,line,left+20,y+13,0x635A4A);
                if(mx>=left+16&&mx<left+panelWidth-88&&my>=y&&my<y+32){
                    String detail=c.kills()>0?"接单后击杀 "+enemyName(c.enemy())+" × "+c.kills()+"。完成后回馆结算。":c.materials().stream().map(m->materialName(m)+" × "+m.count()).reduce((a,b)->a+"，"+b).orElse("")+"。提交消耗材料，返还碗/瓶。";
                    g.renderTooltip(font,font.split(Component.literal(detail),Math.min(260,width-20)),mx,my);
                }
            }
            text(g,"最多3单 / 1单越阶 · 刷新 "+Math.max(1,(AdventureRules.BOARD_PERIOD-data.getLong("ActiveTicks")%AdventureRules.BOARD_PERIOD)/1200)+"分钟",left+170,top+panelHeight-46,0x635A4A);
        } else if(tab==2) {
            int y=paragraph(g,"驻馆匠人 · 初版法杖订单",top+84,0x182C42);
            y=paragraph(g,"4木棍 + 2铜锭。首次免人工费，补造30铜。",y+8,0x635A4A);
            y=paragraph(g,"制作20秒；订单持久保存，满背包可稍后领取。",y+8,0x635A4A);
            long ready=data.getLong("SmithReady");String state=ready<0?"当前没有订单":ready<=data.getLong("ActiveTicks")?"制作完成，可以领取":"匠人正在制作，剩余 "+(ready-data.getLong("ActiveTicks")+19)/20+" 秒";
            paragraph(g,state,Math.min(y+8,top+panelHeight-82),0x246E78);
        } else {
            var house=data.getCompound("Housing");int y=paragraph(g,"南街三层空屋 · 5银（500铜）",top+82,0x182C42);
            y=paragraph(g,house.getBoolean("Preparing")?house.getString("Status"):house.getBoolean("Mine")?"你的住宅：装修与物品长期保留。":house.hasUUID("Owner")?"本存档房源已售出，其他玩家无需付款。":"先看房，在空屋入口或酒馆确认购买。保留建筑结构，移除家具与储存用品。",y+8,0x635A4A);
            y=paragraph(g,"入口："+house.getString("Entry"),y+8,0x246E78);
            y=paragraph(g,"装修边界："+house.getString("Boundary"),y+8,0x635A4A);
            paragraph(g,house.getBoolean("Mine")?"装修授权：/adventure trust 玩家；撤销：/adventure untrust 玩家。右键成年邻居交流，准备两张床与安全空间邀请共同生活。":"东街与南街有六位成年邻居。右键交流，手持料理赠食；两张床和安全空间是共同生活的前提。",y+8,0x635A4A);
        }
        if(!notice.isEmpty())paragraph(g,notice,top+panelHeight-23,0x7B452B);
        super.render(g,mx,my,partial);
    }
    @Override public void tick(){if(++ticks%20==0)send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean isPauseScreen(){return false;}
}
