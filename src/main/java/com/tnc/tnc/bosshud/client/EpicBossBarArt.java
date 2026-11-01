package com.tnc.tnc.bosshud.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.bosshud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/** Layered, pixel-aligned GUI geometry: thirteen silhouettes, no giant images or per-frame textures. */
public final class EpicBossBarArt {
    private EpicBossBarArt(){}
    public static void draw(GuiGraphics g,BossHudTheme theme,Component name,BossHudNetwork.Snapshot data,
                            int x,int y,int width,boolean compact,float progress,float ghost,double time){
        // Keep the volcanic tortoise's original art intact, including its proportions and animation.
        if(theme!=BossHudTheme.TORTOISE){BossBarEngraving.draw(g,theme,name,data,x,y,width,compact,progress,ghost,time);return;}
        var mc=Minecraft.getInstance();var font=mc.font;int h=compact?8:10,barY=y+(compact?17:24);
        int accent=theme.accent,light=theme.light,shade=theme.shade;
        if(theme==BossHudTheme.CANGLAN&&data.phase()==2){accent=0x83D9EE;light=0xE6FFFF;shade=0x254E75;}
        int glow=progress<.2F?(int)(38+12*Math.sin(time*4)):24;
        poly(g,(glow<<24)|accent,x-15,barY-5,x+width+15,barY-5,x+width+25,barY+h/2f,x+width+15,barY+h+5,x-15,barY+h+5,x-25,barY+h/2f);
        poly(g,0xE5090D14,x-12,barY-3,x+width+12,barY-3,x+width+20,barY+h/2f,x+width+12,barY+h+3,x-12,barY+h+3,x-20,barY+h/2f);
        g.fill(x,barY,width+x,barY+h,0xFF05080E);g.fill(x-1,barY-2,x+width+1,barY-1,0xFF000000|shade);g.fill(x-1,barY+h+1,x+width+1,barY+h+2,0xFF000000|shade);
        g.fill(x,barY-1,x+width,barY,0xFF000000|accent);g.fill(x,barY+h,x+width,barY+h+1,0xFF000000|accent);
        for(int i=0;i<width;i+=7)g.fill(x+i,barY+2,x+i+2,barY+h-1,0x183B4E5B);
        int inner=width-2,pixels=Math.max(0,Math.min(inner,Math.round(inner*progress))),trail=Math.max(pixels,Math.min(inner,Math.round(inner*ghost)));
        if(trail>pixels)g.fill(x+1+pixels,barY+1,x+1+trail,barY+h-1,0xA0000000|(theme.ink()?0xB1B4BB:0xE2B47E));
        if(pixels>0){
            g.fillGradient(x+1,barY+1,x+1+pixels,barY+h-1,0xFF000000|BossHudRules.mix(accent,light,.38),0xFF000000|BossHudRules.mix(accent,shade,.55));
            g.fill(x+1,barY+1,x+1+pixels,barY+2,0xD8000000|light);
            g.enableScissor(x+1,barY+1,x+1+pixels,barY+h-1);
            pattern(g,theme,x,barY,inner,h,accent,light,shade,time);
            int glint=x+(int)((time*24)%Math.max(1,width+80))-40;
            poly(g,0x32FFFFFF,glint,barY+1,glint+3,barY+1,glint+16,barY+h-1,glint+10,barY+h-1);
            g.disableScissor();
            g.fill(x+pixels,barY+1,x+pixels+1,barY+h-1,0xEA000000|light);
        }
        frame(g,theme,x,barY,width,h,accent,light,shade,time);
        for(int i=1;i<10;i++){int at=x+Math.round(width*i/10F);g.fill(at,barY,at+1,barY+2,0xA20A111A);g.fill(at,barY+h-2,at+1,barY+h,0x8A0A111A);}
        emblem(g,theme,x-22,barY+h/2f,accent,light,shade,time);
        end(g,theme,x+width+18,barY+h/2f,accent,light,shade,time);
        String title=font.plainSubstrByWidth(name.getString(),Math.max(32,width-70));
        g.drawCenteredString(font,title,x+width/2,y,0xFFF3F2E8);
        String percent=Math.round(progress*100)+"%";g.drawString(font,percent,x+width-font.width(percent),y,0xFF000000|light,true);
        if(!compact){boolean zh=mc.options.languageCode.startsWith("zh");small(g,zh?theme.subtitleZh:theme.subtitleEn,x+width/2f,y+12,0xCF000000|accent,.65F,true);}
        int infoY=barY+h+(compact?5:7),total=Math.max(1,data.phases()),current=BossHudRules.phase(data.phase(),total);
        for(int i=0;i<total;i++)diamond(g,x+i*9+3,infoY+3,2,0xFF000000|(i<current?light:shade));
        String phase=BossHudRules.roman(current)+" / "+BossHudRules.roman(total);
        small(g,phase,x+total*9+5,infoY,0xD0000000|accent,.75F,false);
        String hp=BossHudRules.amount(data.health())+" / "+BossHudRules.amount(data.maximum());
        small(g,hp,x+width,infoY,0xDDE2E6E9,.75F,false,true);
    }
    private static void small(GuiGraphics g,String s,float x,float y,int color,float scale,boolean centered){small(g,s,x,y,color,scale,centered,false);}
    private static void small(GuiGraphics g,String s,float x,float y,int color,float scale,boolean centered,boolean right){
        var font=Minecraft.getInstance().font;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);int offset=centered?-font.width(s)/2:right?-font.width(s):0;g.drawString(font,s,offset,0,color,true);g.pose().popPose();
    }
    private static void pattern(GuiGraphics g,BossHudTheme t,int x,int y,int w,int h,int a,int l,int s,double time){
        switch(t){
            case APOLLO -> {for(int i=0;i<w;i+=22){line(g,x+i,y+h,x+i+9,y+1,.7F,0x77000000|l);diamond(g,x+i+14,y+h/2F,1,0x9AFFFFFF);}}
            case DARK_GIANT,YAN -> {for(int i=0;i<w;i+=18){line(g,x+i,y+2,x+i+6,y+h-2,1,0x7A000000|s);line(g,x+i+6,y+h-2,x+i+10,y+3,.7F,0x80000000|l);}}
            case STONE_LANCER -> {for(int i=0;i<w;i+=21){path(g,0xA0000000|s,1,x+i,y+h-1,x+i+5,y+4,x+i+3,y+1);g.fill(x+i+11,y+3,x+i+16,y+4,0x78000000|l);}}
            case LEVIATHAN -> {for(int i=0;i<w;i++){float yy=(float)(Math.sin(i*.10-time*2)*1.4);g.fill(x+i,y+h/2+(int)yy,x+i+1,y+h/2+(int)yy+1,0x90000000|l);}}
            case TORTOISE -> {for(int i=0;i<w;i+=17){path(g,0xDB000000|s,1.4F,x+i,y+1,x+i+4,y+4,x+i+1,y+h-1);g.fill(x+i+9,y+2,x+i+12,y+3,0xB8FFF1B0);}}
            case WIND_GENERAL,WIND_WARDEN,CANGLAN -> {for(int i=0;i<w;i+=26){curve(g,0x77000000|l,.7F,x+i,y+h-2,x+i+8,y+2,x+i+15,y+h,x+i+23,y+2);}}
            case INK_DRAKE,INK_CRANE,INK_TIGER,INK_DRAGON -> {for(int i=0;i<w;i+=17){int dy=(i*7)%Math.max(2,h-3);g.fill(x+i,y+2+dy,x+i+7,y+3+dy,0x960E1218);line(g,x+i+3,y+1,x+i+10,y+h-1,.7F,0x60000000|l);}}
        }
    }
    private static void frame(GuiGraphics g,BossHudTheme t,int x,int y,int w,int h,int a,int l,int s,double time){
        int c=0xE0000000|a,hi=0xED000000|l,low=0xFF000000|s;
        switch(t){
            case APOLLO -> {
                for(int i=0;i<w;i+=26){tri(g,low,x+i,y-2,x+i+5,y-6,x+i+10,y-2);line(g,x+i+2,y-3,x+i+5,y-5,.7F,hi);}
                line(g,x+7,y+h+3,x+w-7,y+h+3,.7F,c);for(int i=20;i<w;i+=40)diamond(g,x+i,y+h+3,1.6F,hi);
                wing(g,x-3,y+h/2F,-1,hi,low,.7F);wing(g,x+w+3,y+h/2F,1,hi,low,.7F);
            }
            case DARK_GIANT -> {for(int i=0;i<w;i+=13){outlineDiamond(g,x+i+6,y+h+4,4,2.4F,c);line(g,x+i+9,y+h+3,x+i+16,y+h+3,.8F,low);}for(int side:new int[]{-1,1})tri(g,low,side<0?x:x+w,y-3,side<0?x-12:x+w+12,y-13,side<0?x+8:x+w-8,y-2);}
            case STONE_LANCER -> {for(int i=0;i<w;i+=28){poly(g,low,x+i,y-2,x+i+5,y-7,x+i+18,y-5,x+i+23,y-2);path(g,hi,.8F,x+i+3,y-3,x+i+7,y-5,x+i+15,y-4);tri(g,low,x+i+8,y+h+2,x+i+16,y+h+7,x+i+22,y+h+2);}}
            case LEVIATHAN -> {for(int i=0;i<w;i+=35){curve(g,c,1,x+i,y+h+4,x+i+8,y+h+9,x+i+13,y+h-3,x+i+30,y+h+3);tri(g,0xC6D8B960,x+i+4,y-2,x+i+12,y-7,x+i+15,y-2);}line(g,x,y+h+2,x+w,y+h+2,.7F,0xA2F5CF84);}
            case TORTOISE -> {for(int i=0;i<w;i+=24){poly(g,low,x+i,y-2,x+i+5,y-6,x+i+13,y-5,x+i+19,y-1);path(g,0xFFFFA84F,1,x+i+4,y-2,x+i+7,y-4,x+i+10,y-3);tri(g,low,x+i+4,y+h+2,x+i+10,y+h+7,x+i+16,y+h+2);}}
            case YAN -> {for(int i=10;i<w;i+=25){path(g,c,.8F,x+i,y-2,x+i,y-7,x+i+5,y-4,x+i+8,y-7);line(g,x+i+5,y-4,x+i+5,y-1,.8F,hi);diamond(g,x+i,y+h+4,1.3F,c);}curve(g,low,1.2F,x,y+h+3,x+w/3F,y+h+8,x+w*.7F,y+h-1,x+w,y+h+3);}
            case WIND_GENERAL -> {line(g,x-8,y+h+4,x+w+8,y+h+4,1,c);for(int i=12;i<w;i+=35){path(g,hi,.8F,x+i,y-1,x+i+7,y-5,x+i+14,y-1);}for(int side:new int[]{-1,1}){float at=side<0?x:x+w;poly(g,low,at,y,at+side*24,y-4,at+side*17,y+h/2F,at+side*23,y+h+4,at,y+h);}}
            case WIND_WARDEN -> {for(int i=12;i<w;i+=28){leaf(g,x+i,y+h+2,9,4,-1,c,low);leaf(g,x+i+10,y-2,8,3,1,hi,low);}curve(g,c,.8F,x,y+h+4,x+w*.3F,y+h+9,x+w*.7F,y+h-1,x+w,y+h+4);}
            case CANGLAN -> {line(g,x-10,y+h+4,x+w+8,y+h+4,1,c);line(g,x-10,y-3,x+w+8,y-3,.8F,hi);tri(g,hi,x+w+8,y+h+1,x+w+18,y+h+4,x+w+8,y+h+7);for(int i=18;i<w;i+=32){path(g,c,.8F,x+i,y-3,x+i+3,y-7,x+i+7,y-3);line(g,x+i+3,y+h+2,x+i+7,y+h+6,.8F,c);}}
            case INK_DRAKE -> {curve(g,c,1.5F,x-3,y+h+3,x+w*.2F,y+h+10,x+w*.7F,y+h-3,x+w+6,y+h+5);for(int i=20;i<w;i+=38)path(g,hi,.8F,x+i,y-1,x+i+5,y-5,x+i+12,y-4);}
            case INK_CRANE -> {for(int i=12;i<w;i+=27){leaf(g,x+i,y+h+1,13,3,-1,0xD7B9BEC5,0xFF191D24);line(g,x+i-2,y-2,x+i+12,y-4,.8F,hi);}curve(g,c,1,x-7,y+h,x+w*.25F,y+h+7,x+w*.8F,y+h+3,x+w+9,y+h+1);}
            case INK_TIGER -> {for(int i=18;i<w;i+=57)for(int k=0;k<3;k++)line(g,x+i+k*5,y-5,x+i+k*5-4,y+h+4,1.2F,0xC5070A10);line(g,x-3,y+h+3,x+w+3,y+h+3,1,c);octagon(g,x+w+18,y+h/2F,10,c);}
            case INK_DRAGON -> {for(int i=10;i<w;i+=18)path(g,c,.8F,x+i,y-2,x+i+4,y-6,x+i+11,y-2);for(int i=18;i<w;i+=52){curve(g,c,.8F,x+i,y+h+4,x+i+8,y+h-1,x+i+14,y+h+11,x+i+30,y+h+4);curve(g,hi,.6F,x+i+4,y+h+5,x+i+9,y+h+11,x+i+23,y+h+2,x+i+34,y+h+4);}}
        }
        for(int side:new int[]{-1,1}){float at=side<0?x:x+w;path(g,hi,1,at+side*5,y-1,at+side*13,y+h/2F,at+side*5,y+h);}
    }
    private static void emblem(GuiGraphics g,BossHudTheme t,float x,float y,int a,int l,int s,double time){
        int hi=0xFF000000|l,c=0xFF000000|a,lo=0xFF000000|s;
        diamond(g,x,y,15,0xED070B12);outlineDiamond(g,x,y,15,18,lo);outlineDiamond(g,x,y,13,15,c);
        g.pose().pushPose();g.pose().translate(x,y,0);
        switch(t){
            case APOLLO -> {ring(g,0,0,7,c);for(int i=0;i<12;i++){double r=i*Math.PI/6;line(g,(float)Math.cos(r)*9,(float)Math.sin(r)*9,(float)Math.cos(r)*13,(float)Math.sin(r)*13,1,hi);}diamond(g,0,0,4,hi);wing(g,-2,3,-1,c,lo,.45F);wing(g,2,3,1,c,lo,.45F);}
            case DARK_GIANT -> {helmet(g,c,hi,lo,true);outlineDiamond(g,0,4,3,2,0xD9BD72F6);}
            case STONE_LANCER -> {poly(g,lo,-11,6,-8,-7,1,-12,10,-5,12,6,3,11,-7,10);path(g,c,1,-8,5,-6,-5,1,-8,8,-3,8,5);path(g,hi,.9F,0,-8,-2,0,2,5,0,10);g.fill(-6,1,-3,3,hi);g.fill(3,1,6,3,hi);}
            case LEVIATHAN -> {sea(g,c,hi,lo);}
            case TORTOISE -> {poly(g,lo,-11,-4,-6,-11,6,-11,12,-4,10,7,0,12,-10,6);octagon(g,0,0,9,c);path(g,hi,1,-7,-3,0,-6,7,-3,5,4,0,7,-5,4,-7,-3);path(g,0xFFFFAE48,1.4F,0,-6,0,0,-5,4);line(g,0,0,5,4,1.2F,0xFFFFAE48);}
            case YAN -> {poly(g,lo,-10,-7,0,-12,10,-7,7,7,0,12,-7,7);path(g,c,1,-9,-7,-6,-14,-1,-9,3,-15,8,-8);line(g,-6,-1,-1,2,1.3F,hi);line(g,1,2,6,-1,1.3F,hi);path(g,hi,1,0,1,-3,7,0,9,4,6);}
            case WIND_GENERAL -> {line(g,-9,10,8,-12,1.6F,hi);line(g,9,10,-7,-12,1.6F,c);tri(g,hi,4,-10,11,-13,8,-5);poly(g,lo,-5,-7,7,-6,5,0,8,5,-5,3);path(g,c,.8F,-4,-5,5,-4,3,1,-4,1);}
            case WIND_WARDEN -> {for(int i=-2;i<=2;i++){float xx=i*4;leaf(g,xx,4,11-Math.abs(i),3,1,c,lo);}path(g,hi,1.4F,-5,7,-4,0,0,-5,3,-3,7,-2);tri(g,hi,3,-3,11,-1,5,1);}
            case CANGLAN -> {ring(g,0,-1,10,lo);helmet(g,c,hi,lo,false);line(g,-11,10,10,-12,1,hi);tri(g,hi,6,-10,13,-15,11,-7);}
            case INK_DRAKE -> {curve(g,hi,2,-8,9,-13,-3,5,-9,8,-2);curve(g,c,1.2F,8,-2,13,8,-1,11,-2,3);path(g,hi,1.2F,-1,-6,2,-12,4,-6,8,-8,11,-3,7,-1);g.fill(6,-6,8,-5,0xFF080B10);}
            case INK_CRANE -> {curve(g,hi,2.1F,-5,10,-10,2,6,-2,1,-10);curve(g,c,1.2F,1,-10,-1,-15,6,-15,7,-10);tri(g,hi,6,-12,15,-8,6,-9);path(g,hi,.7F,2,-13,0,-19,4,-16,6,-19);g.fill(4,-12,5,-11,0xFF11151B);}
            case INK_TIGER -> {tiger(g,c,hi,lo);}
            case INK_DRAGON -> {dragon(g,c,hi,lo);}
        }
        g.pose().popPose();
    }
    private static void end(GuiGraphics g,BossHudTheme t,float x,float y,int a,int l,int s,double time){
        int c=0xE8000000|a,hi=0xE9000000|l,lo=0xFF000000|s;
        switch(t){
            case APOLLO -> {diamond(g,x,y,5,lo);diamond(g,x,y,3,hi);line(g,x,y-11,x,y-6,1,c);line(g,x,y+6,x,y+11,1,c);}
            case DARK_GIANT -> {for(int i=0;i<3;i++)outlineDiamond(g,x+(float)Math.sin(time+i*.6),y+i*5-3,3,4,c);}
            case STONE_LANCER -> {poly(g,lo,x-5,y-9,x+5,y-6,x+8,y+2,x+1,y+9,x-6,y+5);path(g,hi,1,x+1,y-6,x-1,y,x+3,y+5);}
            case LEVIATHAN -> {curve(g,c,1.4F,x-5,y+7,x+15,y+7,x+10,y-10,x,y-5);curve(g,hi,.8F,x,y-5,x-7,y-5,x-5,y+2,x+2,y);}
            case TORTOISE -> {tri(g,lo,x-6,y+7,x,y-11,x+7,y+7);path(g,0xFFFFB16C,1.2F,x-2,y+4,x+1,y-3,x+4,y+5);}
            case YAN -> {path(g,c,1,x,y-11,x+6,y-5,x,y+11,x-5,y+4,x,y-11);line(g,x-2,y-3,x+3,y+2,1,hi);}
            case WIND_GENERAL -> {poly(g,lo,x-2,y-7,x+12,y-5,x+7,y,x+12,y+6,x-2,y+5);line(g,x-2,y-10,x-2,y+12,1,hi);}
            case WIND_WARDEN -> {leaf(g,x,y,12,4,1,c,lo);leaf(g,x+4,y+2,10,3,-1,hi,lo);}
            case CANGLAN -> {for(int i=0;i<3;i++)leaf(g,x+i*3,y-2+i*4,13-i*2,2,-1,hi,lo);}
            case INK_DRAKE -> {curve(g,c,1.4F,x-7,y+4,x+13,y+13,x+13,y-9,x+2,y-6);}
            case INK_CRANE -> {for(int i=0;i<3;i++)leaf(g,x+i*3,y+i*4,13-i*2,2,-1,hi,lo);}
            case INK_TIGER -> {octagon(g,x,y,9,c);for(int i=-1;i<=1;i++)line(g,x+i*4+2,y-6,x+i*4-2,y+6,1,hi);}
            case INK_DRAGON -> {ring(g,x,y,6,c);diamond(g,x,y,3,hi);curve(g,c,.8F,x-7,y+2,x-14,y-8,x+7,y-12,x+10,y-3);}
        }
    }
    private static void helmet(GuiGraphics g,int c,int hi,int lo,boolean horned){
        poly(g,lo,-8,-6,0,-12,8,-6,7,6,0,12,-7,6);path(g,c,1,-7,-5,0,-9,7,-5,5,6,0,9,-5,6,-7,-5);
        line(g,-6,-1,-1,1,1.3F,hi);line(g,1,1,6,-1,1.3F,hi);line(g,0,-7,0,8,1,c);
        if(horned){path(g,hi,1.8F,-6,-5,-12,-11,-10,-17);path(g,hi,1.8F,6,-5,12,-11,10,-17);}else{tri(g,hi,-4,-9,0,-18,4,-9);}
    }
    private static void sea(GuiGraphics g,int c,int hi,int lo){
        poly(g,lo,-10,-3,-5,-9,6,-7,12,-1,7,7,-4,9,-10,3);path(g,hi,1,-6,-6,-9,-13,-2,-9,3,-12,5,-7);
        line(g,-6,-1,-1,1,1.2F,c);line(g,3,0,8,-2,1.2F,c);for(int i=0;i<3;i++)tri(g,hi,-4+i*4,4,-2+i*4,9,i*4,4);
        curve(g,c,.9F,-10,0,-17,1,-15,10,-7,10);
    }
    private static void tiger(GuiGraphics g,int c,int hi,int lo){
        poly(g,lo,-10,-7,-5,-12,0,-9,6,-12,11,-6,8,7,0,12,-8,7);
        path(g,hi,1.3F,-9,-6,-5,-9,-1,-5,3,-5,7,-9,10,-5);line(g,-7,-1,-2,1,1.6F,hi);line(g,2,1,7,-1,1.6F,hi);
        tri(g,c,-3,3,3,3,0,6);path(g,hi,1,-5,5,-4,10,0,8,4,10,5,5);
        for(int i=-1;i<=1;i++)path(g,c,1,i*4,-7,i*3,-3,i*2,-1);
        tri(g,hi,-6,5,-5,11,-3,6);tri(g,hi,6,5,5,11,3,6);
    }
    private static void dragon(GuiGraphics g,int c,int hi,int lo){
        poly(g,lo,-10,-3,-5,-9,6,-7,12,0,5,6,0,12,-8,7);path(g,hi,1,-8,-5,-6,-12,-12,-17,-7,-15,-4,-9);
        path(g,hi,1,3,-7,8,-14,6,-18,11,-15,7,-7);line(g,-5,-1,0,1,1.4F,hi);tri(g,c,2,0,13,1,4,5);
        path(g,hi,.8F,-3,7,-7,13,-5,18);curve(g,hi,.8F,5,4,20,0,17,13,8,10);curve(g,c,.8F,6,5,16,10,6,17,0,14);
        for(int i=0;i<3;i++)line(g,-9-i*2,1+i*4,-13-i*2,4+i*4,.8F,c);
    }
    private static void wing(GuiGraphics g,float x,float y,int sign,int hi,int lo,float scale){
        for(int i=0;i<5;i++){float reach=(20-i*2)*scale;poly(g,lo,x,y,x+sign*reach,y-(12-i*4)*scale,x+sign*(reach-3),y-(7-i*4)*scale,x+sign*4*scale,y+4*scale);line(g,x+sign*3*scale,y,x+sign*(reach-2),y-(10-i*4)*scale,.6F,hi);}
    }
    private static void leaf(GuiGraphics g,float x,float y,float length,float half,int sign,int color,int dark){poly(g,dark,x-length/2,y,x+length/2,y-sign*half,x+length/3,y+sign*half);line(g,x-length/2,y,x+length/2,y-sign*half,.8F,color);}
    private static void diamond(GuiGraphics g,float x,float y,float r,int color){poly(g,color,x,y-r,x+r,y,x,y+r,x-r,y);}
    private static void outlineDiamond(GuiGraphics g,float x,float y,float rx,float ry,int color){path(g,color,.8F,x-rx,y,x,y-ry,x+rx,y,x,y+ry,x-rx,y);}
    private static void octagon(GuiGraphics g,float x,float y,float r,int color){float[] p=new float[18];for(int i=0;i<=8;i++){double a=Math.PI/8+i*Math.PI/4;p[i*2]=x+(float)Math.cos(a)*r;p[i*2+1]=y+(float)Math.sin(a)*r;}path(g,color,.8F,p);}
    private static void ring(GuiGraphics g,float x,float y,float r,int color){float[] p=new float[50];for(int i=0;i<=24;i++){double a=i*Math.PI/12;p[i*2]=x+(float)Math.cos(a)*r;p[i*2+1]=y+(float)Math.sin(a)*r;}path(g,color,.8F,p);}
    private static void curve(GuiGraphics g,int c,float width,float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3){float px=x0,py=y0;for(int i=1;i<=12;i++){float t=i/12F,u=1-t;float x=u*u*u*x0+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3,y=u*u*u*y0+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3;line(g,px,py,x,y,width,c);px=x;py=y;}}
    private static void path(GuiGraphics g,int color,float width,float... xy){for(int i=2;i<xy.length;i+=2)line(g,xy[i-2],xy[i-1],xy[i],xy[i+1],width,color);}
    private static void line(GuiGraphics g,float ax,float ay,float bx,float by,float width,int color){float dx=bx-ax,dy=by-ay,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.001)return;float nx=-dy/len*width/2,ny=dx/len*width/2;quad(g,color,ax+nx,ay+ny,bx+nx,by+ny,bx-nx,by-ny,ax-nx,ay-ny);}
    private static void tri(GuiGraphics g,int color,float ax,float ay,float bx,float by,float cx,float cy){quad(g,color,ax,ay,bx,by,cx,cy,cx,cy);}
    private static void poly(GuiGraphics g,int color,float... p){for(int i=2;i<p.length-2;i+=2)tri(g,color,p[0],p[1],p[i],p[i+1],p[i+2],p[i+3]);}
    private static void quad(GuiGraphics g,int color,float... p){
        var matrix=g.pose().last().pose();VertexConsumer out=g.bufferSource().getBuffer(RenderType.gui());float area=0;
        for(int i=0;i<8;i+=2){int n=(i+2)%8;area+=p[i]*p[n+1]-p[n]*p[i+1];}
        for(int k=0;k<4;k++){int i=area>0?6-k*2:k*2;out.vertex(matrix,p[i],p[i+1],0).color((color>>16)&255,(color>>8)&255,color&255,(color>>>24)&255).endVertex();}
    }
}
