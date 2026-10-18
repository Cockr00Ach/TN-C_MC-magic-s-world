package com.tnc.tnc.client;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in native UI regression fixture; only the isolated audit save is touched. */
@Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT)
public final class MagicStoneUiAudit {
    private static final int[][] VIEWS={{1600,900,2},{2495,1039,4},{854,480,4},{1920,720,2}};
    private static final List<String> failures=new ArrayList<>();
    private static final List<Map<String,Object>> scenes=new ArrayList<>();
    private static boolean started, captured;
    private static volatile boolean ready, saving;
    private static int stage,ticks,clicks,hoverFrames;
    private static Path folder(){return Path.of(System.getProperty("tnc.magicStoneUiAuditOutput")).toAbsolutePath();}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.magicStoneUiAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(!started){
            started=true;Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                var data=MagicStone.getOrNull(p);data.assignDefaultAffinities(5);
                for(var e:SpellCatalog.all())data.learn(e.id());
                ready=true;
            });return;
        }
        if(!ready||saving)return;
        if(ticks++==0){
            if(stage%2==0){var v=VIEWS[stage/2];mc.setScreen(null);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),v[0],v[1]);mc.options.guiScale().set(v[2]);mc.resizeDisplay();mc.setScreen(new HoverAuditScreen());}
            else click(mc,"配键");
        }
        if(ticks==20)mc.gui.getChat().clearMessages(false);
        if(ticks==30&&stage%2==0)click(mc,"水");
        if(ticks==40&&stage%2==1){
            // Select an actual slot through physical coordinates after fitting the screen.
            var screen=(MagicStoneScreen)mc.screen;
            var slot=buttons(screen).stream().filter(b->b.getMessage().getString().contains("右键")).findFirst().orElseThrow();
            click(screen,slot);
        }
        if(ticks<55)return;
        if(!captured){
            var screen=(MagicStoneScreen)mc.screen;
            var buttons=buttons(screen);
            int vw=mc.getWindow().getGuiScaledWidth(),vh=mc.getWindow().getGuiScaledHeight();
            for(int i=0;i<buttons.size();i++){
                var a=buttons.get(i);float scale=screen.uiScale();
                if(a.getX()*scale<0||a.getY()*scale<0||(a.getX()+a.getWidth())*scale>vw||(a.getY()+a.getHeight())*scale>vh)
                    failures.add("Offscreen stage "+stage+": "+a.getMessage().getString());
                for(int j=i+1;j<buttons.size();j++){
                    var b=buttons.get(j);
                    if(a.getX()<b.getX()+b.getWidth()&&a.getX()+a.getWidth()>b.getX()&&a.getY()<b.getY()+b.getHeight()&&a.getY()+a.getHeight()>b.getY())
                        failures.add("Overlapping buttons stage "+stage+": "+a.getMessage().getString()+" / "+b.getMessage().getString());
                }
            }
            scenes.add(Map.of("stage",stage,"viewport",vw+"x"+vh,"fit",screen.uiScale(),"buttons",buttons.size(),"page",stage%2==0?"water-learn":"water-bind"));
            String name="magic-stone-"+stage+".png";saving=true;captured=true;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots").resolve(name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){failures.add(e.toString());}finally{saving=false;}});return;
        }
        if(++stage==8){
            Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"scenes",scenes,"physical_clicks",clicks,"screenshots",8,"hover_frames",hoverFrames)));
            mc.stop();return;
        }
        ticks=0;captured=false;
    }
    /** The hidden fixture supplies deterministic cursor coordinates to the real game renderer. */
    private static final class HoverAuditScreen extends MagicStoneScreen {
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partialTick) {
            if((stage==4||stage==5)&&ticks>=48){
                String label=stage==4?"水球":"清空";
                var button=buttons(this).stream().filter(b->b.getMessage().getString().contains(label)).findFirst().orElseThrow();
                x=Math.round((button.getX()+button.getWidth()*.7f)*uiScale());
                y=Math.round((button.getY()+button.getHeight()*.5f)*uiScale());
                hoverFrames++;
            }
            super.render(g,x,y,partialTick);
        }
    }
    private static List<Button> buttons(MagicStoneScreen s){return s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();}
    private static void click(Minecraft mc,String label){
        var s=(MagicStoneScreen)mc.screen;
        var b=buttons(s).stream().filter(v->v.getMessage().getString().replace("§l","").equals(label)).findFirst().orElseThrow();click(s,b);
    }
    private static void click(MagicStoneScreen s,Button b){
        double x=(b.getX()+b.getWidth()*.5)*s.uiScale(),y=(b.getY()+b.getHeight()*.5)*s.uiScale();
        if(!s.mouseClicked(x,y,0))failures.add("Physical click missed "+b.getMessage().getString());
        s.mouseReleased(x,y,0);clicks++;
    }
}
