package com.tnc.tnc.warrior;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small, layered cues timed by the server; crowds and scatter arrows share a sound budget. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorUltimateSound {
    private enum Cue { CHARGE, RUSH, SWING, IMPACT, VOLLEY, RECOVER }
    private record Played(ResourceKey<Level> dimension,long tick) {}
    private static final Map<UUID,EnumMap<Cue,Played>> LAST=new HashMap<>();
    private WarriorUltimateSound() {}

    private static boolean permit(ServerPlayer p,Cue cue,int gap) {
        long now=p.level().getGameTime();
        var cues=LAST.computeIfAbsent(p.getUUID(),id->new EnumMap<>(Cue.class));
        var previous=cues.get(cue);
        if(previous!=null && previous.dimension().equals(p.level().dimension())
                && now>=previous.tick() && now-previous.tick()<gap)return false;
        cues.put(cue,new Played(p.level().dimension(),now));
        return true;
    }
    private static void play(ServerPlayer p,Vec3 at,SoundEvent event,float volume,float pitch) {
        p.serverLevel().playSound(null,at.x,at.y,at.z,event,SoundSource.PLAYERS,volume,pitch);
    }
    public static void anticipate(ServerPlayer p,float progress) {
        if(!permit(p,Cue.CHARGE,8))return;
        float t=Math.max(0,Math.min(1,progress));
        play(p,p.position(),SoundEvents.BEACON_POWER_SELECT,.42f+.18f*t,.65f+.55f*t);
        play(p,p.position(),SoundEvents.TRIDENT_RETURN,.25f+.18f*t,.60f+.50f*t);
    }
    public static void rush(ServerPlayer p,boolean spear) {
        if(!permit(p,Cue.RUSH,3))return;
        float variation=(p.level().getGameTime()%3)*.07f;
        play(p,p.position(),SoundEvents.TRIDENT_THROW,spear?.85f:.68f,spear?1.20f+variation:1.55f+variation);
        play(p,p.position(),SoundEvents.PLAYER_ATTACK_SWEEP,spear?.70f:.55f,spear?1.35f:1.75f);
    }
    public static void heavySwing(ServerPlayer p) {
        if(!permit(p,Cue.SWING,6))return;
        play(p,p.position(),SoundEvents.ENDER_DRAGON_FLAP,1.35f,.58f);
        play(p,p.position(),SoundEvents.PLAYER_ATTACK_SWEEP,1.05f,.72f);
    }
    public static void hook(ServerPlayer p) {
        if(!permit(p,Cue.SWING,6))return;
        play(p,p.position(),SoundEvents.TRIDENT_THROW,.95f,.72f);
        play(p,p.position(),SoundEvents.TRIDENT_HIT,.80f,.63f);
    }
    public static void dive(ServerPlayer p) {
        if(!permit(p,Cue.RUSH,8))return;
        play(p,p.position(),SoundEvents.ENDER_DRAGON_FLAP,1.20f,.78f);
        play(p,p.position(),SoundEvents.TRIDENT_THROW,.95f,.65f);
    }
    public static void impact(ServerPlayer p,Vec3 at) {
        if(!permit(p,Cue.IMPACT,8))return;
        play(p,at,SoundEvents.GENERIC_EXPLODE,1.85f,.67f);
        play(p,at,SoundEvents.TRIDENT_HIT_GROUND,1.15f,.58f);
        play(p,at,SoundEvents.ANVIL_LAND,.30f,.65f);
    }
    public static void execution(ServerPlayer p,Vec3 at) {
        if(!permit(p,Cue.IMPACT,8))return;
        play(p,at,SoundEvents.WARDEN_SONIC_BOOM,.90f,1.15f);
        play(p,at,SoundEvents.GENERIC_EXPLODE,1.65f,.62f);
        play(p,at,SoundEvents.TRIDENT_THUNDER,.55f,.93f);
    }
    public static void sheath(ServerPlayer p) {
        if(!permit(p,Cue.RECOVER,10))return;
        play(p,p.position(),SoundEvents.TRIDENT_RETURN,.70f,1.18f);
        play(p,p.position(),SoundEvents.ARMOR_EQUIP_IRON,.48f,.73f);
    }
    public static void bowBurst(ServerPlayer p) {
        if(!permit(p,Cue.IMPACT,10))return;
        play(p,p.position(),SoundEvents.BEACON_ACTIVATE,.70f,1.15f);
        play(p,p.position(),SoundEvents.ENDER_DRAGON_FLAP,.75f,1.10f);
    }
    public static void volley(ServerPlayer p) {
        if(!permit(p,Cue.VOLLEY,6))return;
        play(p,p.position(),SoundEvents.ARROW_SHOOT,.90f,1.15f);
        play(p,p.position(),SoundEvents.TRIDENT_THROW,.42f,1.65f);
    }
    public static void volleyReady(ServerPlayer p) {
        if(!permit(p,Cue.RECOVER,10))return;
        play(p,p.position(),SoundEvents.BEACON_POWER_SELECT,.65f,1.45f);
        play(p,p.position(),SoundEvents.AMETHYST_BLOCK_CHIME,.65f,1.30f);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){LAST.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){LAST.clear();}
}
