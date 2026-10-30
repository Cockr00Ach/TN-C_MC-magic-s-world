package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in integration audit in the disposable dev save only. */
final class InkEncounterAudit {
    private static boolean setup,launched;
    private static volatile boolean prepared;
    private static volatile String failure;
    private static int ticks,captures,saved,finishTicks;
    private static final Set<Integer> heard=new HashSet<>();
    private static final Set<Integer> shots=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<Integer> viewpoints=new HashSet<>();
    private static volatile int phase,intro,move,moveAge,tigerMove,tigerAge;
    private static volatile boolean complete,portalReturn,protectedIntro;
    static void tick(Minecraft mc)throws Exception{
        if(!setup){
            setup=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(12);mc.options.guiScale().set(2);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(.6);
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(.7);
            mc.getWindow().setWidth(1280);mc.getWindow().setHeight(900);mc.resizeDisplay();
            var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                var profile=com.tnc.tnc.journey.JourneyData.get(p.server);profile.player(uuid).putBoolean("WelcomeShown",true);profile.setDirty();p.getInventory().clearContent();
                InkRealmTravel.teleport(p,mc.getSingleplayerServer().overworld(),0,0);
                var origin=p.level().dimension();var at=p.position();
                if(!InkRealmTravel.enter(p)||!InkRealmTravel.leave(p)||!p.level().dimension().equals(origin)||p.position().distanceTo(at)>3)throw new IllegalStateException("Return point failed");
                portalReturn=true;InkRealmTravel.enter(p);var level=p.serverLevel();InkEncounter.get(level).reset(level);
                var old=new ArrayList<Entity>();level.getAllEntities().forEach(old::add);for(var e:old)
                    if(e instanceof InkPortalEntity||e instanceof Mob mob&&InkBossAudio.phaseOf(mob)>0||e instanceof InkCranePhantomEntity||e instanceof InkTigerEffectEntity||e instanceof InkRitualEntity)e.discard();
                for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.getChunkAt(new BlockPos(x*16,0,z*16));
                int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0);
                p.setGameMode(GameType.SURVIVAL);p.teleportTo(level,0,y+1,-26,0,0);
                p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE));
                p.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION));
                p.getInventory().setItem(2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                p.getInventory().setItem(3,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
                if(!InkPortalEntity.place(p))throw new IllegalStateException("Portal placement failed");
                prepared=true;
            }catch(Throwable e){failure=e.toString();}});return;
        }
        if(failure!=null){Files.writeString(Path.of("D:/ModTest/work/ink-encounter-audit-failure.txt"),failure);mc.stop();return;}
        if(++ticks>1500){failure="Timed out: phase="+phase+" intro="+intro+" move="+move+" captures="+captures;return;}
        if(!launched){
            if(prepared&&ticks>=45&&ticks<90){
                if(shots.add(90))mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                if(ticks>=65&&shots.add(91)){
                    if(InkScreenEffects.cursed())throw new IllegalStateException("Unexpected curse on realm entry");
                    Screenshot.grab(mc.gameDirectory,"ink_inventory_uncursed.png",mc.getMainRenderTarget(),m->saved++);captures++;
                }
                return;
            }
            mc.setScreen(null);
            if(prepared&&ticks>=120){launched=true;mc.getSingleplayerServer().execute(()->{
                if(!InkEncounter.start(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())))failure="Encounter did not start";
            });}return;
        }
        if(phase>0&&intro>22&&intro<48&&shots.add(phase)){
            if(!InkEncounterCamera.active()||mc.getCameraEntity()==mc.player||mc.screen!=null){failure="Cinematic not active for phase "+phase;return;}
            if(InkEncounterCamera.aimAlignment()<.99){failure="Intro camera is not aimed at its boss";return;}
            if(InkBossMusic.phase()!=phase||!InkBossMusic.audible()){failure="Music not playing for phase "+phase;return;}
            heard.add(phase);
            Screenshot.grab(mc.gameDirectory,"ink_phase_"+phase+"_intro.png",mc.getMainRenderTarget(),m->saved++);captures++;
        }
        if(tigerMove==4&&tigerAge>=30&&tigerAge<48&&shots.add(24)){
            if(!InkEncounterCamera.active()||InkEncounterCamera.aimAlignment()<.99){failure="Execution camera misses the head";return;}
            Screenshot.grab(mc.gameDirectory,"ink_tiger_execution_closeup.png",mc.getMainRenderTarget(),m->saved++);captures++;
        }
        if(move==5&&moveAge>=98&&moveAge<130&&shots.add(15)){
            if(!InkScreenEffects.cursed()){failure="Dragon drain did not apply status on client";return;}
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        }
        if(shots.contains(15)&&!shots.contains(25)&&moveAge>104&&shots.add(25)){
            Screenshot.grab(mc.gameDirectory,"ink_dragon_drain_cursed_inventory.png",mc.getMainRenderTarget(),m->saved++);captures++;
        }
        if(move>=3&&move<=4&&moveAge>=65&&moveAge<90&&shots.add(10+move)){
            Screenshot.grab(mc.gameDirectory,"ink_dragon_attack_"+move+".png",mc.getMainRenderTarget(),m->saved++);captures++;
        }
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var level=p.serverLevel();var encounter=InkEncounter.get(level);
            phase=encounter.phase();intro=encounter.introTicks();
            var boss=encounter.bossId()==null?null:level.getEntity(encounter.bossId());
            if(boss instanceof Mob mob){
                if(intro>0){if(!mob.isInvulnerable()||!mob.isNoAi())throw new IllegalStateException("Unprotected intro");protectedIntro=true;}
                if(intro==0&&phase==1)mob.hurt(level.damageSources().genericKill(),100000);
                if(intro==0&&mob instanceof InkWingedTigerEntity tiger){
                    tiger.setNoAi(true);tigerMove=tiger.inkMove();tigerAge=tiger.moveAge();
                    if(tigerMove==0&&!shots.contains(24)){
                        p.setGameMode(GameType.SURVIVAL);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(100);
                        var at=tiger.position().add(net.minecraft.world.phys.Vec3.directionFromRotation(0,tiger.getYRot()).scale(22));
                        p.teleportTo(level,at.x,at.y,at.z,tiger.getYRot()+180,0);
                        if(!tiger.startInkAttack(4,p))failure="Execution did not start";
                    }else if(tigerMove==0&&shots.contains(24)){
                        p.setGameMode(GameType.CREATIVE);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);p.setHealth(20);
                        tiger.hurt(level.damageSources().genericKill(),100000);
                    }
                }
                if(intro==0&&boss instanceof InkCelestialDragonEntity dragon){
                    move=dragon.inkMove();moveAge=dragon.moveAge();dragon.setNoAi(true);
                    if(move==0){
                        if(!shots.contains(13))dragon.startInkAttack(3,p);
                        else if(!shots.contains(14))dragon.startInkAttack(4,p);
                        else if(!shots.contains(15)){
                            p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();
                            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,400));
                            p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE));
                            p.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION));
                            dragon.startInkAttack(5,p);
                        }
                        else {dragon.hurt(level.damageSources().genericKill(),100000);complete=encounter.phase()==0;}
                    }
                    // Safe creative viewpoint above the target, after the cinematic restores camera.
                    if(move<=4&&moveAge>=25&&viewpoints.add(move)){
                        var center=dragon.effectVector("point");p.teleportTo(level,center.x+42,center.y+30,center.z+46,137,27);
                        p.getAbilities().flying=true;p.onUpdateAbilities();
                    }
                }
            }
        }catch(Throwable e){failure=e.toString();}});
        if(complete&&saved==captures){
            // Wait for real expiry, not an explicit remove, then verify GUI after it.
            if(InkScreenEffects.cursed())return;
            if(++finishTicks==1)mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            if(finishTicks==20){Screenshot.grab(mc.gameDirectory,"ink_inventory_after_curse.png",mc.getMainRenderTarget(),m->saved++);captures++;return;}
            if(finishTicks<60)return;mc.setScreen(null);
            if(InkBossMusic.ownsChannel()){failure="Music did not stop after trial completion";return;}
            var report=new java.util.LinkedHashMap<String,Object>(Map.of(
                    "threePhasesCompleted",true,"portalReturn",portalReturn,"introProtected",protectedIntro,"screenshots",captures,
                    "nativeGlError",org.lwjgl.opengl.GL11.glGetError(),"cameraRestored",mc.getCameraEntity()==mc.player,
                    "musicPhasesAudible",heard,"musicStopsAfterBattle",true,"ordinaryInventoryChecked",shots.contains(91)));
            report.put("structure",InkCelestialDragonRenderer.structureDiagnostics());
            Files.writeString(Path.of("D:/ModTest/work/ink-encounter-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            mc.stop();
        }
    }
}
