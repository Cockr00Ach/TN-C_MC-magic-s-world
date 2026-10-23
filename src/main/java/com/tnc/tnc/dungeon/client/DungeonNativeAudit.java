package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.dungeon.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated full-pack audit: real renderers, gate lifecycle and five-floor combat progression. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class DungeonNativeAudit {
    private static boolean started,captured,saving;private static volatile boolean ready;private static int stage,ticks,shots,startup;
    private static volatile DungeonData.Run run;private static volatile BlockPos camera;
    private static net.minecraft.world.entity.decoration.ArmorStand cameraRig;
    private static final List<String> failures=new ArrayList<>();private static final Map<String,Object> results=new LinkedHashMap<>();
    private static Path folder(){return Path.of(System.getProperty("tnc.dungeonAuditOutput")).toAbsolutePath();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
      if(Boolean.getBoolean("tnc.leylineAudit")){LeylineNativeAudit.tick(e);return;}
      if(Boolean.getBoolean("tnc.journeyAudit")){com.tnc.tnc.journey.client.JourneyNativeAudit.tick(e);return;}
      if(Boolean.getBoolean("tnc.dungeonUiAudit")){DungeonUiNativeAudit.tick(e);return;}
      if(!Boolean.getBoolean("tnc.dungeonAudit")||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
      if(mc.screen!=null&&mc.screen.getClass().getName().contains(".mca.")){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active&&Set.of("完成","接受","某处","下一步","Done","Next","Accept").contains(b.getMessage().getString())){b.onPress();break;}return;}
      if(!started&&++startup<100)return;if(!started){started=true;Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);return;}
      if(!ready||saving)return;if(stage>=13&&camera!=null){if(cameraRig==null){cameraRig=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,camera.getX()+.5,camera.getY(),camera.getZ()+.5);cameraRig.setNoGravity(true);cameraRig.setInvisible(true);cameraRig.setYRot(0);cameraRig.yRotO=0;cameraRig.setXRot(-12);cameraRig.xRotO=-12;cameraRig.xOld=cameraRig.xo=cameraRig.getX();cameraRig.yOld=cameraRig.yo=cameraRig.getY();cameraRig.zOld=cameraRig.zo=cameraRig.getZ();}mc.setCameraEntity(cameraRig);}if(camera!=null&&(mc.player.blockPosition().distSqr(camera)>16||!DungeonRealm.inside(mc.level))&&stage!=12&&stage!=11&&stage<13)return;ticks++;
      if(stage>=1&&stage<=4){mc.player.setYRot(135);mc.player.setXRot(25);}else if(stage>=5&&stage<=9){mc.player.setYRot(180);mc.player.setXRot(3);}else if(stage>=13){mc.player.setYRot(0);mc.player.setXRot(-12);}
      if(stage==11){if(ticks>6000){failures.add("Five-floor progression timed out");finish(mc);return;}return;}
      if(ticks==8&&stage!=0&&stage!=10)mc.setScreen(null);
      if(ticks==15&&(stage==0||stage==10))mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);DungeonNetwork.open(p,stage==0?"gate":"event");});
      if(ticks==(stage>=13?300:50)&&!captured){if(stage==9&&mc.level.getEntitiesOfClass(DungeonMob.class,mc.player.getBoundingBox().inflate(10)).stream().noneMatch(DungeonMob::visuallyAwake))failures.add("Mimic awake state did not sync");grab(mc,String.format(java.util.Locale.ROOT,"%02d-%s.png",stage,label()));captured=true;return;}
      if(ticks<(stage>=13?320:70))return;if(++stage==17){finish(mc);return;}ticks=0;captured=false;mc.setScreen(null);mc.setCameraEntity(mc.player);cameraRig=null;prepare(mc);
    }
    private static String label(){return stage==0?"entrance-panel":stage<=4?DungeonTheme.values()[stage-1].id+"-room":stage<=8?"monster-gallery-"+(stage-4):stage==9?"mimic-awake":stage==10?"wish-lamps-panel":stage==11?"five-floor-clear":stage==12?"failure-return":DungeonTheme.values()[stage-13].id+"-gate";}
    private static void prepare(Minecraft mc){ready=false;mc.getSingleplayerServer().execute(()->{try{
      var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.server.getLevel(DungeonRealm.KEY);if(level==null)throw new IllegalStateException("Dungeon dimension missing");p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setHealth(p.getMaxHealth());p.getInventory().clearContent();p.removeAllEffects();if(stage>=5&&stage<=9)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,20000,0,false,false));var d=DungeonData.get(p.server);
      if(stage==12){camera=null;ready=true;return;}
      if(stage>=13){var themeGate=DungeonTheme.values()[stage-13];var world=p.server.overworld();int gx=20000+(stage-13)*256;world.setDayTime(6000);p.teleportTo(world,gx+.5,190,20000.5,0,0);DungeonGateAssets.buildHere(p,themeGate);var a=DungeonGateAssets.get(themeGate);camera=new BlockPos(gx,200,19960);p.teleportTo(world,camera.getX()+.5,camera.getY(),camera.getZ()+.5,0,-12);results.put(themeGate.id+"_gate_pieces",a.pieces().size());ready=true;return;}
      if(run!=null)run.closed=true;var theme=stage>=1&&stage<=4?DungeonTheme.values()[stage-1]:DungeonTheme.ASTRAL;run=new DungeonData.Run(UUID.randomUUID(),p.getUUID(),177001+stage,20+stage,theme);var r=run;r.floor=stage==0?0:1;d.runs.put(r.id,r);var profile=d.profile(p.getUUID());profile.putUUID("Run",r.id);profile.putString("ReturnDimension","minecraft:overworld");profile.putDouble("ReturnX",1032.5);profile.putDouble("ReturnY",221);profile.putDouble("ReturnZ",1030.5);profile.putLong("Pending",0);profile.remove("CursorRun");
      if(stage==0){camera=DungeonRealm.lobby(r).offset(0,1,2);DungeonRealm.build(level,r,DungeonRealm.lobbyCells(r),()->{DungeonService.teleport(p,r,camera);ready=true;});return;}
      if(stage==10){while(r.graph().rooms.stream().noneMatch(a->a.kind()==DungeonGraph.Kind.EVENT&&DungeonNetwork.event(r,a)==1))r.seed++;}
      List<DungeonRealm.Cell> cells;
      if(stage>=5&&stage<=9){cells=new ArrayList<>();var o=DungeonRealm.base(r,1);for(int x=-16;x<=16;x++)for(int z=-8;z<=22;z++)cells.add(new DungeonRealm.Cell(o.offset(x,0,z),net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState()));camera=o.offset(0,3,14);}
      else{cells=DungeonRealm.floorCells(r);camera=DungeonRealm.center(r,r.graph().rooms.get(0)).offset(9,5,9);if(stage==10){var room=r.graph().rooms.stream().filter(a->a.kind()==DungeonGraph.Kind.EVENT&&DungeonNetwork.event(r,a)==1).findFirst().orElseThrow();camera=DungeonRealm.center(r,room).offset(0,1,3);}}
      DungeonRealm.build(level,r,cells,()->{r.state().built=true;r.state().visited=255;r.state().cleared=stage==11?0:255;
        if(stage>=5&&stage<=8){int from=(stage-5)*7;for(int i=from;i<Math.min(from+7,MobCatalog.ALL.size());i++){var s=MobCatalog.ALL.get(i);var mob=DungeonContent.MOBS.get(s.id()).create(level);var o=DungeonRealm.base(r,1);mob.configure(1,1,false);mob.setNoAi(true);mob.setPos(o.getX()+((i-from)%4-1.5)*3+.5,o.getY()+1,o.getZ()+.5-((i-from)/4)*5);mob.setYRot(0);mob.setYHeadRot(0);level.addFreshEntity(mob);}}
        if(stage==9){var mob=DungeonContent.MOBS.get("mimic_wood").create(level);var o=DungeonRealm.base(r,1);mob.configure(1,1,false);mob.setNoAi(true);mob.setPos(o.getX()+.5,o.getY()+1,o.getZ()+.5);level.addFreshEntity(mob);camera=o.offset(0,2,5);p.setGameMode(GameType.SURVIVAL);mob.setTarget(p);}
        if(stage==11){camera=DungeonRealm.center(r,r.graph().rooms.get(0)).above();p.setGameMode(GameType.SURVIVAL);p.invulnerableTime=100000;}
        DungeonService.teleport(p,r,camera);p.setYRot(stage>=5?180:135);p.setXRot(stage>=5?3:25);p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());ready=true;
      });d.setDirty();
    }catch(Throwable ex){failures.add(ex.toString());ready=true;}});}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){if(!Boolean.getBoolean("tnc.dungeonAudit")||e.phase!=TickEvent.Phase.END||stage!=11||!ready||run==null)return;var s=e.getServer();var p=s.getPlayerList().getPlayers().stream().findFirst().orElse(null);if(p==null||!DungeonRealm.inside(p.level())||run.building)return;var r=run;
      if(r.floor>=6){results.put("five_floors_completed",true);results.put("unlock",DungeonService.unlocked(p,r.theme));results.put("real_enemy_kills",shots);if(DungeonService.unlocked(p,r.theme)<6)failures.add("Guardian did not unlock next segment");if(!DungeonService.takeSoloDown(p)||DungeonService.takeSoloDown(p))failures.add("Solo chance is not once per segment");p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));var profile=DungeonData.get(s).profile(p.getUUID());long before=profile.getLong("Saved");profile.putLong("Pending",30);DungeonService.fail(p);results.put("failure_kept_inventory",p.getInventory().getItem(0).is(Items.DIAMOND)&&p.getInventory().getItem(0).getCount()==7);results.put("failure_lost_only_six_tokens",profile.getLong("Saved")==before+24);if(!Boolean.TRUE.equals(results.get("failure_kept_inventory"))||!Boolean.TRUE.equals(results.get("failure_lost_only_six_tokens")))failures.add("Failure ledger/inventory incorrect");stage=12;ticks=0;captured=false;camera=null;return;}
      if(s.getTickCount()%15!=0)return;var room=DungeonService.room(p);if(room==null)return;var f=r.state();if(!f.clear(room.id())){
        var enemies=f.enemies.getCompound(Integer.toString(room.id()));for(String id:new HashSet<>(enemies.getAllKeys())){var mob=p.serverLevel().getEntity(UUID.fromString(id));if(mob instanceof DungeonMob m&&m.isAlive()){m.hurt(p.damageSources().playerAttack(p),100000);shots++;}}return;
      }
      var graph=r.graph();int dest=graph.rooms.stream().filter(a->a.kind()==DungeonGraph.Kind.EXIT||a.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow().id();if(room.id()==dest){DungeonService.next(p);return;}var queue=new ArrayDeque<Integer>();var prev=new HashMap<Integer,Integer>();queue.add(room.id());prev.put(room.id(),-1);while(!queue.isEmpty()&&!prev.containsKey(dest)){int at=queue.removeFirst();for(int n:graph.rooms.get(at).links())if(!prev.containsKey(n)){prev.put(n,at);queue.add(n);}}int step=dest;while(prev.get(step)!=room.id())step=prev.get(step);DungeonService.teleport(p,r,DungeonRealm.center(r,graph.rooms.get(step)).above());
    }
    private static void grab(Minecraft mc,String name){saving=true;Screenshot.grab(mc.gameDirectory,"dungeon-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/dungeon-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){failures.add(ex.toString());}finally{saving=false;}});}
    private static void finish(Minecraft mc)throws Exception{results.put("failures",failures);results.put("families",10);results.put("variants",28);results.put("themes",4);results.put("scope","Isolated full PCL runtime; original rendering, real five-floor encounters and failure ledger");Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));mc.stop();}
}
