package com.tnc.tnc.client;

import com.google.gson.GsonBuilder;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in development-save capture; never executes in normal pack play. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class HealthHudClientAudit {
    private static int phase,ticks;
    private static volatile boolean saving,saved;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.healthHudAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(phase==0){
            mc.options.guiScale().set(2);mc.options.hideGui=false;mc.options.pauseOnLostFocus=false;mc.resizeDisplay();mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);
                com.tnc.tnc.combat.DownedCombat.revive(p);
                for(int x=9996;x<=10004;x++)for(int z=9996;z<=10004;z++)p.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                p.connection.teleport(10000.5,100,10000.5,180,0);p.serverLevel().setDayTime(6000);
                p.getAbilities().invulnerable=true;p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);
            });phase=1;ticks=0;return;
        }
        if(++ticks==30)mc.gui.getChat().clearMessages(true);
        if(ticks<60||saving)return;
        if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,phase==1?"hud-a-c-full.png":"hud-a-c-partial.png",mc.getMainRenderTarget(),message->{saved=true;saving=false;});return;}
        saved=false;
        if(phase==1){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setHealth(p.getMaxHealth()*.6F);p.setAbsorptionAmount(4);p.getFoodData().setFoodLevel(14);});phase=2;ticks=0;return;}
        var report=Map.of("health",mc.player.getHealth(),"maximum",mc.player.getMaxHealth(),"absorption",mc.player.getAbsorptionAmount(),"food",mc.player.getFoodData().getFoodLevel(),"screenshots",List.of("hud-a-c-full.png","hud-a-c-partial.png"),"gui_scale",2);
        Files.writeString(Path.of("../work/tavern-revision-20261005/hud-client-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
}
