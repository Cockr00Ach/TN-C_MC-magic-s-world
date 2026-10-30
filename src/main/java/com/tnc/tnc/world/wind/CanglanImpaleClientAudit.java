package com.tnc.tnc.world.wind;

import com.tnc.tnc.world.wind.client.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in native integrated-server execution rehearsal, never shipped in scoped releases. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class CanglanImpaleClientAudit {
    private static volatile int bossId=-1;private static volatile boolean started;
    private static boolean setup,opening;private static int ticks,settle;private static double maxTipError;
    private static boolean sawPin,sawFlight,sawLock,sawCamera;private static final Set<String> images=new HashSet<>();
    private static boolean finiteThroughout=true,resyncPendingRetained,resyncPoseObserved,finalServerFinite;
    private static int resyncTicks;private static CanglanWarlordEntity resyncBody;private static CanglanCinematicNetwork.Execution resyncState;
    private static final List<String> failures=new ArrayList<>();
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void load(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){if(Boolean.getBoolean("tnc.canglanImpaleAudit"))e.enqueueWork(()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
        });}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(!Boolean.getBoolean("tnc.canglanImpaleAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null){
            if(mc.getOverlay()==null&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!opening){opening=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"impale-gallery");}
            if(mc.screen!=null)for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&Set.of("Proceed","Continue","I know what I'm doing!","继续").contains(b.getMessage().getString())){b.onPress();break;}
            return;
        }
        mc.options.pauseOnLostFocus=false;mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);
        if(!setup){setup=true;mc.setScreen(null);var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var level=p.serverLevel();
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.setDayTime(6000);
                for(int x=-24;x<=24;x++)for(int z=-24;z<=32;z++)level.setBlock(new BlockPos(x,95,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(var old:level.getEntitiesOfClass(CanglanWarlordEntity.class,new net.minecraft.world.phys.AABB(-100,60,-100,100,180,100)))old.discard();
                p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(false);p.getAbilities().invulnerable=false;p.setNoGravity(false);
                if(com.tnc.tnc.combat.DownedCombat.isDowned(p))com.tnc.tnc.combat.DownedCombat.revive(p);
                p.setHealth(9);p.setAbsorptionAmount(0);p.teleportTo(.5,96,6.5);
                var b=CanglanWarlordRegistration.CANGLAN_WARLORD.create(level);b.setPos(.5,96,.5);b.setNoAi(true);b.setNoGravity(true);
                var tag=new CompoundTag();b.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);b.readAdditionalSaveData(tag);
                level.addFreshEntity(b);b.setTarget(p);b.battle().pauseTest();bossId=b.getId();
            });return;
        }
        if(bossId<0||mc.level.getEntity(bossId)==null)return;
        if(!started){if(++settle==40){mc.gui.getChat().clearMessages(false);var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var b=(CanglanWarlordEntity)p.level().getEntity(bossId);
            p.setHealth(9);p.teleportTo(.5,96,6.5);b.battle().resumeTest();b.setTarget(p);if(!b.battle().begin(15,p))failures.add("Native execution did not start");started=true;
        });}return;}
        ticks++;finiteThroughout&=Float.isFinite(mc.player.getHealth())&&Float.isFinite(mc.player.getAbsorptionAmount());var state=CanglanExecutionClient.state(bossId);
        if(state!=null){
            int age=(int)CanglanExecutionClient.age(state,0);sawCamera|=CanglanCinematicCamera.active();sawLock|=CanglanExecutionClient.locked();
            sawPin|=state.phase()==1;sawFlight|=state.phase()==2;
            for(var sample:Map.of(14,"impale-windup.png",28,"impale-pierced.png",55,"impale-raised.png",81,"impale-flung.png").entrySet())if(age>=sample.getKey()&&age<sample.getKey()+5&&images.add(sample.getValue()))Screenshot.grab(mc.gameDirectory,sample.getValue(),mc.getMainRenderTarget(),m->{});
        }
        if(ticks>150&&Boolean.getBoolean("tnc.canglanResyncAudit")){
            if(!resync(mc))return;
        }
        if(ticks>150){
            if(!sawPin||!sawFlight||!sawLock||!sawCamera)failures.add("Missing pin/throw/input lock/camera: "+sawPin+"/"+sawFlight+"/"+sawLock+"/"+sawCamera);
            if(images.size()!=(Boolean.getBoolean("tnc.canglanResyncAudit")?5:4))failures.add("Not all execution stages rendered");
            if(maxTipError>.12)failures.add("Rendered spear and authoritative tip diverged: "+maxTipError);
            if(CanglanCinematicCamera.active()||CanglanExecutionClient.locked()||mc.player.isNoGravity())failures.add("Camera, input or gravity did not restore");
            if(!finiteThroughout)failures.add("Execution left non-finite client health or absorption");
            var report=new LinkedHashMap<String,Object>();report.put("failures",failures);report.put("screenshots",images);report.put("maximumRenderedTipError",maxTipError);report.put("sawPin",sawPin);report.put("sawFlight",sawFlight);report.put("sawInputLock",sawLock);report.put("cameraRestored",!CanglanCinematicCamera.active());report.put("finiteHealthThroughout",finiteThroughout);
            report.put("pendingStateRetained",resyncPendingRetained);report.put("noTriggerPoseObserved",resyncPoseObserved);report.put("finalServerHealthFinite",finalServerFinite);
            Files.writeString(Path.of(System.getProperty("tnc.canglanImpaleReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static boolean resync(Minecraft mc){
        if(resyncTicks++==0){
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);finalServerFinite=Float.isFinite(p.getHealth())&&Float.isFinite(p.getAbsorptionAmount());});
            resyncBody=CanglanWarlordRegistration.CANGLAN_WARLORD.create(mc.level);resyncBody.setId(890321);resyncBody.setNoAi(true);resyncBody.setNoGravity(true);
            var at=mc.player.position().add(net.minecraft.world.phys.Vec3.directionFromRotation(0,mc.player.getYRot()).scale(10));resyncBody.setPos(at);
            var tag=new CompoundTag();resyncBody.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);resyncBody.readAdditionalSaveData(tag);
            resyncBody.yBodyRot=resyncBody.yBodyRotO=mc.player.getYRot()+180;resyncBody.setYRot(resyncBody.yBodyRot);resyncBody.setYHeadRot(resyncBody.yBodyRot);
            resyncState=new CanglanCinematicNetwork.Execution(mc.level.dimension().location(),resyncBody.getId(),-1,mc.level.getGameTime()-54,CanglanExecutionPose.PINNED);
            CanglanExecutionClient.accept(resyncState);return false;
        }
        if(resyncTicks==4){resyncPendingRetained=CanglanExecutionClient.state(resyncBody.getId())!=null;mc.level.putNonPlayerEntity(resyncBody.getId(),resyncBody);}
        if(resyncTicks==9){
            if(images.add("resync-raised-without-trigger.png"))Screenshot.grab(mc.gameDirectory,"resync-raised-without-trigger.png",mc.getMainRenderTarget(),m->{});
            CanglanExecutionClient.accept(new CanglanCinematicNetwork.Execution(resyncState.dimension(),resyncState.boss(),-1,resyncState.started(),CanglanExecutionPose.WINDUP));
            if(CanglanExecutionClient.state(resyncBody.getId()).phase()!=CanglanExecutionPose.PINNED)failures.add("Old phase regressed a newer pose");
        }
        if(resyncTicks<12)return false;
        CanglanExecutionClient.accept(new CanglanCinematicNetwork.Execution(resyncState.dimension(),resyncState.boss(),-1,resyncState.started(),CanglanExecutionPose.END));
        if(!resyncPendingRetained||!resyncPoseObserved||!finalServerFinite)failures.add("Late tracking/no-trigger recovery or server finite-health check failed");
        resyncBody.discard();return true;
    }
    @SubscribeEvent public static void rendered(RenderLevelStageEvent e){
        if(!Boolean.getBoolean("tnc.canglanImpaleAudit")||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();
        if(resyncBody!=null&&resyncTicks>=5&&resyncTicks<=10){
            var p=CanglanExecutionClient.state(resyncBody.getId());
            if(p!=null){var predicted=CanglanExecutionPose.offset(CanglanExecutionClient.age(p,e.getPartialTick()),resyncBody.yBodyRot,1);double error=predicted.distanceTo(CanglanWarlordRenderer.tip(resyncBody));
                if(resyncBody.canglanMove()==0&&error<.12&&CanglanWarlordRenderer.tip(resyncBody).y>8)resyncPoseObserved=true;
            }
        }
        var state=CanglanExecutionClient.state(bossId);if(state==null||state.phase()!=1)return;
        if(mc.level.getEntity(bossId) instanceof CanglanWarlordEntity b){
            var predicted=CanglanExecutionPose.offset(CanglanExecutionClient.age(state,e.getPartialTick()),b.yBodyRot,b.battleScale());
            maxTipError=Math.max(maxTipError,predicted.distanceTo(CanglanWarlordRenderer.tip(b)));
        }
    }
}
