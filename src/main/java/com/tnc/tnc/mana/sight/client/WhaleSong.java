package com.tnc.tnc.mana.sight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One local voice for the whale selected by this viewer's mana sight. */
final class WhaleSong {
    private static final ResourceLocation ARRIVAL=ResourceLocation.parse("tnc:whale.arrival");
    private static final ResourceLocation ANSWER=ResourceLocation.parse("tnc:whale.answer");
    private final RandomSource phrasing=RandomSource.create();
    private ManaHabitats.Site singer;
    private SoundInstance voice;
    private long nextCall;

    void update(ManaHabitats.Site whale,long clock){
        boolean arrival=singer==null || singer.seed()!=whale.seed();
        if(arrival){
            clear();
            singer=whale;
            phrasing.setSeed(whale.seed());
        }else if(clock<nextCall)return;

        // Give the last call time to decay before the silhouette dissolves.
        if(!arrival && whale.until()-clock<185)return;
        var pos=whale.centre();
        voice=new SimpleSoundInstance(arrival?ARRIVAL:ANSWER,SoundSource.AMBIENT,
                arrival?.84f:.72f,.95f+.08f*phrasing.nextFloat(),phrasing,false,0,
                SoundInstance.Attenuation.LINEAR,pos.x,pos.y,pos.z,false);
        Minecraft.getInstance().getSoundManager().play(voice);
        nextCall=clock+440+phrasing.nextInt(161);
    }

    void clear(){
        if(voice!=null)Minecraft.getInstance().getSoundManager().stop(voice);
        singer=null;
        voice=null;
    }
}
