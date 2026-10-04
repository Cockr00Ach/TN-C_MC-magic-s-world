package com.tnc.tnc.tavern.client;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.tavern.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.concurrent.ThreadLocalRandom;

/** Called by the existing playlist controller, so only one music owner runs per tick. */
public final class TavernMusicController {
    private static TavernRoomPacket rooms;
    private static final TavernMusicTiming timer=new TavernMusicTiming();
    private static SoundInstance playing;
    private static int age,retry;
    private static final ResourceLocation[] TRACKS={ResourceLocation.fromNamespaceAndPath("tnc","music.tavern.wander_ward"),ResourceLocation.fromNamespaceAndPath("tnc","music.tavern.wander_ward_1")};
    public static void accept(TavernRoomPacket packet){rooms=packet;}
    public static boolean inside(Minecraft mc){return mc.player!=null&&mc.level!=null&&rooms!=null&&rooms.dimension().equals(mc.level.dimension().location())&&rooms.rooms().stream().anyMatch(b->b.contains(mc.player.position()));}
    public static boolean tick(Minecraft mc,boolean boss) {
        boolean inside=inside(mc);int choice=timer.tick(inside,mc.isPaused(),()->ThreadLocalRandom.current().nextInt(2));
        if(!inside){stopAudio(mc);retry=0;return false;}
        if(boss||mc.options.getSoundSourceVolume(SoundSource.MUSIC)<=0||mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0){stopAudio(mc);return true;}
        if(mc.isPaused()||choice<0)return true;
        var manager=mc.getSoundManager();
        if(playing!=null){if(++age<40||manager.isActive(playing))return true;playing=null;age=0;retry=40;}
        if(retry-->0)return true;
        if(!manager.getAvailableSounds().contains(TRACKS[choice])){retry=200;LogUtils.getLogger().warn("[TN-C Tavern] music resource missing: {}",TRACKS[choice]);return true;}
        playing=new SimpleSoundInstance(TRACKS[choice],SoundSource.MUSIC,1F,1F,RandomSource.create(),true,0,SoundInstance.Attenuation.NONE,0,0,0,true);
        age=0;manager.play(playing);mc.getMusicManager().stopPlaying();
        LogUtils.getLogger().info("[TN-C Tavern] looping {} after {} entry ticks",TRACKS[choice],timer.ticks());return true;
    }
    private static void stopAudio(Minecraft mc){if(playing!=null)mc.getSoundManager().stop(playing);playing=null;age=0;}
    public static void disconnect(Minecraft mc){stopAudio(mc);rooms=null;timer.reset();retry=0;}
    static int entryTicks(){return timer.ticks();}
    static int selectedTrack(){return timer.selectedTrack();}
    static boolean active(Minecraft mc){return playing!=null&&mc.getSoundManager().isActive(playing);}
}
