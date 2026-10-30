package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.skyacademy.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in, isolated copied save only; captures the real native renderer. */
public final class AcademyNativeAudit {
    private static int startup,stage,ticks,mainTicks;private static volatile boolean sawMain,roundTrip;private static boolean started,prepared,saving;
    private static volatile boolean ready,complete;private static volatile String status="";
    private static final String[] LABELS={"west-fringe","north-ridge","east-lake","south-fringe","farm","workshop","night-ridge","black-sea","black-sea-f8"};
    private static final int[][] VIEWS={{104,150,355,180,30},{278,168,128,180,24},{468,124,222,155,24},{254,134,484,180,25},{87,100,340,180,22},{419,102,391,180,23},{258,141,61,0,-8},{680,-15,260,90,35},{680,-15,260,90,35}};
    private static Path folder(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started&&++startup<100)return;
        if(!started){
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))throw new IllegalStateException("Refusing non-isolated sky audit");
            started=true;Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(4);mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SPECTATOR);var root=com.tnc.tnc.journey.JourneyData.get(s).player(p.getUUID());root.putBoolean("Named",true);com.tnc.tnc.journey.JourneyData.get(s).setDirty();if(com.tnc.tnc.world.SkyIslandWorlds.separate(s))return;SkyAcademyWorld.enableIsolatedAudit(s);var o=SkyAcademyWorld.origin(s);p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),o.getX()+260.5,o.getY()+190,o.getZ()+260.5,0,0);});return;
        }
        mc.setScreen(null);
        if(!complete){
            if(++ticks%100==0){mc.getSingleplayerServer().execute(()->{complete=SkyAcademyWorld.available(mc.getSingleplayerServer());status=SkyAcademyWorld.status(mc.getSingleplayerServer());});Files.writeString(folder().resolve("progress.txt"),status);}
            if(ticks>24000){Files.writeString(folder().resolve("failed.txt"),"Construction timeout: "+status);mc.stop();}return;
        }
        if(stage>=VIEWS.length){
            if(++ticks%20==0)mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);if(p.serverLevel()==s.overworld()){
                sawMain=true;var gate=com.tnc.tnc.world.SkyIslandSavedData.get(s.overworld()).anchorPos("GROUND_PORTAL");
                // Leave and re-enter the destination trigger: standing still is now
                // intentionally safe and cannot cause repeated dimension changes.
                if(++mainTicks==4)p.teleportTo(s.overworld(),gate.getX()+12.5,gate.getY()+1,gate.getZ()+7.5,0,0);
                if(mainTicks==6)p.teleportTo(s.overworld(),gate.getX()+7.5,gate.getY()+1,gate.getZ()+7.5,0,0);
            }else if(sawMain&&p.serverLevel()==com.tnc.tnc.world.SkyIslandWorlds.island(s))roundTrip=true;});
            if(roundTrip||ticks>1800){Files.writeString(folder().resolve("report.json"),new com.google.gson.Gson().toJson(Map.of("native_screenshots",LABELS,"normal_lifecycle",true,"portal_round_trip",roundTrip,"scope","Isolated lifecycle and actual proximity-triggered paired-portal travel")));mc.stop();}return;
        }
        if(!prepared){prepared=true;ready=false;ticks=0;mc.options.renderDistance().set(stage==6?12:10);int[] v=VIEWS[stage];
            mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var o=SkyAcademyWorld.origin(s);s.overworld().setWeatherParameters(12000,0,false,false);s.overworld().setDayTime(stage==6?18000:6000);p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),o.getX()+v[0]+.5,o.getY()+v[1],o.getZ()+v[2]+.5,v[3],v[4]);ready=true;});return;
        }
        if(!ready||saving)return;
        mc.player.setYRot(VIEWS[stage][3]);mc.player.setXRot(VIEWS[stage][4]);
        // Toggling clears the particle pool. Do it only once at each transition,
        // otherwise an audit frame can never show the living mana currents.
        if(com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()!=(stage==6||stage==8))com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(stage==6||stage==8);
        if(++ticks==400){saving=true;String name=stage+"-"+LABELS[stage]+".png";Screenshot.grab(mc.gameDirectory,"academy-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/academy-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){throw new RuntimeException(ex);}finally{saving=false;}});}
        if(ticks>420){if(++stage==VIEWS.length){ticks=0;mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var at=com.tnc.tnc.world.SkyIslandSavedData.get(s.overworld()).anchorPos("ARRIVAL");p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),at.getX()+.5,at.getY(),at.getZ()+.5,180,0);});return;}prepared=false;}
    }
}
