package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.journey.JourneyData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Instructor-led lessons advance from observed combat on the server. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorTutorial {
    public static final String MENTOR="TncWarriorTutorialMentor";
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static long sequence;
    private static final class Session {
        long token=++sequence,started,expires,lastAction=Long.MIN_VALUE,seenCast,seenCounter;
        int stage,hits;
        boolean skipped,evidenceReady;
        InstructorEntity tutor;
        TestDummyEntity dummy;
        BlockPos terminal;
        Vec3 home,learner;
        Session(InstructorEntity tutor,BlockPos terminal,long now) {
            this.tutor=tutor;this.terminal=terminal;started=now;expires=now+24000;
        }
    }
    public static CompoundTag data(ServerPlayer player) {
        var root=JourneyData.get(player.server).player(player.getUUID());
        if(!root.contains("WarriorTutorial",10))root.put("WarriorTutorial",new CompoundTag());
        return root.getCompound("WarriorTutorial");
    }
    private static void dirty(ServerPlayer player) { JourneyData.get(player.server).setDirty(); }
    private static boolean ready(ServerPlayer player) {
        return player.isAlive()&&!player.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(player);
    }
    private static void packet(ServerPlayer player,Session session,int stage) {
        var tag=new CompoundTag();tag.putInt("Stage",stage);tag.putLong("Token",session.token);
        tag.putInt("Instructor",session.tutor.getId());tag.putLong("Terminal",session.terminal.asLong());
        tag.putLong("Started",session.started);tag.putString("PlayerName",player.getDisplayName().getString());
        WarriorTutorialNetwork.send(player,tag);
    }
    public static boolean active(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }
    static boolean parryTarget(ServerPlayer player,net.minecraft.world.entity.LivingEntity target) {
        var session=SESSIONS.get(player.getUUID());
        return session!=null&&session.stage==4&&target==session.tutor&&valid(player,session)&&!cinematic(player);
    }
    public static boolean cinematic(ServerPlayer player) {
        var session=SESSIONS.get(player.getUUID());
        return session!=null&&!session.skipped&&WarriorTutorialScript.cinematic(session.stage,(int)(player.level().getGameTime()-session.started));
    }
    public static void ensureMentor(MinecraftServer server) {
        var site=PracticeArena.site(server);
        if(site==null||!PracticeArena.ensure(server))return;
        var level=com.tnc.tnc.world.SkyIslandWorlds.island(server);
        var at=site.south(26).above();
        // Wait for a learner to load entity data before checking for a saved permanent mentor.
        // An unloaded chunk must not be mistaken for a missing NPC or forced to stay in memory.
        if(!level.isPositionEntityTicking(at))return;
        for(var actor:level.getEntitiesOfClass(InstructorEntity.class,new AABB(site).inflate(40),
                entity->entity.getPersistentData().getString(InstructorEntity.ROLE).equals("tutorial_actor"))) {
            var owner=actor.getPersistentData();
            var session=owner.hasUUID(InstructorEntity.OWNER)?SESSIONS.get(owner.getUUID(InstructorEntity.OWNER)):null;
            if(session==null||session.tutor!=actor) {
                if(owner.hasUUID(InstructorEntity.OWNER)&&server.getPlayerList().getPlayer(owner.getUUID(InstructorEntity.OWNER))==null)
                    TrainingGroundData.get(server).release(owner.getUUID(InstructorEntity.OWNER));
                actor.discard();
            }
        }
        for(var dummy:level.getEntitiesOfClass(TestDummyEntity.class,new AABB(site).inflate(40),
                entity->entity.getPersistentData().getBoolean("TutorialDummy"))) {
            var owner=dummy.getPersistentData();
            var session=owner.hasUUID("DummyOwner")?SESSIONS.get(owner.getUUID("DummyOwner")):null;
            if(session==null||session.dummy!=dummy)dummy.discard();
        }
        if(!level.getEntitiesOfClass(InstructorEntity.class,new AABB(at).inflate(8),
                entity->entity.getPersistentData().getBoolean(MENTOR)).isEmpty())return;
        var tutor=WarriorEntities.INSTRUCTOR.get().create(level);
        if(tutor==null)return;
        tutor.setVariant(InstructorEntity.VARIANT_BLADE);tutor.equipForRole();
        tutor.setInvulnerable(true);tutor.getPersistentData().putBoolean(MENTOR,true);
        tutor.setCustomName(Component.literal("莉娅 · 战士入门"));tutor.setCustomNameVisible(true);
        tutor.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);level.addFreshEntity(tutor);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND
                ||!(event.getTarget() instanceof InstructorEntity tutor)||!tutor.getPersistentData().getBoolean(MENTOR))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(!ready(player)||player.distanceToSqr(tutor)>64)return;
        cancel(player);
        var arena=PracticeArena.arenaOf(player);
        if(arena==null)return;
        var session=new Session(tutor,arena.center().above(),player.level().getGameTime());
        SESSIONS.put(player.getUUID(),session);packet(player,session,0);
        WarriorTutorialQuests.sync(player,Math.max(0,data(player).getInt("Stage")));
    }
    public static void action(ServerPlayer player,long token,String action) {
        var session=SESSIONS.get(player.getUUID());long now=player.level().getGameTime();
        if(session==null||session.token!=token||!valid(player,session))return;
        if(!action.equals("cancel")&&!action.equals("skip")
                &&session.lastAction!=Long.MIN_VALUE&&now-session.lastAction<4)return;
        session.lastAction=now;
        if(action.equals("cancel")){cancel(player);return;}
        if(action.equals("begin")&&session.stage==0){begin(player,session);return;}
        int age=(int)(now-session.started);
        if(action.equals("repeat")&&session.stage>=3&&session.stage<=5&&!cinematic(player)) {
            var state=WarriorComboService.state(player.getUUID());
            var skill=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,session.stage-3);
            session.seenCast=state.cooldowns.getOrDefault(skill.id(),0L);
            session.seenCounter=state.counterReady;session.hits=session.dummy.hitCount();
            session.started=now;session.skipped=false;session.evidenceReady=false;
            packet(player,session,session.stage);return;
        }
        if(!cinematic(player))return;
        // The bounded demonstration completes on its own even when its camera is skipped.
        if(session.stage==1&&age<185) {
            if(action.equals("skip"))session.skipped=true;
            return;
        }
        if(action.equals("skip")) {
            session.started=now-WarriorTutorialScript.duration(session.stage);
        } else if(action.equals("next")) {
            var lines=WarriorTutorialScript.lines(session.stage);int line=WarriorTutorialScript.lineAt(session.stage,age);
            int next=lines.subList(0,line+1).stream().mapToInt(WarriorTutorialScript.Line::ticks).sum();
            session.started=now-next;
        } else return;
        packet(player,session,session.stage);
    }
    static boolean begin(ServerPlayer player,Session session) {
        if(player.distanceToSqr(session.tutor)>64)return false;
        if(!TrainingGround.qualified(player)) {
            player.sendSystemMessage(Component.literal("莉娅：先在人物档案选择战士天赋，再以生存模式来练习。"));cancel(player);return false;
        }
        var arena=PracticeArena.arenaOf(player);if(arena==null)return false;
        TrainingGround.dismissAll(player);
        int pad=TrainingGroundData.get(player.server).claim(player.getUUID(),arena.key());
        if(pad<0){player.sendSystemMessage(Component.literal("演武位暂时有人，请稍后再来。"));cancel(player);return false;}
        var home=new Vec3(TrainingGroundRules.padX(pad,arena.x())+.5,arena.y()+1,TrainingGroundRules.padZ(pad,arena.z())+.5);
        var tutor=WarriorEntities.INSTRUCTOR.get().create(player.serverLevel());
        var dummy=WarriorEntities.TEST_DUMMY.get().create(player.serverLevel());
        if(tutor==null||dummy==null){cancel(player);TrainingGroundData.get(player.server).release(player.getUUID());return false;}
        tutor.setVariant(InstructorEntity.VARIANT_BLADE);tutor.equipForRole();
        tutor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(TNMod.DRAGON_SWORD.get()));
        tutor.setTutorialActor(true);
        tutor.getPersistentData().putUUID(InstructorEntity.OWNER,player.getUUID());
        tutor.getPersistentData().putString(InstructorEntity.ROLE,"tutorial_actor");
        tutor.setCustomName(Component.literal("莉娅 · 剑术教官"));tutor.setCustomNameVisible(true);
        tutor.moveTo(home.x,home.y,home.z,180,0);
        dummy.getPersistentData().putUUID("DummyOwner",player.getUUID());
        dummy.getPersistentData().putBoolean("TutorialDummy",true);dummy.resetStats();
        dummy.moveTo(home.x,home.y,home.z-3,0,0);
        if(!player.serverLevel().addFreshEntity(tutor)||!player.serverLevel().addFreshEntity(dummy)) {
            tutor.discard();dummy.discard();cancel(player);TrainingGroundData.get(player.server).release(player.getUUID());return false;
        }
        session.tutor=tutor;session.dummy=dummy;session.home=home;session.learner=home.add(3,0,3);
        session.token=++sequence;player.stopRiding();
        player.teleportTo(player.serverLevel(),session.learner.x,session.learner.y,session.learner.z,180,0);
        if(!data(player).getBoolean("PracticeSword")) {
            var sword=new ItemStack(TNMod.ASHWOOD_JIAN.get());
            if(!player.getInventory().add(sword))player.drop(sword,false);
            data(player).putBoolean("PracticeSword",true);dirty(player);
            player.sendSystemMessage(Component.literal("已领取白蜡木练剑。实操时请将它拿在主手。"));
        }
        int checkpoint=data(player).getInt("Stage");
        setStage(player,session,checkpoint>=3&&checkpoint<=5?checkpoint:1);return true;
    }
    private static boolean valid(ServerPlayer player,Session session) {
        return ready(player)&&session.tutor.isAlive()&&player.level()==session.tutor.level()
                &&player.distanceToSqr(session.tutor)<64*64&&player.level().getGameTime()<session.expires;
    }
    private static void setStage(ServerPlayer player,Session session,int stage) {
        session.stage=stage;session.started=player.level().getGameTime();session.skipped=false;session.evidenceReady=false;
        data(player).putInt("Stage",Math.max(data(player).getInt("Stage"),stage));dirty(player);
        var state=WarriorComboService.state(player.getUUID());
        int slot=stage-3;
        if(slot>=0&&slot<3) {
            var skill=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,slot);
            session.seenCast=state.cooldowns.getOrDefault(skill.id(),0L);
            session.seenCounter=state.counterReady;session.hits=session.dummy.hitCount();
        }
        if(stage>=3&&stage<=5) {
            session.learner=session.home.add(0,0,0);
            player.teleportTo(player.serverLevel(),session.learner.x,session.learner.y,session.learner.z,180,0);
            session.tutor.setPos(session.home.x+2,session.home.y,session.home.z-1);
            var facing=session.learner.subtract(session.tutor.position());
            float yaw=(float)Math.toDegrees(Math.atan2(-facing.x,facing.z));
            session.tutor.setYRot(yaw);session.tutor.yBodyRot=yaw;session.tutor.setYHeadRot(yaw);
            session.tutor.setXRot(0);
        }
        WarriorTutorialQuests.sync(player,stage);packet(player,session,stage);
    }
    public static boolean terminal(ServerPlayer player,BlockPos position) {
        var session=SESSIONS.get(player.getUUID());
        if(session==null)return false;
        if(!valid(player,session)){cancel(player);return false;}
        if(!position.equals(session.terminal)||player.distanceToSqr(Vec3.atCenterOf(position))>64)return false;
        if(session.stage==2) {setStage(player,session,3);return true;}
        return session.stage!=0;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();
        if(server.getTickCount()%100==0)ensureMentor(server);
        for(var player:server.getPlayerList().getPlayers()) {
            if(server.getTickCount()%100==0&&JourneyData.get(server).player(player.getUUID()).getBoolean("Named")) {
                WarriorTutorialQuests.sync(player,Math.max(0,data(player).getInt("Stage")));
                var site=PracticeArena.site(server);
                if(site!=null) {
                    var point=new CompoundTag();point.putBoolean("Waypoint",true);
                    point.putString("Dimension",com.tnc.tnc.world.SkyIslandWorlds.island(server).dimension().location().toString());
                    point.putLong("Target",site.south(26).above().asLong());
                    point.putString("Label","莉娅 · 战士入门");WarriorTutorialNetwork.send(player,point);
                }
            }
            var session=SESSIONS.get(player.getUUID());if(session==null)continue;
            if(!valid(player,session)||session.stage>0&&!TrainingGround.inside(player)) {cancel(player);continue;}
            int age=(int)(player.level().getGameTime()-session.started);
            if(session.stage==0)continue;
            if(session.stage==1&&age>=50&&age<=180)WarriorTutorialDemonstration.tick(player,session.tutor,session.home,age-50);
            if(session.stage==1&&session.skipped&&age>=185){setStage(player,session,2);continue;}
            if(session.stage==1&&age>=WarriorTutorialScript.duration(1)){setStage(player,session,2);continue;}
            if(session.stage==6&&age>=WarriorTutorialScript.duration(6)) {
                data(player).putBoolean("Completed",true);setStage(player,session,7);cancel(player);continue;
            }
            if(cinematic(player)) {
                player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
                if(player.position().distanceToSqr(session.learner)>.36)
                    player.teleportTo(session.learner.x,session.learner.y,session.learner.z);
                if(age==20&&session.stage>=3&&session.stage<=5)WarriorTutorialDemonstration.combo(session.tutor,session.stage-3);
                continue;
            }
            if(session.stage<3||session.stage>5)continue;
            if(session.dummy==null||!session.dummy.isAlive()){cancel(player);continue;}
            if(!session.evidenceReady) {
                var state=WarriorComboService.state(player.getUUID());
                var skill=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,session.stage-3);
                session.seenCast=state.cooldowns.getOrDefault(skill.id(),0L);
                session.seenCounter=state.counterReady;session.hits=session.dummy.hitCount();
                session.evidenceReady=true;
            }
            if(WarriorWeapons.family(player.getMainHandItem())!=WarriorWeaponFamily.LONGSWORD) {
                if(age%80==0)player.displayClientMessage(Component.literal("莉娅：拿起练剑，面向木桩。"),true);continue;
            }
            var state=WarriorComboService.state(player.getUUID());int slot=session.stage-3;
            var skill=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,slot);
            if(slot==1&&state.heldSkill!=null&&player.distanceToSqr(session.tutor)<16&&age%30==0) {
                session.tutor.playAnimation("attack");player.hurt(player.damageSources().mobAttack(session.tutor),1);
            }
            boolean cast=state.cooldowns.getOrDefault(skill.id(),0L)>session.seenCast;
            boolean success=slot==1?state.counterReady>session.seenCounter:session.dummy.hitCount()>session.hits;
            if(cast&&success)setStage(player,session,session.stage+1);
        }
    }
    public static void cancel(ServerPlayer player) {
        var session=SESSIONS.remove(player.getUUID());if(session==null)return;
        packet(player,session,-1);
        if(session.stage>0) {
            session.tutor.discard();if(session.dummy!=null)session.dummy.discard();
            TrainingGroundData.get(player.server).release(player.getUUID());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if(event.getEntity() instanceof ServerPlayer p)cancel(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if(event.getEntity() instanceof ServerPlayer p)cancel(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.clear(); }
    private WarriorTutorial() {}
}
