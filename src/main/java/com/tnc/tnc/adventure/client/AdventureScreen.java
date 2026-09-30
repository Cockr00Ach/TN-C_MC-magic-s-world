package com.tnc.tnc.adventure.client;

import com.tnc.tnc.adventure.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Each worker opens only their own counter. The handbook is a separate personal view. */
public final class AdventureScreen extends Screen {
    private CompoundTag data;
    private final ServicePanel panel;
    private final int tab;
    private int page,left,top,panelWidth,panelHeight,ticks,element,armChoice,gearCategory,tier=1,gearTier=1;
    private String notice="";
    private int bodyScroll,scrollLimit,bodyLastY;
    private List<ContractCatalog.Contract> visible=List.of();
    public AdventureScreen(CompoundTag tag){super(Component.literal(ServicePanel.fromRole(tag.getString("ServiceRole")).title()));data=tag;panel=ServicePanel.fromRole(tag.getString("ServiceRole"));tab=panel.tab();}
    private ElementWands.Design design(){return ElementWands.ALL.get(element*5+tier-1);}
    private com.tnc.tnc.equipment.MageGear.Design gear(){
        String style=gearCategory==2?"divine":new String[]{"bastion","astral","runic","wanderer"}[armChoice];
        boolean hat=gearCategory==1||(gearCategory==2&&armChoice==1);
        return com.tnc.tnc.equipment.MageGear.find(style+(hat?"_hat_":"_outfit_")+(gearCategory==2?5:gearTier));
    }
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
        boolean local=data.getBoolean("AtService");var p=profile();
        if(panel==ServicePanel.PROFILE) {
            button("委托旧单记录",left+20,top+panelHeight-66,110,()->{var copy=data.copy();copy.putString("ServiceRole",ServicePanel.ARCHIVE.role());Minecraft.getInstance().setScreen(new AdventureScreen(copy));},true);
        } else if(panel==ServicePanel.GUILD) {
            button(p.registered()?"已登记":"登记冒险者",left+20,top+panelHeight-66,100,()->send(AdventurePackets.Action.REGISTER,""),local&&!p.registered());
            button("申请精锐晋升",left+126,top+panelHeight-66,110,()->send(AdventurePackets.Action.PROMOTE,""),local&&p.registered());
        } else if(tab==1) {
            var active=active();
            local=data.getBoolean("AtBoard");
            button("打开Bountiful原生栏",left+18,top+76,165,()->send(AdventurePackets.Action.OPEN_BOARD,""),local&&p.registered());
            visible=ContractCatalog.ALL.stream().filter(c->active.contains(c.id())||(c.teaching()&&!p.hasMilestone("teaching_paid")))
                    .sorted(Comparator.comparingInt(c->active.contains(c.id())?0:1)).toList();
            int rows=rows();page=Math.max(0,Math.min(page,(visible.size()-1)/rows));
            for(int i=page*rows;i<Math.min(visible.size(),(page+1)*rows);i++) {
                var c=visible.get(i);boolean accepted=active.contains(c.id());
                button(accepted?"旧单交付":"教学接取",left+panelWidth-83,top+135+(i-page*rows)*36,65,()->send(accepted?AdventurePackets.Action.DELIVER:AdventurePackets.Action.ACCEPT,c.id()),local);
            }
            button("上一页",left+18,top+panelHeight-51,65,()->{page--;rebuildWidgets();},page>0);
            button("下一页",left+90,top+panelHeight-51,65,()->{page++;rebuildWidgets();},(page+1)*rows<visible.size());
        } else if(tab==2) {
            int ew=(panelWidth-40)/7;for(int i=0;i<7;i++){final int e=i;button(List.of("水","火","雷","风","土","光","暗").get(i),left+20+i*ew,top+78,ew-3,()->{element=e;rebuildWidgets();},element!=i);}
            int tw=(panelWidth-40)/5;for(int i=1;i<=5;i++){final int t=i;button(i+"阶",left+20+(i-1)*tw,top+102,tw-3,()->{tier=t;rebuildWidgets();},tier!=i);}
            local=data.getBoolean("AtSmith");
            button("交材料下单",left+20,top+panelHeight-66,110,()->send(AdventurePackets.Action.ORDER,design().id()),local&&data.getLong("SmithReady")<0);
            button("领取完成订单",left+138,top+panelHeight-66,110,()->send(AdventurePackets.Action.CLAIM,""),local&&data.getLong("SmithReady")>=0);
        } else if(tab==3) {
            var house=data.getCompound("Housing");
            boolean bank=data.getBoolean("AtBroker");int by=top+panelHeight-92;
            button("存入实体币",left+20,by,90,()->send(AdventurePackets.Action.BANK_DEPOSIT,""),bank);
            button("取铜",left+116,by,48,()->send(AdventurePackets.Action.BANK_WITHDRAW,"copper"),bank);
            button("取银",left+170,by,48,()->send(AdventurePackets.Action.BANK_WITHDRAW,"silver"),bank);
            button("取金",left+224,by,48,()->send(AdventurePackets.Action.BANK_WITHDRAW,"gold"),bank);
            button("确认购买 · 5银",left+20,top+panelHeight-66,140,()->send(AdventurePackets.Action.BUY_HOME,""),data.getBoolean("AtBroker")&&!house.hasUUID("Owner")&&p.coins()>=500);
        } else {
            if(compactGear()){
                int w=(panelWidth-40)/3;
                button(List.of("全身身甲","魔法帽","遗物修复").get(gearCategory),left+20,top+44,w-3,()->{gearCategory=(gearCategory+1)%3;armChoice=0;bodyScroll=0;rebuildWidgets();},true);
                var choices=gearCategory==2?List.of("神袍遗物","星冠遗物"):gearCategory==1?List.of("守望宽檐","观星尖帽","回响法冠","行旅兜帽"):List.of("壁垒战甲","星织长袍","秘纹锁甲","远行外衣");
                button(choices.get(armChoice),left+20+w,top+44,w-3,()->{armChoice=(armChoice+1)%choices.size();bodyScroll=0;rebuildWidgets();},true);
                button(com.tnc.tnc.equipment.MageGear.RANKS[gearCategory==2?5:gearTier],left+20+w*2,top+44,w-3,()->{gearTier=gearTier%4+1;bodyScroll=0;rebuildWidgets();},gearCategory!=2);
            }else{
            int cw=(panelWidth-40)/3;for(int i=0;i<3;i++){final int c=i;button(List.of("全身身甲","魔法帽","遗物修复").get(i),left+20+i*cw,top+75,cw-3,()->{gearCategory=c;armChoice=0;bodyScroll=0;rebuildWidgets();},gearCategory!=i);}
            var choices=gearCategory==2?List.of("神袍遗物","星冠遗物"):gearCategory==1?List.of("守望宽檐","观星尖帽","回响法冠","行旅兜帽"):List.of("壁垒战甲","星织长袍","秘纹锁甲","远行外衣");
            int w=(panelWidth-40)/choices.size();for(int i=0;i<choices.size();i++){final int c=i;button(choices.get(i),left+20+i*w,top+99,w-3,()->{armChoice=c;bodyScroll=0;rebuildWidgets();},armChoice!=i);}
            if(gearCategory!=2){int rw=(panelWidth-40)/4;for(int i=1;i<=4;i++){final int t=i;button(com.tnc.tnc.equipment.MageGear.RANKS[i],left+20+(i-1)*rw,top+123,rw-3,()->{gearTier=t;bodyScroll=0;rebuildWidgets();},gearTier!=i);}}
            }
            int actionY=top+panelHeight-(compactGear()?44:66),actionW=(panelWidth-44)/2;
            button(gearCategory==2?"提交遗物修复":"交材料制作",left+20,actionY,actionW,()->send(AdventurePackets.Action.ORDER,gear().id()),data.getBoolean("AtArmorer")&&data.getLong("SmithReady")<0);
            button("领取装备",left+24+actionW,actionY,actionW,()->send(AdventurePackets.Action.CLAIM,""),data.getBoolean("AtArmorer")&&data.getLong("SmithReady")>=0);
        }
        button("关闭",left+panelWidth-64,top+12,48,this::onClose,true);
    }
    private boolean compactGear(){return tab==4&&panelHeight<260;}
    private Set<String> active(){var ids=new HashSet<String>();for(var c:data.getList("Contracts",10))ids.add(((CompoundTag)c).getString("ID"));return ids;}
    private int rows(){return Math.max(1,(panelHeight-192)/36);}
    private String materialName(ContractCatalog.Material material){
        if(material.tag())return material.id().equals("minecraft:logs")?"任意原木":"匹配材料";
        var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(material.id()));
        return item==null?"所需材料":new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }
    private String enemyName(String id){
        var entity=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse(id));
        return entity==null?"目标敌人":entity.getDescription().getString();
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
    private int paragraph(GuiGraphics g,String s,int y,int color){for(var line:font.split(Component.literal(s),panelWidth-40)){g.drawString(font,line,left+20,y,color,false);y+=12;}bodyLastY=Math.max(bodyLastY,y);return y;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);g.fill(left-2,top-2,left+panelWidth+2,top+panelHeight+2,0xFFA48257);g.fill(left,top,left+panelWidth,top+panelHeight,0xFFF1E7D5);
        g.fill(left,top,left+panelWidth,top+38,0xFF182C42);text(g,panel.title(),left+16,top+14,0xF7E8CF);
        if(!compactGear())text(g,panel.subtitle(),left+20,top+49,0x246E78);
        var p=profile();boolean local=data.getBoolean("AtService");
        String hoveredDetail=null;
        int bodyStart=top+(tab==2?135:tab==4?(compactGear()?70:153):tab==1?101:78),bodyEnd=top+panelHeight-(tab==3?99:tab==1?60:compactGear()?48:76);
        bodyLastY=bodyStart;bodyScroll=Math.min(bodyScroll,scrollLimit);
        g.enableScissor(left+12,bodyStart,left+panelWidth-12,Math.max(bodyStart+1,bodyEnd));g.pose().pushPose();g.pose().translate(0,-bodyScroll,0);
        if(tab==0) {
            int y=top+82;int lv=p.level();long next=lv>=100?0:AdventureRules.levelCost(lv),earned=p.xp()-AdventureRules.xpAtLevel(lv);
            text(g,"Lv."+lv+"   "+(data.getInt("Rank")==0?"初级":"精锐")+"冒险者",left+20,y,0x182C42);y+=20;
            text(g,lv>=100?"冒险成长已满级":"本级经验 "+earned+" / "+next+"   ·   声望 "+p.reputation(),left+20,y,0x544737);y+=18;
            int bar=panelWidth-40;g.fill(left+20,y,left+20+bar,y+5,0xFFD6CBB9);g.fill(left+20,y,left+20+(int)(bar*(lv>=100?1:earned/(double)next)),y+5,0xFF408B92);y+=17;
            text(g,"余额  "+AdventureRules.money(p.coins()),left+20,y,0x544737);y+=20;
            if(panel==ServicePanel.GUILD) {
                y=paragraph(g,"艾琳为你办理协会登记与晋升。精锐晋升需要Lv10与100声望；接取和交付委托请右键旁边的酒馆委托栏。",y,0x635A4A);
            } else {
                y=paragraph(g,"这是你的个人冒险档案。存取与购房请找银行，制作与提货请找相应匠人。",y,0x635A4A);
                y=paragraph(g,data.getString("Directions"),y+8,0x246E78);
                y+=10;for(var entry:data.getList("Ledger",8)){text(g,entry.getAsString(),left+20,y,0x635A4A);y+=12;}
            }
            bodyLastY=Math.max(bodyLastY,y);
        } else if(tab==1) {
            paragraph(g,"原生栏接取纸张，手持完成的委托右键酒馆栏交付。数量与期限见纸张；下方为教学/旧单归档。",top+103,0x635A4A);
            int rows=rows();var active=active();
            for(int i=page*rows;i<Math.min(visible.size(),(page+1)*rows);i++) {
                var c=visible.get(i);int y=top+136+(i-page*rows)*36;
                text(g,c.title(),left+20,y,0x182C42);
                int required=c.kills()>0?c.kills():c.materials().stream().mapToInt(ContractCatalog.Material::count).sum();
                String line=active.contains(c.id())?"进行中 "+data.getCompound("Counts").getInt(c.id())+" / "+required:"经验 "+c.xp()+" · 声望 "+c.reputation()+" · "+c.coins()+"铜";
                text(g,line,left+20,y+13,0x635A4A);
                bodyLastY=Math.max(bodyLastY,y+30);
                if(mx>=left+16&&mx<left+panelWidth-88&&my+bodyScroll>=y&&my+bodyScroll<y+32){
                    String detail=c.kills()>0?"接单后击杀 "+enemyName(c.enemy())+" × "+c.kills()+"。完成后回馆结算。":c.materials().stream().map(m->materialName(m)+" × "+m.count()).reduce((a,b)->a+"，"+b).orElse("")+"。提交消耗材料，返还碗/瓶。";
                    hoveredDetail=detail;
                }
            }
        } else if(tab==2) {
            var d=design();g.pose().pushPose();g.pose().translate(left+20,top+137,0);g.pose().scale(2,2,1);g.renderItem(ElementWands.stack(d),0,0);g.pose().popPose();
            text(g,d.name(),left+63,top+138,0x182C42);text(g,"Lv"+new int[]{0,1,10,25,50,85}[tier]+" · "+d.seconds()+"秒 · "+d.fee()+"铜",left+63,top+155,0x246E78);
            String mats=d.materials().stream().map(m->materialName(m)+"×"+m.count()).reduce((a,b)->a+" / "+b).orElse("");
            int y=paragraph(g,mats,top+177,0x635A4A);
            y=paragraph(g,"首次一阶免人工费。承载本系已学1～"+tier+"阶和通用术；低阶法器不抹除知识。光系普通战斗链待开放。",y+7,0x635A4A);
            var ordered=ElementWands.find(data.getString("SmithDesign"));long ready=data.getLong("SmithReady");String state=ready<0?"去潮生制杖屋找莉娅下单。":"订单："+(ordered==null?"铁匠/旧法杖订单":ordered.name())+" · "+(ready<=data.getLong("ActiveTicks")?"完成可领取":"剩余 "+(ready-data.getLong("ActiveTicks")+19)/20+" 秒");
            paragraph(g,state,y+8,0x246E78);
        } else if(tab==3) {
            var house=data.getCompound("Housing");int y=paragraph(g,"南街三层空屋 · 5银（500铜）",top+82,0x182C42);
            y=paragraph(g,house.getBoolean("Preparing")?house.getString("Status"):house.getBoolean("Mine")?"你的住宅：装修与物品长期保留。":house.hasUUID("Owner")?"本存档房源已售出，其他玩家无需付款。":"先看房，找归航银行米洛确认全款购买。分期与借贷尚未开放。",y+8,0x635A4A);
            y=paragraph(g,"入口："+house.getString("Entry"),y+8,0x246E78);
            y=paragraph(g,"装修边界："+house.getString("Boundary"),y+8,0x635A4A);
            paragraph(g,"账户余额："+AdventureRules.money(p.coins())+"。银行存取与钱袋共用余额；实体币不重复记账。",y+8,0x635A4A);
        } else {
            var d=gear();var r=EquipmentOrders.find(d.id());int gearY=top+(compactGear()?72:155);g.renderItem(com.tnc.tnc.equipment.MageGear.stack(d),left+20,gearY);
            text(g,d.name(),left+48,gearY,0x182C42);text(g,"Lv"+r.level()+" · "+r.seconds()+"秒 · "+r.fee()+"铜",left+48,gearY+18,0x246E78);
            int y=paragraph(g,d.detail(),gearY+39,0x635A4A);
            y=paragraph(g,r.materials().stream().map(m->materialName(m)+"×"+m.count()).reduce((a,b)->a+" / "+b).orElse(""),y+5,0x635A4A);
            y=paragraph(g,d.divine()?"修复需要破损遗物；遗物获取地点尚未开放。":"首次冒险者装备免人工费。帽子与身甲可自由混搭，身甲已包含裤鞋。",y+5,0x635A4A);
            var equipment=EquipmentOrders.find(data.getString("SmithDesign"));var wand=ElementWands.find(data.getString("SmithDesign"));
            String pending=equipment!=null?new net.minecraft.world.item.ItemStack(equipment.item()).getHoverName().getString():wand!=null?wand.name():"旧基础杖";
            paragraph(g,data.getLong("SmithReady")<0?"当前没有订单。":"已有订单："+pending+"；请回原商家领取。",y+10,0x246E78);
        }
        g.pose().popPose();g.disableScissor();scrollLimit=Math.max(0,bodyLastY-bodyEnd+4);
        if(tab==1)text(g,"原生栏已完成 "+data.getInt("NativeBounties")+" 次",left+170,top+panelHeight-46,0x635A4A);
        if(scrollLimit>0&&!compactGear())text(g,"滚轮查看更多",left+panelWidth-90,bodyEnd+2,0x635A4A);
        if(!notice.isEmpty()){
            String shortNotice=font.plainSubstrByWidth(notice,panelWidth-40);text(g,shortNotice,left+20,top+panelHeight-20,0x7B452B);
            if(mx>=left+20&&mx<=left+panelWidth-20&&my>=top+panelHeight-24&&my<top+panelHeight)g.renderTooltip(font,font.split(Component.literal(notice),Math.min(320,width-40)),mx,my);
        }
        super.render(g,mx,my,partial);
        if(hoveredDetail!=null)g.renderTooltip(font,font.split(Component.literal(hoveredDetail),Math.min(260,width-20)),mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=left&&x<=left+panelWidth&&y>=top+72&&y<top+panelHeight-(compactGear()?48:76)&&scrollLimit>0){bodyScroll=Math.max(0,Math.min(scrollLimit,bodyScroll-(int)(delta*18)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void tick(){if(++ticks%20==0)send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean isPauseScreen(){return false;}
}
