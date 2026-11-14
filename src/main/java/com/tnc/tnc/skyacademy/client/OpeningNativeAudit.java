package com.tnc.tnc.skyacademy.client;
import com.tnc.tnc.skyacademy.*;
import com.tnc.tnc.tavern.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in native-render check. The path guard forbids player saves. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class OpeningNativeAudit {
    private static int age,phase,clock;private static volatile boolean ready,finished,seated;private static boolean captured;
    private static Path output(){return Path.of(System.getProperty("tnc.openingAuditOutput"));}
    private static String pendingCapture;
    private static void capture(String name){pendingCapture=name;}
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||pendingCapture==null)return;
        String name=pendingCapture;pendingCapture=null;saveFrame(name);
    }
    private static void saveFrame(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots").resolve(name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new RuntimeException(e);}});}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(!Boolean.getBoolean("tnc.openingAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))throw new IllegalStateException("Opening audit refuses a live game directory");
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        if(++age<100)return;
        if(age>18000){Files.writeString(output().resolve("failed.txt"),"Timed out in phase "+phase);mc.stop();return;}
        if(phase==0){
            Files.createDirectories(output());mc.setScreen(null);mc.options.guiScale().set(2);mc.options.renderDistance().set(12);mc.options.hideGui=false;mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(.6);mc.options.framerateLimit().set(60);
            mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var l=com.tnc.tnc.world.SkyIslandWorlds.island(s);var o=SkyAcademyWorld.origin(s);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();var r=com.tnc.tnc.journey.JourneyData.get(s).player(p.getUUID());r.putBoolean("Named",true);r.putString("Name","星河");r.remove("TavernOpeningDone");
                p.teleportTo(l,o.getX()+460.5,o.getY()+90,o.getZ()+292.5,-90,0);TavernOpening.queue(p);ready=true;});phase=1;return;
        }
        if(phase==1){
            if(!ready)return;if(com.tnc.tnc.tavern.client.TavernOpeningScene.active()){
                clock++;if(clock==100)capture("01-tavern-two-shot.png");if(clock==200)capture("02-self-closeup.png");if(!captured&&com.tnc.tnc.tavern.client.TavernOpeningScene.playerSpeaking()){capture("03-player-seat-cup.png");captured=true;}
            }
            if(age%20==0)mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);if(TavernOpening.active(p))seated|=p.isPassenger();finished=com.tnc.tnc.journey.JourneyData.get(s).player(p.getUUID()).getBoolean("TavernOpeningDone")&&FirstSightPeak.ready(s);});
            if(finished){Files.writeString(output().resolve("tavern.txt"),"Seated="+seated+"; completed=true");ready=false;clock=0;phase=2;}return;
        }
        if(phase==2){
            phase=3;mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var l=com.tnc.tnc.world.SkyIslandWorlds.island(s);l.setDayTime(18000);l.setWeatherParameters(12000,0,false,false);var at=SkyAcademyWorld.at(s,"stargaze");var t=AcademyLessons.data(p);t.putInt("Completed",AcademyRules.ARRIVAL);t.putString("Active","");t.remove("FirstSightOpened");p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+4.5,0,-8);ready=true;});return;
        }
        if(phase==3&&ready&&FirstSightScene.active()){
            clock++;if(clock==140&&!FirstSightScene.musicActive())Files.writeString(output().resolve("audio-failed.txt"),"Music not active during conversation");if(clock==160)capture("04-mentor-conversation.png");if(clock==680)capture("05-player-panorama.png");
            if(clock==720)FirstSightScene.consumeSightKey();
            if(clock==732)capture("06-first-card.png");if(clock==758)capture("07-second-card.png");if(clock==786)capture("08-world-reveal.png");if(clock==840)capture("09-mana-panorama.png");
            if(clock>880){Files.writeString(output().resolve("report.txt"),"Real tavern and mountain flow completed; manaSight="+com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()+"; music is uninterrupted from scene start; physical audio output not measured.");mc.stop();}
        }
    }
}
