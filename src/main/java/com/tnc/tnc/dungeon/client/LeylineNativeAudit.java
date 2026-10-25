package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.dungeon.leyline.*;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;

/** Explicitly enabled only in a disposable full-pack client. Screenshots use original renderers. */
public final class LeylineNativeAudit {
 private static int scene,ticks,startup;
 private static boolean started,saving;
 private static volatile boolean ready;
 private static volatile BlockPos camera;
 private static volatile float viewPitch=19;
 private static volatile CompoundTag projection;
 private static DungeonData.Run run;
 private static final List<String> failures=new ArrayList<>(),shots=new ArrayList<>();
 private static Path folder(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
 public static void tick(TickEvent.ClientTickEvent event)throws Exception {
  if(event.phase!=TickEvent.Phase.END)return;
  var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
  if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
  if(mc.screen!=null&&mc.screen.getClass().getName().contains(".mca.")){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active&&Set.of("完成","接受","某处","下一步","Done","Next","Accept").contains(b.getMessage().getString())){b.onPress();break;}return;}
  if(!started&&++startup<100)return;
  if(!started){started=true;Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.framerateLimit().set(60);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.options.renderDistance().set(5);mc.options.hideGui=true;prepare(mc);return;}
  if(!ready||saving)return;
  if(scene>=14){if(!DungeonRealm.inside(mc.level)||mc.player.blockPosition().distSqr(camera)>16)return;mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(viewPitch);if(projection!=null)LeylineClient.accept(projection);}
  if(++ticks==100){saving=true;String name=label()+".png";Screenshot.grab(mc.gameDirectory,"leyline-"+name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/leyline-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);shots.add(name);}catch(Exception ex){failures.add(ex.toString());}finally{saving=false;}});return;}
  if(ticks<115)return;
  if(++scene>=60){Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("jar_sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mc.gameDirectory.toPath().resolve("mods/tnc-1.0.0.jar")))),"screenshots",shots,"failures",failures,"scope","Real full-pack model and room renderers. Poses and room projection are visual fixtures, not interaction-test evidence.")));WorldFeedbackNativeAudit.finished(mc);return;}
  ticks=0;prepare(mc);
 }
 private static String label(){if(scene>=10&&scene<14)return "ecology-"+(scene-10);return scene<10?String.format("model-%02d-%s",scene,MobCatalog.Family.values()[scene].name().toLowerCase(Locale.ROOT)):String.format("room-%02d-%s-%s",(scene-14)/2,LeylineKind.values()[1+(scene-14)/2].name().toLowerCase(Locale.ROOT),scene%2==1?"f8":"normal");}
 private static void prepare(Minecraft mc){
  ready=false;
  if(scene<10){ManaSightClient.setEnabled(false);mc.setScreen(new Gallery(MobCatalog.Family.values()[scene]));ready=true;return;}
  if(scene<14){ManaSightClient.setEnabled(scene==11||scene==13);mc.setScreen(new EcologyGallery(scene-10));ready=true;return;}
  ManaSightClient.setEnabled(scene%2==1);mc.setScreen(null);
  if(scene%2==1){ready=true;return;}
  mc.getSingleplayerServer().execute(()->{try{
   var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var level=server.getLevel(DungeonRealm.KEY);
   if(scene==14){var file=Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile").getField("INSTANCE").get(null);var chapter=file.getClass().getMethod("getChapter",long.class).invoke(file,Long.parseLong("2F9112CBB07DED36",16));if(chapter==null||((List<?>)chapter.getClass().getMethod("getQuests").invoke(chapter)).size()!=32)throw new IllegalStateException("The 32-quest leyline chapter did not load in real FTB Quests");}
   if(run!=null)run.closed=true;
   var kind=LeylineKind.values()[1+(scene-14)/2];var theme=Arrays.stream(DungeonTheme.values()).filter(t->LeylineDeck.pool(t.id,12).contains(kind)).findFirst().orElse(DungeonTheme.ASTRAL);
   run=new DungeonData.Run(UUID.randomUUID(),p.getUUID(),42427,600+(scene-14)/2,theme);var r=run;r.floor=12;
   var data=DungeonData.get(server);data.runs.put(r.id,r);data.profile(p.getUUID()).remove("Run");
   var room=r.graph().rooms.get(1);var o=DungeonRealm.center(r,room);var state=LeylineService.state(r,room);state.putString("Kind",kind.name());
   var cells=new ArrayList<DungeonRealm.Cell>();
   for(int x=-11;x<=11;x++)for(int z=-11;z<=11;z++){cells.add(new DungeonRealm.Cell(o.offset(x,0,z),theme.floor()));cells.add(new DungeonRealm.Cell(o.offset(x,10,z),theme.wall()));if(Math.abs(x)==11||Math.abs(z)==11)for(int y=1;y<10;y++)cells.add(new DungeonRealm.Cell(o.offset(x,y,z),theme.wall()));}
   LeylineRooms.build(cells,r,room,kind,state);boolean corridor=kind==LeylineKind.BRIDGE||kind==LeylineKind.DUAL_BRIDGE||kind==LeylineKind.ECHO||kind==LeylineKind.TIDE;camera=o.offset(0,corridor?2:5,-9);viewPitch=corridor?10:19;
   p.setGameMode(GameType.SPECTATOR);p.removeAllEffects();p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,20000,0,false,false));
   p.teleportTo(level,camera.getX()+.5,camera.getY(),camera.getZ()+.5,0,viewPitch);
   DungeonRealm.build(level,r,cells,()->{LeylinePuzzles.tick(level,r,room,List.of());projection=new CompoundTag();projection.putString("Kind",kind.name());projection.putLong("Origin",o.asLong());projection.put("State",state.copy());r.closed=true;ready=true;});
  }catch(Throwable error){failures.add(error.toString());ready=true;}});
 }
 private static final class Gallery extends Screen {
  private final MobCatalog.Family family;
  private final List<DungeonMob> mobs=new ArrayList<>();
  Gallery(MobCatalog.Family family){super(Component.literal(family.name()));this.family=family;var level=Minecraft.getInstance().level;for(var species:MobCatalog.ALL)if(species.family()==family){var mob=DungeonContent.MOBS.get(species.id()).create(level);mob.configure(1,1,false);mob.setNoAi(true);mobs.add(mob);}}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){
   g.fill(0,0,width,height,0xff121a20);g.drawCenteredString(font,"TN-C · "+family.name()+" · 原生模型检查",width/2,20,0xf4ddae);
   int panel=width/mobs.size();
   for(int i=0;i<mobs.size();i++){var mob=mobs.get(i);mob.tickCount=ticks;int x=i*panel;g.fill(x+6,49,x+panel-6,height-32,0xff243139);int scale=Math.min(94,(int)((panel-28)/Math.max(2.8,mob.getBbWidth()*2.7)));InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+panel/2,height-76,scale,-30,-8,mob);g.drawCenteredString(font,mob.species().id(),x+panel/2,height-52,0xd6dfdf);}
  }
 }
 private static final class EcologyGallery extends Screen {
  final List<com.tnc.tnc.dungeon.ecology.EcologyMob> mobs=new ArrayList<>();
  EcologyGallery(int mode){super(Component.literal("生态魔物"));for(String key:com.tnc.tnc.dungeon.ecology.EcologyContent.KINDS){var mob=com.tnc.tnc.dungeon.ecology.EcologyContent.TYPES.get(key).create(Minecraft.getInstance().level);var t=new CompoundTag();mob.saveWithoutId(t);if(mode==2&&(key.equals("crab")||key.equals("flash")))t.putInt("EcologyAction",key.equals("crab")?1:16);if(mode==3)t.putBoolean("EcologySmall",true);mob.load(t);mob.setNoAi(true);mobs.add(mob);}}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int x,int y,float dt){g.fill(0,0,width,height,0xff121a20);g.drawCenteredString(font,"TN-C · 四种魔脉生态 · "+(ManaSightClient.enabled()?"F8":"普通视界"),width/2,20,0xf4ddae);int panel=width/4;for(int i=0;i<4;i++){var mob=mobs.get(i);mob.tickCount=ticks;g.fill(i*panel+6,49,(i+1)*panel-6,height-32,0xff243139);InventoryScreen.renderEntityInInventoryFollowsMouse(g,i*panel+panel/2,height-76,65,-30,-8,mob);g.drawCenteredString(font,mob.species().name(),i*panel+panel/2,height-52,0xd6dfdf);}}
 }
 private LeylineNativeAudit(){}
}
