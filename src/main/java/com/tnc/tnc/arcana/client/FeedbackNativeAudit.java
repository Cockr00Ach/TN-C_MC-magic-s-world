package com.tnc.tnc.arcana.client;

import com.tnc.tnc.arcana.*;
import com.tnc.tnc.alchemy.*;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.production.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;

/** Opt-in native visual regression; runs only in the isolated audit instance. */
public final class FeedbackNativeAudit {
 private static final String[] NAMES={"empty-pot","water-pot","boiling-pot","sealed-pot","solar-corona","lunar-full","lunar-crescent","higher-spirit","formed-tower","forge-empty-book","portable-meals"};
 private static final BlockPos BASE=new BlockPos(22400,190,22400);
 private static int startup,scene,ticks;private static boolean started,saving,captured;private static volatile boolean ready;
 private static final Map<String,Object> checks=new LinkedHashMap<>();private static final List<String> errors=new ArrayList<>();
 private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput")).toAbsolutePath();}
 public static void tick(TickEvent.ClientTickEvent event)throws Exception{
  if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
  if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
  if(!started){if(++startup<100)return;started=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);return;}
  if(!ready||saving)return;ticks++;
  if(ticks==8){com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(scene>=2);if(scene!=9&&scene!=10)mc.setScreen(null);}
  if((scene==2||scene==3)&&ticks%10==0)mc.getSingleplayerServer().execute(()->{var pot=AlchemyBlock.master(mc.getSingleplayerServer().overworld(),BASE);if(pot!=null){pot.temperature=450;pot.changed();}});
  if(ticks==120&&!captured){
   if(scene==1)checks.put("water_present",mc.level.getBlockEntity(BASE) instanceof AlchemyEntity a&&a.water==3);
   if(scene==2)checks.put("real_magic_flame",mc.level.getBlockEntity(BASE) instanceof AlchemyEntity a&&a.fire==2&&a.hasIgnited);
   if(scene==3)checks.put("physical_lid_synced",mc.level.getBlockEntity(BASE) instanceof AlchemyEntity a&&a.sealed&&!a.inventory.getItem(6).isEmpty());
   if(scene>=4&&scene<=6)checks.put(NAMES[scene]+"_actual_yield",mc.level.getBlockEntity(BASE) instanceof RouteNodeEntity n&&n.getPersistentData().getInt("CelestialYield")>0);
   if(scene==7)checks.put("spirit_visible",ArcanaClient.spiritVisible());
   if(scene==8)checks.put("tower_formed",mc.level.getBlockEntity(BASE) instanceof RouteNodeEntity n&&n.getPersistentData().getBoolean("ShellFormed"));
   if(scene==9)checks.put("forge_screen",mc.screen instanceof com.tnc.tnc.production.client.MagicForgeScreen);
   if(scene==10)checks.put("meal_models",mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen);
   String name=String.format(Locale.ROOT,"%02d-%s.png",scene,NAMES[scene]);saving=true;captured=true;
   Screenshot.grab(mc.gameDirectory,"feedback-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/feedback-"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){errors.add(e.toString());}finally{saving=false;}});return;
  }
  if(ticks<145)return;mc.setScreen(null);if(++scene>=NAMES.length){var report=new LinkedHashMap<String,Object>();report.put("screenshots",NAMES.length);report.put("checks",checks);report.put("errors",errors);var hashes=new LinkedHashMap<String,String>();for(var c:List.of(AlchemyRendererClass(),DimensionalSpiritGeometry.class,CelestialCollectorVisual.class,ArcanaTowerVisual.class,MagicForgeBlockEntity.class,ArcanaMeals.class))try(var in=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")){hashes.put(c.getName(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}report.put("class_sha256",hashes);auditAssets(mc,report);Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();return;}ticks=0;captured=false;prepare(mc);
 }
 private static void auditAssets(Minecraft mc,Map<String,Object> report)throws Exception{
  var assets=new LinkedHashMap<String,String>();var names=new ArrayList<String>(List.of("block/spellflame_cauldron","block/spellflame_cauldron_lid"));for(var id:ArcanaMealItem.IDS)names.add("item/"+id);
  for(var name:names){var path="assets/tnc/models/"+name+".json";try(var in=FeedbackNativeAudit.class.getResourceAsStream("/"+path)){var bytes=in.readAllBytes();assets.put(path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));var json=com.google.gson.JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();for(var texture:json.getAsJsonObject("textures").entrySet()){var id=texture.getValue().getAsString();if(id.startsWith("#"))continue;var resource=net.minecraft.resources.ResourceLocation.parse(id);checks.put("texture_"+id,mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),"textures/"+resource.getPath()+".png")).isPresent());}}}
  report.put("asset_sha256",assets);var quests=new LinkedHashMap<String,String>();for(var name:List.of("tnc_alchemy","tnc_deep_arcana","tnc_field_04_workshop","tnc_magic_progression")){var bytes=Files.readAllBytes(mc.gameDirectory.toPath().resolve("config/ftbquests/quests/chapters/"+name+".snbt"));quests.put(name+".snbt",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));}report.put("quest_sha256",quests);
 }
 private static Class<?> AlchemyRendererClass(){return com.tnc.tnc.alchemy.client.AlchemyRenderer.class;}
 private static RouteNodeEntity node(ServerPlayer p,BlockPos at,RouteKind kind){p.serverLevel().setBlock(at,RouteContent.NODES.get(kind.id).get().defaultBlockState(),3);var n=(RouteNodeEntity)p.serverLevel().getBlockEntity(at);n.owner=p.getUUID();return n;}
 private static void prepare(Minecraft mc){ready=false;mc.getSingleplayerServer().execute(()->{try{
  var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var l=p.server.overworld();p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(l,BASE.getX()+1,BASE.getY()+1.7,BASE.getZ()+4.5,180,24);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,ItemStack.EMPTY);p.removeAllEffects();
  l.setWeatherParameters(10000,0,false,false);l.setDayTime(scene==4||scene<=3||scene==8?6000:scene==6?3*24000+18000:18000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());
  for(var e:l.getEntitiesOfClass(DimensionalSpirit.class,new AABB(BASE).inflate(40)))e.discard();
  ArcanaPhysiology.data(p).putDouble("vision_sanity",scene==7?90:0);ArcanaPhysiology.data(p).putLong("NextSpirit",Long.MAX_VALUE);com.tnc.tnc.mana.sight.ManaSightServer.subscribe(p,scene>=2);
  for(int x=-5;x<=7;x++)for(int z=-5;z<=7;z++){for(int y=0;y<10;y++)l.setBlock(BASE.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);l.setBlock(BASE.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);}
  for(int x:new int[]{-3,4})l.setBlock(BASE.offset(x,-1,2),Blocks.SEA_LANTERN.defaultBlockState(),3);
  if(scene==0||scene==1||scene==2)p.connection.teleport(BASE.getX()+1,BASE.getY()+2.6,BASE.getZ()+3.7,180,47);
  if(scene<=3){l.setBlock(BASE,AlchemyContent.CAULDRON.defaultBlockState(),3);((AlchemyBlock)AlchemyContent.CAULDRON).setPlacedBy(l,BASE,AlchemyContent.CAULDRON.defaultBlockState(),p,new ItemStack(AlchemyContent.CAULDRON));var pot=(AlchemyEntity)l.getBlockEntity(BASE);pot.owner=p.getUUID();if(scene>0)pot.addWater();if(scene>=2){for(var id:Elixir.SPRING.ingredients)pot.insert(new ItemStack(ArcanaContent.item(id)));pot.ignite(true);pot.temperature=450;pot.progress=50;pot.vortex_sync=30;}if(scene==3){p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ArcanaContent.item("sealing_lid")));pot.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);}pot.changed();}
  if(scene==0){var f=Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile").getField("INSTANCE").get(null);for(var id:Map.of("37B7FFAC33E9E92C",29,"30D9D5470D862905",23,"662EE66DA1916AAF",23).entrySet()){var chapter=f.getClass().getMethod("getChapter",long.class).invoke(f,Long.parseLong(id.getKey(),16));checks.put("ftb_"+id.getKey(),chapter!=null&&((List<?>)chapter.getClass().getMethod("getQuests").invoke(chapter)).size()==id.getValue());}}
  if(scene>=4&&scene<=6){node(p,BASE,scene==4?RouteKind.SOLAR:RouteKind.LUNAR);p.connection.teleport(BASE.getX()+.5,BASE.getY()+1.8,BASE.getZ()+8.5,180,-8);}
  if(scene==7){var spirit=ArcanaContent.SPIRIT.create(l);spirit.owner=p.getUUID();spirit.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5);spirit.setNoAi(true);l.addFreshEntity(spirit);p.connection.teleport(BASE.getX()+.5,BASE.getY()+.6,BASE.getZ()+6,180,-4);}
  if(scene==8){node(p,BASE,RouteKind.ABYSS_TOWER);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)if(x!=0||z!=0)l.setBlock(BASE.offset(x,y,z),ArcanaContent.TOWER_BRICK.defaultBlockState(),3);p.connection.teleport(BASE.getX()+4,BASE.getY()+2.8,BASE.getZ()+7,153,14);}
  if(scene==9){for(int i=0;i<27;i++)l.setBlock(ForgeStructure.at(BASE,Direction.SOUTH,i),ForgeStructure.blockFor(ForgeStructure.expected(i)).defaultBlockState(),3);var forge=(MagicForgeBlockEntity)l.getBlockEntity(BASE);forge.setOwner(p.getUUID());forge.formed();net.minecraftforge.network.NetworkHooks.openScreen(p,forge,b->b.writeBlockPos(BASE));}
  if(scene==10){var inv=new net.minecraft.world.SimpleContainer(27);for(int i=0;i<ArcanaMealItem.IDS.length;i++)inv.setItem(i,new ItemStack(ArcanaContent.item(ArcanaMealItem.IDS[i])));p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,pi,player)->net.minecraft.world.inventory.ChestMenu.threeRows(id,pi,inv),net.minecraft.network.chat.Component.literal("餐盘料理 · 可携带的八种成品")));}
 }catch(Throwable e){errors.add("Scene "+scene+": "+e);}finally{ready=true;}});}
 private FeedbackNativeAudit(){}
}
