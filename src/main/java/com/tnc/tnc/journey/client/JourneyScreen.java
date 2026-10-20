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
    private final CompoundTag state;private final List<Choice> choices=new ArrayList<>();private int page;private String name;private net.minecraft.client.gui.components.EditBox nameBox;private double scale,left,top;
    private record Choice(int x,int y,int w,int h,String label,String action,String id){boolean hit(double a,double b){return a>=x&&a<x+w&&b>=y&&b<y+h;}}
    public JourneyScreen(CompoundTag t){super(Component.literal("RouchNao · 旅途与归处"));state=t.copy();name=t.getString("Name");}
    public static void open(CompoundTag t){if(t.getString("Page").equals("greeting")){JourneyClient.greeting(t);return;}Minecraft.getInstance().setScreen(new JourneyScreen(t));}
    @Override protected void init(){scale=Math.min(1.3,Math.min((width-16)/600.,(height-16)/360.));left=(width-600*scale)/2;top=(height-360*scale)/2;if(state.getString("Page").equals("profile")){nameBox=new net.minecraft.client.gui.components.EditBox(font,240,300,206,14,Component.literal("角色姓名"));nameBox.setMaxLength(20);nameBox.setValue(name);nameBox.setBordered(false);nameBox.setResponder(s->name=s);addWidget(nameBox);setInitialFocus(nameBox);}}
    private void text(GuiGraphics g,String s,int x,int y,int c){g.drawString(font,s,x,y,c,false);}
    private void lines(GuiGraphics g,String s,int x,int y,int w,int c,int max){var a=font.split(Component.literal(s),w);for(int i=0;i<Math.min(max,a.size());i++)g.drawString(font,a.get(i),x,y+12*i,c,false);}
    private void button(GuiGraphics g,int x,int y,int w,int h,String title,String action,String id,double mx,double my){var o=new Choice(x,y,w,h,title,action,id);choices.add(o);boolean hover=o.hit(mx,my);g.fill(x,y,x+w,y+h,hover?0xff315256:0xff1b2229);g.renderOutline(x,y,w,h,hover?0xffcde8d5:0xff9e855b);text(g,title,x+(w-font.width(title))/2,y+(h-8)/2,0xffe6daca);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);ArcaneGui.proportionalFrame(g,0,0,600,360);choices.clear();double mx=(mouseX-left)/scale,my=(mouseY-top)/scale;String kind=state.getString("Page");text(g,kind.equals("board")?"灯下委托栏":kind.equals("profile")?"初来此世 · 人物档案":"留一盏灯 · 家庭时光",24,29,ArcaneGui.TEXT);text(g,kind.equals("board")?"直接接单 · 最多三份 · 回栏交付，无需携带纸张":kind.equals("profile")?"力量属于旅途，名字属于你。":"婚姻与孩子沿用凡家物语，旅途与家在这里相连。",24,49,ArcaneGui.CYAN);
        if(kind.equals("board"))board(g,mx,my);else if(kind.equals("profile"))profile(g,mx,my,partial);else family(g,mx,my);
        text(g,"ESC 返回",24,337,ArcaneGui.DIM);g.pose().popPose();super.render(g,mouseX,mouseY,partial);
    }
    private void board(GuiGraphics g,double mx,double my){var all=state.getList("Cards",10);int pages=Math.max(1,(all.size()+5)/6);page=Math.min(page,pages-1);
        for(int i=0;i<6;i++){int index=page*6+i;if(index>=all.size())break;var c=all.getCompound(index);int x=23+(i%3)*188,y=77+(i/3)*124;boolean active=c.getBoolean("Active"),done=c.getInt("Progress")>=c.getInt("Count");
            g.fill(x+2,y+3,x+180,y+116,0x99000000);g.fillGradient(x,y,x+178,y+113,0xffc3b18e,0xff94866c);g.renderOutline(x,y,178,113,0xffdeca9e);g.fill(x+5,y+108,x+174,y+109,0x77735c3d);g.fill(x+83,y-2,x+95,y+3,0xff393e3b);g.fill(x+86,y-1,x+92,y+4,0xffbba171);
            text(g,font.plainSubstrByWidth(c.getString("Title"),158),x+10,y+11,0xff292b2d);lines(g,c.getString("Story"),x+10,y+28,158,0xff393c3a,2);
            text(g,active?"进度 "+c.getInt("Progress")+" / "+c.getInt("Count"):"冒险等级 "+c.getInt("Level"),x+10,y+56,0xff354c49);text(g,c.getInt("Coins")+"铜  ·  "+c.getInt("XP")+"经验",x+10,y+70,0xff463726);
            if(c.getBoolean("Unavailable"))button(g,x+10,y+87,157,18,"撤销已下架委托","abandon",c.getString("Id"),mx,my);
            else if(active&&!done){button(g,x+91,y+87,76,18,"放弃委托","abandon",c.getString("Id"),mx,my);text(g,"进行中",x+10,y+92,0xff384e45);}
            else if(active)button(g,x+10,y+87,157,18,"交付 · 领取报酬","claim",c.getString("Id"),mx,my);
            else if(c.getLong("Wait")>0)text(g,"下次可接："+(c.getLong("Wait")+59)/60+"分钟后",x+10,y+92,0xff594e40);
            else if(!state.getBoolean("Registered")||state.getInt("PlayerLevel")<c.getInt("Level"))text(g,"先登记／达到所需等级",x+10,y+92,0xff594e40);
            else button(g,x+10,y+87,157,18,"点击接取","accept",c.getString("Id"),mx,my);
        }
        button(g,230,329,35,18,"‹","prev","",mx,my);text(g,(page+1)+" / "+pages,277,334,ArcaneGui.DIM);button(g,337,329,35,18,"›","next","",mx,my);button(g,424,329,151,18,"特别档案／地下城报告","archive","",mx,my);
    }
    private void profile(GuiGraphics g,double mx,double my,float partial){
        g.fill(23,78,205,311,0x7010161e);ArcaneGui.sigil(g,114,178,70,(minecraft.level==null?0:minecraft.level.getGameTime())+partial,.3f,true);
        if(minecraft.player!=null){g.pose().pushPose();g.pose().translate(0,0,150);net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,114,282,70,(float)Math.max(-20,Math.min(20,114-mx)),(float)Math.max(-12,Math.min(12,175-my)),minecraft.player);g.pose().popPose();}
        String[] stats={"基础攻击  "+format(state.getDouble("Attack")),"最大生命  "+format(state.getDouble("Health")),"护甲  "+format(state.getDouble("Armor")),"移动速度  "+format(state.getDouble("Speed")),"魔力  "+state.getInt("Mana")+" / "+state.getInt("MaxMana")};for(int i=0;i<stats.length;i++)text(g,stats[i],233,85+22*i,ArcaneGui.TEXT);
        text(g,"元素亲和",233,206,ArcaneGui.GOLD);int i=0;for(var e:com.tnc.tnc.magic.Element.values()){int x=233+i%4*80,y=227+i/4*20;String label=e==com.tnc.tnc.magic.Element.DARK?"暗":e.cn();text(g,label+"  "+state.getCompound("Affinity").getInt(e.id()),x,y,ArcaneGui.CYAN);i++;}
        text(g,"你的名字（点击后直接输入，最多20字）",233,277,ArcaneGui.DIM);g.fill(232,292,454,317,0xff101921);g.renderOutline(232,292,222,25,0xffaa936c);if(nameBox!=null)nameBox.render(g,(int)mx,(int)my,partial);button(g,467,292,104,25,"确认姓名","name","",mx,my);
    }
    private String format(double n){return String.format(Locale.ROOT,n<1?"%.2f":"%.1f",n);}
    private void family(GuiGraphics g,double mx,double my){ArcaneGui.sigil(g,127,181,72,minecraft.level==null?0:minecraft.level.getGameTime(),.5f,true);text(g,state.getString("Spouse"),245,91,ArcaneGui.TEXT);lines(g,state.getString("Line"),245,115,300,ArcaneGui.DIM,3);text(g,"孩子："+state.getInt("Children")+"（在家 "+state.getInt("PresentChildren")+"）  ·  "+(state.getBoolean("Home")?"在家中":"与家人同行"),245,171,ArcaneGui.CYAN);button(g,245,204,304,31,"一起吃饭 · 需要两份同种食物","dinner","",mx,my);button(g,245,251,304,31,"把这里当作家","home","",mx,my);lines(g,"普通右键继续聊天、送礼与育儿；潜行右键伴侣打开此页。",245,301,300,ArcaneGui.DIM,2);}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b==0){double x=(mx-left)/scale,y=(my-top)/scale;if(nameBox!=null&&nameBox.mouseClicked(x,y,b)){setFocused(nameBox);return true;}for(var o:choices)if(o.hit(x,y)){if(o.action.equals("prev"))page=Math.max(0,page-1);else if(o.action.equals("next"))page++;else JourneyNetwork.send(state.getLong("Token"),o.action,o.action.equals("name")?name:o.id);return true;}}return super.mouseClicked(mx,my,b);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(state.getString("Page").equals("profile")&&key==257){JourneyNetwork.send(state.getLong("Token"),"name",name);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void tick(){if(nameBox!=null)nameBox.tick();}
    @Override public boolean isPauseScreen(){return false;}
}
