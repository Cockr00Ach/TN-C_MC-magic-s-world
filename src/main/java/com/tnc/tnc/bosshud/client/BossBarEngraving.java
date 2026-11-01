package com.tnc.tnc.bosshud.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.bosshud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/** Fine intaglio and brushwork. Ornament lives at the terminals; the long reading area stays quiet. */
final class BossBarEngraving {
    private BossBarEngraving(){}
    private static final int INK=0xFF101318, PAPER=0xFFE0DED5;

    static void draw(GuiGraphics g,BossHudTheme t,Component name,BossHudNetwork.Snapshot data,
                     int x,int y,int w,boolean compact,float progress,float ghost,double time){
        int a=t.accent,l=t.light,s=t.shade;
        if(t==BossHudTheme.CANGLAN&&data.phase()==2){a=0x86B9CA;l=0xDEF0ED;s=0x253E51;}
        int by=y+(compact?18:25),h=compact?5:7;
        int metal=argb(255,BossHudRules.mix(a,0x9B9180,.34)),edge=argb(255,BossHudRules.mix(l,0xAAA69C,.34));
        int shadow=argb(255,BossHudRules.mix(s,0x12171D,.55));
        BossBarMaterials.draw(g,t,data.phase(),x,by,w,h,progress,ghost,time);
        g.pose().pushPose();g.pose().translate(x-20,by+h/2F,0);
        BossHudPortraits.draw(g,t);
        g.pose().popPose();

        var font=Minecraft.getInstance().font;
        String title=font.plainSubstrByWidth(name.getString(),Math.max(24,w-66));
        g.drawCenteredString(font,title,x+w/2,y,0xFFE9E4D9);
        String percent=Math.round(progress*100)+"%";
        text(g,percent,x+w,y+1,.75F,0xC5C5C5BF,1);
        if(!compact){
            boolean zh=Minecraft.getInstance().options.languageCode.startsWith("zh");
            text(g,zh?t.subtitleZh:t.subtitleEn,x+w/2F,y+13,.6F,argb(155,BossHudRules.mix(a,0xC1B8A2,.45)),0);
        }
        int iy=by+h+17,total=Math.max(1,data.phases()),phase=BossHudRules.phase(data.phase(),total);
        // Phase markers resemble small engraved notches, with no repeated gemstone badges.
        for(int i=0;i<total;i++){
            float px=x+i*7;int col=i<phase?edge:shadow;
            line(g,px,iy+1,px+3,iy+1,.8F,col);line(g,px,iy+1,px,iy+3,.6F,col);
        }
        text(g,BossHudRules.roman(phase)+" / "+BossHudRules.roman(total),x+total*7+5,iy,.65F,argb(165,a),-1);
        text(g,BossHudRules.amount(data.health())+" / "+BossHudRules.amount(data.maximum()),x+w,iy,.65F,0xC4CBC9C2,1);
    }

    private static void inlay(GuiGraphics g,BossHudTheme t,int x,int y,int w,int h,int l,int s,double time){
        int c=argb(40,l),dark=argb(60,s);
        switch(t){
            case APOLLO -> {for(int i=8;i<w;i+=34){line(g,x+i,y+h,x+i+8,y,.45F,c);line(g,x+i+2,y+h,x+i+10,y,.35F,c);}}
            case DARK_GIANT -> {for(int i=10;i<w;i+=39){path(g,dark,.6F,x+i,y,x+i+3,y+3,x+i+2,y+h);line(g,x+i+4,y+3,x+i+8,y+2,.4F,c);}}
            case STONE_LANCER -> {for(int i=13;i<w;i+=43)path(g,dark,.6F,x+i,y+h,x+i+6,y+3,x+i+4,y,x+i+10,y+2);}
            case LEVIATHAN -> {bezier(g,c,.45F,x,y+h*.65F,x+w*.3F,y-1,x+w*.6F,y+h+1,x+w,y+2);bezier(g,c,.4F,x+12,y+h,x+w*.4F,y,x+w*.7F,y+h,x+w,y+1);}
            case YAN -> {for(int i=15;i<w;i+=51){line(g,x+i,y,x+i+3,y+h,.5F,dark);line(g,x+i+3,y+h,x+i+6,y+2,.4F,c);}}
            case WIND_GENERAL,WIND_WARDEN,CANGLAN -> {
                bezier(g,c,.45F,x,y+h,x+w*.25F,y-1,x+w*.48F,y+h+3,x+w*.7F,y+1);
                bezier(g,c,.4F,x+w*.4F,y+h,x+w*.6F,y-2,x+w*.85F,y+h+2,x+w,y+1);
            }
            case INK_DRAKE,INK_CRANE,INK_TIGER,INK_DRAGON -> {
                for(int i=0;i<w;i+=3){int n=hash(i*31+t.ordinal()*67);float yy=y+1+(n&15)/15F*Math.max(1,h-2);
                    line(g,x+i,yy,x+i+1+(n>>>5&7),yy-.4F,.35F,argb(20+(n>>>10&31),0x101418));}
                bezier(g,0x19101519,.7F,x,y+h,x+w*.2F,y-2,x+w*.6F,y+h+1,x+w,y+1);
            }
            default -> {}
        }
    }

    private static void terminals(GuiGraphics g,BossHudTheme t,int x,int y,int w,int h,int c,int hi,int lo){
        // Mirror just the terminal mount; individual subjects below are deliberately asymmetric.
        for(int side=0;side<2;side++){
            g.pose().pushPose();g.pose().translate(side==0?x:x+w,y+h/2F,0);g.pose().scale(side==0?1:-1,1,1);
            float hh=h/2F;
            path(g,c,.7F,0,-hh-2,-5,-hh-2,-8,0,-5,hh+2,0,hh+2);
            path(g,lo,.6F,0,-hh-3,-5,-hh-3,-10,0,-5,hh+3,0,hh+3);
            switch(t){
                case APOLLO -> {
                    scroll(g,-1,-hh-3,1,c,hi);scroll(g,1,hh+3,-1,c,hi);
                    for(int k=0;k<3;k++)feather(g,5+k*5,-hh-3-k*.5F,12-k*2,-4-k*.5F,c,hi);
                    line(g,4,hh+4,43,hh+4,.45F,lo);dot(g,44,hh+4,.8F,c);
                }
                case DARK_GIANT -> {
                    path(g,lo,1.4F,0,-hh-4,8,-hh-5,14,-hh-10,19,-hh-8,22,-hh-4,32,-hh-4);
                    path(g,c,.45F,0,-hh-5,8,-hh-6,14,-hh-11,19,-hh-9,23,-hh-5,33,-hh-5);
                    for(int k=0;k<3;k++)ellipse(g,8+k*6,hh+5+k*.25F,3,1.5F,0,Math.PI*2,.6F,c);
                    line(g,23,hh+6,34,hh+4,.5F,lo);
                }
                case STONE_LANCER -> {
                    path(g,c,.7F,0,-hh-3,8,-hh-3,11,-hh-6,21,-hh-6,24,-hh-3,42,-hh-3);
                    path(g,lo,1.5F,1,hh+4,13,hh+4,17,hh+6,28,hh+6,33,hh+4,44,hh+4);
                    path(g,hi,.45F,11,-hh-5,14,-hh-5,16,-hh-4,20,-hh-5);
                    for(int k=0;k<3;k++)line(g,29+k*3,-hh-4,29+k*3,-hh-6,.45F,lo);
                }
                case LEVIATHAN -> {
                    wave(g,1,hh+4,1,c,hi);wave(g,15,hh+5,.7F,lo,c);
                    bezier(g,c,.55F,0,-hh-4,15,-hh-11,21,-hh+1,43,-hh-4);
                    for(int k=0;k<3;k++)ellipse(g,8+k*7,-hh-4,4,2.4F,Math.PI,Math.PI*2,.45F,hi);
                }
                case YAN -> {
                    bezier(g,lo,1.3F,0,-hh-3,17,-hh-12,20,-hh+2,39,-hh-5);
                    bezier(g,c,.5F,0,-hh-4,15,-hh-13,24,-hh+1,42,-hh-6);
                    for(int k=0;k<3;k++){
                        float q=9+k*9;bezier(g,c,.55F,q,hh+4,q-4,hh+8,q+5,hh+7,q+2,hh+11);
                    }
                }
                case WIND_GENERAL -> {
                    path(g,c,.6F,0,-hh-4,13,-hh-4,18,-hh-7,29,-hh-7,32,-hh-4,46,-hh-4);
                    bezier(g,lo,1.4F,0,hh+4,12,hh+1,20,hh+9,42,hh+5);
                    bezier(g,hi,.45F,1,hh+3,13,hh,21,hh+8,40,hh+4);
                    for(int k=0;k<4;k++)line(g,21+k*2,-hh-6,22+k*2,-hh-4,.45F,c);
                }
                case WIND_WARDEN -> {
                    bezier(g,c,.6F,0,hh+4,12,hh+3,22,hh+6,46,hh+1);
                    for(int k=0;k<3;k++)feather(g,5+k*10,hh+4,16-k*3,5-k,c,hi);
                    bezier(g,lo,.6F,0,-hh-4,14,-hh-9,27,-hh-2,44,-hh-5);
                }
                case CANGLAN -> {
                    line(g,0,-hh-4,47,-hh-4,.65F,c);line(g,2,-hh-5,24,-hh-5,.4F,hi);
                    for(int k=0;k<2;k++)path(g,lo,.7F,8+k*15,hh+3,14+k*15,hh+6,23+k*15,hh+4,29+k*15,hh+5);
                    path(g,hi,.45F,5,hh+3,14,hh+5,23,hh+3);
                    line(g,35,-hh-6,39,-hh-6,.55F,hi);line(g,35,-hh-7,39,-hh-7,.35F,lo);
                }
                case INK_DRAKE -> {
                    brush(g,0,hh+4,13,hh+10,28,hh,47,hh+5,1.1F,c);
                    bezier(g,hi,.35F,4,hh+5,17,hh+8,28,hh+3,40,hh+5);
                    brush(g,0,-hh-3,12,-hh-8,23,-hh,37,-hh-5,.7F,lo);
                }
                case INK_CRANE -> {
                    feather(g,0,hh+3,37,5,c,hi);feather(g,2,hh+3,29,8,lo,c);
                    brush(g,0,-hh-4,17,-hh-3,29,-hh-8,48,-hh-5,.7F,c);
                }
                case INK_TIGER -> {
                    // Straight lattice corners recall the tiger's spell window, without a fence across the bar.
                    path(g,c,.6F,0,-hh-4,9,-hh-4,9,-hh-8,18,-hh-8,18,-hh-4,34,-hh-4);
                    path(g,lo,.5F,3,hh+4,15,hh+4,15,hh+8,23,hh+8,23,hh+4,40,hh+4);
                    for(int k=0;k<3;k++)brush(g,24+k*5,-hh-5,21+k*5,-hh-2,22+k*5,-hh-1,20+k*5,hh+4,.8F,c);
                }
                case INK_DRAGON -> {
                    cloud(g,0,hh+4,1,c,hi);cloud(g,13,-hh-5,.8F,lo,c);
                    bezier(g,c,.4F,30,hh+5,41,hh+5,45,hh+1,57,hh+3);
                }
                default -> {}
            }
            g.pose().popPose();
        }
    }

    private static void portrait(GuiGraphics g,BossHudTheme t,int c,int hi,int lo){
        // Individual silhouettes, not a common badge with interchangeable center icons.
        switch(t){
            case APOLLO -> {
                ellipseFill(g,0,0,10,12,0xE4141417);ellipse(g,0,0,10,12,0,Math.PI*2,.55F,c);
                ellipse(g,0,0,8.5F,10.5F,0,Math.PI*2,.4F,lo);
                for(int i=0;i<16;i++){double a=i*Math.PI/8;float r=i%2==0?15:13;
                    line(g,(float)Math.cos(a)*11,(float)Math.sin(a)*13,(float)Math.cos(a)*r,(float)Math.sin(a)*(r+1),.5F,i%2==0?hi:c);}
                ellipse(g,0,0,5,5,0,Math.PI*2,.7F,c);ellipseFill(g,0,0,3,3,hi);
                for(int side:new int[]{-1,1}){g.pose().pushPose();g.pose().scale(side,1,1);
                    for(int k=0;k<4;k++)feather(g,5,3+k*.7F,12-k*2,-8+k*4,c,hi);
                    scroll(g,0,9,1,c,hi);g.pose().popPose();}
            }
            case DARK_GIANT -> {
                polygon(g,lo,3,-9,8,-8,10,0,8,8,3,12,-3,12,-8,8,-10,0,-8,-8,3,-9);
                for(int side:new int[]{-1,1}){g.pose().pushPose();g.pose().scale(side,1,1);
                    brush(g,6,-6,17,-8,15,-18,11,-18,2.4F,c);
                    bezier(g,hi,.45F,8,-7,17,-12,12,-16,12,-18);
                    path(g,c,.7F,1,-9,7,-7,8,0,5,7,0,11);
                    path(g,hi,.65F,2,-2,7,-4,5,0);line(g,3,-1,6,-2,1.1F,0xFFD4B4E3);
                    for(int k=0;k<3;k++)line(g,3+k*2,4,2+k*2,7,.45F,c);
                    g.pose().popPose();}
                path(g,c,1,0,-12,-2,-5,0,7,2,-5,0,-12);line(g,0,-7,0,3,.5F,hi);
                chain(g,-8,11,4,c);chain(g,7,11,3,lo);
            }
            case STONE_LANCER -> {
                polygon(g,lo,-11,8,-12,-2,-7,-12,2,-14,11,-8,13,4,5,13,-5,13);
                path(g,c,.7F,-10,6,-10,-2,-5,-10,2,-12,9,-7,10,3,5,10,-4,11);
                path(g,hi,.6F,-8,-3,-1,-6,7,-4);path(g,c,.5F,-1,-11,-3,-5,0,0,-2,4,1,10);
                path(g,lo,1.8F,-8,0,-3,1,-4,3);path(g,lo,1.8F,3,1,8,-1,7,2);
                line(g,-8,0,-4,0,.7F,hi);line(g,4,0,8,-1,.7F,hi);
                path(g,c,.6F,-7,7,-4,6,0,8,5,5);line(g,-5,9,3,10,.4F,hi);
                for(int k=0;k<4;k++)line(g,7-k*2,-8,8-k*2,-5,.4F,c);
                spear(g,12,1,lo,c);dot(g,-9,-6,.7F,hi);dot(g,6,7,.6F,hi);
            }
            case LEVIATHAN -> {
                brush(g,-9,10,-20,-1,-6,-14,6,-5,4,lo);
                bezier(g,c,.65F,-10,9,-18,-2,-6,-12,5,-6);
                path(g,c,.8F,-7,-5,-8,-14,-3,-10,1,-16,4,-10,9,-12,9,-5);
                polygon(g,lo,-8,-3,-3,-8,6,-7,12,-2,8,6,1,10,-7,6);
                bezier(g,hi,.65F,-8,-2,-2,-8,5,-5,11,-2);
                path(g,c,.6F,-8,-1,-5,3,-1,5,7,2,10,0);
                line(g,-4,-2,0,0,.9F,hi);line(g,4,0,8,-3,.9F,hi);
                for(int i=0;i<3;i++){polygon(g,hi,-4+i*4,4,-2+i*4,8,-1+i*4,4);}
                for(int i=0;i<3;i++)ellipse(g,-11+i*3,3-i*4,2.8F,2,0,Math.PI,.5F,c);
                wave(g,-6,11,.65F,c,hi);
            }
            case YAN -> {
                polygon(g,lo,-9,-7,-7,6,0,12,7,6,9,-7,2,-11,-4,-10);
                path(g,c,.7F,-9,-7,-11,-13,-5,-10,-3,-16,1,-10,7,-14,7,-9,10,-10);
                bezier(g,hi,.5F,-8,-6,-4,-11,3,-8,8,-6);
                for(int side:new int[]{-1,1}){g.pose().pushPose();g.pose().scale(side,1,1);
                    path(g,c,.6F,1,-7,7,-5,6,4,2,9);line(g,2,-1,6,-2,.9F,hi);
                    bezier(g,c,.5F,7,1,14,-1,7,11,13,13);g.pose().popPose();}
                path(g,hi,.5F,0,-7,-2,-3,1,2,-2,6,0,10);dot(g,0,6,.65F,c);
                bezier(g,lo,1,-7,11,-16,5,-15,16,-5,14);
            }
            case WIND_GENERAL -> {
                spear(g,-6,0,lo,hi);
                polygon(g,lo,-3,-10,9,-8,7,-3,12,0,5,2,8,6,-3,4);
                bezier(g,c,.8F,-2,-9,3,-11,5,-5,9,-7);
                bezier(g,hi,.45F,-1,-7,3,-8,4,-3,8,-4);
                bezier(g,c,.6F,-1,0,2,-2,4,3,8,3);
                line(g,-3,-10,-3,10,.7F,c);line(g,-6,9,8,13,.7F,lo);
                bezier(g,c,.5F,-2,9,5,5,13,16,16,9);
                for(int k=0;k<4;k++)line(g,1+k*1.5F,-5,1+k*1.5F,-3,.4F,hi);
            }
            case WIND_WARDEN -> {
                for(int k=0;k<5;k++){
                    float xx=-8+k*4;feather(g,-2,8,xx+4,-21+Math.abs(k-2)*3,k%2==0?c:lo,hi);
                }
                brush(g,-6,9,-8,2,1,-8,5,-4,2.7F,lo);
                bezier(g,hi,.65F,-6,9,-8,0,1,-6,5,-4);
                polygon(g,c,1,-5,5,-6,11,-3,5,-1,1,1);
                line(g,5,-4,13,-2,.6F,hi);dot(g,4,-4,.8F,INK);
                bezier(g,c,.65F,-5,8,4,5,3,12,12,8);
            }
            case CANGLAN -> {
                ellipse(g,0,-1,11,13,Math.PI*.85,Math.PI*2.15,.45F,lo);
                polygon(g,lo,-8,-6,-6,5,0,12,6,5,8,-6,0,-11);
                for(int side:new int[]{-1,1}){g.pose().pushPose();g.pose().scale(side,1,1);
                    path(g,c,.8F,0,-10,7,-5,5,5,0,10);path(g,hi,.45F,1,-6,6,-4,4,0);
                    line(g,1,-1,5,-2,.8F,hi);path(g,c,.6F,2,4,4,6,1,9);
                    g.pose().popPose();}
                brush(g,0,-8,-4,-17,8,-19,7,-10,2.2F,c);bezier(g,hi,.45F,0,-9,-2,-18,8,-17,7,-11);
                line(g,0,-7,0,7,.6F,hi);spear(g,12,1,lo,c);
                bezier(g,c,.5F,-11,9,-4,7,1,16,9,12);
            }
            case INK_DRAKE -> {
                brush(g,8,-6,-13,-16,-17,13,0,11,3.3F,PAPER);
                brush(g,0,11,16,10,15,-7,5,-4,2.3F,c);
                bezier(g,INK,.8F,7,-5,-9,-11,-12,11,-1,9);
                brush(g,4,-6,1,-12,7,-14,5,-17,.9F,PAPER);
                bezier(g,c,.6F,9,-7,13,-11,9,-13,13,-15);
                polygon(g,c,4,-6,11,-8,14,-3,9,0,4,-2);dot(g,10,-5,.8F,INK);
                path(g,hi,.5F,7,-5,10,-6,13,-3);line(g,9,-1,9,1,.7F,hi);line(g,11,-2,11,0,.65F,hi);
                bezier(g,PAPER,.45F,12,-2,19,-1,17,5,13,4);
                for(int k=0;k<4;k++)line(g,-9+k*2,1-k*2,-12+k*2,2-k*2,.5F,hi);
                splatter(g,-11,9,lo,3);
            }
            case INK_CRANE -> {
                // Crane: an open S neck and long tapering feathers, rather than a mask inside a badge.
                brush(g,-6,12,-14,1,4,-2,2,-10,3.2F,lo);
                brush(g,-6,11,-10,1,6,-3,3,-11,1.9F,PAPER);
                bezier(g,INK,.5F,-5,10,-8,2,8,-4,4,-10);
                ellipseFill(g,4,-11,3.7F,2.4F,PAPER);line(g,6,-11,14,-8,.9F,c);
                line(g,7,-10,14,-8,.4F,hi);dot(g,5,-11.4F,.65F,INK);
                brush(g,1,-13,-3,-14,-3,-18,-6,-19,.9F,c);
                brush(g,3,-13,2,-17,7,-17,8,-19,.7F,hi);
                bezier(g,c,.4F,3,-13,7,-14,8,-12,12,-14);
                for(int k=0;k<3;k++)feather(g,-5,9,15+k*2,-5+k*5,k==0?hi:lo,c);
                splatter(g,-10,11,lo,5);
            }
            case INK_TIGER -> {
                // Rounded cheek and brow contours keep this a feline face, not a polygonal helmet.
                for(int side:new int[]{-1,1}){g.pose().pushPose();g.pose().scale(side,1,1);
                    bezier(g,c,1.4F,3,-8,8,-15,13,-8,8,-5);
                    bezier(g,PAPER,.65F,5,-8,8,-12,11,-8,8,-6);
                    brush(g,7,-6,17,0,13,8,4,11,2.7F,lo);
                    bezier(g,hi,.7F,7,-6,13,-1,11,7,4,10);
                    brush(g,1,-2,5,-5,7,-2,10,-3,1.7F,PAPER);
                    line(g,3,-1,7,-2,.8F,INK);dot(g,5,-1.5F,.45F,hi);
                    for(int k=0;k<3;k++)brush(g,10,0+k*3,7,1+k*3,8,3+k*3,6,4+k*3,.9F,c);
                    ellipse(g,3,5,3,2.4F,0,Math.PI*1.4,.6F,hi);
                    polygon(g,PAPER,3,6,5,6,4,11);bezier(g,c,.4F,5,5,15,3,15,6,17,6);
                    g.pose().popPose();}
                brush(g,0,-9,-3,-7,3,-6,0,-4,1.1F,hi);line(g,-3,-8,3,-8,.7F,hi);
                polygon(g,PAPER,-2,2,2,2,0,4);path(g,c,.6F,0,4,0,7,-2,8);path(g,c,.6F,0,7,2,8);
                path(g,lo,.4F,-11,11,-8,14,8,14,11,11);
            }
            case INK_DRAGON -> {
                brush(g,-8,12,-17,2,-6,-12,3,-7,4,lo);
                bezier(g,PAPER,.7F,-9,11,-15,0,-5,-9,2,-7);
                brush(g,-2,-6,-10,-10,-7,-14,-11,-17,1.5F,PAPER);
                path(g,c,.65F,-7,-12,-3,-14,-4,-17);
                brush(g,5,-6,7,-10,12,-11,10,-17,1.6F,PAPER);
                path(g,c,.6F,9,-12,14,-14,13,-17);
                bezier(g,hi,1.2F,-6,-5,0,-12,5,-6,8,-3);
                polygon(g,lo,-5,-4,3,-6,7,-1,13,0,12,4,7,6,4,10,-2,8,-5,3);
                bezier(g,PAPER,.8F,-5,-4,2,-9,5,-3,13,0);
                path(g,c,.6F,-4,0,0,-1,3,2,10,3,12,1);line(g,-3,-1,1,0,1,hi);
                dot(g,0,-1,.55F,INK);line(g,7,4,7,7,.9F,hi);
                brush(g,2,7,-5,9,4,17,-3,17,1.2F,c);
                bezier(g,PAPER,.45F,10,2,24,-2,18,12,10,10);
                bezier(g,c,.5F,10,4,20,8,8,16,3,12);
                for(int k=0;k<4;k++)feather(g,-8,2+k*2,-5-k,5,lo,c);
                cloud(g,-9,13,.5F,c,hi);
            }
            default -> {}
        }
    }

    private static void scroll(GuiGraphics g,float x,float y,int sign,int c,int hi){
        bezier(g,c,.6F,x,y,x+6,y-sign*9,x+18,y-sign*5,x+15,y-sign);
        bezier(g,hi,.4F,x+15,y-sign,x+11,y+sign*3,x+9,y-sign*3,x+13,y-sign*3);
        bezier(g,c,.4F,x+16,y-sign,x+23,y+sign*3,x+28,y-sign*2,x+34,y);
    }
    private static void wave(GuiGraphics g,float x,float y,float scale,int c,int hi){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        bezier(g,c,.7F,0,0,11,6,24,1,19,-5);bezier(g,hi,.5F,19,-5,15,-10,9,-4,15,-3);
        bezier(g,c,.4F,9,3,21,9,31,-2,41,2);g.pose().popPose();
    }
    private static void cloud(GuiGraphics g,float x,float y,float scale,int c,int hi){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        bezier(g,c,.6F,0,0,4,0,4,-5,9,-4);bezier(g,c,.6F,9,-4,7,-12,22,-10,21,-3);
        bezier(g,c,.6F,21,-3,28,-7,33,-2,30,1);bezier(g,c,.6F,30,1,25,5,13,4,7,3);
        bezier(g,hi,.35F,12,-3,12,-7,18,-7,17,-3);bezier(g,c,.4F,2,5,19,8,30,4,43,5);
        g.pose().popPose();
    }
    private static void feather(GuiGraphics g,float x,float y,float dx,float dy,int c,int hi){
        float ex=x+dx,ey=y+dy,nx=-dy*.13F,ny=dx*.13F;
        bezier(g,c,.65F,x,y,x+dx*.4F+nx,y+dy*.4F+ny,ex+nx,ey+ny,ex,ey);
        bezier(g,c,.45F,x,y,x+dx*.5F-nx,y+dy*.5F-ny,ex-nx*.7F,ey-ny*.7F,ex,ey);
        line(g,x,y,ex,ey,.4F,hi);
        for(int k=2;k<6;k++){float t=k/7F;line(g,x+dx*t,y+dy*t,x+dx*(t+.11F)+nx*.5F,y+dy*(t+.11F)+ny*.5F,.32F,c);}
    }
    private static void spear(GuiGraphics g,float x,float y,int lo,int hi){
        line(g,x-7,y+13,x+3,y-12,1.5F,lo);line(g,x-6.8F,y+13,x+3.2F,y-12,.45F,hi);
        polygon(g,hi,x+1,y-10,x+5,y-17,x+5,y-9,x+2,y-6);line(g,x-1,y-7,x+6,y-5,.6F,hi);
        for(int k=0;k<4;k++)line(g,x-4+k*.4F,y+7-k,x-2+k*.4F,y+8-k,.5F,hi);
    }
    private static void chain(GuiGraphics g,float x,float y,int n,int c){for(int k=0;k<n;k++)ellipse(g,x+k*2,y+k*1.2F,1.9F,1.3F,0,Math.PI*2,.5F,c);}
    private static void splatter(GuiGraphics g,float x,float y,int c,int n){for(int i=0;i<n;i++){int v=hash(i*51+n);dot(g,x+(v&7)-3,y+(v>>>4&7)-3,.3F+(v>>>8&3)*.15F,c);}}
    private static int hash(int v){v^=v>>>16;v*=0x45d9f3b;v^=v>>>16;return v;}
    private static int argb(int alpha,int rgb){return alpha<<24|rgb&0xFFFFFF;}
    private static void text(GuiGraphics g,String value,float x,float y,float scale,int color,int align){
        var f=Minecraft.getInstance().font;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.drawString(f,value,align==0?-f.width(value)/2:align>0?-f.width(value):0,0,color,true);g.pose().popPose();
    }
    private static void rect(GuiGraphics g,int x,int y,int xx,int yy,int c){g.fill(x,y,xx,yy,c);}
    private static void dot(GuiGraphics g,float x,float y,float r,int c){ellipseFill(g,x,y,r,r,c);}
    private static void ellipseFill(GuiGraphics g,float x,float y,float rx,float ry,int c){
        for(int i=0;i<20;i++){double a=i*Math.PI/10,b=(i+1)*Math.PI/10;tri(g,c,x,y,x+(float)Math.cos(a)*rx,y+(float)Math.sin(a)*ry,x+(float)Math.cos(b)*rx,y+(float)Math.sin(b)*ry);}
    }
    private static void ellipse(GuiGraphics g,float x,float y,float rx,float ry,double start,double end,float width,int c){
        float px=x+(float)Math.cos(start)*rx,py=y+(float)Math.sin(start)*ry;
        for(int i=1;i<=24;i++){double a=start+(end-start)*i/24;float xx=x+(float)Math.cos(a)*rx,yy=y+(float)Math.sin(a)*ry;line(g,px,py,xx,yy,width,c);px=xx;py=yy;}
    }
    private static void brush(GuiGraphics g,float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3,float thick,int c){
        float px=x0,py=y0;
        for(int i=1;i<=24;i++){float t=i/24F,u=1-t;float x=u*u*u*x0+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3,y=u*u*u*y0+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3;
            float width=thick*(.2F+.8F*(float)Math.sin(Math.PI*t));line(g,px,py,x,y,width,c);px=x;py=y;}
    }
    private static void bezier(GuiGraphics g,int c,float width,float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3){
        float px=x0,py=y0;
        for(int i=1;i<=20;i++){float t=i/20F,u=1-t;float x=u*u*u*x0+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3,y=u*u*u*y0+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3;line(g,px,py,x,y,width,c);px=x;py=y;}
    }
    private static void path(GuiGraphics g,int c,float width,float... p){for(int i=2;i<p.length;i+=2)line(g,p[i-2],p[i-1],p[i],p[i+1],width,c);}
    private static void polygon(GuiGraphics g,int c,float... p){for(int i=2;i<p.length-2;i+=2)tri(g,c,p[0],p[1],p[i],p[i+1],p[i+2],p[i+3]);}
    private static void tri(GuiGraphics g,int c,float ax,float ay,float bx,float by,float cx,float cy){quad(g,c,ax,ay,bx,by,cx,cy,cx,cy);}
    private static void line(GuiGraphics g,float ax,float ay,float bx,float by,float width,int color){
        float dx=bx-ax,dy=by-ay,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.001)return;
        float nx=-dy/len*width/2,ny=dx/len*width/2;quad(g,color,ax+nx,ay+ny,bx+nx,by+ny,bx-nx,by-ny,ax-nx,ay-ny);
    }
    private static void quad(GuiGraphics g,int color,float... p){
        var m=g.pose().last().pose();VertexConsumer out=g.bufferSource().getBuffer(RenderType.gui());float area=0;
        for(int i=0;i<8;i+=2){int n=(i+2)%8;area+=p[i]*p[n+1]-p[n]*p[i+1];}
        // The pose may mirror a terminal; compensate there as well as for the local polygon winding.
        boolean reverse=(area>0)==(m.m00()*m.m11()-m.m01()*m.m10()>0);
        for(int k=0;k<4;k++){int i=reverse?6-k*2:k*2;out.vertex(m,p[i],p[i+1],0).color(color>>16&255,color>>8&255,color&255,color>>>24&255).endVertex();}
    }
}
