package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Controlled idle-dragon A/B/A/B and ground/high sky checks, disposable world only. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkDragonBulkAudit {
    private static boolean setup,saving;
    private static boolean vanilla,fastPicture;
    private static volatile boolean ready,saved;
    private static volatile boolean fastSaved;
    private static volatile String error;
    private static volatile int ground,id;
    private static int state,ticks,frames;
    private static long start,lastEnd;
    private static final List<Double> worldMs=new ArrayList<>(),frameMs=new ArrayList<>();
    private static final List<Map<String,Object>> phases=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static long initialBulk,initialLegacy;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) throws Exception {
        if(!Boolean.getBoolean("tnc.inkDragonBulkAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!setup){
            setup=true;
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(Path.of("D:/ModTest/work")))throw new IllegalStateException("Disposable world required");
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.options.framerateLimit().set(260);
            mc.getSingleplayerServer().execute(()->{try{
                var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);
                realm.getChunkAt(new BlockPos(16,0,16));ground=realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
                var previous=new ArrayList<net.minecraft.world.entity.Entity>();realm.getAllEntities().forEach(previous::add);
                for(var old:previous)if(old instanceof InkCelestialDragonEntity)old.discard();
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(realm,16,ground,61,180,0);ready=true;
            }catch(Throwable failure){error=failure.toString();}});return;
        }
        if(error!=null)throw new IllegalStateException(error);
        if(!ready||!mc.level.dimension().equals(InkRealmTravel.INK_REALM))return;
        mc.setScreen(null);ticks++;
        if(state<2){
            if(ticks<80)return;
            double horizon=mc.level.getLevelData().getHorizonHeight(mc.level);
            if(horizon>mc.player.getEyeY())failures.add("Black below-horizon sky at stage "+state+": "+horizon);
            if(mc.level.getLevelData().getClearColorScale()!=1)failures.add("Incorrect void clear color scale");
            if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,(state==0?"ink-sky-ground":"ink-sky-high")+(vanilla?"-no-shaders":"")+".png",mc.getMainRenderTarget(),m->saved=true);return;}
            if(!saved)return;
            saving=false;saved=false;ticks=0;state++;
            if(state==2&&vanilla){
                Files.writeString(Path.of("D:/ModTest/work/ink-sky-height-20261024-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"shaderAndVanillaSkyChecked",true,"groundY",ground,"horizon",horizon,"clearColorScale",mc.level.getLevelData().getClearColorScale())));mc.stop();return;
            }
            if(state==1)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(p.serverLevel(),16,ground+120,61,180,0);});
            else mc.getSingleplayerServer().execute(()->{try{
                var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);
                var previous=new ArrayList<net.minecraft.world.entity.Entity>();realm.getAllEntities().forEach(previous::add);
                for(var old:previous)if(old instanceof InkCelestialDragonEntity)old.discard();
                var dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(realm);dragon.setNoAi(true);dragon.setGroundedForObservation(false);dragon.moveTo(16,ground+8,16,90,0);realm.addFreshEntity(dragon);id=dragon.getId();
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(realm,16,ground+20,61,180,10);
            }catch(Throwable failure){error=failure.toString();}});
            return;
        }
        if(mc.level.getEntity(id)==null)return;
        if(state==2){state=Boolean.getBoolean("tnc.inkDragonBulkVisualAudit")?4:3;beginPhase();}
        if(state>=3&&state<=6&&frames>=200){
            var record=new LinkedHashMap<String,Object>();var stats=InkDragonBulkSubmit.diagnostics();
            record.put("mode",state%2==1?"legacy":"bulk");record.put("samples",worldMs.size());
            record.put("worldMedianMs",percentile(worldMs,.5));record.put("worldP95Ms",percentile(worldMs,.95));
            record.put("frameMedianMs",percentile(frameMs,.5));record.put("frameP95Ms",percentile(frameMs,.95));
            record.put("bulkVertices",((Number)stats.get("bulkVertices")).longValue()-initialBulk);
            record.put("legacyVertices",((Number)stats.get("legacyVertices")).longValue()-initialLegacy);
            phases.add(record);System.out.println("INK DRAGON A/B: "+record);
            if(state%2==0&&((Number)record.get("bulkVertices")).longValue()==0)failures.add("Bulk API never activated");
            if(state==4&&!fastPicture){fastPicture=true;Screenshot.grab(mc.gameDirectory,"ink-dragon-bulk.png",mc.getMainRenderTarget(),m->fastSaved=true);}
            if(Boolean.getBoolean("tnc.inkDragonBulkVisualAudit")){state=7;}else
            if(++state<=6)beginPhase();
        }
        if(state==7){
            if(fastPicture&&!fastSaved)return;
            int gl=org.lwjgl.opengl.GL11.glGetError();if(gl!=0)failures.add("OpenGL error: "+gl);
            var iris=Class.forName("net.irisshaders.iris.api.v0.IrisApi");var api=iris.getMethod("getInstance").invoke(null);
            boolean shaders=(boolean)iris.getMethod("isShaderPackInUse").invoke(api);if(!shaders)failures.add("Shader pipeline not active");
            var report=new LinkedHashMap<String,Object>();report.put("failures",failures);report.put("phases",phases);
            report.put("structure",InkCelestialDragonRenderer.structureDiagnostics());report.put("bulk",InkDragonBulkSubmit.diagnostics());
            report.put("groundY",ground);report.put("horizon",mc.level.getLevelData().getHorizonHeight(mc.level));report.put("clearColorScale",mc.level.getLevelData().getClearColorScale());report.put("shadersActive",shaders);
            Files.writeString(Path.of(Boolean.getBoolean("tnc.inkDragonBulkVisualAudit")?"D:/ModTest/work/ink-dragon-bulk-20261024-visual-report.json":"D:/ModTest/work/ink-dragon-bulk-20261024-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            // The remaining sky check is on the same GPU/client, but without shaders.
            var config=iris.getMethod("getConfig").invoke(api);Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply",boolean.class).invoke(config,false);
            vanilla=true;state=0;ticks=0;saving=false;saved=false;
            mc.getSingleplayerServer().execute(()->{var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var dragon=realm.getEntity(id);if(dragon!=null)dragon.discard();var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(realm,16,ground,61,180,0);});
        }
    }
    private static void beginPhase(){
        System.setProperty("tnc.inkDragonLegacyVertices",Boolean.toString(state%2==1));frames=0;lastEnd=0;worldMs.clear();frameMs.clear();
        var stats=InkDragonBulkSubmit.diagnostics();initialBulk=((Number)stats.get("bulkVertices")).longValue();initialLegacy=((Number)stats.get("legacyVertices")).longValue();
    }
    private static double percentile(List<Double> samples,double p){if(samples.isEmpty())return -1;var sorted=new ArrayList<>(samples);Collections.sort(sorted);return sorted.get((int)Math.min(sorted.size()-1,Math.floor(p*sorted.size())));}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(!Boolean.getBoolean("tnc.inkDragonBulkAudit")||state<3||state>6||InkShaderPass.shadow())return;
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)start=System.nanoTime();
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||start==0)return;
        org.lwjgl.opengl.GL11.glFinish();long end=System.nanoTime();
        if(++frames>80){worldMs.add((end-start)/1e6);if(lastEnd!=0)frameMs.add((end-lastEnd)/1e6);}
        lastEnd=end;start=0;
    }
}
