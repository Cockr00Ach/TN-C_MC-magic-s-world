package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.world.skyhaven.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Isolated camera audit for the island, open air offshore, and the black sea. */
public final class SeaTornadoNativeAudit {
    private static int phase,ticks;
    private static boolean prepared,saving,configured;
    private static long started;
    private static volatile boolean ready;
    private static volatile SeaGyreLayout.Gyre storm;
    private static volatile BlockPos shore;
    private static volatile BlockPos standing;
    private static final String[] VIEWS={"edge-day","edge-night","island-interior","offshore-air","black-sea"};
    private static final List<String> images=new ArrayList<>();
    private static final List<Map<String,Object>> cameras=new ArrayList<>();
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    private SeaTornadoNativeAudit(){}
    public static void tick(Minecraft mc)throws Exception{
        if(!Boolean.getBoolean("tnc.skyhavenTornadoAudit")||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))
            throw new IllegalStateException("Separate prepared audit save required");
        if(!configured){
            configured=true;started=System.nanoTime();Files.createDirectories(output());mc.options.fov().set(70);mc.options.renderDistance().set(8);mc.options.hideGui=true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if(System.nanoTime()-started>180_000_000_000L){Files.writeString(output().resolve("sea-tornado-failed.txt"),"Sea tornado audit did not complete within 180 seconds");mc.stop();return;}
        if(mc.screen!=null)mc.setScreen(null);
        if(saving)return;
        if(phase>=VIEWS.length){
            var report=new LinkedHashMap<String,Object>();report.put("separate_audit_save",true);report.put("images",images);
            report.put("framebuffer_width",mc.getWindow().getWidth());report.put("framebuffer_height",mc.getWindow().getHeight());
            report.put("f8",true);report.put("storm",storm);report.put("column_height",SeaTornadoGeometry.height(storm));
            report.put("max_visible_storms",SeaTornadoGeometry.MAX_VISIBLE);
            report.put("shore",List.of(shore.getX(),shore.getY(),shore.getZ()));
            report.put("standing",List.of(standing.getX(),standing.getY(),standing.getZ()));
            report.put("clear_water_blocks",SeaStormCoast.CLEAR_WATER);report.put("fov",70);report.put("render_distance",8);
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
                if(shore==null){
                    shore=findShore(sea);int x=shore.getX(),z=shore.getZ();
                    standing=new BlockPos(x,sea.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                }
                if(storm==null){
                    for(var g:SeaTornadoLayout.near(shore.getX(),shore.getZ())){
                        if(!SeaStormCoast.offshore(g)||g.x()<shore.getX()+100)continue;
                        var at=BlockPos.containing(g.x(),48,g.z());
                        if(sea.getFluidState(at).is(SkyhavenContent.SOURCE)&&sea.getHeight(Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())<=51){storm=g;break;}
                    }
                    if(storm==null)throw new IllegalStateException("No genuine exposed abyssal sea center for audit");
                }
                player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=phase==3;player.onUpdateAbilities();
                player.setNoGravity(phase==4);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                sea.setWeatherParameters(12000,0,false,false);server.overworld().setDayTime(phase==1?18000:6000);
                if(phase<2)player.teleportTo(sea,standing.getX()+.5,standing.getY(),standing.getZ()+.5,-90,26);
                else if(phase==2){
                    int x=standing.getX()-24,z=standing.getZ();
                    player.teleportTo(sea,x+.5,sea.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z+.5,-90,26);
                }else if(phase==3){
                    player.teleportTo(sea,SeaStormCoast.LAND_MAX+24,110,standing.getZ()+.5,-90,13);
                }else{
                    int x=standing.getX()-16,z=standing.getZ();
                    var water=new BlockPos(x,46,z);
                    if(!sea.getFluidState(water).is(SkyhavenContent.SOURCE))
                        throw new IllegalStateException("Black sea audit requires a submerged position below the island");
                    player.teleportTo(sea,x+.5,46,z+.5,-90,-12);
                }
                ready=true;
            });return;
        }
        if(!ready||!mc.level.dimension().equals(SkyhavenContent.KEY)||++ticks<240)return;
        String image="sea-tornado-"+VIEWS[phase]+".png";
        var camera=mc.gameRenderer.getMainCamera();
        cameras.add(Map.of("phase",phase,"position",List.of(camera.getPosition().x,camera.getPosition().y,camera.getPosition().z),"yaw",camera.getYRot(),"pitch",camera.getXRot(),"grounded",mc.player.onGround(),"flying",mc.player.getAbilities().flying,
                "in_black_sea",mc.player.isInFluidType(SkyhavenContent.TYPE),"effects",SkyhavenSeaVision.auditSnapshot()));
        saving=true;images.add(image);
        Screenshot.grab(mc.gameDirectory,image,mc.getMainRenderTarget(),message->{
            try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+image),output().resolve(image),StandardCopyOption.REPLACE_EXISTING);}
            catch(Exception ex){throw new RuntimeException(ex);}finally{saving=false;}
        });
        phase++;prepared=false;
    }
    private static BlockPos findShore(net.minecraft.server.level.ServerLevel sea){
        BlockPos best=null;
        for(int z=-96;z<=96;z+=8)for(int x=270;x>=120;x-=2){
            // Level.getHeight returns the minimum height for absent chunks. This
            // explicit preparation is only inside the guarded isolated audit;
            // the player's sea renderer never requests a chunk.
            sea.getChunkAt(new BlockPos(x,0,z));
            int y=sea.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            if(y<160||y>195)continue;
            sea.getChunkAt(new BlockPos(x+4,0,z));
            if(sea.getHeight(Heightmap.Types.WORLD_SURFACE,x+4,z)>61)continue;
            var floor=new BlockPos(x,y-1,z);var state=sea.getBlockState(floor);
            if(!state.getFluidState().isEmpty()||state.getCollisionShape(sea,floor).isEmpty())continue;
            if(best==null||x>best.getX()||(x==best.getX()&&Math.abs(z)<Math.abs(best.getZ())))best=new BlockPos(x,y,z);
            break;
        }
        if(best==null)throw new IllegalStateException("No genuine walkable east island shore in the isolated authored save");
        return best;
    }
}
