package com.tnc.tnc.world.ink.client;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Opt-in native VBO audit, isolated from all formal worlds and disabled during normal play. */
final class InkDragonMeshAudit {
    private static InkCelestialDragonEntity dragon;
    private static int frames;private static boolean saving;private static volatile boolean saved;
    private static boolean worldStarted;private static volatile int worldEntityId=-1;private static volatile String worldFailure;
    private static final List<String> failures=new ArrayList<>();
    static void tick(Minecraft mc)throws Exception{
        if(Boolean.getBoolean("tnc.inkDragonWorldAudit")){world(mc);return;}
        if(dragon==null){
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(mc.level);dragon.setPos(0,0,0);
            dragon.setYRot(90);dragon.yRotO=90;dragon.yHeadRot=90;dragon.yHeadRotO=90;dragon.yBodyRot=90;
            mc.setScreen(new Gallery());
        }
        dragon.tickCount++;
        if(++frames<65)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"ink_celestial_dragon_native_v1.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){
            Map<String,Object> report=new LinkedHashMap<>(InkCelestialDragonRenderer.diagnostics());report.put("failures",failures);
            var shot=javax.imageio.ImageIO.read(Path.of("D:/ModTest/work/ink-crane-client-audit/screenshots/ink_celestial_dragon_native_v1.png").toFile());
            int dark=0;for(int y=100;y<Math.min(940,shot.getHeight());y++)for(int x=20;x<Math.min(1060,shot.getWidth());x++){
                int c=shot.getRGB(x,y);if(((c>>16)&255)<90&&((c>>8)&255)<90&&(c&255)<90)dark++;
            }
            report.put("visibleMeshPixels",dark);if(dark<10000)failures.add("Approved mesh did not visibly cover the expected gallery area");
            report.put("scope","Native entity renderer and shared GPU buffers; not a full-modpack FPS benchmark");
            Files.writeString(Path.of("D:/ModTest/work/ink-celestial-dragon-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static void world(Minecraft mc)throws Exception{
        if(!worldStarted){
            worldStarted=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(12);mc.options.hideGui=false;
            var server=mc.getSingleplayerServer();if(server==null)throw new IllegalStateException("Isolated integrated server required");
            UUID playerId=mc.player.getUUID();
            server.execute(()->{
                try{
                    var realm=server.getLevel(InkRealmTravel.INK_REALM);if(realm==null)throw new IllegalStateException("Ink realm missing");
                    realm.getChunkAt(new net.minecraft.core.BlockPos(16,0,16));
                    int base=realm.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
                    var player=server.getPlayerList().getPlayer(playerId);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    player.teleportTo(realm,40,base+14,40,135,6);
                    var e=InkBossRegistration.INK_CELESTIAL_DRAGON.create(realm);e.moveTo(16,base+2,16,90,0);e.yBodyRot=90;e.yHeadRot=90;
                    realm.addFreshEntity(e);worldEntityId=e.getId();
                }catch(Throwable failure){worldFailure=failure.toString();}
            });
            return;
        }
        if(worldFailure!=null)throw new IllegalStateException(worldFailure);
        if(!mc.level.dimension().equals(InkRealmTravel.INK_REALM)||worldEntityId<0||mc.level.getEntity(worldEntityId)==null)return;
        mc.setScreen(null);if(++frames<150)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"ink_celestial_dragon_world_v1.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){
            Map<String,Object> report=new LinkedHashMap<>(InkCelestialDragonRenderer.diagnostics());
            report.put("serverSpawnedAndClientTracked",true);report.put("dimension",mc.level.dimension().location().toString());
            report.put("entityId",worldEntityId);report.put("scope","Isolated native world view, not full-modpack FPS benchmarking");
            Files.writeString(Path.of("D:/ModTest/work/ink-celestial-dragon-world-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static final class Gallery extends Screen{
        Gallery(){super(Component.literal("Ink celestial dragon native mesh audit"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xFFE7E5DF);g.drawString(font,"INK CELESTIAL DRAGON / APPROVED CURVED MESH / NATIVE GPU VERTEX BUFFERS",24,20,0xFF222222,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int i=0;i<3;i++){
                int x=i==0?12:width*3/5,y=i==0?55:i==1?55:height/2+20;
                int w=i==0?width*3/5-24:width*2/5-12,h=i==0?height-95:height/2-60;
                g.fill(x,y,x+w,y+h,0xFFF1EFE9);
                g.drawString(font,new String[]{"FULL 140,414 TRIANGLE MESH / NO CUBES","SCULPTED HEAD / ANTLERS / WHISKERS","SIDE SILHOUETTE / ROUNDED COILS"}[i],x+18,y+14,0xFF333333,false);
                g.flush();g.enableScissor(x+2,y+36,x+w-2,y+h-2);g.pose().pushPose();
                try{
                    g.pose().translate(x+w*.54,i==0?y+h*.94:i==1?y+h*.54:y+h*.95,450);
                    float scale=i==0?38:i==1?48:18;g.pose().scale(scale,-scale,scale);
                    g.pose().mulPose(Axis.XP.rotationDegrees(i==1?6:10));g.pose().mulPose(Axis.YP.rotationDegrees(i==2?0:-20));
                    if(i==1)g.pose().translate(5.9,-16.5,0);
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    mc.getEntityRenderDispatcher().render(dragon,0,0,0,90,partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);buffers.endBatch();
                    int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0&&!failures.contains("GL error "+error))failures.add("GL error "+error);
                }catch(Throwable failure){if(!failures.contains(failure.toString()))failures.add(failure.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"Same approved geometry / hover + mane + beard motion / no combat moves configured yet",24,height-22,0xFF333333,false);
        }
    }
}
