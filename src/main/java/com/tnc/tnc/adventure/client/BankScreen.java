package com.tnc.tnc.adventure.client;
import com.tnc.tnc.adventure.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Only Milo opens this bank; ordinary service screens remain separate. */
public final class BankScreen extends Screen {
    private CompoundTag data;private int page,left,top,w,h,ticks,confirmScroll,confirmLimit;
    private EditBox amount;private String typed="100",notice="",confirmation="",confirmId="";
    private AdventurePackets.Action confirmAction;
    public BankScreen(CompoundTag data){super(Component.literal("归航银行 · 米洛"));this.data=data;}
    private AdventureProfile profile(){return AdventureSavedData.readProfile(data);}
    public void update(CompoundTag next){
        var before=profile();data=next;var after=profile();
        if(!next.getString("Message").isEmpty()){notice=next.getString("Message");confirmation="";}
        if(before.bank.loan.active()!=after.bank.loan.active()||before.bank.mortgage.active()!=after.bank.mortgage.active()||!next.getString("Message").isEmpty())rebuildWidgets();
    }
    private void button(String name,int x,int y,int width,Runnable action,boolean active){var b=Button.builder(Component.literal(name),v->action.run()).bounds(x,y,width,20).build();b.active=active;addRenderableWidget(b);}
    private void send(AdventurePackets.Action a,String id){AdventurePackets.send(a,id);}
    private void prepare(AdventurePackets.Action a,String id,String text){confirmation=text;confirmScroll=0;confirmAction=a;confirmId=id;rebuildWidgets();}
    @Override protected void init(){
        if(amount!=null)typed=amount.getValue();amount=null;
        w=Math.min(440,width-16);h=Math.min(310,height-16);left=(width-w)/2;top=(height-h)/2;
        boolean bank=data.getBoolean("AtBroker");var p=profile();
        int tw=(w-32)/4;for(int i=0;i<4;i++){final int n=i;button(List.of("存钱","取钱","借钱","房子/土地").get(i),left+16+i*tw,top+70,tw-3,()->{if(n==3){net.minecraft.client.Minecraft.getInstance().setScreen(new PropertyScreen(data));return;}page=n;confirmation="";rebuildWidgets();},page!=i);}
        button("关闭",left+w-49,top+8,40,this::onClose,true);
        if(!confirmation.isEmpty()){
            button("确认办理",left+16,top+h-48,(w-36)/2,()->send(confirmAction,confirmId),bank);
            button("返回",left+20+(w-36)/2,top+h-48,(w-36)/2,()->{confirmation="";rebuildWidgets();},true);return;
        }
        if(page<2){
            var action=page==0?AdventurePackets.Action.BANK_SAVE:AdventurePackets.Action.BANK_TAKE;
            int bw=(w-32)/4;
            button("1银",left+16,top+97,bw-3,()->send(action,"100"),bank);
            button("5银",left+16+bw,top+97,bw-3,()->send(action,"500"),bank);
            button("全部",left+16+bw*2,top+97,bw-3,()->send(action,"all"),bank);
            button(page==0?"实体币":"取银币",left+16+bw*3,top+97,bw-3,()->send(page==0?AdventurePackets.Action.BANK_DEPOSIT:AdventurePackets.Action.BANK_WITHDRAW,page==0?"":"silver"),bank);
            amount=new EditBox(font,left+16,top+123,w-110,20,Component.literal("铜币金额"));amount.setMaxLength(10);amount.setFilter(v->v.matches("[0-9]*"));amount.setValue(typed);addRenderableWidget(amount);
            button(page==0?"存入（铜）":"取出（铜）",left+w-88,top+123,72,()->send(action,amount.getValue()),bank);
        }else if(page==2){
            if(p.bank.loan.active())button("提前还清 "+p.bank.loan.clearAmount(p.bank.onlineTicks)+"铜",left+16,top+97,w-32,()->prepare(AdventurePackets.Action.BANK_CLEAR,"loan","结清普通借款需"+profile().bank.loan.clearAmount(profile().bank.onlineTicks)+"铜，未来费用免除。"),bank);
            else {int bw=(w-32)/3;for(int n=0;n<3;n++){int value=new int[]{200,500,1000}[n];int parts=value==200?4:value==500?5:10;
                button(value+"→"+value*11/10,left+16+n*bw,top+97,bw-3,()->prepare(AdventurePackets.Action.BANK_BORROW,""+value,"借到"+value+"铜，共还"+value*11/10+"铜；每游玩40分钟自动还"+(value*11/10/parts)+"铜，共"+parts+"次。钱袋优先，存款补足；离线不催款。"),bank&&p.registered()&&p.level()>=(value==200?5:value==500?10:20)&&!p.bank.arrears());}}
        }else{
            var house=data.getCompound("Housing");long price=house.getLong("SalePrice"),down=house.getLong("DownPayment");
            if(!house.hasUUID("Owner")){
                int bw=(w-36)/2;
                button("全款 "+price+"铜",left+16,top+97,bw,()->prepare(AdventurePackets.Action.BUY_HOME,"","购买40号南街住宅，支付"+price+"铜。确认后获得居住和装修权限。"),bank&&p.coins()+p.bank.savings>=price);
                button("首付 "+down+"铜",left+20+bw,top+97,bw,()->prepare(AdventurePackets.Action.BUY_HOME_INSTALLMENT,"","首付"+down+"铜，之后每游玩40分钟自动还231铜，共10次，合计3210铜。离线不催款；钱不够不拆房。"),bank&&p.bank.canMortgage(p)&&p.coins()+p.bank.savings>=down);
            }else if(house.getBoolean("Mine")&&p.bank.mortgage.active())button("结清房贷 "+p.bank.mortgage.clearAmount(p.bank.onlineTicks)+"铜",left+16,top+97,w-32,()->prepare(AdventurePackets.Action.BANK_CLEAR,"home","结清房贷需"+profile().bank.mortgage.clearAmount(profile().bank.onlineTicks)+"铜，未来费用免除。"),bank);
        }
    }
    private int lines(GuiGraphics g,String s,int y,int color){for(var line:font.split(Component.literal(s),w-32)){if(y>=top+h-(confirmation.isEmpty()?24:58))break;g.drawString(font,line,left+16,y,color,false);y+=11;}return y;}
    private String debt(BankAccount.Debt d,BankAccount a){return !d.active()?"暂无欠款":"剩余"+d.remaining()+"铜 · 每次"+d.nextAmount()+"铜 · "+(d.overdue(a.onlineTicks)?"本期未付":"约"+(d.ticksUntilNext(a.onlineTicks)+1199)/1200+"分钟后还款");}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.fill(left-2,top-2,left+w+2,top+h+2,0xFFA48254);g.fill(left,top,left+w,top+h,0xFFF1E7D5);g.fill(left,top,left+w,top+32,0xFF182C42);g.drawString(font,title,left+14,top+12,0xFFF5E6CB,false);
        var p=profile();var b=p.bank;
        g.drawString(font,"钱袋 "+p.coins()+"铜   存款 "+b.savings+"铜",left+16,top+39,0xFF182C42,false);
        g.drawString(font,"日息约"+b.interestPrincipal()*data.getInt("BankInterestPercent")/100+"铜 · 日薪"+(p.registered()?data.getInt("BankWage"):0)+"铜",left+16,top+52,0xFF246E78,false);
        if(!confirmation.isEmpty()){
            var text=font.split(Component.literal(confirmation),w-32);int view=Math.max(11,h-161);
            confirmLimit=Math.max(0,text.size()*11-view);confirmScroll=Math.min(confirmScroll,confirmLimit);
            g.enableScissor(left+16,top+99,left+w-16,top+99+view);
            for(int i=0;i<text.size();i++)g.drawString(font,text.get(i),left+16,top+99+i*11-confirmScroll,0xFF544737,false);
            g.disableScissor();if(confirmLimit>0)g.drawString(font,"滚轮查看完整条款",left+16,top+h-60,0xFF246E78,false);
        }
        else if(page<2){
            int y=lines(g,page==0?"随时存取，每现实天"+data.getInt("BankInterestPercent")+"%利息，离线也算。":"取回钱袋无需手续费。取银币用钱袋兑换1枚实体银币。",top+149,0xFF544737);
            if(h>=240){y=lines(g,"最多1万铜计息；借来的本金不赚利息。",y+7,0xFF635A4A);lines(g,p.registered()?"协会工资每日自动入钱袋，离线期间登录后补结。":"到酒馆找艾琳登记，开始领取每日工资。",y+7,0xFF246E78);}
        }else if(page==2){int y=lines(g,debt(b.loan,b),top+125,0xFF246E78);lines(g,"借款只加一次10%费用。离线不催款，钱不够只暂停新借款。",y+8,0xFF635A4A);}
        else {var house=data.getCompound("Housing");int y=lines(g,house.hasUUID("Owner")?(house.getBoolean("Mine")?"你的南街住宅 · "+(house.getBoolean("Mortgage")?"分期还款中":"产权已结清"):"南街住宅已售出"):"40号南街住宅：全款3000，分期首付900。",top+125,0xFF246E78);y=lines(g,debt(b.mortgage,b),y+7,0xFF635A4A);if(h>=250)lines(g,"房屋位置："+house.getString("Entry"),y+8,0xFF635A4A);}
        if(!notice.isEmpty()){String shortText=font.plainSubstrByWidth(notice,w-32);g.drawString(font,shortText,left+16,top+h-17,0xFF7B452B,false);if(my>=top+h-22&&mx>=left&&mx<left+w)g.renderTooltip(font,font.split(Component.literal(notice),Math.min(300,width-24)),mx,my);}
        super.render(g,mx,my,partial);
    }
    @Override public void tick(){if(amount!=null)amount.tick();if(++ticks%20==0)send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(!confirmation.isEmpty()&&confirmLimit>0&&x>=left&&x<left+w&&y>=top+94&&y<top+h-48){confirmScroll=Math.max(0,Math.min(confirmLimit,confirmScroll-(int)(delta*18)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean isPauseScreen(){return false;}
}
