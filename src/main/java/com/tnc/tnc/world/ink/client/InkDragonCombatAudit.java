package com.tnc.tnc.world.ink.client;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Live synchronized server/client audit, not a staged gallery or a formal player save. */
final class InkDragonCombatAudit {
    private static boolean setup,second,burstCamera;private static volatile int id=-1,pigId=-1;private static volatile String failure;
    private static long start;private static int captures,clientTicks;private static volatile int saved;
    private static final List<Object> samples=new ArrayList<>();
    static void tick(Minecraft mc)throws Exception{
        if(!setup){
            setup=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(12);
            mc.getWindow().setWidth(1280);mc.getWindow().setHeight(900);mc.resizeDisplay();
            UUID player=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{try{
                var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);
                var old=new ArrayList<Entity>();realm.getAllEntities().forEach(old::add);
                for(var e:old)if(e instanceof InkCelestialDragonEntity||e.getType()==EntityType.PIG)e.discard();
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)realm.getChunkAt(new BlockPos(x*16,0,z*16));
                int ground=realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
                var camera=mc.getSingleplayerServer().getPlayerList().getPlayer(player);camera.setGameMode(GameType.SPECTATOR);camera.teleportTo(realm,-30,ground+82,72,-150,14);
                var dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(realm);dragon.moveTo(16,ground+30,16,90,0);dragon.yBodyRot=90;dragon.yHeadRot=90;dragon.setNoAi(true);realm.addFreshEntity(dragon);
                var pig=EntityType.PIG.create(realm);pig.moveTo(-55,ground+1,16,0,0);pig.setNoAi(true);pig.setNoGravity(true);pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);realm.addFreshEntity(pig);
                id=dragon.getId();pigId=pig.getId();dragon.startInkAttack(1,pig);start=realm.getGameTime();
            }catch(Throwable e){failure=e.toString();}});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(++clientTicks>600)throw new IllegalStateException("Combat audit timed out: captures="+captures);
        if(!(mc.level.getEntity(id) instanceof InkCelestialDragonEntity dragon))return;
        mc.setScreen(null);
        mc.getSingleplayerServer().execute(()->{try{
            var realm=mc.getSingleplayerServer().getLevel(InkRealmTravel.INK_REALM);var e=(InkCelestialDragonEntity)realm.getEntity(id);var p=(net.minecraft.world.entity.LivingEntity)realm.getEntity(pigId);
            int ground=realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,16,16);
            var camera=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(!second&&realm.getGameTime()-start>=132){second=true;camera.teleportTo(realm,112,ground+135,130,140,6);if(!e.startInkAttack(2,p))throw new IllegalStateException("Server dive did not start");}
            if(second&&!burstCamera&&e.moveAge()>=114){burstCamera=true;camera.teleportTo(realm,70,ground+45,110,127,13);}
        }catch(Throwable e){failure=e.toString();}});
        int move=dragon.inkMove(),age=dragon.moveAge();
        if(captures==0&&move==1&&age>=52&&age<90||captures==1&&move==2&&age>=90&&age<109||captures==2&&move==2&&age>=119){
            String[] names={"ink_dragon_live_breath_v2.png","ink_dragon_live_dive_v2.png","ink_dragon_live_burst_v2.png"};
            samples.add(Map.of("move",move,"age",age,"jaw",dragon.jawOpen(0),"pitch",dragon.breathPitch(),"impact",dragon.breathHit(),"serverPoint",dragon.effectVector("point").toString()));
            Screenshot.grab(mc.gameDirectory,names[captures++],mc.getMainRenderTarget(),m->saved++);
        }
        if(captures==3&&saved==3){
            var report=new LinkedHashMap<String,Object>(InkCelestialDragonRenderer.diagnostics());report.put("samples",samples);report.put("failure",failure);
            report.put("nativeGlError",org.lwjgl.opengl.GL11.glGetError());report.put("scope","Actual dedicated game logic on integrated server, synchronized client model and spell effects");
            if(!Boolean.TRUE.equals(report.get("continuousWaveShader")))throw new IllegalStateException("Wave shader missing");
            Files.writeString(Path.of("D:/ModTest/work/ink-dragon-live-combat-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
}
