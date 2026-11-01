package com.tnc.tnc.world.skyhaven;

import com.tnc.tnc.journey.client.JourneyScreen;
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
        if(next.equals("done")){
            if(!active()||t.getLong("Token")!=token)return;
            var resume=deferred;deferred=null;clear();mc.setScreen(resume);
            com.mojang.logging.LogUtils.getLogger().info("[Skyhaven Arrival] Loading UI released; resumed={}",resume==null?"game":resume.getClass().getSimpleName());return;
        }
        if(!active()){deferred=mc.screen instanceof JourneyScreen?mc.screen:null;quiet=ticks=0;acknowledged=false;sceneAt=lastFrame=0;}
        token=t.getLong("Token");at=BlockPos.of(t.getLong("At"));ready=t.getInt("Ready");total=t.getInt("Total");
        radius=Math.max(1,Math.min(Math.min(3,t.getInt("ClientRadius")),mc.options.renderDistance().get()-1));required=(radius*2+1)*(radius*2+1);
        if(next.equals("scene")&&!phase.equals("scene"))sceneAt=System.nanoTime();phase=next;if(mc.screen!=SCREEN)mc.setScreen(SCREEN);
    }
    private static void clear(){phase="";token=sceneAt=lastFrame=0;quiet=chunks=compiled=0;acknowledged=false;}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){if(!active()||e.getNewScreen()==SCREEN)return;
        if(e.getNewScreen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen||e.getNewScreen() instanceof net.minecraft.client.gui.screens.TitleScreen){clear();deferred=null;return;}
        // Joining/progress/terrain screens are transient. Restoring one after its
        // owning connection transition has finished leaves it on screen forever.
        if(e.getNewScreen() instanceof JourneyScreen)deferred=e.getNewScreen();e.setNewScreen(SCREEN);
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
            // User supplied artwork, kept at its original aspect ratio.
            var background=net.minecraft.resources.ResourceLocation.parse("tnc:textures/gui/skyhaven_arrival.png");
            float scale=Math.max(width/1672f,height/941f);
            g.pose().pushPose();g.pose().scale(scale,scale,1);
            g.blit(background,Math.round((width/scale-1672)/2),Math.round((height/scale-941)/2),0,0,1672,941,1672,941);g.pose().popPose();
            int barWidth=Math.min(480,width-56),x=(width-barWidth)/2,y=height-35;
            for(int row=0;row<84;row++){int alpha=(int)(160*(row/83.));g.fill(0,height-84+row,width,height-83+row,alpha<<24);}
            boolean scene=phase.equals("scene");
            double progress=scene?.7+.15*Math.min(1,chunks/(double)required)+.1*Math.min(1,compiled/9.)+.05*Math.min(1,quiet/40.):.7*ready/Math.max(1.,total);
            int filled=(int)((barWidth-2)*Math.min(.99,progress));
            g.fill(x-1,y-1,x+barWidth+1,y+6,0x998fbcce);g.fill(x,y,x+barWidth,y+5,0xd5162030);
            g.fill(x+1,y+1,x+1+filled,y+4,0xffb8e9ee);
            g.drawCenteredString(font,scene?"正在准备眼前的风景…":"正在准备天空岛…",width/2,y-16,0xfff5f0e9);
            g.drawCenteredString(font,(int)(Math.min(.99,progress)*100)+"%",width/2,y+11,0xffdedfe9);
        }
    }
    private SkyhavenArrivalClient(){}
}
