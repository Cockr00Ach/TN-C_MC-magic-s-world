package com.tnc.tnc.tavern.client;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.tavern.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.ThreadLocalRandom;

/** Owns the music channel for the whole indoor visit, including the entry delay. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class TavernMusicController {
    private static TavernRoomPacket rooms;
    private static final TavernMusicTiming timer=new TavernMusicTiming();
    private static SoundInstance playing;
    private static int age,retry;
    private static boolean visiting;
    private static long lastClockNanos,activeNanos;
    private static boolean wasPaused;
    private static volatile boolean reloadInterrupted;
    private static final double[] DURATIONS={196.16,174.96,207.60};
    private static final ResourceLocation[] TRACKS={ResourceLocation.fromNamespaceAndPath("tnc","music.tavern.wander_ward"),ResourceLocation.fromNamespaceAndPath("tnc","music.tavern.wander_ward_1"),ResourceLocation.fromNamespaceAndPath("tnc","music.tavern.third")};
    public static void accept(TavernRoomPacket packet){rooms=packet;}
    public static boolean inside(Minecraft mc){return mc.player!=null&&mc.level!=null&&rooms!=null&&rooms.dimension().equals(mc.level.dimension().location())&&rooms.rooms().stream().anyMatch(b->b.contains(mc.player.position()));}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void preventOtherMusic(PlaySoundEvent event){var sound=event.getSound();if(sound!=null&&inside(Minecraft.getInstance())&&sound!=playing&&(sound.getSource()==SoundSource.MUSIC||sound.getSource()==SoundSource.RECORDS))event.setSound(null);}
    @SubscribeEvent public static void soundEngineReloaded(SoundEngineLoadEvent event){if(playing!=null)reloadInterrupted=true;}
    public static boolean tick(Minecraft mc) {
        long now=System.nanoTime();boolean paused=mc.isPaused();
        if(playing!=null&&!paused&&!wasPaused)activeNanos+=Math.max(0,now-lastClockNanos);
        lastClockNanos=now;wasPaused=paused;
        boolean inside=inside(mc);
        if(inside&&!visiting){mc.getMusicManager().stopPlaying();mc.getSoundManager().stop(null,SoundSource.MUSIC);mc.getSoundManager().stop(null,SoundSource.RECORDS);}
        visiting=inside;int choice=timer.tick(inside,mc.isPaused(),()->ThreadLocalRandom.current().nextInt(3));
        if(!inside){stopAudio(mc);retry=0;return false;}
        if(mc.options.getSoundSourceVolume(SoundSource.MUSIC)<=0||mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0){stopAudio(mc);return true;}
        if(mc.isPaused()||choice<0)return true;
        var manager=mc.getSoundManager();
        if(reloadInterrupted){stopAudio(mc);reloadInterrupted=false;retry=20;}
        if(playing!=null){
            if(++age<40||manager.isActive(playing))return true;
            playing=null;age=0;
            if(activeNanos/1_000_000_000.0>=DURATIONS[choice]-1)choice=timer.finished(()->ThreadLocalRandom.current().nextInt(2));
            else {retry=20;LogUtils.getLogger().warn("[TN-C Tavern] early sound interruption; retaining song {}",TRACKS[choice]);}
        }
        if(retry-->0)return true;
        if(!manager.getAvailableSounds().contains(TRACKS[choice])){retry=200;LogUtils.getLogger().warn("[TN-C Tavern] music resource missing: {}",TRACKS[choice]);return true;}
        playing=new SimpleSoundInstance(TRACKS[choice],SoundSource.MUSIC,1F,1F,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);
        age=0;activeNanos=0;lastClockNanos=System.nanoTime();manager.play(playing);
        LogUtils.getLogger().info("[TN-C Tavern] playing full song {} after {} entry ticks",TRACKS[choice],timer.ticks());return true;
    }
    private static void stopAudio(Minecraft mc){if(playing!=null)mc.getSoundManager().stop(playing);playing=null;age=0;}
    public static void disconnect(Minecraft mc){stopAudio(mc);rooms=null;timer.reset();retry=0;visiting=false;reloadInterrupted=false;activeNanos=0;}
    static int entryTicks(){return timer.ticks();}
    static int selectedTrack(){return timer.selectedTrack();}
    static boolean active(Minecraft mc){return playing!=null&&mc.getSoundManager().isActive(playing);}
}
