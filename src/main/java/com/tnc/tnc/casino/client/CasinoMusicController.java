package com.tnc.tnc.casino.client;
import com.tnc.tnc.casino.CasinoRoomPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class CasinoMusicController {
 private static CasinoRoomPacket rooms;private static SoundInstance playing;private static int ticks,age,retry;private static boolean visiting;
 private static final ResourceLocation TRACK=ResourceLocation.fromNamespaceAndPath("tnc","music.casino.all");
 public static void accept(CasinoRoomPacket p){rooms=p;}
 public static boolean inside(Minecraft m){return m.player!=null&&m.level!=null&&rooms!=null&&rooms.dimension().equals(m.level.dimension().location())&&rooms.rooms().stream().anyMatch(b->b.contains(m.player.position()));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void exclusive(PlaySoundEvent e){var s=e.getSound();if(s!=null&&s!=playing&&inside(Minecraft.getInstance())&&(s.getSource()==SoundSource.MUSIC||s.getSource()==SoundSource.RECORDS))e.setSound(null);}
 public static boolean tick(Minecraft m){boolean here=inside(m);if(!here){stop(m);ticks=0;visiting=false;return false;}
  if(!visiting){m.getMusicManager().stopPlaying();m.getSoundManager().stop(null,SoundSource.MUSIC);m.getSoundManager().stop(null,SoundSource.RECORDS);visiting=true;ticks=0;}
  if(m.isPaused())return true;if(ticks<200)ticks++;
  if(m.options.getSoundSourceVolume(SoundSource.MUSIC)<=0||m.options.getSoundSourceVolume(SoundSource.MASTER)<=0){stop(m);return true;}
  if(ticks<200)return true;var sounds=m.getSoundManager();if(playing!=null){if(++age<40||sounds.isActive(playing))return true;playing=null;retry=20;}
  if(retry-->0)return true;if(!sounds.getAvailableSounds().contains(TRACK)){retry=200;return true;}
  playing=new SimpleSoundInstance(TRACK,SoundSource.MUSIC,1,1,net.minecraft.util.RandomSource.create(),true,0,SoundInstance.Attenuation.NONE,0,0,0,true);age=0;sounds.play(playing);return true;
 }
 private static void stop(Minecraft m){if(playing!=null)m.getSoundManager().stop(playing);playing=null;age=0;retry=0;}
 public static void disconnect(Minecraft m){stop(m);rooms=null;ticks=0;visiting=false;}
}
