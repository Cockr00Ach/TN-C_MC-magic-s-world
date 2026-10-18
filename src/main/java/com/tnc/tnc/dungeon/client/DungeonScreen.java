package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.dungeon.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class DungeonScreen extends Screen {
    public static CompoundTag latest=new CompoundTag();private static int received;
    private final CompoundTag state;private final List<Option> options=new ArrayList<>();private double scale,left,top;
    private record Option(int y,String label,String action,int value){}
    public DungeonScreen(CompoundTag data){super(Component.literal("门后的三十层"));state=data.copy();}
    public static void accept(DungeonNetwork.State s){latest=s.data().copy();var mc=Minecraft.getInstance();received=mc.player==null?0:mc.player.tickCount;if(s.open())mc.setScreen(new DungeonScreen(s.data()));}
    @Override protected void init(){scale=Math.min(1.3,Math.min((width-16)/560.,(height-16)/320.));left=(width-560*scale)/2;top=(height-320*scale)/2;options.clear();String kind=state.getString("Kind");int y=94;
      if(kind.equals("gate")){int unlocked=state.getInt("Unlocked");for(int f=1;f<=26;f+=5)if(f<=unlocked){options.add(new Option(y,"从第 "+f+"—"+(f+4)+" 层开始","start",f));y+=24;}options.add(new Option(265,"返回野外大门","leave",0));}
      else if(kind.equals("cache"))options.add(new Option(117,"领取这份宝匣奖励","claim",0));
      else if(kind.equals("rest")){options.add(new Option(115,"整备：恢复生命与少量法力","rest",0));options.add(new Option(145,"保存现状并返回大门","leave",0));}
      else if(kind.equals("event")){switch(state.getInt("Event")){case 0->{options.add(new Option(105,"旅途餐 ×4  · 8遗币","buy",0));options.add(new Option(135,"主题材料 ×4 · 12遗币","buy",1));options.add(new Option(165,"旅途纪念 · 20遗币","buy",2));}case 1->{options.add(new Option(105,"回声甘露 · 清场回复5法力","bless",1));options.add(new Option(135,"归途护灯 · 每房一次减伤","bless",2));options.add(new Option(165,"轻风足迹 · 本段移速提升","bless",3));}case 2->{options.add(new Option(105,"拿取普通补给","event",0));options.add(new Option(135,"唤醒守卫，清场拿额外奖励","event",1));}case 3->options.add(new Option(115,"请测绘员点亮一间未知房","event",0));case 4->options.add(new Option(115,"听听旧门环的提示","event",0));case 5->options.add(new Option(115,"保存现状并返回大门","leave",0));}}
    }
    private void lines(GuiGraphics g,List<String> text,int x,int y,int color){for(String s:text){for(var line:font.split(Component.literal(s),232)){g.drawString(font,line,x,y,color,false);y+=13;}y+=5;}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);com.tnc.tnc.client.ui.ArcaneGui.proportionalFrame(g,0,0,560,320);g.fill(16,52,544,295,0xb0111b23);g.fill(16,48,544,49,0xff73644c);var theme=DungeonTheme.find(state.getString("Theme"));String kind=state.getString("Kind");g.drawString(font,"门后的三十层  /  "+(state.contains("Theme")?theme.name:"探索手册"),20,20,0xffeadbc0,false);g.drawString(font,"遗币 "+state.getLong("Saved")+"  · 本段 "+state.getLong("Pending")+"  · 失败损失 "+DungeonData.failureLoss(state.getLong("Pending")),20,59,0xffb2d8d3,false);g.fill(280,78,281,293,0xff5d625c);
      if(kind.equals("guide")){lines(g,List.of("野外四座大门通向四个主题，各30层，每5层一位守关者。","每层8间随机连接的房间；清过的房间不重复刷怪。找到下层界碑即可前进。","单人每个五层段可倒地自救一次；多人只能队友救，全员倒地才失败。","失败保留装备背包与经验，仅扣本段遗币20%。","/dungeon locate <主题> 寻找大门。队伍在入口使用 /dungeon invite 玩家，再由对方 /dungeon accept。"),20,88,0xffc4ceca);}
      else{String label=switch(kind){case "gate"->"选择已解锁的五层挑战段";case "cache"->"宝匣：主题材料、补给与收藏";case "rest"->"守关前的片刻安宁";default->new String[]{"迷路的行商","三盏许愿灯","沉睡的补给匣","断路测绘员","会说话的旧门环","归途石碑"}[Math.floorMod(state.getInt("Event"),6)];};g.drawString(font,label,20,82,0xffeadbc0,false);
        for(var o:options){double x=(mx-left)/scale,y=(my-top)/scale;boolean hover=x>=20&&x<=262&&y>=o.y&&y<=o.y+20;g.fill(20,o.y,262,o.y+20,hover?0xff43595b:0xff26373e);g.renderOutline(20,o.y,242,20,hover?0xffd1b47c:0xff7c7058);g.drawString(font,o.label,27,o.y+6,0xffe1d4ba,false);}
        if(kind.equals("gate"))lines(g,List.of("队伍人数 "+state.getInt("Party")+" / 4","单人：本段一次自救；多人：队友救援。","基础守关试炼已开放，专属Boss机制后续补充。"),300,243,0xffabbfc2);
      }
      if(state.contains("Map"))map(g,state,300,88,238,142);else if(kind.equals("gate"))lines(g,List.of("穿过未知门，也许是激战，也许是宝藏。","每5层存档。可以分段探索，正常离开后仍能继续原来的挑战。"),300,95,0xffa9c4c6);
      g.drawString(font,"ESC 关闭 · 奖励与传送由服务器核实",20,301,0xff879da2,false);g.pose().popPose();super.render(g,mx,my,partial);}
    public static void map(GuiGraphics g,CompoundTag t,int x,int y,int w,int h){var rooms=t.getList("Map",10);g.fill(x,y,x+w,y+h,0xff111b23);Map<Integer,CompoundTag> all=new HashMap<>();for(Tag e:rooms){var r=(CompoundTag)e;all.put(r.getInt("Id"),r);}int step=Math.min(w,h)/8;
      for(var r:all.values()){int rx=x+w/2+r.getInt("X")*step,rz=y+h/2+r.getInt("Z")*step;for(int dest:r.getIntArray("Links")){var b=all.get(dest);if(b==null)continue;int bx=x+w/2+b.getInt("X")*step,bz=y+h/2+b.getInt("Z")*step;g.fill(Math.min(rx,bx)-1,Math.min(rz,bz)-1,Math.max(rx,bx)+2,Math.max(rz,bz)+2,0xff48646b);}}
      for(var r:all.values()){int id=r.getInt("Id"),rx=x+w/2+r.getInt("X")*step,rz=y+h/2+r.getInt("Z")*step;boolean cleared=(t.getInt("Cleared")&(1<<id))!=0;int c=id==t.getInt("Room")?0xfff0d08d:cleared?0xff659990:0xff58788e;g.fill(rx-5,rz-5,rx+6,rz+6,c);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){double x=(mx-left)/scale,y=(my-top)/scale;for(var o:options)if(x>=20&&x<=262&&y>=o.y&&y<=o.y+20){DungeonNetwork.send(state.getLong("Token"),o.action,o.value);if(!o.action.equals("buy"))onClose();return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean isPauseScreen(){return false;}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!DungeonRealm.inside(mc.level)||mc.options.hideGui||mc.screen!=null||mc.player.tickCount-received>160||latest.getInt("Floor")==0)return;var g=e.getGuiGraphics();int x=g.guiWidth()/2-115;g.fill(x,8,x+230,36,0xb9111b23);g.drawString(mc.font,DungeonTheme.find(latest.getString("Theme")).name+"  · "+latest.getInt("Floor")+" / 30层  · 房间 "+Integer.bitCount(latest.getInt("Visited"))+" / 8",x+7,13,0xffe2cfaa,false);g.drawString(mc.font,"本段遗币 "+latest.getLong("Pending")+"  · "+(latest.getBoolean("Multiplayer")?"队友救援":latest.getBoolean("SoloUsed")?"自救已用完":"可自救一次"),x+7,24,0xff9cd9d5,false);}
}
