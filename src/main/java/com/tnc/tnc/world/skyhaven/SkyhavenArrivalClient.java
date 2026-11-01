package com.tnc.tnc.world.skyhaven;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.skyacademy.AcademyNetwork;
import com.tnc.tnc.world.SkyhavenReadiness;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SkyhavenArrivalClient {
    private static long token,sceneAt,lastFrame;private static String phase="";private static int ready,total,chunks,compiled,quiet,ticks,radius=3,required=49;
    private static double frameMillis;private static BlockPos at;private static Screen deferred;private static boolean acknowledged;
    private static final Loading SCREEN=new Loading();
    public static boolean active(){return !phase.isEmpty();}
    public static void receive(CompoundTag t){
        var mc=Minecraft.getInstance();String next=t.getString("Phase");
        if(next.equals("done")){clear();mc.setScreen(deferred);deferred=null;return;}
        if(!active()){deferred=mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen?null:mc.screen;quiet=ticks=0;acknowledged=false;sceneAt=lastFrame=0;}
        token=t.getLong("Token");at=BlockPos.of(t.getLong("At"));ready=t.getInt("Ready");total=t.getInt("Total");
        radius=Math.max(1,Math.min(Math.min(3,t.getInt("ClientRadius")),mc.options.renderDistance().get()-1));required=(radius*2+1)*(radius*2+1);
        if(next.equals("scene")&&!phase.equals("scene"))sceneAt=System.nanoTime();phase=next;if(mc.screen!=SCREEN)mc.setScreen(SCREEN);
    }
    private static void clear(){phase="";token=sceneAt=lastFrame=0;quiet=chunks=compiled=0;acknowledged=false;}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){if(!active()||e.getNewScreen()==SCREEN)return;
        if(e.getNewScreen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen||e.getNewScreen() instanceof net.minecraft.client.gui.screens.TitleScreen){clear();deferred=null;return;}
        if(e.getNewScreen()!=null&&!(e.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen))deferred=e.getNewScreen();e.setNewScreen(SCREEN);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!active())return;var mc=Minecraft.getInstance();
        if(mc.getConnection()==null){clear();deferred=null;mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());return;}
        if(mc.screen!=SCREEN)mc.setScreen(SCREEN);
        if(!phase.equals("scene")||mc.level==null||!mc.level.dimension().equals(SkyhavenContent.KEY)||mc.player==null)return;
        if(++ticks%5==0){chunks=compiled=0;int cx=at.getX()>>4,cz=at.getZ()>>4;
            for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(mc.level.hasChunk(cx+x,cz+z))chunks++;
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(mc.levelRenderer.isChunkCompiled(at.offset(x*16,0,z*16)))compiled++;
            if(mc.levelRenderer.hasRenderedAllChunks())compiled=9;
        }
        boolean calm=mc.getOverlay()==null&&frameMillis>0&&frameMillis<50&&chunks==required&&compiled==9;
        quiet=calm?Math.min(quiet+1,40):0;
        if(!acknowledged&&SkyhavenReadiness.ready((System.nanoTime()-sceneAt)/1_000_000,chunks,required,compiled,quiet)){
            acknowledged=true;AcademyNetwork.arrivalReady(token);
            com.mojang.logging.LogUtils.getLogger().info("[Skyhaven Arrival] Client warm-up complete: chunks={}, sections={}, quietTicks={}",chunks,compiled,quiet);
        }
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(!active()||e.phase!=TickEvent.Phase.END)return;long now=System.nanoTime();if(lastFrame!=0)frameMillis=(now-lastFrame)/1_000_000.;lastFrame=now;}
    private static final class Loading extends Screen {
        Loading(){super(Component.literal("抵达云溪"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean shouldCloseOnEsc(){return false;}
        @Override public void onClose(){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff080f17);int w=Math.min(380,width-30),h=190,x=(width-w)/2,y=(height-h)/2;
            ArcaneGui.proportionalFrame(g,x,y,w,h);ArcaneGui.sigil(g,width/2,y+48,23,System.currentTimeMillis()/50.,.5f,true);
            g.drawCenteredString(font,"云溪 · 群岛之门",width/2,y+85,ArcaneGui.GOLD);
            boolean scene=phase.equals("scene");String text=scene?"整理光影与眼前的风景":"准备岛屿与周边地形";
            g.drawCenteredString(font,text,width/2,y+110,ArcaneGui.TEXT);
            double progress=scene?.7+.15*Math.min(1,chunks/(double)required)+.1*Math.min(1,compiled/9.)+.05*Math.min(1,quiet/40.):.7*ready/Math.max(1.,total);
            g.fill(x+28,y+134,x+w-28,y+139,0xff202932);g.fill(x+28,y+134,x+28+(int)((w-56)*Math.min(.99,progress)),y+139,0xff91c9c5);
            g.drawCenteredString(font,scene?"让风先走遍街巷，再与你一同启程。":ready+" / "+total,width/2,y+151,ArcaneGui.DIM);
        }
    }
    private SkyhavenArrivalClient(){}
}
