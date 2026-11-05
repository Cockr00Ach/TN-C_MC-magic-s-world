package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.world.skyhaven.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Explicit isolated three-angle screenshot audit; never runs for ordinary players. */
public final class SeaTornadoNativeAudit {
    private static int phase,ticks;
    private static boolean prepared,saving,configured;
    private static long started;
    private static volatile boolean ready;
    private static volatile SeaGyreLayout.Gyre storm;
    private static final List<String> images=new ArrayList<>();
    private static final List<Map<String,Object>> cameras=new ArrayList<>();
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    private SeaTornadoNativeAudit(){}
    public static void tick(Minecraft mc)throws Exception{
        if(!Boolean.getBoolean("tnc.skyhavenTornadoAudit")||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))
            throw new IllegalStateException("Separate prepared audit save required");
        if(!configured){
            configured=true;started=System.nanoTime();Files.createDirectories(output());mc.options.fov().set(90);mc.options.renderDistance().set(10);mc.options.hideGui=true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if(System.nanoTime()-started>180_000_000_000L){Files.writeString(output().resolve("sea-tornado-failed.txt"),"Three-angle audit did not complete within 180 seconds");mc.stop();return;}
        if(mc.screen!=null)mc.setScreen(null);
        if(saving)return;
        if(phase>=3){
            var report=new LinkedHashMap<String,Object>();report.put("separate_audit_save",true);report.put("images",images);
            report.put("framebuffer_width",mc.getWindow().getWidth());report.put("framebuffer_height",mc.getWindow().getHeight());
            report.put("f8",true);report.put("storm",storm);report.put("column_height",SeaTornadoGeometry.height(storm));
            report.put("max_visible_storms",SeaTornadoGeometry.MAX_VISIBLE);
            report.put("cameras",cameras);
            Files.writeString(output().resolve("sea-tornado-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            mc.stop();return;
        }
        com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);
        if(!prepared){
            prepared=true;ready=false;ticks=0;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);
                var sea=com.tnc.tnc.world.SkyIslandWorlds.island(server);
                com.tnc.tnc.journey.JourneyData.get(server).player(player.getUUID()).putBoolean("Named",true);
                if(storm==null){
                    for(var g:SeaTornadoLayout.near(650,0)){
                        var at=BlockPos.containing(g.x(),48,g.z());
                        if(sea.getFluidState(at).is(SkyhavenContent.SOURCE)&&sea.getHeight(Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())<=51){storm=g;break;}
                    }
                    if(storm==null)throw new IllegalStateException("No genuine exposed abyssal sea center for audit");
                }
                player.setGameMode(GameType.SPECTATOR);sea.setWeatherParameters(12000,0,false,false);server.overworld().setDayTime(phase==1?18000:6000);
                if(phase==0)player.teleportTo(sea,storm.x()-116,177,storm.z()-116,-45,24);
                else if(phase==1)player.teleportTo(sea,storm.x()+116,177,storm.z()-116,45,24);
                else player.teleportTo(sea,storm.x(),74,storm.z()-105,0,-15);
                ready=true;
            });return;
        }
        if(!ready||!mc.level.dimension().equals(SkyhavenContent.KEY)||++ticks<240)return;
        String image="sea-tornado-"+new String[]{"day-side","night-diagonal","sea-level"}[phase]+".png";
        var camera=mc.gameRenderer.getMainCamera();
        cameras.add(Map.of("phase",phase,"position",List.of(camera.getPosition().x,camera.getPosition().y,camera.getPosition().z),"yaw",camera.getYRot(),"pitch",camera.getXRot(),"effects",SkyhavenSeaVision.auditSnapshot()));
        saving=true;images.add(image);
        Screenshot.grab(mc.gameDirectory,image,mc.getMainRenderTarget(),message->{
            try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+image),output().resolve(image),StandardCopyOption.REPLACE_EXISTING);}
            catch(Exception ex){throw new RuntimeException(ex);}finally{saving=false;}
        });
        phase++;prepared=false;
    }
}
