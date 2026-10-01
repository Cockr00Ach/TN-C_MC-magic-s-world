package com.tnc.tnc.adventure.client;

import com.tnc.tnc.adventure.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** One selected story at a time, with scrollable reading space even at large GUI scales. */
public final class TownStoryScreen extends Screen {
    private CompoundTag data;private int left,top,w,h,choice,ticks,scroll,limit;private String notice="";
    public TownStoryScreen(CompoundTag tag){super(Component.literal("归航札记 · 艾琳"));data=tag;}
    public void update(CompoundTag tag){data=tag;if(!tag.getString("Message").isEmpty())notice=tag.getString("Message");rebuildWidgets();}
    private TownStories.Story story(){return TownStories.ALL.get(choice);}
    private CompoundTag record(){return data.getCompound("TownStories").getCompound(story().id());}
    private void button(String text,int x,int y,int width,Runnable run,boolean active){var b=Button.builder(Component.literal(text),v->run.run()).bounds(x,y,width,20).build();b.active=active;addRenderableWidget(b);}
    @Override protected void init(){
        w=Math.min(480,width-16);h=Math.min(334,height-16);left=(width-w)/2;top=(height-h)/2;int tw=(w-32)/3;
        for(int i=0;i<3;i++){final int index=i;button(TownStories.ALL.get(i).title(),left+16+tw*i,top+43,tw-3,()->{choice=index;scroll=0;notice="";rebuildWidgets();},choice!=i);}
        int stage=record().getInt("Stage");button(stage==0?"领取这份札记":stage>=4?"故事已归档":"提交本章 / 归档",left+16,top+h-62,Math.min(150,w-120),()->AdventurePackets.send(AdventurePackets.Action.STORY_ADVANCE,story().id()),data.getBoolean("AtService")&&stage<4);
        button("回协会柜台",left+w-104,top+h-62,88,()->{var copy=data.copy();copy.putString("ServiceRole","guild");Minecraft.getInstance().setScreen(new AdventureScreen(copy));},true);
    }
    private int paragraph(GuiGraphics g,String text,int y,int color){for(var line:font.split(Component.literal(text),w-48)){g.drawString(font,line,left+24,y,color,false);y+=13;}return y;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.fill(left-2,top-2,left+w+2,top+h+2,0xffa88752);g.fill(left,top,left+w,top+h,0xfff1e7d5);g.fill(left,top,left+w,top+34,0xff1d3440);g.drawString(font,title,left+16,top+12,0xfff3dfb6,false);
        var story=story();var r=record();int stage=r.getInt("Stage");int start=top+77,end=top+h-72,y=start;
        g.enableScissor(left+12,start,left+w-12,Math.max(start+1,end));g.pose().pushPose();g.pose().translate(0,-scroll,0);
        y=paragraph(g,story.opening(),y,0xff365b5d)+12;
        for(int i=0;i<3;i++){var ch=story.chapters().get(i);String status=stage>i+1?"已完成":stage==i+1?"进行中":"未开始";
            y=paragraph(g,"第"+(i+1)+"章 · "+ch.title()+"  /  "+status,y,stage==i+1?0xff176e73:0xff6d5840)+4;
            if(stage==i+1){y=paragraph(g,ch.scene(),y,0xff5f5b50)+5;y=paragraph(g,ch.goal(),y,0xff274655)+4;
                if(story.id().equals("journey")&&stage==1)y=paragraph(g,"已记录 "+r.getList("Biomes",8).size()+" / 3 种群系："+r.getList("Biomes",8).stream().map(t->t.getAsString()).reduce((a,b)->a+"、"+b).orElse("等你启程"),y,0xff387573)+4;
                if((story.id().equals("journey")||story.id().equals("supper"))&&stage==3)y=paragraph(g,"已交流 "+r.getList("Guests",8).size()+" / "+(story.id().equals("supper")?2:3)+" 位邻居（23、31、37号住宅）。",y,0xff387573)+4;
                if(story.id().equals("home")&&stage==2){int f=r.getInt("Furnishing");y=paragraph(g,"房屋检查：床 "+((f&1)!=0?"✓":"待放置")+" · 灯 "+((f&2)!=0?"✓":"待点亮")+" · 家具 "+((f&4)!=0?"✓":"待放置"),y,0xff387573)+4;}
                if(story.id().equals("home")&&stage==3)y=paragraph(g,r.getBoolean("Rested")?"自家过夜记录已保存，可以归档。":"等你在自家的床睡到天亮。",y,0xff387573)+4;
                y=paragraph(g,"本章一次奖励："+ch.coins()+"铜、"+ch.xp()+"冒险经验。",y,0xff9a7040)+8;
            }else if(stage==0)y=paragraph(g,ch.goal(),y,0xff807665)+8;
            else y+=8;
        }
        y=paragraph(g,stage>=4?"纪念已留下："+story.keepsake()+"。朝夕商行已解锁专属家具。":"完成整段故事：盖章纪念页 + 朝夕商行专属家具。没有期限，可并行进行。",y+4,0xff735833);
        g.pose().popPose();g.disableScissor();limit=Math.max(0,y-end+6);scroll=Math.min(scroll,limit);
        if(limit>0){int track=end-start,thumb=Math.max(12,track*track/(track+limit));g.fill(left+w-9,start,left+w-7,end,0xffd9cbb6);int sy=start+(track-thumb)*scroll/Math.max(1,limit);g.fill(left+w-9,sy,left+w-7,sy+thumb,0xff6f9b94);}
        g.drawString(font,font.plainSubstrByWidth(notice,w-32),left+16,top+h-31,0xff9a552e,false);super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll=Math.max(0,Math.min(limit,scroll-(int)(delta*25)));return true;}
    @Override public void tick(){if(++ticks%40==0)AdventurePackets.send(AdventurePackets.Action.REQUEST,"");}
    @Override public boolean isPauseScreen(){return false;}
}
