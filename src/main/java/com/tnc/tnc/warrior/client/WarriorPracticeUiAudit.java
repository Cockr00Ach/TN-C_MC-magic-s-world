package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.TemperingPacket;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in render audit at the title screen; never enters a world or changes player data. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WarriorPracticeUiAudit {
    private static final TemperingPacket[] CASES={
        new TemperingPacket(false,0,0,false,false,0,0,0,false),
        new TemperingPacket(true,200,2,false,false,0,0,0,false),
        new TemperingPacket(true,180,1,false,true,3,0,0,false),
        new TemperingPacket(true,6000,6,true,true,3,80,160,false),
        new TemperingPacket(true,200,2,false,false,0,0,0,false,true),
        new TemperingPacket(true,320,2,true,false,0,0,0,false,true,2,12,95,7),
        new TemperingPacket(true,320,2,true,true,3,35,0,false,true,2,12,95,7)
    };
    private static final String[] NAMES={"no-talent.png","ready-to-practice.png","low-talent-expired.png","high-talent-active.png","inside-training-ground.png","expert-records.png","expert-counter.png"};
    private static int index,ticks,bootTicks;private static boolean opened;private static volatile boolean saving,saved;
    private WarriorPracticeUiAudit(){}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.warriorUiAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(++bootTicks>2400){mc.stop();throw new IllegalStateException("Warrior UI audit timed out");}
        if(!opened){
            if(mc.getOverlay()!=null || !(mc.screen instanceof TitleScreen
                    || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen))return;
            opened=true;mc.options.pauseOnLostFocus=false;show(mc);return;
        }
        if(saving)return;
        if(saved){
            index++;saved=false;ticks=0;
            if(index==CASES.length){
                Path folder=mc.gameDirectory.toPath().resolve("screenshots");
                Files.writeString(folder.resolve("warrior-ui-audit.json"),"{\"cases\":7,\"buttonStatesVerified\":true,\"hotkeyRegistered\":true,\"scope\":\"Actual client render with simulated display state; no world entered\"}");
                mc.stop();return;
            }
            show(mc);return;
        }
        if(++ticks<30)return;
        if(!java.util.Arrays.asList(mc.options.keyMappings).contains(WarriorPracticeKeys.OPEN)){mc.stop();throw new IllegalStateException("Warrior practice key was not registered");}
        if(mc.screen instanceof WarriorPracticeScreen screen)for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b){
            String title=b.getMessage().getString();
            boolean expected=title.equals("开始训练")?TemperingClient.enrolled()&&!TemperingClient.training():title.equals("取消训练")?TemperingClient.training():title.startsWith("进入个人训练场")?TemperingClient.enrolled():title.startsWith("陪练难度")?TemperingClient.ground()&&TemperingClient.enrolled()&&!TemperingClient.training():true;
            if(b.active!=expected){mc.stop();throw new IllegalStateException("Unexpected practice control state: "+title);}
        }
        saving=true;
        Screenshot.grab(mc.gameDirectory,NAMES[index],mc.getMainRenderTarget(),message->{saved=true;saving=false;});
    }
    private static void show(Minecraft mc){TemperingClient.accept(CASES[index]);mc.setScreen(new WarriorPracticeScreen());}
    @Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup{
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            if(Boolean.getBoolean("tnc.warriorUiAudit"))event.enqueueWork(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
        }
    }
}
