package com.tnc.tnc.world.ink;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Server-authoritative cast cues and music heartbeats, including late joiners and untracked dragons. */
@Mod.EventBusSubscriber(modid="tnc")
public final class InkBossAudio {
    private static final SoundEvent[] CAST=new SoundEvent[3];
    private static final String[] SLUGS={"crane","tiger","dragon"};
    private InkBossAudio(){}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registry {
        @SubscribeEvent public static void sounds(RegisterEvent event){event.register(Registries.SOUND_EVENT,helper->{
            for(int i=0;i<3;i++){
                var id=ResourceLocation.fromNamespaceAndPath("tnc","ink."+SLUGS[i]+".cast");
                CAST[i]=SoundEvent.createFixedRangeEvent(id,96);helper.register(id,CAST[i]);
            }
        });}
    }
    public static void cast(Mob boss,int phase){
        if(!(boss.level() instanceof ServerLevel)||phase<1||phase>3||CAST[phase-1]==null)return;
        long now=boss.level().getGameTime();var n=boss.getPersistentData();
        if(n.contains("InkLastCry")&&now-n.getLong("InkLastCry")<30)return;
        n.putLong("InkLastCry",now);
        boss.playSound(CAST[phase-1],1F,1F+(boss.getRandom().nextFloat()-.5F)*.06F);
        for(var player:((ServerLevel)boss.level()).players()){
            double distance=player.distanceTo(boss);if(distance<160)InkEncounterNetwork.shake(player,24,(float)(11*(1-distance/160)));
        }
    }
    @SubscribeEvent public static void music(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
        var trial=level.dimension().equals(InkRealmTravel.INK_REALM)?InkEncounter.get(level):null;
        for(var player:level.players()){
            int phase=0;
            if(player.isAlive()){
                if(trial!=null&&trial.phase()>0&&trial.musicContains(player.position()))phase=trial.musicPhase();
                if(phase==0){
                    var boss=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(160),e->e.isAlive()&&phaseOf(e)>0)
                            .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                    if(boss!=null)phase=phaseOf(boss);
                }
            }
            InkEncounterNetwork.music(player,phase);
        }
    }
    public static int phaseOf(Mob boss){return boss instanceof InkCraneBossEntity?1:boss instanceof InkWingedTigerEntity?2:boss instanceof InkCelestialDragonEntity?3:0;}
}
