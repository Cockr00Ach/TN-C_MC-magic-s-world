package com.tnc.tnc.world.ink.client;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Opt-in integrated-server tracking loss/re-entry/removal regression in disposable dev save. */
final class InkDragonVisibilityAudit {
    private static boolean setup,away,returning,killed,captured,nearVisible;
    private static volatile boolean ready,saved,keptTicking;
    private static volatile int serverTicks;
    private static volatile String failure;
    private static volatile int id,ground;
    private static int ticks,stageTick;
    private static UUID player;
    private static net.minecraft.world.phys.Vec3 remoteStart;
    private static boolean remoteMoved;
    private static volatile UUID dragonUuid;
    private static boolean tracked(Minecraft mc){
        for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(dragonUuid))return true;
        return false;
    }
    static void tick(Minecraft mc)throws Exception{
        if(!setup){setup=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(12);
            mc.getWindow().setWidth(1280);mc.getWindow().setHeight(900);mc.resizeDisplay();player=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{try{
                var level=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);
                level.getChunkAt(new BlockPos(16,0,16));
                var old=new ArrayList<Entity>();level.getAllEntities().forEach(old::add);
                for(var e:old)if(e instanceof InkCelestialDragonEntity)e.discard();
                ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(player);p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(level,16,ground+20,61,180,10);
                var e=InkBossRegistration.INK_CELESTIAL_DRAGON.create(level);e.moveTo(16,ground+8,16,90,0);e.setGroundedForObservation(false);level.addFreshEntity(e);id=e.getId();dragonUuid=e.getUUID();ready=true;
            }catch(Throwable e){failure=e.toString();}});return;}
        if(failure!=null)throw new IllegalStateException(failure);
        if(!ready)return;
        if(++ticks>700)throw new IllegalStateException("Visibility audit timeout: away="+away+" returning="+returning+" killed="+killed+" cache="+InkDragonDistantView.cachedViews()+" tracked="+tracked(mc)+" drawn="+InkDragonDistantView.drawnViews()+" player="+mc.player.position());
        mc.setScreen(null);
        if(!away&&ticks>=35&&tracked(mc)&&InkDragonDistantView.drawnLive()>0)nearVisible=true;
        if(!away&&ticks>=60&&tracked(mc)&&nearVisible&&InkDragonDistantView.cachedViews()>0){
            away=true;stageTick=ticks;mc.options.renderDistance().set(2);
            mc.getSingleplayerServer().execute(()->{try{
                mc.getSingleplayerServer().getPlayerList().setViewDistance(2);
                var dragon=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM).getEntity(dragonUuid);serverTicks=dragon==null?0:dragon.tickCount;
                mc.getSingleplayerServer().getPlayerList().getPlayer(player).teleportTo(mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM),115,ground+35,210,180,6);
            }catch(Throwable e){failure=e.toString();}});
        }
        if(away&&!returning&&ticks-stageTick>30&&!tracked(mc)){
            var at=InkDragonDistantView.remotePosition(dragonUuid);
            if(at!=null){if(remoteStart==null)remoteStart=at;else if(at.distanceTo(remoteStart)>1)remoteMoved=true;}
        }
        if(away&&!returning&&ticks-stageTick>120&&!tracked(mc)&&remoteMoved&&InkDragonDistantView.drawnViews()>0){
            if(!captured){captured=true;Screenshot.grab(mc.gameDirectory,"ink_dragon_distant_v5.png",mc.getMainRenderTarget(),m->saved=true);}
            if(!saved)return;
            returning=true;stageTick=ticks;
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var dragon=level.getEntity(dragonUuid);
                keptTicking=dragon!=null&&dragon.tickCount>serverTicks+40&&InkDragonKeepAlive.active(level);
                mc.getSingleplayerServer().getPlayerList().getPlayer(player).teleportTo(level,16,ground+20,30,180,10);
            });
        }
        if(returning&&!killed&&ticks-stageTick>70&&tracked(mc)&&InkDragonDistantView.drawnViews()==0){
            killed=true;stageTick=ticks;
            mc.getSingleplayerServer().execute(()->{var e=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM).getEntity(dragonUuid);if(e!=null)e.discard();});
        }
        if(killed&&ticks-stageTick>50&&!InkDragonDistantView.hasRemote(dragonUuid)&&!tracked(mc)){
            var png=javax.imageio.ImageIO.read(Path.of("D:/ModTest/work/ink-crane-client-audit/screenshots/ink_dragon_distant_v5.png").toFile());
            if(png.getWidth()<1000||png.getHeight()<700)throw new IllegalStateException("Invalid audit framebuffer size");
            var report=new LinkedHashMap<String,Object>(InkCelestialDragonRenderer.diagnostics());
            report.put("networkSnapshotReceived",true);report.put("untrackedDragonStillRendered",captured);report.put("realEntityReentrySuppressesProxy",returning);
            report.put("trackedEntityDrawnByIndependentPass",nearVisible);
            report.put("serverBossKeptTickingBeyondSimulationRange",keptTicking);
            report.put("flyingProxyMovedWhileRealEntityUntracked",remoteMoved);
            if(!keptTicking)throw new IllegalStateException("Boss region did not keep ticking while player was far away");
            report.put("discardClearsProxy",true);report.put("nativeGlError",org.lwjgl.opengl.GL11.glGetError());report.put("scope","Integrated server view distance 2: tracking loss, distant render, re-entry, discard; no full-pack FPS claim");
            report.put("screenshotWidth",png.getWidth());report.put("screenshotHeight",png.getHeight());
            Files.writeString(Path.of("D:/ModTest/work/ink-dragon-visibility-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
}
