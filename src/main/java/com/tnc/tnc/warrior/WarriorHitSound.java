package com.tnc.tnc.warrior;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class WarriorHitSound {
    public record Profile(String soundId,float volume,float pitch,int interval){
        public SoundEvent sound(){return java.util.Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(net.minecraft.resources.ResourceLocation.tryParse(soundId)),soundId);}
    }
    public static Profile profile(WarriorWeaponFamily family,boolean arrow){
        if(arrow)return new Profile("minecraft:entity.arrow.hit",.45f,1.15f,3);
        return switch(family){
            case GREATSWORD -> new Profile("minecraft:block.anvil.land",.42f,.85f,4);
            case HAMMER -> new Profile("minecraft:block.anvil.land",.58f,.58f,5);
            case SPEAR -> new Profile("minecraft:item.trident.hit",.5f,1.2f,3);
            default -> new Profile("minecraft:item.shield.block",.42f,1.65f,3);
        };
    }
    public static boolean ready(long now,long previous,int interval){return previous==Long.MIN_VALUE||now-previous>=interval;}
    private WarriorHitSound(){}
}
