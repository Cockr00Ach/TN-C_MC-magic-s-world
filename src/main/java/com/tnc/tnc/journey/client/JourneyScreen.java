package com.tnc.tnc.journey.client;
import com.tnc.tnc.journey.*;
import com.tnc.tnc.client.ui.ArcaneGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import java.util.*;
public final class JourneyScreen extends Screen {
    private final CompoundTag state;private final List<Choice> choices=new ArrayList<>();private int page;private String name;private net.minecraft.client.gui.components.EditBox nameBox;private double scale,left,top;private int canvasHeight=360;
    private String nameStatus="";private long nameSentAt;
    private record Choice(int x,int y,int w,int h,String label,String action,String id){boolean hit(double a,double b){return a>=x&&a<x+w&&b>=y&&b<y+h;}}
    public JourneyScreen(CompoundTag t){super(Component.literal("RouchNao · 旅途与归处"));state=t.copy();name=t.getString("Name");}
    public static void open(CompoundTag t){if(t.getString("Page").equals("name_result")){var mc=Minecraft.getInstance();if(mc.screen instanceof JourneyScreen screen&&screen.state.getString("Page").equals("profile")&&screen.state.getLong("Token")==t.getLong("Token")){screen.nameSentAt=0;if(t.getBoolean("Success"))mc.setScreen(null);else screen.nameStatus=t.getString("Message");}return;}if(t.getBoolean("InitialWelcome")){JourneyClient.queueWelcome(t);return;}if(t.getString("Page").equals("greeting")){JourneyClient.greeting(t);return;}Minecraft.getInstance().setScreen(new JourneyScreen(t));}
    @Override protected void init(){canvasHeight=state.getString("Page").equals("board")?400:360;scale=Math.min(1.3,Math.min((width-16)/600.,(height-16)/(double)canvasHeight));left=(width-600*scale)/2;top=(height-canvasHeight*scale)/2;if(state.getString("Page").equals("profile")){nameBox=new net.minecraft.client.gui.components.EditBox(font,240,300,206,14,Component.literal("角色姓名"));nameBox.setMaxLength(20);nameBox.setValue(name);nameBox.setBordered(false);nameBox.setResponder(s->name=s);addWidget(nameBox);setInitialFocus(nameBox);}}
    private void text(GuiGraphics g,String s,int x,int y,int c){g.drawString(font,s,x,y,c,false);}
    private void lines(GuiGraphics g,String s,int x,int y,int w,int c,int max){var a=font.split(Component.literal(s),w);for(int i=0;i<Math.min(max,a.size());i++)g.drawString(font,a.get(i),x,y+12*i,c,false);}
    private void button(GuiGraphics g,int x,int y,int w,int h,String title,String action,String id,double mx,double my){var o=new Choice(x,y,w,h,title,action,id);choices.add(o);boolean hover=o.hit(mx,my);g.fill(x,y,x+w,y+h,hover?0xff315256:0xff1b2229);g.renderOutline(x,y,w,h,hover?0xffcde8d5:0xff9e855b);text(g,title,x+(w-font.width(title))/2,y+(h-8)/2,0xffe6daca);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);ArcaneGui.proportionalFrame(g,0,0,600,canvasHeight);choices.clear();double mx=(mouseX-left)/scale,my=(mouseY-top)/scale;String kind=state.getString("Page");text(g,kind.equals("board")?"灯下委托栏":kind.equals("profile")?"初来此世 · 人物档案":"留一盏灯 · 家庭时光",24,29,ArcaneGui.TEXT);text(g,kind.equals("board")?"直接接单 · 最多三份 · 回栏交付，无需携带纸张":kind.equals("profile")?"力量属于旅途，名字属于你。":"婚姻与孩子沿用凡家物语，旅途与家在这里相连。",24,49,ArcaneGui.CYAN);
        if(kind.equals("board"))board(g,mx,my);else if(kind.equals("profile"))profile(g,mx,my,partial);else family(g,mx,my);
        text(g,"ESC 返回",24,canvasHeight-23,ArcaneGui.DIM);g.pose().popPose();super.render(g,mouseX,mouseY,partial);
    }
    private void board(GuiGraphics g,double mx,double my){var all=state.getList("Cards",10);int pages=Math.max(1,(all.size()+5)/6);page=Math.min(page,pages-1);
        for(int i=0;i<6;i++){int index=page*6+i;if(index>=all.size())break;var c=all.getCompound(index);int x=23+(i%3)*188,y=77+(i/3)*140;boolean active=c.getBoolean("Active"),done=c.getInt("Progress")>=c.getInt("Count");
            g.fill(x+3,y+4,x+182,y+130,0x99000000);g.fillGradient(x,y,x+178,y+126,0xffdbcaab,0xffb5a17e);g.renderOutline(x,y,178,126,0xffe8d4a8);g.fill(x+84,y-3,x+94,y+4,0xffbda374);
            text(g,font.plainSubstrByWidth(c.getString("Title"),158),x+10,y+10,0xff292b2d);lines(g,c.getString("Story"),x+10,y+27,158,0xff565044,1);
            String goal="通过地下城楼层 ×"+c.getInt("Count");if(c.getString("Kind").equals("kill")){var id=net.minecraft.resources.ResourceLocation.tryParse(c.getString("Target"));var mob=id==null?null:net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);goal="讨伐 "+(mob==null?c.getString("Target"):mob.getDescription().getString())+" ×"+c.getInt("Count");}
            text(g,font.plainSubstrByWidth(goal,158),x+10,y+45,0xff243d42);text(g,"要求等级 "+c.getInt("Level")+(active?" · "+c.getInt("Progress")+"/"+c.getInt("Count"):""),x+10,y+60,0xff526052);g.fill(x+10,y+73,x+168,y+74,0x667e6a4b);text(g,"报酬  "+c.getInt("Coins")+"铜 · "+c.getInt("XP")+"经验",x+10,y+82,0xff533b23);
            if(c.getBoolean("Unavailable"))button(g,x+10,y+101,157,18,"撤销已下架委托","abandon",c.getString("Id"),mx,my);
            else if(active&&!done){button(g,x+91,y+101,76,18,"放弃委托","abandon",c.getString("Id"),mx,my);text(g,"进行中",x+10,y+106,0xff384e45);}
            else if(active)button(g,x+10,y+101,157,18,"交付 · 领取报酬","claim",c.getString("Id"),mx,my);
            else if(c.getLong("Wait")>0)text(g,"下次可接："+(c.getLong("Wait")+59)/60+"分钟后",x+10,y+106,0xff594e40);
            else if(!state.getBoolean("Registered"))text(g,"先向艾琳登记冒险者",x+10,y+106,0xff594e40);
            else if(state.getInt("PlayerLevel")<c.getInt("Level"))text(g,"冒险等级尚未达到",x+10,y+106,0xff594e40);
            else button(g,x+10,y+101,157,18,"点击接取","accept",c.getString("Id"),mx,my);
        }
        button(g,230,369,35,18,"‹","prev","",mx,my);text(g,(page+1)+" / "+pages,277,374,ArcaneGui.DIM);button(g,337,369,35,18,"›","next","",mx,my);button(g,424,369,151,18,"酒馆特别档案","archive","",mx,my);
    }
    private void profile(GuiGraphics g,double mx,double my,float partial){
        g.fill(23,78,205,311,0x7010161e);ArcaneGui.sigil(g,114,178,70,(minecraft.level==null?0:minecraft.level.getGameTime())+partial,.3f,true);
        if(minecraft.player!=null){g.pose().pushPose();g.pose().translate(0,0,150);net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,114,282,70,(float)Math.max(-20,Math.min(20,114-mx)),(float)Math.max(-12,Math.min(12,175-my)),minecraft.player);g.pose().popPose();}
        String[] stats={"基础攻击  "+format(state.getDouble("Attack")),"最大生命  "+format(state.getDouble("Health")),"护甲  "+format(state.getDouble("Armor")),"移动速度  "+format(state.getDouble("Speed")),"魔力  "+state.getInt("Mana")+" / "+state.getInt("MaxMana")};for(int i=0;i<stats.length;i++)text(g,stats[i],233,85+22*i,ArcaneGui.TEXT);
        text(g,"元素亲和",233,206,ArcaneGui.GOLD);int i=0;for(var e:com.tnc.tnc.magic.Element.values()){int x=233+i%4*80,y=227+i/4*20;String label=e==com.tnc.tnc.magic.Element.DARK?"暗":e.cn();text(g,label+"  "+state.getCompound("Affinity").getInt(e.id()),x,y,ArcaneGui.CYAN);i++;}
        text(g,"你的名字（点击后直接输入，最多20字）",233,277,ArcaneGui.DIM);g.fill(232,292,454,317,0xff101921);g.renderOutline(232,292,222,25,0xffaa936c);if(nameBox!=null)nameBox.render(g,(int)mx,(int)my,partial);button(g,467,292,104,25,nameSentAt==0?"确认姓名":"保存中…","name","",mx,my);
        text(g,nameStatus,233,323,ArcaneGui.CYAN);
    }
    private String format(double n){return String.format(Locale.ROOT,n<1?"%.2f":"%.1f",n);}
    private void family(GuiGraphics g,double mx,double my){ArcaneGui.sigil(g,127,181,72,minecraft.level==null?0:minecraft.level.getGameTime(),.5f,true);text(g,state.getString("Spouse"),245,91,ArcaneGui.TEXT);lines(g,state.getString("Line"),245,115,300,ArcaneGui.DIM,3);text(g,"孩子："+state.getInt("Children")+"（在家 "+state.getInt("PresentChildren")+"）  ·  "+(state.getBoolean("Home")?"在家中":"与家人同行"),245,171,ArcaneGui.CYAN);button(g,245,204,304,31,"一起吃饭 · 需要两份同种食物","dinner","",mx,my);button(g,245,251,304,31,"把这里当作家","home","",mx,my);lines(g,"普通右键继续聊天、送礼与育儿；潜行右键伴侣打开此页。",245,301,300,ArcaneGui.DIM,2);}
    private void submitName(){if(nameSentAt!=0)return;String value=nameBox==null?name:nameBox.getValue();if(value.strip().isBlank()){nameStatus="请输入名字后再确认。";return;}nameSentAt=System.currentTimeMillis();nameStatus="正在保存姓名…";JourneyNetwork.send(state.getLong("Token"),"name",value);}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b==0){double x=(mx-left)/scale,y=(my-top)/scale;if(nameBox!=null&&nameBox.mouseClicked(x,y,b)){setFocused(nameBox);return true;}for(var o:choices)if(o.hit(x,y)){if(o.action.equals("prev"))page=Math.max(0,page-1);else if(o.action.equals("next"))page++;else if(o.action.equals("name"))submitName();else JourneyNetwork.send(state.getLong("Token"),o.action,o.id);return true;}}return super.mouseClicked(mx,my,b);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(state.getString("Page").equals("profile")&&(key==257||key==335)){submitName();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void tick(){if(nameBox!=null)nameBox.tick();if(nameSentAt!=0&&System.currentTimeMillis()-nameSentAt>8000){nameSentAt=0;nameStatus="暂未收到服务器回复，可以重试。";}}
    @Override public boolean isPauseScreen(){return false;}
}
