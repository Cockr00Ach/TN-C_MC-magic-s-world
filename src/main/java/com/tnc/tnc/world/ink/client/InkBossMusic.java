package com.tnc.tnc.world.ink.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Local streaming music, bounded cross-fades, and a title that follows actual audible playback. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkBossMusic {
    private static final String[] SLUGS={"crane","tiger","dragon"};
    private static final String[] TITLES={"大漠行","火焰帝皇以骸 BOSS战","骤雨狂风 · 基塔布鲁 BOSS战"};
    private static final List<Track> fading=new ArrayList<>();
    private static Track playing;
    private static int desired,lease,retry,playingAge;
    private static ResourceLocation dimension;
    public static void accept(ResourceLocation dim,int phase){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(dim))return;
        dimension=dim;desired=Math.max(0,Math.min(3,phase));lease=80;
    }
    public static int phase(){return playing==null?0:playing.phase;}
    public static String title(){return playing==null?"":TITLES[playing.phase-1];}
    public static boolean ownsChannel(){return desired>0||playing!=null||!fading.isEmpty();}
    public static boolean audible(){var mc=Minecraft.getInstance();return playing!=null&&mc.getSoundManager().isActive(playing)&&playing.getVolume()>.02F;}
    private static final class Track extends AbstractTickableSoundInstance {
        private final int phase;
        private float target=1;
        private int age;
        Track(int phase){
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("tnc","music.ink."+SLUGS[phase-1])),SoundSource.MUSIC,RandomSource.create());
            this.phase=phase;looping=true;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=.001F;
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){if(Minecraft.getInstance().isPaused())return;age++;volume=target>0?Math.min(1,volume+.025F):Math.max(0,volume-.025F);if(target==0&&volume==0)stop();}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void exclusive(PlaySoundEvent event){
        var s=event.getSound();if(ownsChannel()&&s!=null&&!(s instanceof Track)&&(s.getSource()==SoundSource.MUSIC||s.getSource()==SoundSource.RECORDS))event.setSound(null);
    }
    @SubscribeEvent public static void reload(SoundEngineLoadEvent event){playing=null;fading.clear();retry=10;}
    private static void clear(Minecraft mc){if(playing!=null)mc.getSoundManager().stop(playing);for(var t:fading)mc.getSoundManager().stop(t);playing=null;fading.clear();desired=0;lease=0;dimension=null;retry=0;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isAlive()||dimension!=null&&!mc.level.dimension().location().equals(dimension)){clear(mc);return;}
        if(mc.isPaused())return;if(lease>0&&--lease==0)desired=0;
        var manager=mc.getSoundManager();
        fading.removeIf(t->!manager.isActive(t));
        boolean audible=mc.options.getSoundSourceVolume(SoundSource.MUSIC)>0&&mc.options.getSoundSourceVolume(SoundSource.MASTER)>0;
        int requested=audible?desired:0;
        if(playing!=null&&playing.phase!=requested){playing.target=0;fading.add(playing);playing=null;retry=0;}
        if(playing!=null&&++playingAge>60&&!manager.isActive(playing)){playing=null;retry=20;}
        if(requested==0||playing!=null||retry-->0)return;
        var id=ResourceLocation.fromNamespaceAndPath("tnc","music.ink."+SLUGS[requested-1]);
        if(!manager.getAvailableSounds().contains(id)){retry=200;return;}
        if(fading.isEmpty()){mc.getMusicManager().stopPlaying();manager.stop(null,SoundSource.MUSIC);manager.stop(null,SoundSource.RECORDS);}
        playing=new Track(requested);playingAge=0;manager.play(playing);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void overlay(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();Track displayed=playing;
        if(displayed==null||!mc.getSoundManager().isActive(displayed))for(var old:fading)if(mc.getSoundManager().isActive(old)&&old.getVolume()>.02F)displayed=old;
        if(displayed==null||!mc.getSoundManager().isActive(displayed)||displayed.getVolume()<.02F||mc.options.hideGui
                ||mc.options.getSoundSourceVolume(SoundSource.MUSIC)<=0||mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0)return;
        var title=TITLES[displayed.phase-1];int width=Math.min(mc.getWindow().getGuiScaledWidth()-24,Math.max(118,mc.font.width(title)+20));
        var lines=mc.font.split(net.minecraft.network.chat.Component.literal(title),Math.max(40,width-16));int height=21+lines.size()*10;
        var g=event.getGuiGraphics();g.fill(8,8,8+width,8+height,0xB0101010);g.fill(8,8,10,8+height,0xCCDDDDDD);
        g.drawString(mc.font,"正在播放",16,13,0xAAAAAA,false);
        int y=25;for(var line:lines){g.drawString(mc.font,line,16,y,0xEEEEEE,false);y+=10;}
    }
}
