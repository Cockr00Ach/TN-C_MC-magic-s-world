package com.tnc.tnc.tavern;

import com.tnc.tnc.dialogue.*;
import com.tnc.tnc.journey.JourneyData;
import com.tnc.tnc.npc.SelfNpcEntity;
import com.tnc.tnc.skyacademy.*;
import com.tnc.tnc.world.SkyIslandWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** An interrupted opening is resumed on login; only the completed scene advances the story. */
@Mod.EventBusSubscriber(modid="tnc")
public final class TavernOpening {
    public static final BlockPos SEAT=new BlockPos(461,90,292);
    private static final ResourceLocation SCRIPT=ResourceLocation.parse("tnc:self");
    private static final Map<UUID,Scene> SCENES=new HashMap<>();
    private static final class Scene {
        final ServerPlayer player; final ArmorStand seat; final SelfNpcEntity self;
        final DialogueScript script; final List<Integer> times; int age,line=-1;
        Scene(ServerPlayer p,ArmorStand seat,SelfNpcEntity self,DialogueScript script,List<Integer> times){this.player=p;this.seat=seat;this.self=self;this.script=script;this.times=times;}
    }
    public static boolean active(ServerPlayer p){return SCENES.containsKey(p.getUUID());}
    public static void queue(ServerPlayer p){var data=JourneyData.get(p.server);data.player(p.getUUID()).putBoolean("TavernOpeningPending",true);data.setDirty();}
    public static int duration(String text){return Math.max(65,Math.min(210,35+text.codePointCount(0,text.length())*3));}
    private static void start(ServerPlayer p){
        if(SCENES.values().stream().anyMatch(s->s.player.serverLevel()==p.serverLevel()))return;
        var l=p.serverLevel();var origin=SkyAcademyWorld.origin(p.server);if(origin==null||!TavernUpgrade.complete(p.server))return;
        var at=origin.offset(SEAT);
        var npcs=l.getEntitiesOfClass(SelfNpcEntity.class,new AABB(origin.offset(TavernUpgrade.SELF)).inflate(8));if(npcs.isEmpty())return;
        var script=DialogueLoader.get(p.server.getResourceManager(),SCRIPT).orElse(null);if(script==null)return;
        for(var guest:l.getEntitiesOfClass(TavernGuestEntity.class,new AABB(at).inflate(2),e->e.seatId().equals("guest_23")))guest.discard();
        // Remove only our abandoned temporary seats after a server restart.
        p.stopRiding();
        for(var old:l.getEntitiesOfClass(ArmorStand.class,new AABB(at).inflate(3),e->e.getTags().contains("tnc_opening_seat")&&e.getPassengers().isEmpty()))old.discard();
        var seat=new ArmorStand(l,at.getX()+.5,at.getY()+.10625,at.getZ()+.5);
        var seatData=new CompoundTag();seatData.putBoolean("Marker",true);seat.readAdditionalSaveData(seatData);
        seat.setInvisible(true);seat.setNoGravity(true);seat.setInvulnerable(true);seat.addTag("tnc_opening_seat");
        l.addFreshEntity(seat);p.stopRiding();p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,-90,0);p.startRiding(seat,true);
        var self=npcs.get(0);self.getNavigation().stop();self.setYRot(90);self.setYHeadRot(90);self.setYBodyRot(90);
        String name=AcademyLessons.playerName(p);var lines=new ArrayList<DialogueScript.Line>();var times=new ArrayList<Integer>();var list=new ListTag();
        for(var original:script.lines()){
            String speaker=original.speaker().equals("归")?name:original.speaker();
            String text=original.text().replace("，归。","，"+name+"。").replace("酒馆里只有你们两个人。","远处的杯盏声渐渐低下去。");
            var line=new DialogueScript.Line(speaker,text,original.action());lines.add(line);times.add(duration(text));
            var t=new CompoundTag();t.putString("Speaker",speaker);t.putString("Text",text);t.putInt("Ticks",duration(text));list.add(t);
        }
        var personalized=new DialogueScript(script.id(),script.next(),script.theme(),script.quests(),script.activate(),script.requires(),script.excludes(),List.copyOf(lines));
        SCENES.put(p.getUUID(),new Scene(p,seat,self,personalized,List.copyOf(times)));
        var packet=new CompoundTag();packet.putString("Type","tavern_opening");packet.putInt("Self",self.getId());packet.put("Lines",list);AcademyNetwork.send(p,packet);
    }
    private static void end(Scene scene,boolean completed){
        var p=scene.player;p.stopRiding();scene.seat.discard();
        if(!p.hasDisconnected()){
            var at=SkyAcademyWorld.origin(p.server).offset(SEAT).west();
            if(p.serverLevel()==scene.self.level())p.teleportTo(p.serverLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,-90,0);
            var packet=new CompoundTag();packet.putString("Type","tavern_end");AcademyNetwork.send(p,packet);
        }
        if(completed){
            var data=JourneyData.get(p.server);var record=data.player(p.getUUID());record.remove("TavernOpeningPending");record.putBoolean("TavernOpeningDone",true);data.setDirty();
            DialogueProgress.markSeen(p,SCRIPT);
            com.tnc.tnc.dialogue.compat.WhisperingQuestBridge.tryStartQuest(p,scene.script.activate());
            for(var quest:scene.script.quests())com.tnc.tnc.dialogue.compat.WhisperingQuestBridge.onDialogueFinished(p,quest);
            AcademyLessons.finish(p,AcademyRules.ARRIVAL,"arrival");
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();
        for(var it=SCENES.values().iterator();it.hasNext();){var s=it.next();if(s.player.server!=server)continue;
            if(s.player.hasDisconnected()||!s.player.isAlive()||s.player.serverLevel()!=s.self.level()){end(s,false);it.remove();continue;}
            if(s.player.getVehicle()!=s.seat)s.player.startRiding(s.seat,true);
            s.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);s.player.fallDistance=0;
            int index=0,time=s.age++;while(index<s.times.size()&&time>=s.times.get(index))time-=s.times.get(index++);
            if(index>=s.times.size()){end(s,true);it.remove();continue;}
            if(index!=s.line){s.line=index;if(s.script.lines().get(index).speaker().equals("Self"))
                com.tnc.tnc.npc.compat.NpcAnimationTrigger.trigger(s.player,s.self,index==1?"pointcup":index%2==0?"wipehand":"confused");}
        }
        if(server.getTickCount()%20==0)for(var p:server.getPlayerList().getPlayers()){
            var record=JourneyData.get(server).player(p.getUUID());
            if(record.getBoolean("TavernOpeningPending")&&record.getBoolean("Named")&&!active(p)&&p.isAlive()&&p.serverLevel()==SkyIslandWorlds.island(server))start(p);
        }
    }
    @SubscribeEvent public static void protect(net.minecraftforge.event.entity.living.LivingAttackEvent event){if(event.getEntity() instanceof ServerPlayer p&&active(p))event.setCanceled(true);}
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tncopening").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("tavern").executes(c->{var p=c.getSource().getPlayerOrException();if(active(p))return 0;var origin=SkyAcademyWorld.origin(p.server);if(origin==null||!TavernUpgrade.complete(p.server))return 0;var at=origin.offset(SEAT).west();p.teleportTo(SkyIslandWorlds.island(p.server),at.getX()+.5,at.getY(),at.getZ()+.5,-90,0);queue(p);return 1;}))
            .then(net.minecraft.commands.Commands.literal("sight").executes(c->{var p=c.getSource().getPlayerOrException();if(!FirstSightPeak.ready(p.server))return 0;var at=SkyAcademyWorld.at(p.server,"stargaze");var data=AcademyLessons.data(p);data.putInt("Completed",data.getInt("Completed")|AcademyRules.ARRIVAL);data.putBoolean("ReplayFirstSight",true);JourneyData.get(p.server).setDirty();p.teleportTo(SkyIslandWorlds.island(p.server),at.getX()+.5,at.getY(),at.getZ()+4.5,0,-8);AcademyLessons.open(p,"stargaze");return 1;})));
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event){SCENES.values().removeIf(s->{if(s.player.server!=event.getServer())return false;end(s,false);return true;});}
    private TavernOpening(){}
}
