package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.dungeon.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Grimoire materials and proportional ornaments shared by the independent dungeon service pages. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class DungeonScreen extends Screen {
    public static CompoundTag latest=new CompoundTag();
    private static int received;
    private static final int W=600,H=360,INK=0xffe6daca,DIM=0xffb5aa98,GOLD=0xffb79a6b,CYAN=0xff91d6d5;
    private final CompoundTag state;
    private final List<Option> options=new ArrayList<>();
    private double scale,left,top;
    private record Option(int x,int y,int w,int h,String label,String detail,Item icon,String action,int value,boolean enabled) {
        boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}
    }
    public DungeonScreen(CompoundTag data){super(Component.literal("门后的三十层"));state=data.copy();}
    public static void accept(DungeonNetwork.State s){
        latest=s.data().copy();var mc=Minecraft.getInstance();received=mc.player==null?0:mc.player.tickCount;
        if(s.open())mc.setScreen(new DungeonScreen(s.data()));
        else if(mc.screen instanceof DungeonScreen screen&&screen.state.getString("Theme").equals(s.data().getString("Theme"))&&screen.state.getInt("Floor")==s.data().getInt("Floor"))screen.state.merge(s.data());
    }
    private String kind(){return state.getString("Kind");}
    private DungeonTheme theme(){return DungeonTheme.find(state.getString("Theme"));}
    private Item material(){var item=DungeonRewards.material(theme());return item==null?Items.AMETHYST_SHARD:item;}
    private void option(int y,String label,String detail,Item icon,String action,int value){options.add(new Option(26,y,278,40,label,detail,icon,action,value,true));}
    @Override protected void init(){
        scale=Math.min(1.3,Math.min((width-16)/(double)W,(height-16)/(double)H));left=(width-W*scale)/2;top=(height-H*scale)/2;options.clear();
        switch(kind()){
            case "gate"->{
                int unlocked=state.getInt("Unlocked");boolean leader=state.getBoolean("Leader");
                for(int i=0;i<6;i++){int floor=1+i*5;boolean open=floor<=unlocked;
                    options.add(new Option(26+(i%2)*144,123+(i/2)*50,134,42,"第 "+floor+"—"+(floor+4)+" 层",!open?"尚未解锁":leader?"已解锁 · 启程":"等待队长选择",open?Items.ENDER_PEARL:Items.IRON_NUGGET,"start",floor,open&&leader));
                }
                options.add(new Option(26,285,134,26,"返回野外界门","",Items.ENDER_EYE,"leave",0,true));
                options.add(new Option(170,285,134,26,"整理行囊·备战","",Items.BUNDLE,"prepare",0,true));
            }
            case "cache"->option(232,"收下这份旅途馈赠","背包满时，奖励会为你保留",Items.CHEST,"claim",0);
            case "rest"->{option(148,"在灯下整备","恢复生命，补充食物与40法力",Items.LANTERN,"rest",0);option(204,"保存旅途 · 返回界门","保留当前挑战，下次继续",Items.ENDER_EYE,"leave",0);}
            case "event"->{switch(state.getInt("Event")){
                case 0->{option(128,"旅途餐 ×4","8 遗币 · 补充探险所需的口粮",Items.COOKED_BEEF,"buy",0);option(181,"主题材料 ×4","12 遗币 · 来自这片遗庭的结晶",material(),"buy",1);option(234,"旅途纪念","20 遗币 · 为这段旅程留个纪念",DungeonContent.ITEMS.get("dungeon_memento"),"buy",2);}
                case 1->{option(128,"回声甘露","清理战斗房后，恢复5点法力",Items.POTION,"bless",1);option(181,"归途护灯","每个新战斗房，首次伤害减半",Items.LANTERN,"bless",2);option(234,"轻风足迹","本段移动速度提高8%",Items.FEATHER,"bless",3);}
                case 2->{option(160,"拿取普通补给","安稳地收下匣中的礼物",Items.CHEST,"event",0);option(216,"唤醒匣中守卫","完成额外战斗，获得更多主题材料",Items.IRON_SWORD,"event",1);}
                case 3->option(180,"请测绘员指路","点亮本层一间尚未探索的房间",Items.MAP,"event",0);
                case 4->option(180,"听听旧门环的故事","也许它还记得一扇没走过的门",Items.TRIPWIRE_HOOK,"event",0);
                case 5->option(180,"记下归途 · 返回界门","保留本次挑战的房间和战利品",Items.ENDER_EYE,"leave",0);
            }}
        }
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
    private int paragraph(GuiGraphics g,String text,int x,int y,int maxWidth,int color){
        for(var line:font.split(Component.literal(text),maxWidth)){g.drawString(font,line,x,y,color,false);y+=13;}return y;
    }
    private void diamond(GuiGraphics g,double x,double y,int radius,int color){
        ArcaneGui.line(g,x,y-radius,x+radius,y,.8,color);ArcaneGui.line(g,x+radius,y,x,y+radius,.8,color);
        ArcaneGui.line(g,x,y+radius,x-radius,y,.8,color);ArcaneGui.line(g,x-radius,y,x,y-radius,.8,color);
    }
    private void rule(GuiGraphics g,int x,int y,int w){g.fill(x,y,x+w,y+1,0xff63513b);diamond(g,x+w/2.,y,3,0xffab9468);}
    private void ledger(GuiGraphics g,int x,String name,long value,boolean risk){
        g.fill(x,67,x+172,93,0xa20c1117);g.fill(x,92,x+172,93,0xff5d4c37);
        text(g,name,x+9,76,DIM);String digits=Long.toString(value);text(g,digits,x+162-font.width(digits),76,risk&&value>0?0xffd5a18a:CYAN);
    }
    private void drawOption(GuiGraphics g,Option o,double mx,double my){
        boolean hover=o.enabled&&o.contains(mx,my);int edge=o.enabled?(hover?0xffd8b77a:0xff94794f):0xff4d4841;
        g.fill(o.x,o.y+2,o.x+o.w,o.y+o.h+2,0x80000000);g.fill(o.x,o.y,o.x+o.w,o.y+o.h,0xff3b3227);
        g.fill(o.x+1,o.y+1,o.x+o.w-1,o.y+o.h-1,hover?0xff263b3d:0xff181d23);
        g.fill(o.x,o.y,o.x+o.w,o.y+1,edge);g.fill(o.x,o.y+o.h-1,o.x+o.w,o.y+o.h,0xff57452f);
        g.fill(o.x,o.y,o.x+1,o.y+o.h,edge);g.fill(o.x+o.w-1,o.y,o.x+o.w,o.y+o.h,0xff57452f);
        for(int px:new int[]{o.x,o.x+o.w-3})for(int py:new int[]{o.y,o.y+o.h-3})g.fill(px,py,px+3,py+3,edge);
        if(hover)g.fill(o.x+2,o.y+4,o.x+3,o.y+o.h-4,0xff9ce2d6);
        int iy=o.y+(o.h-16)/2;
        if(o.icon!=null){g.renderItem(new ItemStack(o.icon),o.x+9,iy);if(!o.enabled)g.fill(o.x+9,iy,o.x+25,iy+16,0x8c16191c);}
        int tx=o.x+34,available=o.w-42;
        text(g,font.plainSubstrByWidth(o.label,available),tx,o.y+(o.detail.isEmpty()?9:8),o.enabled?INK:0xff837c6f);
        if(!o.detail.isEmpty())text(g,font.plainSubstrByWidth(o.detail,available),tx,o.y+24,o.enabled?(hover?CYAN:DIM):0xff726d65);
    }
    private String heading(){return switch(kind()){
        case "gate"->"选择下一段旅程";case "cache"->"遗庭宝匣";case "rest"->"守关前的片刻安宁";case "guide"->"旅人手记";
        default->new String[]{"迷路的行商","三盏许愿灯","沉睡的补给匣","断路测绘员","会说话的旧门环","归途石碑"}[Math.floorMod(state.getInt("Event"),6)];};}
    private void page(GuiGraphics g){
        text(g,heading(),26,106,GOLD);
        if(kind().equals("cache")){
            paragraph(g,"藏在岔路深处的馈赠，愿它陪你多走几间未知的房。",26,129,270,DIM);
            Item[] rewards={material(),Items.COOKED_BEEF,Items.GOLD_NUGGET};String[] labels={"主题材料","旅途补给","少量遗币"};
            for(int i=0;i<3;i++){int x=38+i*86;ArcaneGui.slot(g,x+17,177);g.renderItem(new ItemStack(rewards[i]),x+17,177);text(g,labels[i],x+25-font.width(labels[i])/2,204,INK);}
        }else if(kind().equals("rest"))paragraph(g,"炉火尚暖。整备好行囊，再决定继续前进，还是暂且归家。",26,124,270,DIM);
        else if(kind().equals("guide")){
            String[] names={"穿过界门","沿门探索","每五层留档","把旅途带回家"};
            String[] details={"四座野外大门，通向四个三十层主题。","每层八间随机房。清场开门，岔路有宝匣与奇遇。","击败守关者后保存进度，下次可从已解锁的段首启程。","正常离开可继续原挑战；勘察报告回酒馆委托栏交付。"};
            int y=124;for(int i=0;i<names.length;i++){diamond(g,32,y+4,3,GOLD);text(g,names[i],44,y,INK);y=paragraph(g,details[i],44,y+13,252,DIM)+8;}
            text(g,"寻找界门：/dungeon locate <主题>",26,310,CYAN);
        }else if(kind().equals("event")&&state.getInt("Event")>=2){
            String copy=switch(state.getInt("Event")){case 2->"匣中的光还没有熄灭。你可以安稳取走补给，也可以主动迎接一场挑战。";case 3->"他的纸页画满了岔路。未知之处，也许藏着下一段旅程。";case 4->"旧铜环轻轻晃动，像是有句话，已经在门边等了很久。";default->"在石碑上留下这一刻。门后的路，会等你下次回来。";};
            paragraph(g,copy,26,128,272,DIM);
        }
    }
    private void rightPage(GuiGraphics g,double time){
        boolean hasMap=state.contains("Map")&&!state.getList("Map",10).isEmpty();
        text(g,hasMap?"探索星图":kind().equals("guide")?"四道界门":kind().equals("gate")?"界门共鸣 · 基础守关试炼":"界门共鸣",342,106,GOLD);
        if(kind().equals("guide")){
            ArcaneGui.sigil(g,454,158,35,time,.6f,true);
            g.drawCenteredString(font,Component.literal("黄沙遗庭 · 锁钟地牢"),454,207,INK);
            g.drawCenteredString(font,Component.literal("沉潮回廊 · 星眠幻庭"),454,222,DIM);
            text(g,"单人：每五层段可自救一次。",342,242,DIM);
            text(g,"多人：队友救援，全员倒地才失败。",342,255,DIM);
            paragraph(g,"失败保留装备与背包，只扣本段遗币20%。",342,273,226,DIM);
            text(g,"组队：/dungeon invite 玩家",342,301,CYAN);
            text(g,"接受：/dungeon accept",342,314,CYAN);
            return;
        }
        if(hasMap){map(g,state,336,126,238,138);text(g,"◆ 当前房间    ◇ 已走过的路",342,273,DIM);}
        else{
            ArcaneGui.sigil(g,454,185,62,time,.6f,true);String name=state.contains("Theme")?theme().name:"门后的三十层";g.drawCenteredString(font,Component.literal(name),454,261,INK);
        }
        if(kind().equals("gate")){
            text(g,"队伍 "+state.getInt("Party")+" / 4",342,285,INK);
            paragraph(g,state.getBoolean("Leader")?"每五层保存一次进度，选择已解锁的段落启程。":"由队长选择段落；先完成前一段试炼。",342,301,228,DIM);
        }else if(!kind().equals("guide")){
            String rule=state.getBoolean("Multiplayer")?"倒地请等待队友救援；全员同时倒地才算失败。":"单人每五层段可自救一次，重登不会补充次数。";
            paragraph(g,rule,342,hasMap?294:288,226,DIM);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);
        ArcaneGui.proportionalFrame(g,0,0,W,H);g.fill(20,98,314,321,0x6010141a);g.fill(326,98,580,321,0x3810141a);
        text(g,"门后的三十层",26,24,INK);text(g,state.contains("Theme")?theme().name+"  /  "+(state.getInt("Floor")==0?"界门入口":"第 "+state.getInt("Floor")+" 层"):"探索手册  /  旅途与归处",26,43,CYAN);
        ledger(g,26,"遗币",state.getLong("Saved"),false);ledger(g,214,"本段收益",state.getLong("Pending"),false);ledger(g,402,"失败损失",DungeonData.failureLoss(state.getLong("Pending")),true);
        rule(g,26,59,548);g.fill(318,104,319,320,0xff65563f);page(g);
        double time=minecraft.level==null?0:minecraft.level.getGameTime()+partial;rightPage(g,time);
        double x=(mx-left)/scale,y=(my-top)/scale;for(var o:options)drawOption(g,o,x,y);
        rule(g,26,328,548);text(g,"ESC 返回",26,337,DIM);String footer=kind().equals("gate")?"每五层留档 · 让旅程慢一点":kind().equals("guide")?"手册可在酒馆委托栏领取":"沿着星图，记下走过的门";text(g,footer,574-font.width(footer),337,DIM);
        g.pose().popPose();super.render(g,mx,my,partial);
    }
    public static void map(GuiGraphics g,CompoundTag t,int x,int y,int w,int h){
        var font=Minecraft.getInstance().font;var rooms=t.getList("Map",10);Map<Integer,CompoundTag> all=new HashMap<>();for(Tag e:rooms){var r=(CompoundTag)e;all.put(r.getInt("Id"),r);}
        double cx=x+w/2.,cy=y+h/2.;ArcaneGui.ring(g,cx,cy,65,65,0,0x886d6048);ArcaneGui.ring(g,cx,cy,59,59,0,0x485b978c);
        int minX=0,maxX=0,minZ=0,maxZ=0;for(var r:all.values()){minX=Math.min(minX,r.getInt("X"));maxX=Math.max(maxX,r.getInt("X"));minZ=Math.min(minZ,r.getInt("Z"));maxZ=Math.max(maxZ,r.getInt("Z"));}
        double step=Math.min(23,Math.min((w-32.)/Math.max(1,maxX-minX),(h-26.)/Math.max(1,maxZ-minZ)));double ox=cx-(minX+maxX)*step/2,oz=cy-(minZ+maxZ)*step/2;
        for(var r:all.values())for(int dest:r.getIntArray("Links")){var b=all.get(dest);if(b==null||dest<r.getInt("Id"))continue;ArcaneGui.line(g,ox+r.getInt("X")*step,oz+r.getInt("Z")*step,ox+b.getInt("X")*step,oz+b.getInt("Z")*step,1.4,0xff8a7755);}
        for(var r:all.values()){
            int id=r.getInt("Id"),rx=(int)Math.round(ox+r.getInt("X")*step),rz=(int)Math.round(oz+r.getInt("Z")*step);boolean current=id==t.getInt("Room"),cleared=(t.getInt("Cleared")&(1<<id))!=0;
            int color=current?0xffa9f3e0:cleared?0xff8f9d7a:0xffad936b;g.fill(rx-7,rz-7,rx+8,rz+8,0xff141b21);g.renderOutline(rx-7,rz-7,15,15,color);
            if(current)g.renderOutline(rx-9,rz-9,19,19,0x888ecfc5);String number=r.getString("Type").equals("UNKNOWN")?"?":Integer.toString(id+1);g.drawString(font,number,rx-font.width(number)/2,rz-4,color,false);
        }
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0){double x=(mx-left)/scale,y=(my-top)/scale;for(var o:options)if(o.contains(x,y)){
            if(o.enabled){minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1));DungeonNetwork.send(state.getLong("Token"),o.action,o.value);if(!o.action.equals("buy"))onClose();}return true;
        }}return super.mouseClicked(mx,my,button);
    }
    @Override public boolean isPauseScreen(){return false;}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!DungeonRealm.inside(mc.level)||mc.options.hideGui||mc.screen!=null||mc.player.tickCount-received>160||latest.getInt("Floor")==0)return;
        progress(e.getGuiGraphics(),latest);
    }
    static void progress(GuiGraphics g,CompoundTag data){
        var font=Minecraft.getInstance().font;int x=g.guiWidth()/2-136;
        ArcaneGui.proportionalFrame(g,x,8,272,68);g.fill(x+14,34,x+16,60,0xff80baad);
        g.drawString(font,DungeonTheme.find(data.getString("Theme")).name+"  · "+data.getInt("Floor")+" / 30层  · 探索 "+Integer.bitCount(data.getInt("Visited"))+" / 8",x+23,35,INK,false);
        String rescue=data.getBoolean("Multiplayer")?"队友救援":data.getBoolean("SoloUsed")?"自救已用完":"可自救一次";
        g.drawString(font,"本段遗币 "+data.getLong("Pending")+"  · "+rescue,x+23,50,CYAN,false);
    }
}
